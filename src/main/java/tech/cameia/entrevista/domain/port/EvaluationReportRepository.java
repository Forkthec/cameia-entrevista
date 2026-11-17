package tech.cameia.entrevista.domain.port;

import java.util.Optional;
import java.util.UUID;

import tech.cameia.entrevista.domain.model.EvaluationReport;

/**
 * Puerto de persistencia del Reporte de Evaluación: uno por sesión, que una vez emitido no se modifica.
 */
public interface EvaluationReportRepository {

    /**
     * Busca el reporte de una sesión.
     *
     * @param sessionId identificador de la sesión
     * @return el reporte con su resultado STAR por pregunta, o vacío si aún no se emitió
     */
    Optional<EvaluationReport> findBySession(UUID sessionId);

    /**
     * Guarda el reporte y sus filas STAR en una sola transacción.
     *
     * @param report reporte a emitir
     * @return el reporte guardado
     * @throws AlreadyIssuedException si la sesión ya tenía su reporte
     */
    EvaluationReport saveIssued(EvaluationReport report);

    /**
     * Indica que la sesión ya tiene su reporte (otro proceso lo emitió primero). Es interna: el servicio la
     * convierte en la lectura del reporte ya guardado y nunca llega a la persona.
     */
    class AlreadyIssuedException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /** Crea la excepción sin datos de la persona. */
        public AlreadyIssuedException() {
            super("La sesión ya tiene su reporte emitido");
        }
    }
}
