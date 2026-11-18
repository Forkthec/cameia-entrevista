package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tech.cameia.entrevista.infrastructure.persistence.entity.EvaluationReportEntity;

/**
 * Repositorio de Spring Data sobre {@code reporte_evaluacion}. Es una pieza interna: el resto del servicio usa el
 * puerto {@code EvaluationReportRepository}.
 */
interface EvaluationReportJpaRepository extends JpaRepository<EvaluationReportEntity, UUID> {

    /**
     * Lee el idioma de la sesión.
     *
     * @param sessionId sesión
     * @return el código del idioma; vacío si la sesión no existe
     */
    @Query(value = "SELECT s.idioma FROM microentrevista.sesion s WHERE s.id = :sessionId", nativeQuery = true)
    List<String> languageOf(@Param("sessionId") UUID sessionId);

    /**
     * Lee el resultado STAR de la sesión con el número y el texto de cada pregunta, por posición.
     *
     * @param sessionId sesión
     * @return las filas STAR
     */
    @Query(value = """
            SELECT p.orden AS number, p.texto_original AS question, r.situacion AS situation, r.tarea AS task,
                   r.accion AS action, r.resultado AS result
              FROM microentrevista.reporte_star r
              JOIN microentrevista.pregunta p ON p.id = r.pregunta_id
             WHERE r.sesion_id = :sessionId
             ORDER BY p.orden
            """, nativeQuery = true)
    List<StarRow> starOf(@Param("sessionId") UUID sessionId);

    /**
     * Identifica una pregunta de la sesión por su número.
     *
     * @param sessionId sesión
     * @param number posición de la pregunta
     * @return el identificador de la pregunta; vacío si no existe
     */
    @Query(value = "SELECT p.id FROM microentrevista.pregunta p WHERE p.sesion_id = :sessionId AND p.orden = :number",
            nativeQuery = true)
    List<UUID> questionId(@Param("sessionId") UUID sessionId, @Param("number") int number);

    /** Fila de la consulta del resultado STAR. */
    interface StarRow {

        /** @return posición de la pregunta */
        Integer getNumber();

        /** @return texto de la pregunta */
        String getQuestion();

        /** @return componente situación, o nulo */
        Boolean getSituation();

        /** @return componente tarea, o nulo */
        Boolean getTask();

        /** @return componente acción, o nulo */
        Boolean getAction();

        /** @return componente resultado, o nulo */
        Boolean getResult();
    }
}
