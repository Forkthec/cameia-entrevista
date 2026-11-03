package tech.cameia.entrevista.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProfessionalRoleReplicaTest {

    private static final UUID ID = UUID.fromString("a0000001-0000-0000-0000-000000000001");

    @Test
    @DisplayName("Un rol válido conserva sus datos")
    void constructor_shouldKeepData_whenValid() {
        var role = new ProfessionalRoleReplica(ID, "Desarrollador Frontend", "Frontend Developer", RoleStatus.ACTIVE);

        assertThat(role.id()).isEqualTo(ID);
        assertThat(role.nameEs()).isEqualTo("Desarrollador Frontend");
        assertThat(role.nameEn()).isEqualTo("Frontend Developer");
        assertThat(role.isActive()).isTrue();
        assertThat(new ProfessionalRoleReplica(ID, "a", "b", RoleStatus.INACTIVE).isActive()).isFalse();
    }

    @Test
    @DisplayName("255 puntos de código pasan, también con emoji (510 unidades UTF-16); 256 fallan")
    void constructor_shouldEnforceCodePointLength() {
        assertThatCode(() -> new ProfessionalRoleReplica(ID, "x".repeat(255), "😀".repeat(255), RoleStatus.ACTIVE))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> new ProfessionalRoleReplica(ID, "x".repeat(256), "ok", RoleStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nameEs");
        assertThatThrownBy(() -> new ProfessionalRoleReplica(ID, "ok", "😀".repeat(256), RoleStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nameEn");
    }

    @ParameterizedTest(name = "nombre [{0}]")
    @ValueSource(strings = { "", " ", "   ", "\t\n" })
    @DisplayName("Un nombre vacío o en blanco se rechaza")
    void constructor_shouldRejectBlankNames(String blank) {
        assertThatThrownBy(() -> new ProfessionalRoleReplica(ID, blank, "ok", RoleStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProfessionalRoleReplica(ID, "ok", blank, RoleStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Ningún componente puede ser nulo")
    void constructor_shouldRejectNulls() {
        assertThatThrownBy(() -> new ProfessionalRoleReplica(null, "a", "b", RoleStatus.ACTIVE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProfessionalRoleReplica(ID, null, "b", RoleStatus.ACTIVE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProfessionalRoleReplica(ID, "a", null, RoleStatus.ACTIVE))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProfessionalRoleReplica(ID, "a", "b", null))
                .isInstanceOf(NullPointerException.class);
    }
}
