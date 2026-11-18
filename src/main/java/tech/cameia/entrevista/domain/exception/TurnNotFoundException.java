package tech.cameia.entrevista.domain.exception;

/**
 * El turno no existe, no es de la sesión o su identificador no es un UUID; no se distingue para no revelar su existencia.
 */
public class TurnNotFoundException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "No encontramos lo que buscabas.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public TurnNotFoundException() {
        super(ErrorCode.TURN_NOT_FOUND, MESSAGE);
    }
}
