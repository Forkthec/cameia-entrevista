package tech.cameia.entrevista.domain.policy;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import tech.cameia.entrevista.domain.model.InterviewLanguage;

/**
 * Lista cerrada de muletillas por idioma y la regla para reconocerlas en una palabra transcrita: sin distinguir
 * mayúsculas, tildes ni signos de puntuación.
 */
public final class FillerWords {

    private static final Set<String> SPANISH = Set.of("eh", "ehh", "ehm", "em", "emm", "mm", "mmm", "ah");
    private static final Set<String> ENGLISH = Set.of("um", "umm", "uh", "uhh", "er", "erm", "hmm", "mm");
    private static final Pattern NOT_LETTER_OR_DIGIT = Pattern.compile("[^\\p{L}\\p{Nd}]");
    private static final Pattern MARKS = Pattern.compile("\\p{M}");

    private FillerWords() {
    }

    /**
     * Indica si una palabra transcrita es una muletilla del idioma de la entrevista.
     *
     * @param word palabra tal como salió de la transcripción; puede traer puntuación o mayúsculas
     * @param language idioma de la entrevista, que fija la lista que se consulta
     * @return {@code true} si la palabra, normalizada, está en la lista del idioma
     */
    public static boolean isFiller(String word, InterviewLanguage language) {
        if (word == null) {
            return false;
        }
        Set<String> fillers = language == InterviewLanguage.ENGLISH ? ENGLISH : SPANISH;
        return fillers.contains(normalize(word));
    }

    /**
     * Minúsculas, sin marcas diacríticas (descomposición NFD) y sin nada que no sea letra o dígito.
     *
     * @param word palabra original
     * @return la forma comparable de la palabra
     */
    static String normalize(String word) {
        String decomposed = Normalizer.normalize(word.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return NOT_LETTER_OR_DIGIT.matcher(MARKS.matcher(decomposed).replaceAll("")).replaceAll("");
    }
}
