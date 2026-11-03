package tech.cameia.entrevista.domain.port;

import java.util.UUID;

/**
 * Consulta del catálogo de roles profesionales, que el servicio guarda como copia local del de Perfil.
 */
public interface RoleCatalog {

    /**
     * Indica si un rol existe en la copia local y está activo.
     *
     * @param roleId identificador del rol
     * @return {@code true} solo si el rol está en el catálogo y activo
     */
    boolean isActive(UUID roleId);
}
