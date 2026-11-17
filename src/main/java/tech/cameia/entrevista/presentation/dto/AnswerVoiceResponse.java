package tech.cameia.entrevista.presentation.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

import tech.cameia.entrevista.domain.model.VoiceMetrics;
import tech.cameia.entrevista.domain.model.VoiceSample;

/**
 * Datos de voz de una respuesta por voz y sus métricas, calculadas con las mismas fórmulas que las del reporte.
 *
 * @param words palabras de la respuesta
 * @param fillers muletillas
 * @param speechRate palabras por minuto, o {@code null} si no se pudo calcular
 * @param pauseTime porcentaje del tiempo en pausas, o {@code null}
 * @param hesitationRate muletillas por cada 100 palabras, o {@code null}
 */
@Schema(description = "Datos de voz de una respuesta por voz; null en la pregunta si fue escrita o no hay datos")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AnswerVoiceResponse(
        @Schema(example = "56") int words,
        @Schema(example = "4") int fillers,
        @Schema(description = "Palabras por minuto", nullable = true, example = "110") Integer speechRate,
        @Schema(description = "Porcentaje del tiempo en pausas", nullable = true, example = "28.3")
        BigDecimal pauseTime,
        @Schema(description = "Muletillas por cada 100 palabras", nullable = true, example = "7.1")
        BigDecimal hesitationRate) {

    /**
     * Traduce los datos de voz de una respuesta al contrato.
     *
     * @param sample los cuatro datos guardados; {@code null} si no hay
     * @return el DTO, o {@code null} si no hay datos
     */
    static AnswerVoiceResponse from(VoiceSample sample) {
        if (sample == null) {
            return null;
        }
        VoiceMetrics metrics = VoiceMetrics.of(java.util.List.of(sample));
        return new AnswerVoiceResponse(sample.words(), sample.fillers(), metrics.speechRate(), metrics.pauseTime(),
                metrics.hesitationRate());
    }
}
