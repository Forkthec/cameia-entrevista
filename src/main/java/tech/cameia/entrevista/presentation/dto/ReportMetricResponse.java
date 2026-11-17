package tech.cameia.entrevista.presentation.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Una métrica del reporte. {@code value} en {@code null} significa «sin resultado»: nunca se manda 0 en su lugar.
 *
 * @param code identificador de la métrica
 * @param group {@code CONTENT} o {@code VOICE}
 * @param value valor con la escala guardada (2 decimales en contenido, 1 en pausas y vacilación, entero en ritmo), o
 *        {@code null}
 * @param unit unidad fija de la métrica; {@code null} solo en el Entusiasmo
 */
@Schema(description = "Una métrica del reporte; value null = sin resultado")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReportMetricResponse(
        @Schema(description = "Identificador de la métrica", allowableValues = {"PRECISION_CLARITY", "VERBAL_CHARISMA",
                "INCLUSIVE_LANGUAGE", "SPEECH_RATE", "PAUSE_TIME", "HESITATION_RATE", "ENTHUSIASM"},
                example = "PRECISION_CLARITY") String code,
        @Schema(description = "Grupo de la métrica", allowableValues = {"CONTENT", "VOICE"}, example = "CONTENT")
        String group,
        @Schema(description = "Valor con la escala guardada; null si no se pudo calcular", nullable = true,
                example = "3.46") BigDecimal value,
        @Schema(description = "Unidad fija de la métrica; null solo en ENTHUSIASM", nullable = true,
                allowableValues = {"SCORE_1_5", "WORDS_PER_MINUTE", "PERCENT", "PER_100_WORDS"},
                example = "SCORE_1_5") String unit) {
}
