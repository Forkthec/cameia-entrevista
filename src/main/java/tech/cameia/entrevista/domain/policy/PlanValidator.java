package tech.cameia.entrevista.domain.policy;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import tech.cameia.entrevista.domain.exception.LlmInvalidOutputException;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.InterviewQuestion;
import tech.cameia.entrevista.domain.model.PlanDraft;
import tech.cameia.entrevista.domain.model.PlannedQuestion;
import tech.cameia.entrevista.domain.model.QuestionType;

/**
 * Valida el plan que propone el modelo. La salida del modelo no es de confianza: o cumple todas las reglas o se
 * descarta entera.
 *
 * <p>Reglas: el idioma declarado es el de la sesión; hay exactamente cinco preguntas; la posición de cada una es
 * su lugar en la lista; ninguna está vacía ni supera {@value InterviewQuestion#MAX_TEXT_LENGTH} puntos de código
 * (tras normalizar a NFC); su tipo, pasado a mayúsculas, es el que la plantilla asigna a su posición; y no hay dos
 * preguntas iguales (sin contar espacios sobrantes ni mayúsculas). Las razones de rechazo no incluyen texto.</p>
 */
public final class PlanValidator {

    /**
     * Valida el plan.
     *
     * @param sessionLanguage idioma de la sesión
     * @param plan plan leído de la salida del modelo
     * @return las cinco preguntas, con el tipo en mayúsculas y el texto sin espacios en los extremos
     * @throws LlmInvalidOutputException si alguna regla se incumple
     */
    public List<PlannedQuestion> validate(InterviewLanguage sessionLanguage, PlanDraft plan) {
        if (plan == null || plan.questions() == null) {
            throw invalid("plan ausente");
        }
        if (!sessionLanguage.code().equals(plan.language())) {
            throw invalid("idioma distinto del de la sesión");
        }
        if (plan.questions().size() != InterviewQuestion.TOTAL) {
            throw invalid("cantidad de preguntas distinta de " + InterviewQuestion.TOTAL);
        }
        List<PlannedQuestion> valid = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < plan.questions().size(); index++) {
            PlannedQuestion question = plan.questions().get(index);
            int position = index + 1;
            if (question == null || question.position() != position) {
                throw invalid("posición " + position + " fuera de lugar");
            }
            String text = question.text() == null ? "" : Normalizer.normalize(question.text(), Normalizer.Form.NFC)
                    .strip();
            if (text.isEmpty()) {
                throw invalid("pregunta " + position + " vacía");
            }
            if (text.codePointCount(0, text.length()) > InterviewQuestion.MAX_TEXT_LENGTH) {
                throw invalid("pregunta " + position + " demasiado larga");
            }
            // Un carácter de control (NUL, salto de línea, tabulación) es JSON válido, pero la base de datos lo
            // rechazaría al guardar; se descarta aquí como salida inválida del modelo.
            if (text.chars().anyMatch(Character::isISOControl)) {
                throw invalid("pregunta " + position + " con caracteres de control");
            }
            String type = question.type() == null ? "" : question.type().toUpperCase(Locale.ROOT);
            if (!type.equals(expectedType(position).code())) {
                throw invalid("tipo de la pregunta " + position + " distinto del de la plantilla");
            }
            if (!seen.add(text.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT))) {
                throw invalid("pregunta " + position + " repetida");
            }
            valid.add(new PlannedQuestion(position, type, text));
        }
        return valid;
    }

    private static QuestionType expectedType(int position) {
        return CompetencyTemplate.typeAt(position);
    }

    private static LlmInvalidOutputException invalid(String reason) {
        return new LlmInvalidOutputException(reason);
    }
}
