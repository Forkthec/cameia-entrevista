package tech.cameia.entrevista.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.InvalidRequestException;
import tech.cameia.entrevista.domain.model.FieldRejection;
import tech.cameia.entrevista.domain.model.ValidatedAnswerRequest;

class AnswerRequestPolicyTest {

    private static final String TURN = "9b2e6d44-5a3c-4f0b-8e71-2c4d6f8a0b12";
    private static final String KEY = "clave-respuesta-1";

    private final AnswerRequestPolicy policy = new AnswerRequestPolicy();

    private List<FieldRejection> rejectionsOf(String turn, String answer, String key) {
        return Assertions.assertThrows(InvalidRequestException.class, () -> policy.validate(turn, answer, key))
                .rejections();
    }

    @Test
    @DisplayName("Acepta exactamente 4000 puntos de código")
    void validate_shouldAccept_whenExactly4000CodePoints() {
        ValidatedAnswerRequest request = policy.validate(TURN, "a".repeat(4000), KEY);

        assertThat(request.answerText()).hasSize(4000);
        assertThat(request.turnId()).isEqualTo(UUID.fromString(TURN));
        assertThat(request.idempotencyKey()).isEqualTo(KEY);
    }

    @Test
    @DisplayName("Acepta 3999 puntos de código")
    void validate_shouldAccept_when3999CodePoints() {
        assertThat(policy.validate(TURN, "a".repeat(3999), KEY).answerText()).hasSize(3999);
    }

    @Test
    @DisplayName("Cuatro mil emoji son 4000 puntos de código aunque sean 8000 unidades UTF-16")
    void validate_shouldAccept_whenFourThousandEmojis() {
        assertThat(policy.validate(TURN, "😀".repeat(4000), KEY).answerText()).isNotNull();
    }

    @Test
    @DisplayName("Cuatro mil «é» en dos piezas se normalizan a 4000 y se guardan normalizados")
    void validate_shouldAccept_whenNfdFitsInNfc() {
        String saved = policy.validate(TURN, "é".repeat(4000), KEY).answerText();

        assertThat(saved).isEqualTo("é".repeat(4000));
    }

    @Test
    @DisplayName("4001 puntos de código se rechazan con ANSWER_TEXT_TOO_LONG")
    void validate_shouldRejectTooLong_when4001CodePoints() {
        assertThat(rejectionsOf(TURN, "a".repeat(4001), KEY)).singleElement().satisfies(rejection -> {
            assertThat(rejection.field()).isEqualTo("answerText");
            assertThat(rejection.code()).isEqualTo(ErrorCode.ANSWER_TEXT_TOO_LONG);
            assertThat(rejection.message()).isEqualTo("Tu respuesta no puede superar los 4000 caracteres.");
        });
    }

    @Test
    @DisplayName("Los espacios cuentan: 4000 caracteres con dos espacios delante son 4002")
    void validate_shouldRejectTooLong_whenSpacesPushOver() {
        assertThat(rejectionsOf(TURN, "  " + "a".repeat(3999), KEY)).singleElement()
                .extracting(FieldRejection::code).isEqualTo(ErrorCode.ANSWER_TEXT_TOO_LONG);
    }

    @ParameterizedTest(name = "[{index}]")
    @ValueSource(strings = {"", " ", "\t", "\n\n", " ", "​", " \t\n  ", "　",
            "﻿⁠‌‍"})
    @DisplayName("Una respuesta vacía o solo de blancos e invisibles es ANSWER_TEXT_REQUIRED")
    void validate_shouldRejectRequired_whenBlank(String blank) {
        assertThat(rejectionsOf(TURN, blank, KEY)).singleElement().satisfies(rejection -> {
            assertThat(rejection.code()).isEqualTo(ErrorCode.ANSWER_TEXT_REQUIRED);
            assertThat(rejection.message()).isEqualTo("Ingresa tu respuesta.");
        });
    }

    @Test
    @DisplayName("Una respuesta nula es ANSWER_TEXT_REQUIRED")
    void validate_shouldRejectRequired_whenNull() {
        assertThat(rejectionsOf(TURN, null, KEY)).singleElement().extracting(FieldRejection::code)
                .isEqualTo(ErrorCode.ANSWER_TEXT_REQUIRED);
    }

    @ParameterizedTest(name = "[{index}]")
    @ValueSource(strings = {"Hola\u0000", "\uD800x", "x\uDC00", "\uD83D"})
    @DisplayName("El carácter nulo y los sustitutos sueltos son ANSWER_TEXT_INVALID_CHARACTERS")
    void validate_shouldRejectInvalidCharacters(String text) {
        assertThat(rejectionsOf(TURN, text, KEY)).singleElement().satisfies(rejection -> {
            assertThat(rejection.code()).isEqualTo(ErrorCode.ANSWER_TEXT_INVALID_CHARACTERS);
            assertThat(rejection.message()).isEqualTo("Tu respuesta contiene un carácter no permitido.");
        });
    }

    @ParameterizedTest(name = "[{index}]")
    @ValueSource(strings = {"<script>alert(1)</script>", "'; DROP TABLE x;--", "100% _x_", "123"})
    @DisplayName("El texto hostil se acepta y se guarda literal")
    void validate_shouldKeepHostileTextLiteral(String text) {
        assertThat(policy.validate(TURN, text, KEY).answerText()).isEqualTo(text);
    }

    @Test
    @DisplayName("Los espacios al inicio y al final se conservan")
    void validate_shouldKeepLeadingAndTrailingSpaces() {
        assertThat(policy.validate(TURN, "  hola  ", KEY).answerText()).isEqualTo("  hola  ");
    }

    @Test
    @DisplayName("Los tres campos inválidos se devuelven a la vez y en orden")
    void validate_shouldReportAllFieldsAtOnce() {
        List<FieldRejection> rejections = rejectionsOf(null, "", "");

        assertThat(rejections).extracting(FieldRejection::field)
                .containsExactly("turnId", "answerText", "Idempotency-Key");
        assertThat(rejections).extracting(FieldRejection::code).containsExactly(ErrorCode.TURN_ID_REQUIRED,
                ErrorCode.ANSWER_TEXT_REQUIRED, ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
    }

    @ParameterizedTest(name = "[{index}]")
    @ValueSource(strings = {"", "   "})
    @DisplayName("Un turnId vacío o en blanco es TURN_ID_REQUIRED")
    void validate_shouldRejectTurnIdRequired(String turn) {
        assertThat(rejectionsOf(turn, "hola", KEY)).singleElement().extracting(FieldRejection::code)
                .isEqualTo(ErrorCode.TURN_ID_REQUIRED);
    }

    @ParameterizedTest(name = "[{index}]")
    @ValueSource(strings = {"abc", "9b2e6d445a3c4f0b8e712c4d6f8a0b12", "9b2e6d44-5a3c-4f0b-8e71-2c4d6f8a0b1",
            "9b2e6d44-5a3c-4f0b-8e71-2c4d6f8a0b1z", "{9b2e6d44-5a3c-4f0b-8e71-2c4d6f8a0b12}"})
    @DisplayName("Un turnId que no es un UUID canónico es TURN_ID_INVALID_FORMAT")
    void validate_shouldRejectTurnId(String turn) {
        assertThat(rejectionsOf(turn, "hola", KEY)).singleElement().extracting(FieldRejection::code)
                .isEqualTo(ErrorCode.TURN_ID_INVALID_FORMAT);
    }

    @Test
    @DisplayName("La clave vacía es REQUIRED; con espacios, 65 caracteres o ñ es INVALID_FORMAT")
    void validate_shouldRejectKey() {
        assertThat(rejectionsOf(TURN, "hola", "")).singleElement().extracting(FieldRejection::code)
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        for (String bad : List.of("a b", "x".repeat(65), "ñ")) {
            assertThat(rejectionsOf(TURN, "hola", bad)).singleElement().extracting(FieldRejection::code)
                    .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_INVALID_FORMAT);
        }
        assertThat(policy.validate(TURN, "hola", "x".repeat(64)).idempotencyKey()).hasSize(64);
    }

    @Test
    @DisplayName("Un turnId nulo rechaza la petición aunque lo demás sea válido")
    void validate_shouldReject_whenTurnNull() {
        assertThatThrownBy(() -> policy.validate(null, "hola", KEY)).isInstanceOf(InvalidRequestException.class);
    }
}
