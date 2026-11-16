package tech.cameia.entrevista.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.entrevista.domain.model.SpeechDirection;
import tech.cameia.entrevista.domain.model.Tone;

/** Etiquetas y estilos por tono, y cuándo se acepta la dirección propuesta por el modelo. */
class SpeechDirectionPolicyTest {

    private static final String QUESTION = "Describa exactamente una ocasión en la que un despliegue falló. ¿Qué"
            + " métrica usó para medir el impacto?";

    private final SpeechDirectionPolicy policy = new SpeechDirectionPolicy();

    @Test
    @DisplayName("Una dirección con etiquetas del tono, sin cambiar palabras, se acepta tal cual")
    void accept_shouldKeepTagsAndStyle_whenProposalIsValid() {
        String tagged = "<clear throat> Describa exactamente una ocasión en la que un despliegue falló."
                + " <medium pause> ¿Qué métrica usó para medir el impacto?";

        Optional<SpeechDirection> direction = policy.accept(QUESTION, Tone.STRICT, tagged, "deliberate and measured");

        assertThat(direction).contains(new SpeechDirection(tagged, "deliberate and measured", true));
    }

    @Test
    @DisplayName("El estricto admite gruñidos y suspiros; el cálido, risas suaves")
    void rulesFor_shouldDifferByTone() {
        assertThat(policy.rulesFor(Tone.STRICT).tags()).contains("grunt", "groan", "sigh").doesNotContain("laugh");
        assertThat(policy.rulesFor(Tone.WARM).tags()).contains("chuckle", "laugh").doesNotContain("grunt");
        assertThat(policy.rulesFor(Tone.PROFESSIONAL_NEUTRAL).tags()).doesNotContain("laugh", "grunt", "groan");
    }

    @ParameterizedTest
    @EnumSource(Tone.class)
    @DisplayName("Ningún tono admite gritos, llanto, gemidos ni sarcasmo")
    void rulesFor_shouldExcludeInappropriateTagsAndStyles(Tone tone) {
        SpeechDirectionPolicy.Rules rules = policy.rulesFor(tone);

        assertThat(rules.tags()).doesNotContain("scream", "shout", "cry", "sob", "moan", "whimper", "cackle");
        assertThat(rules.styles()).doesNotContain("angry tone", "sarcastic", "shouting", "sad and melancholic");
        assertThat(rules.defaultStyle()).isEqualTo(rules.styles().getFirst());
        assertThat(rules.guide()).isNotBlank();
    }

    @Test
    @DisplayName("Sin dirección, la pregunta va tal cual con el estilo por defecto del tono")
    void plain_shouldUseQuestionAndDefaultStyle() {
        assertThat(policy.plain(QUESTION, Tone.WARM))
                .isEqualTo(new SpeechDirection(QUESTION, "empathetic and warm", false));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        // cambia una palabra
        "<sigh> Describa brevemente una ocasión en la que un despliegue falló. ¿Qué métrica usó para medir el impacto?",
        // traduce
        "<sigh> Describe a time a deployment failed. What metric did you use to measure the impact?",
        // etiqueta de otro tono
        "<laugh> Describa exactamente una ocasión en la que un despliegue falló. ¿Qué métrica usó para medir el impacto?",
        // etiqueta inexistente
        "<scream> Describa exactamente una ocasión en la que un despliegue falló. ¿Qué métrica usó para medir el impacto?",
        // dos etiquetas seguidas
        "<sigh> <grunt> Describa exactamente una ocasión en la que un despliegue falló. ¿Qué métrica usó para medir el"
                + " impacto?",
        // sin etiquetas
        "Describa exactamente una ocasión en la que un despliegue falló. ¿Qué métrica usó para medir el impacto?",
        // más de cuatro
        "<sigh> Describa <breath> exactamente una <grunt> ocasión en la <inhale> que un <exhale> despliegue falló."
                + " ¿Qué métrica usó para medir el impacto?",
        // corchetes o ángulos sueltos
        "<sigh> Describa exactamente una ocasión en la que un despliegue falló. ¿Qué métrica usó para medir el"
                + " impacto? <",
        // instrucción agregada
        "<sigh> Say angrily: Describa exactamente una ocasión en la que un despliegue falló. ¿Qué métrica usó para"
                + " medir el impacto?"
    })
    @DisplayName("Una propuesta que cambia la pregunta o incumple las etiquetas se descarta")
    void accept_shouldReject_whenProposalBreaksARule(String proposal) {
        assertThat(policy.accept(QUESTION, Tone.STRICT, proposal, "confident")).isEmpty();
    }

    @Test
    @DisplayName("Un estilo que no es del tono, o datos ausentes, se descartan")
    void accept_shouldReject_whenStyleIsNotOfTheToneOrDataIsMissing() {
        String tagged = "<sigh> " + QUESTION;

        assertThat(policy.accept(QUESTION, Tone.STRICT, tagged, "cheerful and friendly")).isEmpty();
        assertThat(policy.accept(QUESTION, Tone.STRICT, null, "confident")).isEmpty();
        assertThat(policy.accept(QUESTION, Tone.STRICT, tagged, null)).isEmpty();
    }

    @Test
    @DisplayName("Los espacios de más y los saltos de línea se unifican sin cambiar las palabras")
    void accept_shouldNormalizeSpaces() {
        String tagged = "  <breath>   Describa exactamente una ocasión en la que un despliegue falló.\n <short pause>"
                + " ¿Qué métrica usó para medir el impacto?  ";

        Optional<SpeechDirection> direction = policy.accept(QUESTION, Tone.PROFESSIONAL_NEUTRAL, tagged, " confident ");

        assertThat(direction).isPresent();
        assertThat(direction.get().text()).isEqualTo("<breath> Describa exactamente una ocasión en la que un"
                + " despliegue falló. <short pause> ¿Qué métrica usó para medir el impacto?");
        assertThat(direction.get().style()).isEqualTo("confident");
    }

    @Test
    @DisplayName("Exactamente cuatro etiquetas se aceptan")
    void accept_shouldAcceptFourTags() {
        String tagged = "<sigh> Describa exactamente <breath> una ocasión en la que <grunt> un despliegue falló."
                + " <medium pause> ¿Qué métrica usó para medir el impacto?";

        assertThat(policy.accept(QUESTION, Tone.STRICT, tagged, "urgent")).isPresent();
    }

    @Test
    @DisplayName("La representación no imprime el texto de la pregunta")
    void toString_shouldNotPrintText() {
        assertThat(policy.plain(QUESTION, Tone.STRICT).toString()).doesNotContain("despliegue");
    }
}
