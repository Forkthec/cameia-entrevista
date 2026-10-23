package tech.cameia.entrevista.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.text.Normalizer;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.InvalidRequestException;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.FieldRejection;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.Modality;
import tech.cameia.entrevista.domain.model.SessionConfigurationDraft;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.domain.model.ValidatedSessionRequest;

class SessionConfigurationPolicyTest {

    private static final String PROFILE = "7c9e6679-7425-40de-944b-e07fc1f90ae7";
    private static final String ROLE = "a0000001-0000-0000-0000-000000000002";
    private static final String OTHER_ACTIVE_ROLE = "a0000001-0000-0000-0000-000000000001";
    private static final String UNKNOWN_ROLE = "00000000-0000-0000-0000-000000000000";

    private final AtomicInteger roleQueries = new AtomicInteger();
    private final Set<UUID> activeRoles = Set.of(UUID.fromString(ROLE), UUID.fromString(OTHER_ACTIVE_ROLE));
    private final SessionConfigurationPolicy policy = new SessionConfigurationPolicy(new PersonalizationCatalog(),
            roleId -> {
                roleQueries.incrementAndGet();
                return activeRoles.contains(roleId);
            });

    private static SessionConfigurationDraft valid() {
        return new SessionConfigurationDraft(PROFILE, ROLE, null, "SIMULACION", "CALIDO", "TEXTO", "es-CO", null);
    }

    private static SessionConfigurationDraft with(String profile, String role, String offer, String mode,
            String tone, String format, String language, String key) {
        return new SessionConfigurationDraft(profile, role, offer, mode, tone, format, language, key);
    }

    private List<FieldRejection> rejectionsOf(SessionConfigurationDraft draft) {
        InvalidRequestException error = org.junit.jupiter.api.Assertions.assertThrows(InvalidRequestException.class,
                () -> policy.validate(draft));
        return error.rejections();
    }

    private void assertSingleRejection(SessionConfigurationDraft draft, String field, ErrorCode code,
            String message) {
        assertThat(rejectionsOf(draft)).containsExactly(new FieldRejection(field, code, message));
    }

    @Test
    @DisplayName("Una configuración válida se devuelve normalizada")
    void validate_shouldReturnNormalized_whenValid() {
        ValidatedSessionRequest result = policy.validate(
                with(PROFILE, ROLE, "  Buscamos  ", " SIMULACION ", "CALIDO", "TEXTO", "es-CO", "clave-1"));

        assertThat(result.profileId()).isEqualTo(UUID.fromString(PROFILE));
        assertThat(result.targetRoleId()).isEqualTo(UUID.fromString(ROLE));
        assertThat(result.jobOffer()).isEqualTo("Buscamos");
        assertThat(result.mode()).isEqualTo(Modality.SIMULATION);
        assertThat(result.tone()).isEqualTo(Tone.WARM);
        assertThat(result.answerFormat()).isEqualTo(AnswerMode.TEXT);
        assertThat(result.sessionLanguage()).isEqualTo(InterviewLanguage.SPANISH_COLOMBIA);
        assertThat(result.idempotencyKey()).isEqualTo("clave-1");
    }

    @Test
    @DisplayName("Con todo ausente salen los seis obligatorios en el orden del formulario")
    void validate_shouldReportSixRequired_whenAllMissing() {
        List<FieldRejection> rejections = rejectionsOf(with(null, null, null, null, null, null, null, null));

        assertThat(rejections).extracting(FieldRejection::field).containsExactly("profileId", "targetRoleId", "mode",
                "tone", "answerFormat", "sessionLanguage");
        assertThat(rejections).extracting(FieldRejection::code).containsExactly(ErrorCode.PROFILE_ID_REQUIRED,
                ErrorCode.TARGET_ROLE_ID_REQUIRED, ErrorCode.MODE_REQUIRED, ErrorCode.TONE_REQUIRED,
                ErrorCode.ANSWER_FORMAT_REQUIRED, ErrorCode.SESSION_LANGUAGE_REQUIRED);
    }

    @Test
    @DisplayName("Todos los valores fuera del catálogo se informan a la vez")
    void validate_shouldRejectEveryOutOfCatalogValue_atOnce() {
        List<FieldRejection> rejections = rejectionsOf(
                with(PROFILE, UNKNOWN_ROLE, null, "ENTRENO", "AMABLE", "VIDEO", "fr", null));

        assertThat(rejections).extracting(FieldRejection::code).containsExactly(
                ErrorCode.TARGET_ROLE_ID_INVALID_VALUE, ErrorCode.MODE_NOT_ALLOWED, ErrorCode.TONE_INVALID_VALUE,
                ErrorCode.ANSWER_FORMAT_INVALID_VALUE, ErrorCode.SESSION_LANGUAGE_INVALID_VALUE);
    }

    @Test
    @DisplayName("La clave inválida se suma a las demás causas, con el nombre del encabezado")
    void validate_shouldReportKeyAfterBodyFields() {
        List<FieldRejection> rejections = rejectionsOf(with(null, ROLE, null, "SIMULACION", "CALIDO", "TEXTO",
                "es-CO", "a b"));

        assertThat(rejections).extracting(FieldRejection::field).containsExactly("profileId", "Idempotency-Key");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    @DisplayName("profileId ausente o en blanco")
    void validate_shouldReject_whenProfileIdBlank(String value) {
        assertSingleRejection(with(value, ROLE, null, "SIMULACION", "CALIDO", "TEXTO", "es-CO", null), "profileId",
                ErrorCode.PROFILE_ID_REQUIRED, "Elige un Perfil Profesional.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"perfil-1", "7c9e6679742540de944be07fc1f90ae7", "7c9e6679-7425-40de-944b-e07fc1f90ae7x",
            "7c9e6679-7425-40de-944b-e07fc1f90aeg"})
    @DisplayName("profileId que no es un UUID con guiones")
    void validate_shouldReject_whenProfileIdInvalid(String value) {
        assertSingleRejection(with(value, ROLE, null, "SIMULACION", "CALIDO", "TEXTO", "es-CO", null), "profileId",
                ErrorCode.PROFILE_ID_INVALID_FORMAT, "El Perfil Profesional elegido no es válido.");
    }

    @Test
    @DisplayName("Un UUID en mayúsculas se acepta y se guarda en minúsculas")
    void validate_shouldAcceptUppercaseUuid() {
        ValidatedSessionRequest result = policy.validate(with("7C9E6679-7425-40DE-944B-E07FC1F90AE7", ROLE, null,
                "SIMULACION", "CALIDO", "TEXTO", "es-CO", null));

        assertThat(result.profileId()).hasToString(PROFILE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    @DisplayName("targetRoleId ausente o en blanco")
    void validate_shouldReject_whenTargetRoleIdBlank(String value) {
        assertSingleRejection(with(PROFILE, value, null, "SIMULACION", "CALIDO", "TEXTO", "es-CO", null),
                "targetRoleId", ErrorCode.TARGET_ROLE_ID_REQUIRED, "Elige un rol objetivo.");
        assertThat(roleQueries).hasValue(0);
    }

    @Test
    @DisplayName("targetRoleId con formato inválido no consulta el catálogo")
    void validate_shouldNotQueryRoles_whenFormatInvalid() {
        assertSingleRejection(with(PROFILE, "backend", null, "SIMULACION", "CALIDO", "TEXTO", "es-CO", null),
                "targetRoleId", ErrorCode.TARGET_ROLE_ID_INVALID_FORMAT, "El rol objetivo elegido no es válido.");
        assertThat(roleQueries).hasValue(0);
    }

    @Test
    @DisplayName("targetRoleId que no está activo en el catálogo")
    void validate_shouldReject_whenTargetRoleNotInCatalog() {
        assertSingleRejection(with(PROFILE, UNKNOWN_ROLE, null, "SIMULACION", "CALIDO", "TEXTO", "es-CO", null),
                "targetRoleId", ErrorCode.TARGET_ROLE_ID_INVALID_VALUE, "Elige un rol objetivo del catálogo.");
        assertThat(roleQueries).hasValue(1);
    }

    @Test
    @DisplayName("Un rol activo se acepta aunque no sea de los que tiene el perfil")
    void validate_shouldAcceptActiveRoleNotInProfile() {
        assertThat(policy.validate(with(PROFILE, OTHER_ACTIVE_ROLE, null, "SIMULACION", "CALIDO", "TEXTO", "es-CO",
                null)).targetRoleId()).hasToString(OTHER_ACTIVE_ROLE);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    @DisplayName("La oferta ausente o en blanco queda nula")
    void validate_shouldReturnNullOffer_whenBlank(String value) {
        assertThat(policy.validate(with(PROFILE, ROLE, value, "SIMULACION", "CALIDO", "TEXTO", "es-CO", null))
                .jobOffer()).isNull();
    }

    @Test
    @DisplayName("3000 puntos de código se aceptan completos, también los de dos unidades UTF-16")
    void validate_shouldAccept3000CodePointOffer() {
        String emojis = "😀".repeat(3000);
        String accents = "á".repeat(3000);

        assertThat(policy.validate(with(PROFILE, ROLE, emojis, "SIMULACION", "CALIDO", "TEXTO", "es-CO", null))
                .jobOffer()).isEqualTo(emojis);
        assertThat(policy.validate(with(PROFILE, ROLE, accents, "SIMULACION", "CALIDO", "TEXTO", "es-CO", null))
                .jobOffer()).isEqualTo(accents);
    }

    @Test
    @DisplayName("3001 caracteres se rechazan")
    void validate_shouldReject3001Offer() {
        assertSingleRejection(with(PROFILE, ROLE, "a".repeat(3001), "SIMULACION", "CALIDO", "TEXTO", "es-CO", null),
                "jobOffer", ErrorCode.JOB_OFFER_TOO_LONG, "La oferta laboral no puede superar los 3000 caracteres.");
        assertSingleRejection(with(PROFILE, ROLE, "😀".repeat(3001), "SIMULACION", "CALIDO", "TEXTO", "es-CO", null),
                "jobOffer", ErrorCode.JOB_OFFER_TOO_LONG, "La oferta laboral no puede superar los 3000 caracteres.");
    }

    @Test
    @DisplayName("Una oferta en NFD que en NFC cabe se acepta y se guarda en NFC")
    void validate_shouldAcceptNfdThatFitsInNfc() {
        String nfd = Normalizer.normalize("é".repeat(3000), Normalizer.Form.NFD);

        String saved = policy.validate(with(PROFILE, ROLE, nfd, "SIMULACION", "CALIDO", "TEXTO", "es-CO", null))
                .jobOffer();

        assertThat(nfd.codePointCount(0, nfd.length())).isEqualTo(6000);
        assertThat(saved).isEqualTo("é".repeat(3000));
    }

    @Test
    @DisplayName("Los espacios de los extremos no cuentan para el largo y se recortan")
    void validate_shouldStripOfferEdges() {
        String offer = "a".repeat(3000);

        assertThat(policy.validate(with(PROFILE, ROLE, "  " + offer + "  ", "SIMULACION", "CALIDO", "TEXTO", "es-CO",
                null)).jobOffer()).isEqualTo(offer);
    }

    @Test
    @DisplayName("El texto hostil se acepta literal: la política no lo interpreta")
    void validate_shouldKeepHostileOfferLiteral() {
        String hostile = "<script>alert(1)</script> ' % _";

        assertThat(policy.validate(with(PROFILE, ROLE, hostile, "SIMULACION", "CALIDO", "TEXTO", "es-CO", null))
                .jobOffer()).isEqualTo(hostile);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("mode ausente o en blanco")
    void validate_shouldReject_whenModeBlank(String value) {
        assertSingleRejection(with(PROFILE, ROLE, null, value, "CALIDO", "TEXTO", "es-CO", null), "mode",
                ErrorCode.MODE_REQUIRED, "Elige la modalidad.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"VIDEO", "simulacion", "5", "SIMULATION"})
    @DisplayName("mode desconocido o con otras mayúsculas")
    void validate_shouldReject_whenModeInvalid(String value) {
        assertSingleRejection(with(PROFILE, ROLE, null, value, "CALIDO", "TEXTO", "es-CO", null), "mode",
                ErrorCode.MODE_INVALID_VALUE, "Elige una modalidad de la lista.");
    }

    @Test
    @DisplayName("ENTRENO existe pero no está disponible")
    void validate_shouldReject_whenModeNotAvailable() {
        assertSingleRejection(with(PROFILE, ROLE, null, "ENTRENO", "CALIDO", "TEXTO", "es-CO", null), "mode",
                ErrorCode.MODE_NOT_ALLOWED, "Esta modalidad estará disponible próximamente.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("tone ausente o en blanco")
    void validate_shouldReject_whenToneBlank(String value) {
        assertSingleRejection(with(PROFILE, ROLE, null, "SIMULACION", value, "TEXTO", "es-CO", null), "tone",
                ErrorCode.TONE_REQUIRED, "Elige el tono del entrevistador.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"AMABLE", "calido"})
    @DisplayName("tone desconocido")
    void validate_shouldReject_whenToneInvalid(String value) {
        assertSingleRejection(with(PROFILE, ROLE, null, "SIMULACION", value, "TEXTO", "es-CO", null), "tone",
                ErrorCode.TONE_INVALID_VALUE, "Elige un tono de la lista.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("answerFormat ausente o en blanco")
    void validate_shouldReject_whenAnswerFormatBlank(String value) {
        assertSingleRejection(with(PROFILE, ROLE, null, "SIMULACION", "CALIDO", value, "es-CO", null),
                "answerFormat", ErrorCode.ANSWER_FORMAT_REQUIRED, "Elige la forma de respuesta.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"VIDEO", "texto"})
    @DisplayName("answerFormat desconocido")
    void validate_shouldReject_whenAnswerFormatInvalid(String value) {
        assertSingleRejection(with(PROFILE, ROLE, null, "SIMULACION", "CALIDO", value, "es-CO", null),
                "answerFormat", ErrorCode.ANSWER_FORMAT_INVALID_VALUE, "Elige una forma de respuesta de la lista.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("sessionLanguage ausente o en blanco")
    void validate_shouldReject_whenSessionLanguageBlank(String value) {
        assertSingleRejection(with(PROFILE, ROLE, null, "SIMULACION", "CALIDO", "TEXTO", value, null),
                "sessionLanguage", ErrorCode.SESSION_LANGUAGE_REQUIRED, "Elige el idioma de la entrevista.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"es", "ES-CO", "fr"})
    @DisplayName("sessionLanguage desconocido")
    void validate_shouldReject_whenSessionLanguageInvalid(String value) {
        assertSingleRejection(with(PROFILE, ROLE, null, "SIMULACION", "CALIDO", "TEXTO", value, null),
                "sessionLanguage", ErrorCode.SESSION_LANGUAGE_INVALID_VALUE, "Elige un idioma de la lista.");
    }

    @Test
    @DisplayName("Sin clave la petición se acepta; con 64 caracteres válidos también")
    void validate_shouldAcceptAbsentOrMaxLengthKey() {
        assertThat(policy.validate(valid()).idempotencyKey()).isNull();
        String key = "k".repeat(64);

        assertThat(policy.validate(with(PROFILE, ROLE, null, "SIMULACION", "CALIDO", "TEXTO", "es-CO", key))
                .idempotencyKey()).isEqualTo(key);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a b", "a/b", " ", "clave\n", "ñ"})
    @DisplayName("Clave vacía, con espacio, con barra, con salto de línea o fuera de ASCII")
    void validate_shouldReject_whenIdempotencyKeyInvalid(String value) {
        assertSingleRejection(with(PROFILE, ROLE, null, "SIMULACION", "CALIDO", "TEXTO", "es-CO", value),
                "Idempotency-Key", ErrorCode.IDEMPOTENCY_KEY_INVALID_FORMAT, "Ocurrió un error. Inténtalo de nuevo.");
    }

    @Test
    @DisplayName("Una clave de 65 caracteres se rechaza")
    void validate_shouldReject_whenIdempotencyKeyTooLong() {
        assertThatThrownBy(() -> policy.validate(with(PROFILE, ROLE, null, "SIMULACION", "CALIDO", "TEXTO", "es-CO",
                "k".repeat(65)))).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("Dos peticiones que solo difieren en espacios dan la misma huella")
    void validate_shouldGiveSameFingerprint_whenOnlyWhitespaceDiffers() {
        ValidatedSessionRequest first = policy.validate(
                with(PROFILE, ROLE, "Buscamos", "SIMULACION", "CALIDO", "TEXTO", "es-CO", null));
        ValidatedSessionRequest second = policy.validate(
                with(" " + PROFILE + " ", ROLE, "  Buscamos \n", " SIMULACION", "CALIDO ", "TEXTO", "es-CO", null));

        assertThat(second.fingerprint()).isEqualTo(first.fingerprint());
    }

    @Test
    @DisplayName("La excepción lleva una copia inmutable de los rechazos y el mensaje fijo")
    void invalidRequest_shouldCarryImmutableRejections() {
        InvalidRequestException error = new InvalidRequestException(List.of());

        assertThat(error.getMessage()).isEqualTo("Revisa los campos marcados.");
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThatThrownBy(() -> error.rejections().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }
}
