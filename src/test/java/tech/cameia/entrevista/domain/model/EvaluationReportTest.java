package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tech.cameia.entrevista.domain.model.AnswerAttempt.AnswerOrigin;

/** El reporte emitido de una sesión y la entrada de cada pregunta. */
class EvaluationReportTest {

    private static final UUID SESSION = UUID.randomUUID();

    private static EvaluationReport report(List<StarResult> star, Instant issuedAt) {
        return new EvaluationReport(SESSION, InterviewLanguage.SPANISH_COLOMBIA, ContentScores.NONE, VoiceMetrics.NONE, star,
                issuedAt);
    }

    @Test
    @DisplayName("El entusiasmo no se mide en el MVP: siempre es nulo")
    void enthusiasm_shouldAlwaysBeNull() {
        assertThat(report(List.of(), Instant.parse("2026-11-17T15:00:00Z")).enthusiasm()).isNull();
    }

    @Test
    @DisplayName("Las preguntas STAR quedan ordenadas por número")
    void star_shouldBeSortedByNumber() {
        EvaluationReport report = report(List.of(StarResult.unavailable(5, "b"), StarResult.unavailable(3, "a")),
                Instant.parse("2026-11-17T15:00:00Z"));

        assertThat(report.star()).extracting(StarResult::questionNumber).containsExactly(3, 5);
    }

    @Test
    @DisplayName("La lista del reporte es una copia que no se puede modificar")
    void star_shouldBeAnUnmodifiableCopy() {
        List<StarResult> source = new ArrayList<>(List.of(StarResult.unavailable(3, "a")));
        EvaluationReport report = report(source, Instant.parse("2026-11-17T15:00:00Z"));

        source.add(StarResult.unavailable(5, "b"));

        assertThat(report.star()).hasSize(1);
        assertThatThrownBy(() -> report.star().add(StarResult.unavailable(5, "b")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("La fecha de emisión se trunca a segundos")
    void issuedAt_shouldBeTruncatedToSeconds() {
        EvaluationReport report = report(List.of(), Instant.parse("2026-11-17T15:00:00.987654Z"));

        assertThat(report.issuedAt()).isEqualTo(Instant.parse("2026-11-17T15:00:00Z"));
    }

    @Test
    @DisplayName("Ningún componente del reporte puede ser nulo")
    void nullParts_shouldBeRejected() {
        Instant now = Instant.parse("2026-11-17T15:00:00Z");

        assertThatThrownBy(() -> new EvaluationReport(null, InterviewLanguage.SPANISH_COLOMBIA, ContentScores.NONE,
                VoiceMetrics.NONE, List.of(), now)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EvaluationReport(SESSION, null, ContentScores.NONE, VoiceMetrics.NONE,
                List.of(), now)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EvaluationReport(SESSION, InterviewLanguage.SPANISH_COLOMBIA, null, VoiceMetrics.NONE,
                List.of(), now)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EvaluationReport(SESSION, InterviewLanguage.SPANISH_COLOMBIA, ContentScores.NONE, null,
                List.of(), now)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EvaluationReport(SESSION, InterviewLanguage.SPANISH_COLOMBIA, ContentScores.NONE,
                VoiceMetrics.NONE, null, now)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new EvaluationReport(SESSION, InterviewLanguage.SPANISH_COLOMBIA, ContentScores.NONE,
                VoiceMetrics.NONE, List.of(), null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Una pregunta del reporte por voz conserva sus datos de voz")
    void reportQuestion_shouldKeepVoice() {
        VoiceSample voice = new VoiceSample(10, 6000, 0, 0);

        ReportQuestion question = new ReportQuestion(1, QuestionType.APERTURA, "p", "r", AnswerOrigin.AUDIO, voice);

        assertThat(question.voice()).isEqualTo(voice);
        assertThat(question.number()).isEqualTo(1);
    }

    @Test
    @DisplayName("Una pregunta del reporte escrita con datos de voz se rechaza")
    void reportQuestion_shouldRejectVoiceOnText() {
        assertThatThrownBy(() -> new ReportQuestion(1, QuestionType.APERTURA, "p", "r", AnswerOrigin.TEXTO,
                new VoiceSample(10, 6000, 0, 0))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("El número de la pregunta debe ser de 1 a 5 y los textos no pueden faltar")
    void reportQuestion_shouldValidateItsFields() {
        assertThatThrownBy(() -> new ReportQuestion(0, QuestionType.APERTURA, "p", "r", AnswerOrigin.TEXTO, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportQuestion(6, QuestionType.APERTURA, "p", "r", AnswerOrigin.TEXTO, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReportQuestion(1, null, "p", "r", AnswerOrigin.TEXTO, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReportQuestion(1, QuestionType.APERTURA, null, "r", AnswerOrigin.TEXTO, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReportQuestion(1, QuestionType.APERTURA, "p", null, AnswerOrigin.TEXTO, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReportQuestion(1, QuestionType.APERTURA, "p", "r", null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("toString de la pregunta del reporte no imprime la pregunta ni la respuesta")
    void reportQuestion_toStringShouldNotPrintText() {
        ReportQuestion question = new ReportQuestion(1, QuestionType.APERTURA, "pregunta privada",
                "respuesta secreta", AnswerOrigin.TEXTO, null);

        assertThat(question.toString()).doesNotContain("privada").doesNotContain("secreta");
    }
}
