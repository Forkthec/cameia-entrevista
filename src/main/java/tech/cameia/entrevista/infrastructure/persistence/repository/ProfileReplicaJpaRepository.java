package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tech.cameia.entrevista.infrastructure.persistence.entity.ProfileReplicaEntity;

/**
 * Repositorio de Spring Data sobre la tabla {@code contexto_profesional_replica}. Es una pieza
 * interna: el resto del servicio usa el puerto {@code ProfileReplicaRepository}.
 */
interface ProfileReplicaJpaRepository extends JpaRepository<ProfileReplicaEntity, UUID> {

    /**
     * Inserta la copia, o la reemplaza solo si no es más vieja que la guardada y conserva el dueño.
     * Es una sola sentencia, así que dos instancias que aplican mensajes del mismo perfil a la vez
     * dejan siempre el más nuevo. Los parámetros son tantos como columnas porque es SQL, no un
     * método de diseño.
     *
     * @return filas cambiadas: 1 si se insertó o reemplazó, 0 si la guardada era más nueva o de otro dueño
     */
    @Modifying
    @Query(value = """
            INSERT INTO microentrevista.contexto_profesional_replica
                (perfil_id, firebase_uid, nombre, estado, fecha_archivado, contexto_profesional, roles_aspirados,
                 actualizado_en, fecha_replicacion)
            VALUES (:profileId, :owner, :name, :status, CAST(:archivedAt AS timestamptz),
                    CAST(:context AS jsonb), CAST(:roles AS jsonb), :updatedAt, :replicatedAt)
            ON CONFLICT (perfil_id) DO UPDATE SET
                nombre = EXCLUDED.nombre, estado = EXCLUDED.estado, fecha_archivado = EXCLUDED.fecha_archivado,
                contexto_profesional = EXCLUDED.contexto_profesional, roles_aspirados = EXCLUDED.roles_aspirados,
                actualizado_en = EXCLUDED.actualizado_en, fecha_replicacion = EXCLUDED.fecha_replicacion
            WHERE microentrevista.contexto_profesional_replica.actualizado_en <= EXCLUDED.actualizado_en
              AND microentrevista.contexto_profesional_replica.firebase_uid = EXCLUDED.firebase_uid
            """, nativeQuery = true)
    int upsertIfNotOlder(@Param("profileId") UUID profileId, @Param("owner") String owner,
            @Param("name") String name, @Param("status") String status, @Param("archivedAt") Instant archivedAt,
            @Param("context") String context, @Param("roles") String roles,
            @Param("updatedAt") Instant updatedAt, @Param("replicatedAt") Instant replicatedAt);
}
