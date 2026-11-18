package tech.cameia.entrevista.domain.exception;

/**
 * Se pidió el reporte de una entrevista que todavía no terminó: está configurada, en curso o evaluándose.
 */
public class SessionNotFinishedException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Tu entrevista aún no ha terminado.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public SessionNotFinishedException() {
        super(ErrorCode.SESSION_NOT_FINISHED, MESSAGE);
    }
}
