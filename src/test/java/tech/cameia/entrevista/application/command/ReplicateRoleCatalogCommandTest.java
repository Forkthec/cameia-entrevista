package tech.cameia.entrevista.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tech.cameia.entrevista.domain.model.ProfessionalRoleReplica;
import tech.cameia.entrevista.domain.model.RoleStatus;

class ReplicateRoleCatalogCommandTest {

    private static final Instant T = Instant.parse("2026-10-24T12:00:00.123Z");

    private static List<ProfessionalRoleReplica> roles(int count) {
        List<ProfessionalRoleReplica> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new ProfessionalRoleReplica(UUID.randomUUID(), "Rol " + i, "Role " + i, RoleStatus.ACTIVE));
        }
        return list;
    }

    @Test
    @DisplayName("De 1 a 200 roles pasan y la lista queda copiada e inmutable")
    void constructor_shouldAcceptOneToTwoHundred() {
        List<ProfessionalRoleReplica> source = roles(200);
        var command = new ReplicateRoleCatalogCommand(UUID.randomUUID(), source, T);
        source.clear();

        assertThat(command.roles()).hasSize(200);
        assertThatThrownBy(() -> command.roles().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(new ReplicateRoleCatalogCommand(UUID.randomUUID(), roles(1), T).roles()).hasSize(1);
    }

    @Test
    @DisplayName("0 y 201 roles se rechazan")
    void constructor_shouldRejectOutOfRange() {
        assertThatThrownBy(() -> new ReplicateRoleCatalogCommand(UUID.randomUUID(), List.of(), T))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReplicateRoleCatalogCommand(UUID.randomUUID(), roles(201), T))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Faltar algún componente se rechaza")
    void constructor_shouldRejectNulls() {
        assertThatThrownBy(() -> new ReplicateRoleCatalogCommand(null, roles(1), T))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReplicateRoleCatalogCommand(UUID.randomUUID(), null, T))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ReplicateRoleCatalogCommand(UUID.randomUUID(), roles(1), null))
                .isInstanceOf(NullPointerException.class);
    }
}
