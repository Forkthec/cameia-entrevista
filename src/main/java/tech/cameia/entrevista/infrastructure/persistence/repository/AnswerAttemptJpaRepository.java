package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tech.cameia.entrevista.infrastructure.persistence.entity.AnswerAttemptEntity;

/**
 * Repositorio de Spring Data sobre la tabla {@code intento_respuesta}. Es una pieza interna: el resto del servicio
 * usa el puerto {@code AnswerAttemptRepository}.
 */
interface AnswerAttemptJpaRepository extends JpaRepository<AnswerAttemptEntity, UUID> {

    /**
     * Busca la respuesta definitiva de un turno.
     *
     * @param turnId turno
     * @return la respuesta, o vacío si no hay
     */
    @Query(value = """
            SELECT i.* FROM microentrevista.intento_respuesta i
             WHERE i.turno_id = :turnId AND i.es_definitiva
            """, nativeQuery = true)
    Optional<AnswerAttemptEntity> findDefinitive(@Param("turnId") UUID turnId);

    /**
     * Lista las preguntas respondidas de una sesión con su respuesta definitiva, por posición.
     *
     * @param sessionId sesión
     * @return filas de posición, pregunta y respuesta
     */
    @Query(value = """
            SELECT p.orden AS number, p.texto_original AS question, i.respuesta_texto AS answer
              FROM microentrevista.intento_respuesta i
              JOIN microentrevista.turno t ON t.id = i.turno_id
              JOIN microentrevista.pregunta p ON p.id = t.pregunta_id
             WHERE p.sesion_id = :sessionId AND i.es_definitiva
             ORDER BY p.orden
            """, nativeQuery = true)
    List<HistoryRow> historyOf(@Param("sessionId") UUID sessionId);

    /**
     * Lista, por posición, lo que el reporte necesita de cada pregunta con respuesta definitiva.
     *
     * @param sessionId sesión
     * @return las filas de pregunta, respuesta y datos de voz
     */
    @Query(value = """
            SELECT p.orden AS number, p.tipo AS type, p.texto_original AS question, i.respuesta_texto AS answer,
                   i.origen_respuesta AS origin, i.voz_palabras AS words, i.voz_tramo_ms AS spanMs,
                   i.voz_pausas_ms AS pauseMs, i.voz_muletillas AS fillers
              FROM microentrevista.intento_respuesta i
              JOIN microentrevista.turno t ON t.id = i.turno_id
              JOIN microentrevista.pregunta p ON p.id = t.pregunta_id
             WHERE p.sesion_id = :sessionId AND i.es_definitiva
             ORDER BY p.orden
            """, nativeQuery = true)
    List<ReportInputRow> reportInputsOf(@Param("sessionId") UUID sessionId);

    /** Fila de la consulta de las entradas del reporte. */
    interface ReportInputRow {
        /** @return posición de la pregunta */
        Integer getNumber();

        /** @return tipo de la pregunta */
        String getType();

        /** @return texto de la pregunta */
        String getQuestion();

        /** @return respuesta definitiva */
        String getAnswer();

        /** @return origen de la respuesta, {@code TEXTO} o {@code AUDIO} */
        String getOrigin();

        /** @return palabras de la respuesta por voz, o nulo */
        Integer getWords();

        /** @return milisegundos del tramo hablado, o nulo */
        Integer getSpanMs();

        /** @return milisegundos de pausas, o nulo */
        Integer getPauseMs();

        /** @return muletillas, o nulo */
        Integer getFillers();
    }

    /** Fila de la consulta del historial. */
    interface HistoryRow {

        /** @return posición de la pregunta */
        Integer getNumber();

        /** @return texto original de la pregunta */
        String getQuestion();

        /** @return respuesta definitiva */
        String getAnswer();
    }
}
