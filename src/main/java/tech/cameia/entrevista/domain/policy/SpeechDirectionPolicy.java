package tech.cameia.entrevista.domain.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tech.cameia.entrevista.domain.model.SpeechDirection;
import tech.cameia.entrevista.domain.model.Tone;

/**
 * Qué etiquetas de voz y qué estilos puede usar el entrevistador según su tono, y cuándo una pregunta dirigida por el
 * modelo se acepta.
 *
 * <p>Las listas salen de las etiquetas del modelo de voz de Gemini; se dejan fuera las que no caben en una
 * entrevista de trabajo (gritos, llanto, gemidos, sarcasmo, enojo). El tono cambia cómo suena la pregunta, no la voz
 * (CA-5.15.3): el cálido admite una risa suave y el estricto un suspiro, un carraspeo o un gruñido breve.</p>
 *
 * <p>Una dirección se acepta solo si cumple todo; si no, se lee la pregunta tal cual con el estilo por defecto del
 * tono, de modo que una respuesta mala del modelo nunca cambia lo que se pregunta:</p>
 * <ul>
 *   <li>el estilo es uno de los del tono;</li>
 *   <li>cada etiqueta es una de las del tono, y hay de 1 a {@value #MAX_TAGS};</li>
 *   <li>no hay dos etiquetas seguidas;</li>
 *   <li>al quitar las etiquetas y unificar los espacios queda exactamente la pregunta original.</li>
 * </ul>
 */
public final class SpeechDirectionPolicy {

    /** Máximo de etiquetas por pregunta: pocas suenan naturales; muchas, actuadas. */
    public static final int MAX_TAGS = 4;

    private static final Pattern TAG = Pattern.compile("<([a-z]+(?: [a-z]+)?)>");
    private static final Pattern ANY_ANGLE = Pattern.compile("[<>]");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private static final List<String> COMMON_TAGS = List.of("breath", "inhale", "exhale", "short pause",
            "medium pause", "clear throat", "hum");

    private static final Map<Tone, Rules> RULES = Map.of(
            Tone.PROFESSIONAL_NEUTRAL, new Rules(
                    List.of("confident", "deliberate and measured", "news anchor", "documentary narrator"),
                    with("throat clearing"),
                    "Tono profesional y neutro: sereno y cortés; pausas y respiraciones, sin risas ni quejidos."),
            Tone.WARM, new Rules(
                    List.of("empathetic and warm", "cheerful and friendly", "casual conversation with a friend",
                            "soft-spoken"),
                    with("chuckle", "laugh", "sigh"),
                    "Tono cálido: cercano y amable; una risa suave o una respiración tranquila ayudan, sin exagerar."),
            Tone.STRICT, new Rules(
                    List.of("confident", "deliberate and measured", "speaking slowly", "urgent", "monotone and flat"),
                    with("sigh", "grunt", "groan", "throat clearing"),
                    "Tono estricto: exigente e impaciente sin ser grosero; un carraspeo, un suspiro o un gruñido breve"
                            + " antes de exigir el dato concreto."));

    /**
     * Reglas de un tono.
     *
     * @param styles estilos admitidos; el primero es el de por defecto
     * @param tags etiquetas admitidas, sin ángulos
     * @param guide indicación del tono para el modelo que dirige la voz
     */
    public record Rules(List<String> styles, List<String> tags, String guide) {

        /** Copia las listas. */
        public Rules {
            styles = List.copyOf(styles);
            tags = List.copyOf(tags);
        }

        /** @return el estilo que se usa si el modelo no dirige la pregunta */
        public String defaultStyle() {
            return styles.getFirst();
        }
    }

    /**
     * Reglas de un tono.
     *
     * @param tone tono de la entrevista
     * @return estilos y etiquetas admitidos
     */
    public Rules rulesFor(Tone tone) {
        return RULES.get(tone);
    }

    /**
     * La pregunta tal cual, con el estilo por defecto del tono.
     *
     * @param question texto de la pregunta
     * @param tone tono de la entrevista
     * @return la dirección sin etiquetas
     */
    public SpeechDirection plain(String question, Tone tone) {
        return new SpeechDirection(question, rulesFor(tone).defaultStyle(), false);
    }

    /**
     * Acepta la dirección propuesta por el modelo si cumple todas las reglas.
     *
     * @param question texto original de la pregunta
     * @param tone tono de la entrevista
     * @param proposedText texto propuesto, con etiquetas
     * @param proposedStyle estilo propuesto
     * @return la dirección aceptada, o vacío si incumple alguna regla
     */
    public Optional<SpeechDirection> accept(String question, Tone tone, String proposedText, String proposedStyle) {
        Rules rules = rulesFor(tone);
        if (proposedText == null || proposedStyle == null || !rules.styles().contains(proposedStyle.strip())) {
            return Optional.empty();
        }
        String text = proposedText.strip();
        Matcher matcher = TAG.matcher(text);
        List<int[]> spans = new ArrayList<>();
        StringBuilder spoken = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            if (!rules.tags().contains(matcher.group(1))) {
                return Optional.empty();
            }
            if (!spans.isEmpty() && text.substring(spans.getLast()[1], matcher.start()).isBlank()) {
                return Optional.empty();
            }
            spans.add(new int[] {matcher.start(), matcher.end()});
            spoken.append(text, last, matcher.start()).append(' ');
            last = matcher.end();
        }
        spoken.append(text.substring(last));
        if (spans.isEmpty() || spans.size() > MAX_TAGS || ANY_ANGLE.matcher(spoken).find()
                || !normalize(spoken.toString()).equals(normalize(question))) {
            return Optional.empty();
        }
        return Optional.of(new SpeechDirection(SPACES.matcher(text).replaceAll(" "), proposedStyle.strip(), true));
    }

    private static String normalize(String text) {
        return SPACES.matcher(text.strip()).replaceAll(" ");
    }

    private static List<String> with(String... extra) {
        List<String> tags = new ArrayList<>(COMMON_TAGS);
        tags.addAll(List.of(extra));
        return tags;
    }
}
