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
    private static final String NO_OFFER = "0:";
    private static final String OFFER_PREFIX = "1:";
    private static final String HASH_ALGORITHM = "SHA-256";

    /**
     * Huella de la configuración para reconocer una repetición con la misma clave: SHA-256 en hexadecimal (64
     * caracteres, minúsculas) de una cadena canónica con los siete valores ya normalizados, separados por un salto
     * de línea. La oferta se codifica con un prefijo ({@code "0:"} si es nula, {@code "1:"} seguido del texto si existe) para
     * que una oferta ausente nunca coincida con una oferta cuyo texto sea «-». Dos peticiones que solo difieren en espacios o en el
     * orden de los campos dan la misma.
     *
     * @return la huella en hexadecimal
     */
    public String fingerprint() {
        String canonical = String.join(SEPARATOR, profileId.toString(), targetRoleId.toString(),
                jobOffer == null ? NO_OFFER : OFFER_PREFIX + jobOffer, mode.code(), tone.code(), answerFormat.code(),
                sessionLanguage.code());
        return sha256Hex(HASH_ALGORITHM, canonical);
    }

    // El algoritmo es parámetro para poder probar el camino en que la JVM no lo trae.
    static String sha256Hex(String algorithm, String text) {
        try {
            byte[] digest = MessageDigest.getInstance(algorithm).digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 es obligatorio en toda JVM; si faltara, la instalación está rota.
            throw new IllegalStateException(algorithm + " no disponible", e);
        }
    }
}
