package tech.cameia.entrevista.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Configuración de una sesión ya validada y normalizada.
 *
 * @param profileId perfil profesional elegido
 * @param targetRoleId rol objetivo del catálogo
 * @param jobOffer oferta laboral normalizada, o {@code null} si no hay
 * @param mode modalidad disponible
 * @param tone tono del entrevistador
 * @param answerFormat forma de respuesta
 * @param sessionLanguage idioma de la entrevista
 * @param idempotencyKey clave anti-duplicados, o {@code null} si no se envió
 */
public record ValidatedSessionRequest(
        UUID profileId,
        UUID targetRoleId,
        String jobOffer,
        Modality mode,
        Tone tone,
        AnswerMode answerFormat,
        InterviewLanguage sessionLanguage,
        String idempotencyKey) {

    private static final String SEPARATOR = "\n";
    private static final String NO_OFFER = "-";

    /**
     * Huella de la configuración para reconocer una repetición con la misma clave: SHA-256 en hexadecimal (64
     * caracteres, minúsculas) de una cadena canónica con los siete valores ya normalizados, separados por un salto
     * de línea y con {@code "-"} en lugar de una oferta nula. Dos peticiones que solo difieren en espacios o en el
     * orden de los campos dan la misma.
     *
     * @return la huella en hexadecimal
     */
    public String fingerprint() {
        String canonical = String.join(SEPARATOR, profileId.toString(), targetRoleId.toString(),
                jobOffer == null ? NO_OFFER : jobOffer, mode.code(), tone.code(), answerFormat.code(),
                sessionLanguage.code());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM; si faltara, la instalación está rota.
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
