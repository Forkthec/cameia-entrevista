package tech.cameia.entrevista.domain.policy;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.InvalidRequestException;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.FieldRejection;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.Modality;
import tech.cameia.entrevista.domain.model.SessionConfigurationDraft;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.domain.model.ValidatedSessionRequest;
import tech.cameia.entrevista.domain.port.RoleCatalog;

/**
 * Reglas de la configuración de una sesión: valida los siete datos que envía la persona y la clave
 * anti-duplicados, y los deja normalizados.
 *
 * <p>Recorre todos los campos sin detenerse en el primer fallo y devuelve una causa por campo, en el orden del
 * formulario. Las opciones se validan contra {@link PersonalizationCatalog} y el rol contra la copia local del
 * catálogo de roles.</p>
 */
public final class SessionConfigurationPolicy {

    /** Nombre del encabezado de la clave anti-duplicados, tal como lo ve el cliente. */
    public static final String IDEMPOTENCY_KEY_FIELD = "Idempotency-Key";

    static final String PROFILE_ID_REQUIRED = "Elige un Perfil Profesional.";
    static final String PROFILE_ID_INVALID = "El Perfil Profesional elegido no es válido.";
    static final String TARGET_ROLE_REQUIRED = "Elige un rol objetivo.";
    static final String TARGET_ROLE_INVALID_FORMAT = "El rol objetivo elegido no es válido.";
    static final String TARGET_ROLE_NOT_IN_CATALOG = "Elige un rol objetivo del catálogo.";
    static final String JOB_OFFER_TOO_LONG = "La oferta laboral no puede superar los "
            + InterviewSession.MAX_JOB_OFFER_LENGTH + " caracteres.";
    static final String MODE_REQUIRED = "Elige la modalidad.";
    static final String MODE_INVALID = "Elige una modalidad de la lista.";
    static final String MODE_NOT_AVAILABLE = "Esta modalidad estará disponible próximamente.";
    static final String TONE_REQUIRED = "Elige el tono del entrevistador.";
    static final String TONE_INVALID = "Elige un tono de la lista.";
    static final String ANSWER_FORMAT_REQUIRED = "Elige la forma de respuesta.";
    static final String ANSWER_FORMAT_INVALID = "Elige una forma de respuesta de la lista.";
    static final String LANGUAGE_REQUIRED = "Elige el idioma de la entrevista.";
    static final String LANGUAGE_INVALID = "Elige un idioma de la lista.";
    static final String IDEMPOTENCY_KEY_INVALID = "Ocurrió un error. Inténtalo de nuevo.";

    private static final Pattern UUID_FORMAT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern IDEMPOTENCY_KEY_FORMAT = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final PersonalizationCatalog catalog;
    private final RoleCatalog roles;

    /**
     * Crea la política.
     *
     * @param catalog opciones de personalización y su disponibilidad
     * @param roles copia local del catálogo de roles
     */
    public SessionConfigurationPolicy(PersonalizationCatalog catalog, RoleCatalog roles) {
        this.catalog = catalog;
        this.roles = roles;
    }

    /**
     * Valida la configuración de una sesión y devuelve todas las causas a la vez, una por campo y en el orden del
     * formulario. Los textos de una línea se recortan; la oferta laboral se normaliza a NFC y se recorta, y vacía
     * queda nula.
     *
     * @param draft lo que envió la persona, sin validar
     * @return la configuración validada y normalizada
     * @throws InvalidRequestException con la lista de rechazos si algún campo falla
     */
    public ValidatedSessionRequest validate(SessionConfigurationDraft draft) {
        List<FieldRejection> rejections = new ArrayList<>();
        UUID profileId = uuid(draft.profileId(), "profileId", ErrorCode.PROFILE_ID_REQUIRED, PROFILE_ID_REQUIRED,
                ErrorCode.PROFILE_ID_INVALID_FORMAT, PROFILE_ID_INVALID, rejections);
        UUID targetRoleId = targetRole(draft.targetRoleId(), rejections);
        String jobOffer = jobOffer(draft.jobOffer(), rejections);
        Modality mode = mode(draft.mode(), rejections);
        Tone tone = option(draft.tone(), "tone", Tone::fromCode, ErrorCode.TONE_REQUIRED, TONE_REQUIRED,
                ErrorCode.TONE_INVALID_VALUE, TONE_INVALID, rejections);
        AnswerMode answerFormat = option(draft.answerFormat(), "answerFormat", AnswerMode::fromCode,
                ErrorCode.ANSWER_FORMAT_REQUIRED, ANSWER_FORMAT_REQUIRED, ErrorCode.ANSWER_FORMAT_INVALID_VALUE,
                ANSWER_FORMAT_INVALID, rejections);
        InterviewLanguage language = option(draft.sessionLanguage(), "sessionLanguage", InterviewLanguage::fromCode,
                ErrorCode.SESSION_LANGUAGE_REQUIRED, LANGUAGE_REQUIRED, ErrorCode.SESSION_LANGUAGE_INVALID_VALUE,
                LANGUAGE_INVALID, rejections);
        String key = idempotencyKey(draft.idempotencyKey(), rejections);

        if (!rejections.isEmpty()) {
            throw new InvalidRequestException(rejections);
        }
        return new ValidatedSessionRequest(profileId, targetRoleId, jobOffer, mode, tone, answerFormat, language, key);
    }

    private UUID targetRole(String raw, List<FieldRejection> rejections) {
        UUID id = uuid(raw, "targetRoleId", ErrorCode.TARGET_ROLE_ID_REQUIRED, TARGET_ROLE_REQUIRED,
                ErrorCode.TARGET_ROLE_ID_INVALID_FORMAT, TARGET_ROLE_INVALID_FORMAT, rejections);
        // El catálogo solo se consulta con un identificador bien formado.
        if (id != null && !roles.isActive(id)) {
            rejections.add(new FieldRejection("targetRoleId", ErrorCode.TARGET_ROLE_ID_INVALID_VALUE,
                    TARGET_ROLE_NOT_IN_CATALOG));
            return null;
        }
        return id;
    }

    private UUID uuid(String raw, String field, ErrorCode required, String requiredMessage, ErrorCode invalid,
            String invalidMessage, List<FieldRejection> rejections) {
        if (isBlank(raw)) {
            rejections.add(new FieldRejection(field, required, requiredMessage));
            return null;
        }
        String text = raw.strip();
        if (!UUID_FORMAT.matcher(text).matches()) {
            rejections.add(new FieldRejection(field, invalid, invalidMessage));
            return null;
        }
        return UUID.fromString(text.toLowerCase(Locale.ROOT));
    }

    private String jobOffer(String raw, List<FieldRejection> rejections) {
        if (isBlank(raw)) {
            return null;
        }
        String offer = Normalizer.normalize(raw, Normalizer.Form.NFC).strip();
        if (offer.codePointCount(0, offer.length()) > InterviewSession.MAX_JOB_OFFER_LENGTH) {
            rejections.add(new FieldRejection("jobOffer", ErrorCode.JOB_OFFER_TOO_LONG, JOB_OFFER_TOO_LONG));
            return null;
        }
        return offer;
    }

    private Modality mode(String raw, List<FieldRejection> rejections) {
        Modality mode = option(raw, "mode", Modality::fromCode, ErrorCode.MODE_REQUIRED, MODE_REQUIRED,
                ErrorCode.MODE_INVALID_VALUE, MODE_INVALID, rejections);
        if (mode != null && !catalog.isAvailable(mode)) {
            rejections.add(new FieldRejection("mode", ErrorCode.MODE_NOT_ALLOWED, MODE_NOT_AVAILABLE));
            return null;
        }
        return mode;
    }

    private <E extends Enum<E>> E option(String raw, String field, Function<String, E> fromCode,
            ErrorCode required, String requiredMessage, ErrorCode invalid, String invalidMessage,
            List<FieldRejection> rejections) {
        if (isBlank(raw)) {
            rejections.add(new FieldRejection(field, required, requiredMessage));
            return null;
        }
        Optional<E> parsed = parse(raw.strip(), fromCode);
        if (parsed.isEmpty()) {
            rejections.add(new FieldRejection(field, invalid, invalidMessage));
        }
        return parsed.orElse(null);
    }

    // Los enumerados solo ofrecen una búsqueda que lanza; aquí se convierte en un resultado vacío.
    private <E extends Enum<E>> Optional<E> parse(String code, Function<String, E> fromCode) {
        try {
            return Optional.of(fromCode.apply(code));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    private String idempotencyKey(String raw, List<FieldRejection> rejections) {
        if (raw == null) {
            return null;
        }
        if (!IDEMPOTENCY_KEY_FORMAT.matcher(raw).matches()) {
            rejections.add(new FieldRejection(IDEMPOTENCY_KEY_FIELD, ErrorCode.IDEMPOTENCY_KEY_INVALID_FORMAT,
                    IDEMPOTENCY_KEY_INVALID));
            return null;
        }
        return raw;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
