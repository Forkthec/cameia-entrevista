package tech.cameia.entrevista.domain.policy;

import java.util.List;

import tech.cameia.entrevista.domain.model.QuestionType;

/**
 * Plantilla de tipos de pregunta por posición: apertura, técnica, conductual, técnica y situacional. Es una
 * constante del código y no depende de la sesión.
 */
public final class CompetencyTemplate {

    /** Tipo de cada posición, de la 1 a la 5. */
    public static final List<QuestionType> TYPES = List.of(QuestionType.APERTURA, QuestionType.TECNICA,
            QuestionType.CONDUCTUAL, QuestionType.TECNICA, QuestionType.SITUACIONAL);

    private CompetencyTemplate() {
    }

    /**
     * Tipo que la plantilla asigna a una posición.
     *
     * @param position posición de 1 a 5
     * @return el tipo de esa posición
     * @throws IndexOutOfBoundsException si la posición está fuera de 1 a 5
     */
    public static QuestionType typeAt(int position) {
        return TYPES.get(position - 1);
    }
}
