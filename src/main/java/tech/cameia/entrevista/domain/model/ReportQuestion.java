package tech.cameia.entrevista.domain.model;

import java.util.Objects;

import tech.cameia.entrevista.domain.model.AnswerAttempt.AnswerOrigin;

/**
 * Una pregunta de la sesión con su respuesta definitiva: entrada del reporte y fila de su detalle.
 *
 * @param number número de la pregunta, de 1 a 5
 * @param type tipo de la pregunta
 * @param questionText texto de la pregunta
 * @param answerText texto de la respuesta definitiva
 * @param origin si la respuesta se escribió o se dijo por voz
 * @param voice datos de voz de la respuesta; solo si fue por voz y se pudieron calcular, si no {@code null}
 */
public record ReportQuestion(int number, QuestionType type, String questionText, String answerText,
        AnswerOrigin origin, VoiceSample voice) {

    /** Valida el número, exige los textos y no admite datos de voz en una respuesta escrita. */
    public ReportQuestion {
        QuestionNumbers.require(number);
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(questionText, "questionText");
        Objects.requireNonNull(answerText, "answerText");
        Objects.requireNonNull(origin, "origin");
        if (voice != null && origin != AnswerOrigin.AUDIO) {
            throw new IllegalArgumentException("Una respuesta escrita no tiene datos de voz");
        }
    }

    @Override
    public String toString() {
        return "ReportQuestion[number=" + number + ", type=" + type + ", origin=" + origin + ", voice="
                + (voice != null) + "]";
    }
}
