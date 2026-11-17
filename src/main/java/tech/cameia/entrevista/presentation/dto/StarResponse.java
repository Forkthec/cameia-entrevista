package tech.cameia.entrevista.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

import tech.cameia.entrevista.domain.model.StarResult;

/**
 * Resultado STAR de una pregunta conductual. Sin resultado = los cuatro componentes y {@code level} en {@code null}.
 *
 * @param questionNumber número de la pregunta, de 1 a 5
 * @param questionText texto de la pregunta
 * @param situation si la respuesta describe la situación
 * @param task si describe la tarea
 * @param action si describe la acción
 * @param result si describe el resultado
 * @param level cuántos componentes se cumplen, de 0 a 4
 */
@Schema(description = "Resultado STAR de una pregunta conductual; sin resultado = todo null")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record StarResponse(
        @Schema(minimum = "1", maximum = "5", example = "3") int questionNumber,
        @Schema(example = "Cuéntame de una vez que resolviste un conflicto en equipo.") String questionText,
        @Schema(nullable = true, example = "true") Boolean situation,
        @Schema(nullable = true, example = "true") Boolean task,
        @Schema(nullable = true, example = "true") Boolean action,
        @Schema(nullable = true, example = "false") Boolean result,
        @Schema(description = "Componentes cumplidos, de 0 a 4", nullable = true, minimum = "0", maximum = "4",
                example = "3") Integer level) {

    /**
     * Traduce el resultado del dominio al contrato.
     *
     * @param star resultado STAR del dominio
     * @return el DTO
     */
    static StarResponse from(StarResult star) {
        return new StarResponse(star.questionNumber(), star.questionText(), star.situation(), star.task(),
                star.action(), star.result(), star.level());
    }
}
