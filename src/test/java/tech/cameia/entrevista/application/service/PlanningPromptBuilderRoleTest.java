package tech.cameia.entrevista.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import tech.cameia.entrevista.domain.exception.ProfileSnapshotInvalidException;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.Modality;
import tech.cameia.entrevista.domain.model.SessionConfiguration;
import tech.cameia.entrevista.domain.model.Tone;

/** El nombre del rol sale de la copia del perfil guardada en la sesión; si no está, no se llama a la IA. */
class PlanningPromptBuilderRoleTest {

    private final PlanningPromptBuilder builder = new PlanningPromptBuilder(JsonMapper.builder().build());

    private SessionConfiguration configuration(String rolesJson) {
        String snapshot = "{\"perfilId\":\"22222222-2222-4222-8222-222222222222\",\"nombre\":\"Perfil\","
                + "\"contextoProfesional\":{\"resumen\":\"SQL\"},\"rolesAspirados\":" + rolesJson
                + ",\"actualizadoEn\":\"2026-11-01T00:00:00Z\"}";
        return new SessionConfiguration(UUID.randomUUID(), UUID.fromString("a0000001-0000-0000-0000-000000000002"),
                null, snapshot, Modality.SIMULATION, Tone.WARM, AnswerMode.TEXT, InterviewLanguage.SPANISH_COLOMBIA);
    }

    @Test
    void build_shouldFailAsInvalidSnapshot_whenRoleIsNotInTheSnapshot() {
        assertThatThrownBy(() -> builder.build(configuration(
                "[{\"codigo\":\"a0000001-0000-0000-0000-000000000009\",\"nombre\":\"Otro\"}]")))
                .isInstanceOf(ProfileSnapshotInvalidException.class);
    }

    @Test
    void build_shouldFailAsInvalidSnapshot_whenSnapshotHasNoRoles() {
        assertThatThrownBy(() -> builder.build(configuration("[]"))).isInstanceOf(ProfileSnapshotInvalidException.class);
    }
}
