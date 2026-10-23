package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class ProfileReplicaTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-10-15T12:00:00.123Z");

    private static ProfileReplica replica(ReplicatedProfileStatus status, Instant archivedAt) {
        return new ProfileReplica(UUID.randomUUID(), new FirebaseUid("uid-a"), "Analista", status, archivedAt,
                "{}", "[]", UPDATED_AT);
    }

    @ParameterizedTest(name = "{0} archivado={1} → {2}")
    @CsvSource(value = {
            "COMPLETED, null, true",
            "COMPLETED, 2026-10-15T12:00:00Z, false",
            "PENDING, null, false",
            "IN_PROGRESS, null, false",
            "IN_REVIEW, null, false"
    }, nullValues = "null")
    @DisplayName("Solo un perfil COMPLETED y sin archivar sirve para una entrevista")
    void isUsableForInterview_shouldBeTrue_onlyWhenCompletedAndNotArchived(ReplicatedProfileStatus status,
            Instant archivedAt, boolean expected) {
        assertThat(replica(status, archivedAt).isUsableForInterview()).isEqualTo(expected);
    }

    static Stream<Arguments> requiredComponents() {
        UUID id = UUID.randomUUID();
        FirebaseUid owner = new FirebaseUid("uid-a");
        return Stream.of(
                Arguments.of("profileId", (Function<Object, ProfileReplica>) v -> new ProfileReplica(null, owner,
                        "n", ReplicatedProfileStatus.COMPLETED, null, "{}", "[]", UPDATED_AT)),
                Arguments.of("owner", (Function<Object, ProfileReplica>) v -> new ProfileReplica(id, null,
                        "n", ReplicatedProfileStatus.COMPLETED, null, "{}", "[]", UPDATED_AT)),
                Arguments.of("name", (Function<Object, ProfileReplica>) v -> new ProfileReplica(id, owner,
                        null, ReplicatedProfileStatus.COMPLETED, null, "{}", "[]", UPDATED_AT)),
                Arguments.of("status", (Function<Object, ProfileReplica>) v -> new ProfileReplica(id, owner,
                        "n", null, null, "{}", "[]", UPDATED_AT)),
                Arguments.of("professionalContextJson", (Function<Object, ProfileReplica>) v -> new ProfileReplica(
                        id, owner, "n", ReplicatedProfileStatus.COMPLETED, null, null, "[]", UPDATED_AT)),
                Arguments.of("aspiredRolesJson", (Function<Object, ProfileReplica>) v -> new ProfileReplica(id,
                        owner, "n", ReplicatedProfileStatus.COMPLETED, null, "{}", null, UPDATED_AT)),
                Arguments.of("updatedAt", (Function<Object, ProfileReplica>) v -> new ProfileReplica(id, owner,
                        "n", ReplicatedProfileStatus.COMPLETED, null, "{}", "[]", null)));
    }

    @ParameterizedTest(name = "falta {0}")
    @MethodSource("requiredComponents")
    @DisplayName("El constructor exige todos los componentes menos la fecha de archivado")
    void constructor_shouldThrow_whenRequiredNull(String component, Function<Object, ProfileReplica> build) {
        assertThatThrownBy(() -> build.apply(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage(component);
    }
}
