package tech.cameia.entrevista.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import jakarta.persistence.AttributeConverter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.Modality;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.domain.model.TurnStatus;
import tech.cameia.entrevista.infrastructure.persistence.entity.converter.AnswerModeConverter;
import tech.cameia.entrevista.infrastructure.persistence.entity.converter.InterviewLanguageConverter;
import tech.cameia.entrevista.infrastructure.persistence.entity.converter.ModalityConverter;
import tech.cameia.entrevista.infrastructure.persistence.entity.converter.SessionStatusConverter;
import tech.cameia.entrevista.infrastructure.persistence.entity.converter.ToneConverter;
import tech.cameia.entrevista.infrastructure.persistence.entity.converter.TurnStatusConverter;

/** Cada convertidor guarda el valor del backlog, devuelve {@code null} para {@code null} y rechaza un valor desconocido. */
class AttributeConvertersTest {

    record Case(AttributeConverter<Object, String> converter, Object domain, String stored) {
    }

    @SuppressWarnings("unchecked")
    private static Case caseOf(AttributeConverter<?, String> converter, Object domain, String stored) {
        return new Case((AttributeConverter<Object, String>) converter, domain, stored);
    }

    static Stream<Case> cases() {
        return Stream.of(
                caseOf(new SessionStatusConverter(), SessionStatus.IN_PROGRESS, "EN_CURSO"),
                caseOf(new TurnStatusConverter(), TurnStatus.RETRYING, "RETRYING"),
                caseOf(new ModalityConverter(), Modality.SIMULATION, "SIMULACION"),
                caseOf(new ToneConverter(), Tone.WARM, "CALIDO"),
                caseOf(new AnswerModeConverter(), AnswerMode.AUDIO, "AUDIO"),
                caseOf(new InterviewLanguageConverter(), InterviewLanguage.SPANISH_COLOMBIA, "es-CO"));
    }

    @ParameterizedTest
    @MethodSource("cases")
    @DisplayName("El convertidor guarda el valor del backlog y lo lee de vuelta")
    void convert_shouldRoundTrip(Case c) {
        assertThat(c.converter().convertToDatabaseColumn(c.domain())).isEqualTo(c.stored());
        assertThat(c.converter().convertToEntityAttribute(c.stored())).isEqualTo(c.domain());
    }

    @ParameterizedTest
    @MethodSource("cases")
    @DisplayName("El convertidor devuelve null para null en los dos sentidos")
    void convert_shouldReturnNull_whenNull(Case c) {
        assertThat(c.converter().convertToDatabaseColumn(null)).isNull();
        assertThat(c.converter().convertToEntityAttribute(null)).isNull();
    }

    @ParameterizedTest
    @MethodSource("cases")
    @DisplayName("El convertidor rechaza un valor guardado que no existe en el dominio")
    void convert_shouldThrow_whenStoredValueUnknown(Case c) {
        assertThatThrownBy(() -> c.converter().convertToEntityAttribute("PAUSADA"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
