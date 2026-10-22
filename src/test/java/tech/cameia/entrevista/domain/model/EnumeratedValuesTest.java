package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EnumeratedValuesTest {

    @Test
    @DisplayName("Modality guarda los valores del backlog y los reconoce")
    void modality_shouldMapBacklogValues() {
        assertThat(Modality.SIMULATION.code()).isEqualTo("SIMULACION");
        assertThat(Modality.fromCode("ENTRENO")).isEqualTo(Modality.TRAINING);
        assertThatThrownBy(() -> Modality.fromCode("PRESENCIAL")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Tone guarda los valores del backlog y los reconoce")
    void tone_shouldMapBacklogValues() {
        assertThat(Tone.PROFESSIONAL_NEUTRAL.code()).isEqualTo("PROFESIONAL_NEUTRO");
        assertThat(Tone.fromCode("CALIDO")).isEqualTo(Tone.WARM);
        assertThat(Tone.fromCode("ESTRICTO")).isEqualTo(Tone.STRICT);
        assertThat(Tone.fromCode("MOTIVADOR")).isEqualTo(Tone.MOTIVATING);
        assertThatThrownBy(() -> Tone.fromCode("AMABLE")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("AnswerMode guarda los valores del backlog y los reconoce")
    void answerMode_shouldMapBacklogValues() {
        assertThat(AnswerMode.TEXT.code()).isEqualTo("TEXTO");
        assertThat(AnswerMode.fromCode("AUDIO")).isEqualTo(AnswerMode.AUDIO);
        assertThatThrownBy(() -> AnswerMode.fromCode("VIDEO")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("InterviewLanguage guarda los valores del backlog y los reconoce")
    void interviewLanguage_shouldMapBacklogValues() {
        assertThat(InterviewLanguage.SPANISH_COLOMBIA.code()).isEqualTo("es-CO");
        assertThat(InterviewLanguage.fromCode("en")).isEqualTo(InterviewLanguage.ENGLISH);
        assertThatThrownBy(() -> InterviewLanguage.fromCode("es")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("SessionConfiguration acepta la oferta nula y rechaza cualquier otro dato nulo")
    void sessionConfiguration_shouldRequireEverythingButJobOffer() {
        UUID id = UUID.randomUUID();

        assertThat(new SessionConfiguration(id, id, null, "{}", Modality.TRAINING, Tone.WARM, AnswerMode.AUDIO,
                InterviewLanguage.ENGLISH).jobOffer()).isNull();
        assertThatThrownBy(() -> new SessionConfiguration(null, id, null, "{}", Modality.TRAINING, Tone.WARM,
                AnswerMode.AUDIO, InterviewLanguage.ENGLISH)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SessionConfiguration(id, null, null, "{}", Modality.TRAINING, Tone.WARM,
                AnswerMode.AUDIO, InterviewLanguage.ENGLISH)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SessionConfiguration(id, id, null, null, Modality.TRAINING, Tone.WARM,
                AnswerMode.AUDIO, InterviewLanguage.ENGLISH)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SessionConfiguration(id, id, null, "{}", null, Tone.WARM, AnswerMode.AUDIO,
                InterviewLanguage.ENGLISH)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SessionConfiguration(id, id, null, "{}", Modality.TRAINING, null,
                AnswerMode.AUDIO, InterviewLanguage.ENGLISH)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SessionConfiguration(id, id, null, "{}", Modality.TRAINING, Tone.WARM, null,
                InterviewLanguage.ENGLISH)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SessionConfiguration(id, id, null, "{}", Modality.TRAINING, Tone.WARM,
                AnswerMode.AUDIO, null)).isInstanceOf(NullPointerException.class);
    }
}
