package tech.cameia.entrevista.domain.port;

import java.time.Instant;
import java.util.List;

import tech.cameia.entrevista.domain.model.ProfessionalRoleReplica;

/**
 * Puerto de escritura de la copia local del catálogo de roles profesionales. La lectura para validar
 * el rol objetivo de una sesión es de la tarea que crea la sesión.
 */
public interface RoleCatalogReplicaRepository {

    /**
     * Inserta los roles de una instantánea, o actualiza cada uno solo si el que está guardado no es
     * más nuevo que la instantánea. Los roles guardados que la instantánea no trae no se tocan. Debe
     * llamarse dentro de una transacción.
     *
     * @param roles        roles de la instantánea
     * @param snapshotAt   {@code actualizadoEn} de la instantánea
     * @param replicatedAt instante en que el servicio la aplica
     * @return cuántas filas se insertaron o actualizaron
     */
    int upsertSnapshot(List<ProfessionalRoleReplica> roles, Instant snapshotAt, Instant replicatedAt);
}
