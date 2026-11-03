package tech.cameia.entrevista.domain.exception;

import java.util.List;

import tech.cameia.entrevista.domain.model.FieldRejection;

/**
 * La configuración enviada incumple una o varias reglas. Lleva todas las causas a la vez, una por campo,
 * para que el formulario las muestre juntas.
 */
public class InvalidRequestException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Revisa los campos marcados.";

    private final List<FieldRejection> rejections;

    /**
     * Crea la excepción con las causas encontradas.
     *
     * @param rejections un rechazo por campo, en el orden del formulario; se copia
     */
    public InvalidRequestException(List<FieldRejection> rejections) {
        super(ErrorCode.VALIDATION_FAILED, MESSAGE);
        this.rejections = List.copyOf(rejections);
    }

    /** @return los campos rechazados; lista inmodificable */
    public List<FieldRejection> rejections() {
        return rejections;
    }
}
