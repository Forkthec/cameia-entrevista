package tech.cameia.entrevista.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import tech.cameia.entrevista.domain.exception.LlmInvalidOutputException;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.PlanDraft;
import tech.cameia.entrevista.domain.model.PlannedQuestion;

/** Reglas de validación del plan que propone el modelo. */
class PlanValidatorTest {

    private static final String[] TYPES = {"APERTURA", "TECNICA", "CONDUCTUAL", "TECNICA", "SITUACIONAL"};
    private static final String SMILE = new String(Character.toChars(0x1F600));
    private final PlanValidator validator = new PlanValidator();

    private static List<PlannedQuestion> five() {
        List<PlannedQuestion> list = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            list.add(new PlannedQuestion(i, TYPES[i - 1], "Pregunta número " + i));
        }
        return list;
    }

    private static PlanDraft draft(List<PlannedQuestion> questions) {
        return new PlanDraft("es-CO", questions);
    }

    private static List<PlannedQuestion> with(int index, PlannedQuestion replacement) {
        List<PlannedQuestion> list = five();
        list.set(index, replacement);
        return list;
    }

    private void assertInvalid(PlanDraft plan) {
        assertThatThrownBy(() -> validator.validate(InterviewLanguage.SPANISH_COLOMBIA, plan))
                .isInstanceOf(LlmInvalidOutputException.class);
    }

    @Test
    void validate_shouldAcceptFiveQuestionsMatchingTemplate() {
        List<PlannedQuestion> result = validator.validate(InterviewLanguage.SPANISH_COLOMBIA, draft(five()));
        assertThat(result).hasSize(5);
        assertThat(result.get(1).type()).isEqualTo("TECNICA");
    }

    @Test
    void validate_shouldRejectNullPlanOrNullList() {
        assertInvalid(null);
        assertInvalid(new PlanDraft("es-CO", null));
    }

    @Test
    void validate_shouldRejectOtherLanguage() {
        assertInvalid(new PlanDraft("en", five()));
        assertInvalid(new PlanDraft(null, five()));
    }

    @Test
    void validate_shouldRejectFourOrSixQuestions() {
        assertInvalid(draft(five().subList(0, 4)));
        List<PlannedQuestion> six = five();
        six.add(new PlannedQuestion(6, "SITUACIONAL", "Otra"));
        assertInvalid(draft(six));
    }

    @Test
    void validate_shouldRejectWrongPositionAndNullQuestion() {
        assertInvalid(draft(with(0, new PlannedQuestion(2, "APERTURA", "Hola"))));
        assertInvalid(draft(with(0, null)));
    }

    @Test
    void validate_shouldRejectBlankOrNullText() {
        assertInvalid(draft(with(0, new PlannedQuestion(1, "APERTURA", "   "))));
        assertInvalid(draft(with(0, new PlannedQuestion(1, "APERTURA", null))));
    }

    @Test
    void validate_shouldRejectControlCharactersInQuestionText() {
        for (int control : new int[] {0, 10, 9, 7, 127}) {
            assertInvalid(draft(with(2, new PlannedQuestion(3, "CONDUCTUAL", "Háblame de un reto" + (char) control + "difícil"))));
        }
    }

    @Test
    void validate_shouldAcceptText500AndRejectText501CodePoints() {
        assertThat(validator.validate(InterviewLanguage.SPANISH_COLOMBIA,
                draft(with(0, new PlannedQuestion(1, "APERTURA", "a".repeat(500)))))).hasSize(5);
        assertInvalid(draft(with(0, new PlannedQuestion(1, "APERTURA", "a".repeat(501)))));
        String emoji500 = SMILE.repeat(500);
        assertThat(validator.validate(InterviewLanguage.SPANISH_COLOMBIA,
                draft(with(0, new PlannedQuestion(1, "APERTURA", emoji500))))).hasSize(5);
        assertInvalid(draft(with(0, new PlannedQuestion(1, "APERTURA", emoji500 + SMILE))));
    }

    @Test
    void validate_shouldCountNfcCodePoints() {
        String decomposed = "é".repeat(300);
        assertThat(validator.validate(InterviewLanguage.SPANISH_COLOMBIA,
                draft(with(0, new PlannedQuestion(1, "APERTURA", decomposed))))).hasSize(5);
    }

    @Test
    void validate_shouldUpperCaseTypeWithRootLocale() {
        List<PlannedQuestion> result = validator.validate(InterviewLanguage.SPANISH_COLOMBIA,
                draft(with(1, new PlannedQuestion(2, "TEcnICA", "Segunda"))));
        assertThat(result.get(1).type()).isEqualTo("TECNICA");
    }

    @Test
    void validate_shouldRejectAccentedOrWrongType() {
        assertInvalid(draft(with(1, new PlannedQuestion(2, "TÉCNICA", "Segunda"))));
        assertInvalid(draft(with(1, new PlannedQuestion(2, "CONDUCTUAL", "Segunda"))));
        assertInvalid(draft(with(1, new PlannedQuestion(2, null, "Segunda"))));
    }

    @Test
    void validate_shouldRejectDuplicatesIgnoringCaseAndSpaces() {
        assertInvalid(draft(with(1, new PlannedQuestion(2, "TECNICA", "  PREGUNTA   número 1 "))));
    }

    @Test
    void validate_shouldTrimTextAndKeepWording() {
        List<PlannedQuestion> result = validator.validate(InterviewLanguage.SPANISH_COLOMBIA,
                draft(with(0, new PlannedQuestion(1, "APERTURA", "  ¿Quién eres?  "))));
        assertThat(result.get(0).text()).isEqualTo("¿Quién eres?");
    }

    @Test
    void validate_shouldNotLeakModelTextInReason() {
        assertThatThrownBy(() -> validator.validate(InterviewLanguage.SPANISH_COLOMBIA,
                draft(with(1, new PlannedQuestion(2, "CONDUCTUAL", "SECRETO-DEL-MODELO")))))
                .isInstanceOfSatisfying(LlmInvalidOutputException.class,
                        e -> assertThat(e.getReason()).doesNotContain("SECRETO-DEL-MODELO"));
    }

    @Test
    void plannedQuestionToString_shouldOmitText() {
        assertThat(new PlannedQuestion(1, "APERTURA", "SECRETO").toString()).doesNotContain("SECRETO");
    }
}
