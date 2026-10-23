package tech.cameia.entrevista.domain.exception;

/**
 * El perfil pedido no existe o pertenece a otra persona; las dos causas responden igual para no revelar su existencia.
 */
public class ProfileNotFoundException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "No encontramos lo que buscabas.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public ProfileNotFoundException() {
        super(ErrorCode.PROFILE_NOT_FOUND, MESSAGE);
    }
}
