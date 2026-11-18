package tech.cameia.entrevista.presentation.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

import tech.cameia.entrevista.application.service.ReportOutcome;
import tech.cameia.entrevista.domain.model.ContentScores;
import tech.cameia.entrevista.domain.model.EvaluationReport;
import tech.cameia.entrevista.domain.model.VoiceMetrics;

/**
 * Reporte de Evaluación de una entrevista finalizada: siete métricas en orden fijo, el STAR de las preguntas
 * conductuales y las cinco preguntas con sus respuestas. Un valor {@code null} es «sin resultado», nunca 0.
 *
 * @param sessionId sesión evaluada
 * @param issuedAt momento de emisión, UTC a segundos
 * @param sessionLanguage {@code es-CO} o {@code en}
 * @param interviewDate momento en que terminó la entrevista, UTC a segundos
 * @param tone tono de la entrevista
 * @param metrics las siete métricas, siempre en el mismo orden
 * @param star un elemento por pregunta conductual, en orden; vacío si no hay
 * @param questions las cinco preguntas con su respuesta, en orden
 */
@Schema(description = "Reporte de Evaluación de la entrevista; el mismo cuerpo en cada consulta")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record EvaluationReportResponse(
        @Schema(format = "uuid", example = "3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10") UUID sessionId,
        @Schema(example = "2026-11-17T15:04:05Z") Instant issuedAt,
        @Schema(allowableValues = {"es-CO", "en"}, example = "es-CO") String sessionLanguage,
        @Schema(example = "2026-11-17T14:58:40Z") Instant interviewDate,
        @Schema(allowableValues = {"PROFESIONAL_NEUTRO", "CALIDO", "ESTRICTO"}, example = "PROFESIONAL_NEUTRO")
        String tone,
        @Schema(description = "Siete métricas en orden fijo: precisión y claridad, carisma verbal, lenguaje inclusivo,"
                + " ritmo de habla, tiempo de pausas, tasa de vacilación y entusiasmo") List<ReportMetricResponse> metrics,
        @Schema(description = "Un elemento por pregunta conductual, en orden; lista vacía si no hay")
        List<StarResponse> star,
        @Schema(description = "Las cinco preguntas con su respuesta definitiva, en orden de número")
        List<ReportQuestionResponse> questions) {

    private static final String CONTENT = "CONTENT";

    private static final String VOICE = "VOICE";

    private static final String SCORE = "SCORE_1_5";

    /**
     * Traduce el reporte emitido al contrato.
     *
     * @param issued reporte con lo que se muestra de la sesión
     * @return el DTO, con las siete métricas en el orden del contrato
     */
    public static EvaluationReportResponse from(ReportOutcome.Issued issued) {
        EvaluationReport report = issued.report();
        return new EvaluationReportResponse(report.sessionId(), report.issuedAt(), report.language().code(),
                issued.interviewDate(), issued.tone().code(), metricsOf(report),
                report.star().stream().map(StarResponse::from).toList(),
                issued.questions().stream().map(ReportQuestionResponse::from).toList());
    }

    private static List<ReportMetricResponse> metricsOf(EvaluationReport report) {
        ContentScores content = report.content();
        VoiceMetrics voice = report.voice();
        return List.of(
                new ReportMetricResponse("PRECISION_CLARITY", CONTENT, content.precision(), SCORE),
                new ReportMetricResponse("VERBAL_CHARISMA", CONTENT, content.charisma(), SCORE),
                new ReportMetricResponse("INCLUSIVE_LANGUAGE", CONTENT, content.language(), SCORE),
                new ReportMetricResponse("SPEECH_RATE", VOICE, speechRate(voice), "WORDS_PER_MINUTE"),
                new ReportMetricResponse("PAUSE_TIME", VOICE, voice.pauseTime(), "PERCENT"),
                new ReportMetricResponse("HESITATION_RATE", VOICE, voice.hesitationRate(), "PER_100_WORDS"),
                new ReportMetricResponse("ENTHUSIASM", VOICE, report.enthusiasm(), null));
    }

    private static BigDecimal speechRate(VoiceMetrics voice) {
        return voice.speechRate() == null ? null : BigDecimal.valueOf(voice.speechRate());
    }
}
