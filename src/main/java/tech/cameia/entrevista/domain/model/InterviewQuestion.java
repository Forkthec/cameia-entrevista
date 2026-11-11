package tech.cameia.entrevista.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Pregunta de una sesión: una de las cinco que prepara el modelo. Su texto, tipo y posición no cambian una
 * vez la sesión sale de {@code CONFIGURADA}.
 *
 * @param id identificador de la pregunta
 * @param sessionId sesión a la que pertenece
 * @param position posición en la entrevista, de 1 a {@value #TOTAL}
 * @param type tipo de la pregunta
 * @param originalText texto tal como lo escribió el modelo, nunca reformulado
 * @param executionId ejecución de la IA que la produjo
 * @param createdAt fecha de creación
 */
public record InterviewQuestion(UUID id, UUID sessionId, int position, QuestionType type, String originalText,
        UUID executionId, Instant createdAt) {

    /** Cantidad de preguntas de toda entrevista. */
    public static final int TOTAL = 5;

    /** Largo máximo del texto de una pregunta, en puntos de código; es el de la columna. */
    public static final int MAX_TEXT_LENGTH = 500;

    /**
     * Exige todos los datos y una posición de 1 a {@value #TOTAL}.
     *
     * @throws IllegalArgumentException si la posición está fuera de rango
     */
    public InterviewQuestion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(originalText, "originalText");
        Objects.requireNonNull(executionId, "executionId");
        Objects.requireNonNull(createdAt, "createdAt");
        if (position < 1 || position > TOTAL) {
            throw new IllegalArgumentException("La posición de la pregunta debe estar entre 1 y " + TOTAL);
        }
    }
}
