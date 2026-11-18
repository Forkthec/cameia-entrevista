package tech.cameia.entrevista.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta escrita a la pregunta pendiente. Los dos campos llegan como texto: la validación completa, con una causa
 * por campo, es del dominio y responde todos los errores a la vez.
 *
 * @param turnId turno que se responde, tal como lo devolvió el estado de la sesión
 * @param answerText respuesta escrita de la persona
 */
@Schema(description = "Respuesta escrita a la pregunta pendiente. Los campos desconocidos se ignoran")
public record AnswerRequest(
        @Schema(description = "Turno que se responde (UUID)", format = "uuid",
                example = "9b2e6d44-5a3c-4f0b-8e71-2c4d6f8a0b12", requiredMode = Schema.RequiredMode.REQUIRED)
        String turnId,
        @Schema(description = "Respuesta escrita: de 1 a 4000 caracteres, se guarda tal cual", maxLength = 4000,
                example = "En mi último proyecto lideré la migración de la base de datos sin tiempo de inactividad.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String answerText) {

    /** El texto es de la persona: nunca se imprime. */
    @Override
    public String toString() {
        return "AnswerRequest[turnId=" + turnId + "]";
    }
}
