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

class TurnStatusTest {

    private static final Set<String> VALID = Set.of(
            "PENDING>PROCESSING", "PROCESSING>COMPLETED", "PROCESSING>RETRYING", "RETRYING>PROCESSING");

    @ParameterizedTest
    @CsvSource({
        "PENDING, PROCESSING",
        "PROCESSING, COMPLETED",
        "PROCESSING, RETRYING",
        "RETRYING, PROCESSING"
    })
    @DisplayName("Admite cada una de las cuatro transiciones del turno")
    void transition_shouldBeAllowed_whenListed(TurnStatus from, TurnStatus to) {
        assertThat(from.canChangeTo(to)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("notListed")
    @DisplayName("Rechaza las 12 combinaciones que no están en la lista (incluye RETRYING a COMPLETED y PROCESSING a PENDING)")
    void transition_shouldBeRejected_whenNotListed(TurnStatus from, TurnStatus to) {
        assertThat(from.canChangeTo(to)).isFalse();
    }

    static Stream<Arguments> notListed() {
        return Stream.of(TurnStatus.values())
                .flatMap(from -> Stream.of(TurnStatus.values()).map(to -> Arguments.of(from, to)))
                .filter(pair -> !VALID.contains(pair.get()[0] + ">" + pair.get()[1]));
    }

    @Test
    @DisplayName("Las combinaciones rechazadas son 12 de las 16")
    void notListed_shouldHave12Combinations() {
        assertThat(notListed().count()).isEqualTo(12);
    }

    @ParameterizedTest
    @EnumSource(TurnStatus.class)
    @DisplayName("fromCode devuelve el estado de cada valor guardado, que es su nombre")
    void fromCode_shouldMapEveryValue(TurnStatus status) {
        assertThat(status.code()).isEqualTo(status.name());
        assertThat(TurnStatus.fromCode(status.code())).isEqualTo(status);
    }

    @Test
    @DisplayName("fromCode lanza IllegalArgumentException con un valor desconocido")
    void fromCode_shouldThrow_whenUnknown() {
        assertThatThrownBy(() -> TurnStatus.fromCode("FAILED")).isInstanceOf(IllegalArgumentException.class);
    }
}
