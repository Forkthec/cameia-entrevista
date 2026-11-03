package tech.cameia.entrevista.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Configuración con la que se prepara una sesión. Este objeto solo exige que los datos estén
 * presentes; la validación de su contenido (catálogo, largo de la oferta) es del caso de uso que
 * crea la sesión.
 *
 * @param profileId perfil profesional de la persona
 * @param targetRoleId rol al que aspira
 * @param jobOffer texto libre de la oferta laboral, o {@code null} si no hay
 * @param profileSnapshotJson copia del contexto del perfil al crear la sesión, como objeto JSON
 * @param modality simulación o entrenamiento
 * @param tone tono del entrevistador
 * @param answerMode forma de respuesta
 * @param language idioma de la entrevista
 */
public record SessionConfiguration(
        UUID profileId,
        UUID targetRoleId,
        String jobOffer,
        String profileSnapshotJson,
        Modality modality,
        Tone tone,
        AnswerMode answerMode,
        InterviewLanguage language) {

    /** Exige todos los datos salvo la oferta laboral, que es opcional. */
    public SessionConfiguration {
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(targetRoleId, "targetRoleId");
        Objects.requireNonNull(profileSnapshotJson, "profileSnapshotJson");
        Objects.requireNonNull(modality, "modality");
        Objects.requireNonNull(tone, "tone");
        Objects.requireNonNull(answerMode, "answerMode");
        Objects.requireNonNull(language, "language");
    }
}
