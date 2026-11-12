package tech.cameia.entrevista.domain.policy;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.InvalidRequestException;
import tech.cameia.entrevista.domain.model.FieldRejection;
import tech.cameia.entrevista.domain.model.ValidatedAnswerRequest;

/**
 * Reglas de forma de la respuesta escrita: devuelve la respuesta validada o todos los errores a la vez, uno por
 * campo y en el orden {@code turnId}, {@code answerText}, {@code Idempotency-Key}.
 *
 * <p>El largo se cuenta en puntos de código Unicode del texto ya normalizado a NFC y sin recortar: un emoji vale uno
 * y «é» en dos piezas vale uno. El texto se guarda tal como llegó, con sus espacios.</p>
 */
public final class AnswerRequestPolicy {

    /** Largo máximo de la respuesta, en puntos de código; el de la columna. */
    public static final int MAX_ANSWER_LENGTH = 4000;

    static final String GENERIC = "Ocurrió un error. Inténtalo de nuevo.";
    static final String ANSWER_REQUIRED = "Ingresa tu respuesta.";
    static final String ANSWER_TOO_LONG = "Tu respuesta no puede superar los " + MAX_ANSWER_LENGTH + " caracteres.";
    static final String ANSWER_INVALID_CHARACTERS = "Tu respuesta contiene un carácter no permitido.";

    private static final String TURN_ID_FIELD = "turnId";
    private static final String ANSWER_FIELD = "answerText";
    private static final int UUID_LENGTH = 36;
    private static final char NUL = (char) 0;

    private static final Pattern UUID_FORMAT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern KEY_FORMAT = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    /**
     * Valida los tres datos de la petición.
     *
     * @param rawTurnId turno que se responde, tal como llegó
     * @param rawAnswer respuesta escrita, tal como llegó
     * @param rawKey valor del encabezado de la clave anti-duplicados
     * @return la respuesta validada y normalizada
     * @throws InvalidRequestException con un elemento por cada campo inválido
     */
    public ValidatedAnswerRequest validate(String rawTurnId, String rawAnswer, String rawKey) {
        List<FieldRejection> rejections = new ArrayList<>();
        UUID turnId = checkTurnId(rawTurnId, rejections);
        String answer = checkAnswer(rawAnswer, rejections);
        String key = checkKey(rawKey, rejections);
        if (!rejections.isEmpty()) {
            throw new InvalidRequestException(rejections);
        }
        return new ValidatedAnswerRequest(turnId, answer, key);
    }

    private UUID checkTurnId(String raw, List<FieldRejection> rejections) {
        if (raw == null || raw.isBlank()) {
            rejections.add(new FieldRejection(TURN_ID_FIELD, ErrorCode.TURN_ID_REQUIRED, GENERIC));
            return null;
        }
        if (raw.length() != UUID_LENGTH || !UUID_FORMAT.matcher(raw).matches()) {
            rejections.add(new FieldRejection(TURN_ID_FIELD, ErrorCode.TURN_ID_INVALID_FORMAT, GENERIC));
            return null;
        }
        return UUID.fromString(raw);
    }

    private String checkAnswer(String raw, List<FieldRejection> rejections) {
        if (raw == null) {
            rejections.add(new FieldRejection(ANSWER_FIELD, ErrorCode.ANSWER_TEXT_REQUIRED, ANSWER_REQUIRED));
            return null;
        }
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFC);
        if (normalized.codePoints().allMatch(AnswerRequestPolicy::isBlankPoint)) {
            rejections.add(new FieldRejection(ANSWER_FIELD, ErrorCode.ANSWER_TEXT_REQUIRED, ANSWER_REQUIRED));
        } else if (normalized.codePointCount(0, normalized.length()) > MAX_ANSWER_LENGTH) {
            rejections.add(new FieldRejection(ANSWER_FIELD, ErrorCode.ANSWER_TEXT_TOO_LONG, ANSWER_TOO_LONG));
        } else if (hasForbiddenCharacter(normalized)) {
            rejections.add(new FieldRejection(ANSWER_FIELD, ErrorCode.ANSWER_TEXT_INVALID_CHARACTERS,
                    ANSWER_INVALID_CHARACTERS));
        } else {
            return normalized;
        }
        return null;
    }

    private String checkKey(String raw, List<FieldRejection> rejections) {
        String field = SessionConfigurationPolicy.IDEMPOTENCY_KEY_FIELD;
        if (raw == null || raw.isEmpty()) {
            rejections.add(new FieldRejection(field, ErrorCode.IDEMPOTENCY_KEY_REQUIRED, GENERIC));
            return null;
        }
        if (!KEY_FORMAT.matcher(raw).matches()) {
            rejections.add(new FieldRejection(field, ErrorCode.IDEMPOTENCY_KEY_INVALID_FORMAT, GENERIC));
            return null;
        }
        return raw;
    }

    /** Espacio en el sentido amplio de la regla: separadores de Unicode y los invisibles que no muestran nada. */
    private static boolean isBlankPoint(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || codePoint == 0x200B
                || codePoint == 0x200C || codePoint == 0x200D || codePoint == 0x2060 || codePoint == 0xFEFF;
    }

    /** El carácter nulo o un sustituto UTF-16 sin su pareja: la base de datos no los guarda. */
    private static boolean hasForbiddenCharacter(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == NUL) {
                return true;
            }
            if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                i++;
            } else if (Character.isSurrogate(c)) {
                return true;
            }
        }
        return false;
    }
}
