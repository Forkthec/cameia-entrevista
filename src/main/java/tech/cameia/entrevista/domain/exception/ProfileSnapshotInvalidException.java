package tech.cameia.entrevista.domain.exception;

/**
 * La copia del perfil que la sesión congeló al crearse no sirve para preparar las preguntas: es demasiado grande,
 * no es un JSON legible o no trae el rol de la sesión.
 *
 * <p>Reintentar no lo arregla mientras la persona no cambie su perfil, así que no gasta ningún intento de
 * planificación y no llama al modelo. La causa técnica (tamaño, rol ausente) viaja como {@link #getCause()} y solo
 * llega al log, nunca con el texto del perfil.</p>
 */
public class ProfileSnapshotInvalidException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE =
            "No pudimos preparar tu entrevista con tu perfil actual. Revisa tu perfil e inténtalo de nuevo.";

    /**
     * Crea la excepción conservando la causa técnica.
     *
     * @param cause motivo para el log, sin texto del perfil
     */
    public ProfileSnapshotInvalidException(Throwable cause) {
        super(ErrorCode.PROFILE_SNAPSHOT_INVALID, MESSAGE);
        initCause(cause);
    }
}
