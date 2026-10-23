package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import tech.cameia.entrevista.domain.model.RoleStatus;
import tech.cameia.entrevista.domain.port.RoleCatalog;

/**
 * Adaptador de lectura del catálogo de roles sobre la copia local {@code rol_profesional}.
 */
@Repository
class RoleCatalogAdapter implements RoleCatalog {

    private final ProfessionalRoleJpaRepository jpa;

    RoleCatalogAdapter(ProfessionalRoleJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(UUID roleId) {
        return jpa.existsByIdAndStatus(roleId, RoleStatus.ACTIVE.name());
    }
}
