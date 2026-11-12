package tech.cameia.entrevista.domain.model;

import java.util.Set;

import tech.cameia.entrevista.domain.exception.LlmInvalidOutputException;

/**
 * Evaluación que el modelo hizo de una respuesta, ya validada. La pregunta de seguimiento que el modelo
 * pueda devolver no se conserva.
 *
 * @param action {@code AVANZAR} o {@code FOLLOW_UP}
 * @param classification {@code COMPLETA}, {@code VAGA}, {@code INCOMPLETA}, {@code IRRELEVANTE} o {@code EVASIVA}
 * @param reason motivo de la clasificación, de 1 a {@value #MAX_TEXT_LENGTH} caracteres
 * @param feedback retroalimentación para la persona, de 1 a {@value #MAX_TEXT_LENGTH} caracteres
 */
public record EvaluationResult(String action, String classification, String reason, String feedback) {

    /** Largo máximo de {@code reason} y {@code feedback}, en puntos de código. */
    public static final int MAX_TEXT_LENGTH = 4000;

    /** Acciones que el modelo puede decidir. */
    public static final Set<String> ACTIONS = Set.of("AVANZAR", "FOLLOW_UP");

    /** Clasificaciones que el modelo puede dar. */
    public static final Set<String> CLASSIFICATIONS =
            Set.of("COMPLETA", "VAGA", "INCOMPLETA", "IRRELEVANTE", "EVASIVA");

    /**
     * Valida la salida del modelo.
     *
     * @throws LlmInvalidOutputException si un valor no está en su conjunto o un texto falta o es demasiado largo
     */
    public EvaluationResult {
        if (action == null || !ACTIONS.contains(action)) {
            throw new LlmInvalidOutputException("evaluación con acción no admitida");
        }
        if (classification == null || !CLASSIFICATIONS.contains(classification)) {
            throw new LlmInvalidOutputException("evaluación con clasificación no admitida");
        }
        requireText(reason, "motivo");
        requireText(feedback, "retroalimentación");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > MAX_TEXT_LENGTH) {
            throw new LlmInvalidOutputException("evaluación con " + name + " ausente o demasiado largo");
        }
    }

    /** @return descripción sin los textos, que no deben llegar a un registro */
    @Override
    public String toString() {
        return "EvaluationResult[action=" + action + ", classification=" + classification + "]";
    }
}
