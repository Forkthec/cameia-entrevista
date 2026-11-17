package tech.cameia.entrevista.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import tech.cameia.entrevista.domain.model.AnswerAttempt.AnswerOrigin;
import tech.cameia.entrevista.domain.model.EvaluationReport;
import tech.cameia.entrevista.domain.model.QuestionType;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.domain.model.VoiceSample;

/**
 * Resultado de pedir el reporte: o ya está emitido, con lo que la pantalla muestra de la sesión, o lo está
 * generando otra petición en este momento.
 */
public sealed interface ReportOutcome {

    /**
     * Reporte emitido.
     *
     * @param report el reporte
     * @param interviewDate momento en que terminó la entrevista, a segundos
     * @param tone tono de la entrevista
     * @param questions las cinco preguntas de la sesión con su respuesta definitiva, en orden de número
     */
    record Issued(EvaluationReport report, Instant interviewDate, Tone tone, List<QuestionRow> questions)
            implements ReportOutcome {

        /** Exige todas las partes y copia la lista. */
        public Issued {
            Objects.requireNonNull(report, "report");
            Objects.requireNonNull(interviewDate, "interviewDate");
            Objects.requireNonNull(tone, "tone");
            questions = List.copyOf(questions);
        }
    }

    /** Otra petición tiene la marca de generación viva: se consulta de nuevo. */
    record Generating() implements ReportOutcome {
    }

    /**
     * Una pregunta de la sesión con su respuesta, si la hay.
     *
     * @param number número de la pregunta, de 1 a 5
     * @param type tipo de la pregunta
     * @param questionText texto con que se le mostró a la persona
     * @param answerText respuesta definitiva, o {@code null} si no hay
     * @param origin si se escribió o se dijo; {@code null} si no hay respuesta
     * @param voice datos de voz de la respuesta; {@code null} si fue escrita o no los tiene
     */
    record QuestionRow(int number, QuestionType type, String questionText, String answerText, AnswerOrigin origin,
            VoiceSample voice) {

        @Override
        public String toString() {
            return "QuestionRow[number=" + number + ", type=" + type + ", origin=" + origin + "]";
        }
    }
}
