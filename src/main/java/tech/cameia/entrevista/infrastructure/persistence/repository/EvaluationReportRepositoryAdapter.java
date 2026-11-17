package tech.cameia.entrevista.infrastructure.persistence.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import tech.cameia.entrevista.domain.exception.DatabaseUnavailableException;
import tech.cameia.entrevista.domain.model.ContentScores;
import tech.cameia.entrevista.domain.model.EvaluationReport;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.StarResult;
import tech.cameia.entrevista.domain.model.VoiceMetrics;
import tech.cameia.entrevista.domain.port.EvaluationReportRepository;
import tech.cameia.entrevista.infrastructure.persistence.entity.EvaluationReportEntity;
import tech.cameia.entrevista.infrastructure.persistence.entity.StarResultEntity;

/**
 * Adaptador del puerto del reporte sobre JPA. Inserta con {@code persist} (nunca con un {@code merge} que haría un
 * {@code UPDATE}, que el esquema rechaza) y traduce la clave primaria repetida a
 * {@link EvaluationReportRepository.AlreadyIssuedException}.
 */
@Repository
class EvaluationReportRepositoryAdapter implements EvaluationReportRepository {

    /** Clave primaria: un solo reporte por sesión. */
    static final String PK_REPORT = "pk_reporte_evaluacion";

    private final EvaluationReportJpaRepository jpa;

    private final EntityManager entityManager;

    EvaluationReportRepositoryAdapter(EvaluationReportJpaRepository jpa, EntityManager entityManager) {
        this.jpa = jpa;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EvaluationReport> findBySession(UUID sessionId) {
        try {
            return jpa.findById(sessionId)
                    .map(entity -> toDomain(entity, jpa.languageOf(sessionId).get(0), jpa.starOf(sessionId)));
        } catch (DataAccessException failure) {
            throw new DatabaseUnavailableException(failure);
        }
    }

    @Override
    @Transactional
    public EvaluationReport saveIssued(EvaluationReport report) {
        try {
            entityManager.persist(toEntity(report));
            entityManager.flush();
            for (StarResult star : report.star()) {
                UUID questionId = jpa.questionId(report.sessionId(), star.questionNumber()).get(0);
                entityManager.persist(toEntity(report.sessionId(), questionId, star));
            }
            entityManager.flush();
            return report;
        } catch (PersistenceException failure) {
            // El flush lanza la excepción de JPA sin traducir; el choque de la clave primaria es «ya emitido».
            if (ConstraintNames.violates(failure, PK_REPORT)) {
                throw new AlreadyIssuedException();
            }
            throw failure;
        } catch (DataAccessException failure) {
            throw new DatabaseUnavailableException(failure);
        }
    }

    private static EvaluationReportEntity toEntity(EvaluationReport r) {
        return new EvaluationReportEntity(r.sessionId(), r.content().precision(), r.content().charisma(),
                r.content().language(), r.voice().speechRate(), r.voice().pauseTime(), r.voice().hesitationRate(),
                r.issuedAt());
    }

    private static StarResultEntity toEntity(UUID sessionId, UUID questionId, StarResult star) {
        Short level = star.level() == null ? null : star.level().shortValue();
        return new StarResultEntity(sessionId, questionId, star.situation(), star.task(), star.action(),
                star.result(), level);
    }

    private static EvaluationReport toDomain(EvaluationReportEntity entity, String language,
            List<EvaluationReportJpaRepository.StarRow> rows) {
        List<StarResult> star = rows.stream().map(row -> new StarResult(row.getNumber(), row.getQuestion(),
                row.getSituation(), row.getTask(), row.getAction(), row.getResult())).toList();
        return new EvaluationReport(entity.getSessionId(), InterviewLanguage.fromCode(language),
                new ContentScores(entity.getPrecision(), entity.getCharisma(), entity.getLanguage()),
                new VoiceMetrics(entity.getSpeechRate(), entity.getPauseTime(), entity.getHesitationRate()), star,
                entity.getIssuedAt());
    }
}
