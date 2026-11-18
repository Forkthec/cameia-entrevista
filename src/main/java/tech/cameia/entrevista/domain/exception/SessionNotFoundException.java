package tech.cameia.entrevista.domain.exception;

/**
 * La sesión pedida no existe, es de otra persona o su identificador no es un UUID.
 *
 * <p>Los tres casos responden igual para no revelar si una sesión ajena existe.</p>
 */
public class SessionNotFoundException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "No encontramos lo que buscabas.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public SessionNotFoundException() {
        super(ErrorCode.SESSION_NOT_FOUND, MESSAGE);
    }
}
