package tech.cameia.entrevista.domain.model;

/**
 * Estado de un rol profesional del catálogo, tal como lo publica Perfil. Perfil nunca borra un rol:
 * lo desactiva.
 */
public enum RoleStatus {

    /** El rol puede ser el rol objetivo de una sesión nueva. */
    ACTIVE,

    /** El rol sigue en el catálogo pero ya no se ofrece. */
    INACTIVE
}
