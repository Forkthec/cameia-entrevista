package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class SessionStatusTest {

    private static final Set<String> VALID = Set.of(
            "CONFIGURED>IN_PROGRESS", "IN_PROGRESS>ABANDONED", "IN_PROGRESS>EVALUATING", "EVALUATING>FINISHED");

    @ParameterizedTest
    @CsvSource({
        "CONFIGURED, IN_PROGRESS",
        "IN_PROGRESS, ABANDONED",
        "IN_PROGRESS, EVALUATING",
        "EVALUATING, FINISHED"
    })
    @DisplayName("Admite cada una de las cuatro transiciones de la máquina")
    void transition_shouldBeAllowed_whenListed(SessionStatus from, SessionStatus to) {
        assertThat(from.canChangeTo(to)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("notListed")
    @DisplayName("Rechaza las 21 combinaciones que no están en la lista, incluida la de un estado a sí mismo")
    void transition_shouldBeRejected_whenNotListed(SessionStatus from, SessionStatus to) {
        assertThat(from.canChangeTo(to)).isFalse();
    }

    static Stream<Arguments> notListed() {
        return Stream.of(SessionStatus.values())
                .flatMap(from -> Stream.of(SessionStatus.values()).map(to -> Arguments.of(from, to)))
                .filter(pair -> !VALID.contains(pair.get()[0] + ">" + pair.get()[1]));
    }

    @Test
    @DisplayName("Las combinaciones rechazadas son 21 de las 25")
    void notListed_shouldHave21Combinations() {
        assertThat(notListed().count()).isEqualTo(21);
    }

    @ParameterizedTest
    @EnumSource(SessionStatus.class)
    @DisplayName("fromCode devuelve el estado de cada valor guardado")
    void fromCode_shouldMapEveryValue(SessionStatus status) {
        assertThat(SessionStatus.fromCode(status.code())).isEqualTo(status);
    }

    @Test
    @DisplayName("Los valores guardados son los del backlog")
    void code_shouldMatchBacklogValues() {
        assertThat(Stream.of(SessionStatus.values()).map(SessionStatus::code))
                .containsExactly("CONFIGURADA", "EN_CURSO", "EVALUANDO", "FINALIZADA", "ABANDONADA");
    }

    @Test
    @DisplayName("fromCode lanza IllegalArgumentException con un valor desconocido")
    void fromCode_shouldThrow_whenUnknown() {
        assertThatThrownBy(() -> SessionStatus.fromCode("PAUSADA")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fromCode lanza IllegalArgumentException con un valor nulo")
    void fromCode_shouldThrow_whenNull() {
        assertThatThrownBy(() -> SessionStatus.fromCode(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
