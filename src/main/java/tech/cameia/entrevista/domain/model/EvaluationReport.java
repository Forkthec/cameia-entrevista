package tech.cameia.entrevista.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Reporte de evaluación emitido de una sesión: métricas de contenido, de voz y resultado STAR por pregunta
 * conductual. Una vez emitido no cambia.
 *
 * @param sessionId sesión evaluada
 * @param language idioma de la entrevista
 * @param content promedios de contenido
 * @param voice métricas de voz
 * @param star resultado STAR de cada pregunta conductual, ordenado por número
 * @param issuedAt momento de emisión, en segundos
 */
public record EvaluationReport(UUID sessionId, InterviewLanguage language, ContentScores content,
        VoiceMetrics voice, List<StarResult> star, Instant issuedAt) {

    /** Exige todas las partes, copia y ordena la lista STAR y trunca la fecha a segundos. */
    public EvaluationReport {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(voice, "voice");
        Objects.requireNonNull(star, "star");
        Objects.requireNonNull(issuedAt, "issuedAt");
        star = star.stream().sorted(Comparator.comparingInt(StarResult::questionNumber)).toList();
        issuedAt = issuedAt.truncatedTo(ChronoUnit.SECONDS);
    }

    /**
     * El Entusiasmo no se mide todavía: siempre queda «sin resultado».
     *
     * @return siempre {@code null}
     */
    public BigDecimal enthusiasm() {
        return null;
    }
}
