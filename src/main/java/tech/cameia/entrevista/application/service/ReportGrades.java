package tech.cameia.entrevista.application.service;

import java.util.List;

import tech.cameia.entrevista.domain.model.ContentGrade;

/**
 * Lo que el modelo calificó, ya validado: la nota de cada pregunta no conductual y las marcas STAR de cada
 * conductual.
 *
 * @param content notas de contenido, una por pregunta no conductual enviada
 * @param star marcas STAR, una por pregunta conductual enviada
 */
public record ReportGrades(List<ContentGrade> content, List<StarMarks> star) {

    /** Copia las listas para que no cambien después de validarse. */
    public ReportGrades {
        content = List.copyOf(content);
        star = List.copyOf(star);
    }

    /**
     * Los cuatro componentes del método STAR de una pregunta conductual.
     *
     * @param number número de la pregunta
     * @param situation si describe el contexto concreto
     * @param task si dice qué le correspondía lograr
     * @param action si explica qué hizo la persona
     * @param result si cuenta cómo terminó
     */
    public record StarMarks(int number, boolean situation, boolean task, boolean action, boolean result) {
    }
}
