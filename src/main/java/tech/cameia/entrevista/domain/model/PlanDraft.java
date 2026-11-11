package tech.cameia.entrevista.domain.model;

import java.util.List;

/**
 * El plan que propone el modelo: el idioma en que dice haber escrito y sus preguntas, antes de validarlas.
 *
 * @param language código de idioma declarado por el modelo
 * @param questions preguntas en el orden en que llegaron
 */
public record PlanDraft(String language, List<PlannedQuestion> questions) {
}
