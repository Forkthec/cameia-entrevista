package tech.cameia.entrevista.infrastructure.persistence.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tech.cameia.entrevista.domain.model.TranscriptionExecution;
import tech.cameia.entrevista.domain.port.TranscriptionExecutionRepository;
import tech.cameia.entrevista.infrastructure.persistence.entity.TranscriptionExecutionEntity;

/**
 * Adaptador del puerto de ejecuciones de transcripción sobre JPA. Cada registro va en su propia transacción: debe
 * quedar aunque después se rechace la transcripción o falle el registro de la respuesta (CA-5.12.3).
 */
@Repository
class TranscriptionExecutionRepositoryAdapter implements TranscriptionExecutionRepository {

    /** Valor de {@code tipo_operacion} de una transcripción. */
    static final String TRANSCRIBE = "TRANSCRIBIR";

    private final TranscriptionExecutionJpaRepository jpa;

    TranscriptionExecutionRepositoryAdapter(TranscriptionExecutionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(TranscriptionExecution e) {
        // La clave de la ejecución es la del registro: cada llamada al proveedor es una fila distinta.
        jpa.saveAndFlush(new TranscriptionExecutionEntity(e.id(), e.sessionId(), e.turnId(), TRANSCRIBE,
                (short) e.attempt(), e.id(), e.status().code(), e.model(), e.errorCode(), e.provider(),
                e.startedAt(), e.finishedAt()));
    }
}
