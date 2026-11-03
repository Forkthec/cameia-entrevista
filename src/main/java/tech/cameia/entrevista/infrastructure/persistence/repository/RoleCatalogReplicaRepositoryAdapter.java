package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tech.cameia.entrevista.domain.model.ProfessionalRoleReplica;
import tech.cameia.entrevista.domain.port.RoleCatalogReplicaRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adaptador del puerto de escritura del catálogo de roles sobre JPA. Arma el parámetro JSON de la
 * sentencia condicionada del repositorio a partir de la lista de dominio.
 */
@Repository
class RoleCatalogReplicaRepositoryAdapter implements RoleCatalogReplicaRepository {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final ProfessionalRoleJpaRepository jpa;

    RoleCatalogReplicaRepositoryAdapter(ProfessionalRoleJpaRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * Solo se llama dentro de la transacción del caso de uso: el Inbox y los roles se confirman juntos.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int upsertSnapshot(List<ProfessionalRoleReplica> roles, Instant snapshotAt, Instant replicatedAt) {
        return jpa.upsertSnapshot(toJson(roles), snapshotAt, replicatedAt);
    }

    private static String toJson(List<ProfessionalRoleReplica> roles) {
        return MAPPER.writeValueAsString(roles.stream().map(RoleCatalogReplicaRepositoryAdapter::toRow).toList());
    }

    private static Map<String, String> toRow(ProfessionalRoleReplica role) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("id", role.id().toString());
        row.put("nombre_es", role.nameEs());
        row.put("nombre_en", role.nameEn());
        row.put("estado", role.status().name());
        return row;
    }
}
