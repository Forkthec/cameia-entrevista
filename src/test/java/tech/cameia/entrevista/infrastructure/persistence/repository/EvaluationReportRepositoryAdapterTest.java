package tech.cameia.entrevista.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import tech.cameia.entrevista.domain.exception.DatabaseUnavailableException;
import tech.cameia.entrevista.domain.model.ContentScores;
import tech.cameia.entrevista.domain.model.EvaluationReport;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.VoiceMetrics;

/** El adaptador del reporte traduce los fallos de la base: la caída es un error de negocio y lo demás se propaga. */
class EvaluationReportRepositoryAdapterTest {

    private final EvaluationReportJpaRepository jpa = mock(EvaluationReportJpaRepository.class);

    private final EntityManager entityManager = mock(EntityManager.class);

    private final EvaluationReportRepositoryAdapter adapter = new EvaluationReportRepositoryAdapter(jpa, entityManager);

    private static EvaluationReport report() {
        return new EvaluationReport(UUID.randomUUID(), InterviewLanguage.ENGLISH, ContentScores.NONE,
                VoiceMetrics.NONE, List.of(), Instant.parse("2026-11-17T15:00:00Z"));
    }

    @Test
    @DisplayName("Si la base falla al leer, la lectura responde que no está disponible")
    void findBySession_shouldTranslateDatabaseFailure() {
        UUID id = UUID.randomUUID();
        when(jpa.findById(id)).thenThrow(new DataAccessResourceFailureException("caída"));

        assertThatThrownBy(() -> adapter.findBySession(id)).isInstanceOf(DatabaseUnavailableException.class);
    }

    @Test
    @DisplayName("Si la base falla al guardar, el guardado responde que no está disponible")
    void saveIssued_shouldTranslateDatabaseFailure() {
        doThrow(new DataAccessResourceFailureException("caída")).when(entityManager).persist(any());

        assertThatThrownBy(() -> adapter.saveIssued(report())).isInstanceOf(DatabaseUnavailableException.class);
    }

    @Test
    @DisplayName("Un fallo de JPA que no es la clave primaria del reporte se propaga sin traducir")
    void saveIssued_shouldRethrowOtherPersistenceFailures() {
        PersistenceException other = new PersistenceException("otra restricción");
        doThrow(other).when(entityManager).persist(any());

        assertThatThrownBy(() -> adapter.saveIssued(report())).isSameAs(other);
        assertThat(other).hasMessage("otra restricción");
    }
}
