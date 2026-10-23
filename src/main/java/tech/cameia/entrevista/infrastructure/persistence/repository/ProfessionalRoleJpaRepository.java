package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tech.cameia.entrevista.infrastructure.persistence.entity.ProfessionalRoleEntity;

/**
 * Repositorio de Spring Data sobre la tabla {@code rol_profesional}. Es una pieza interna: el resto
 * del servicio usa los puertos del catálogo.
 */
interface ProfessionalRoleJpaRepository extends JpaRepository<ProfessionalRoleEntity, UUID> {

    /**
     * Aplica una instantánea completa en una sola sentencia: inserta los roles nuevos y actualiza los
     * existentes solo si la fila guardada no es más nueva que la instantánea. Los roles guardados que
     * la instantánea no trae no se tocan. Al ser una sola sentencia con {@code ON CONFLICT}, dos
     * instancias que aplican instantáneas a la vez no chocan por clave duplicada. Los roles viajan
     * como un único parámetro JSON con las claves {@code id}, {@code nombre_es}, {@code nombre_en} y
     * {@code estado}.
     *
     * @return filas insertadas o actualizadas
     */
    @Modifying
    @Query(value = """
            INSERT INTO microentrevista.rol_profesional
                (id, nombre_es, nombre_en, estado, actualizado_en, fecha_replicacion)
            SELECT r.id, r.nombre_es, r.nombre_en, r.estado, CAST(:snapshotAt AS timestamptz),
                   CAST(:replicatedAt AS timestamptz)
            FROM jsonb_to_recordset(CAST(:roles AS jsonb))
                 AS r(id uuid, nombre_es text, nombre_en text, estado text)
            ON CONFLICT (id) DO UPDATE SET
                nombre_es = EXCLUDED.nombre_es, nombre_en = EXCLUDED.nombre_en, estado = EXCLUDED.estado,
                actualizado_en = EXCLUDED.actualizado_en, fecha_replicacion = EXCLUDED.fecha_replicacion
            WHERE microentrevista.rol_profesional.actualizado_en <= EXCLUDED.actualizado_en
            """, nativeQuery = true)
    int upsertSnapshot(@Param("roles") String roles, @Param("snapshotAt") Instant snapshotAt,
            @Param("replicatedAt") Instant replicatedAt);
}
