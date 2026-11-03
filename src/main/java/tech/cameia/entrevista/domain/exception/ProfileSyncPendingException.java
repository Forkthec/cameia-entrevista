package tech.cameia.entrevista.domain.exception;

/**
 * La copia local del perfil todavía no llegó o no está lista para crear una sesión.
 */
public class ProfileSyncPendingException extends BusinessException {

    /** Mensaje que ve la persona. */
    public static final String MESSAGE = "Tu perfil se está actualizando. Inténtalo en unos segundos.";

    /** Crea la excepción con su código y su mensaje fijos. */
    public ProfileSyncPendingException() {
        super(ErrorCode.PROFILE_SYNC_PENDING, MESSAGE);
    }
}
