package tech.cameia.entrevista.domain.model;

/**
 * Resultado de aplicar un mensaje de Perfil a las copias locales.
 */
public enum ReplicationOutcome {

    /** El mensaje cambió al menos una copia. */
    APPLIED,

    /** El mensaje era anterior a lo guardado: se anotó como procesado y no cambió nada. */
    STALE_IGNORED,

    /** El mensaje ya se había aplicado antes: no tuvo efecto. */
    DUPLICATE_IGNORED,

    /** El mensaje pretendía cambiar el dueño de un perfil ya copiado: no se escribió nada. */
    OWNER_MISMATCH
}
