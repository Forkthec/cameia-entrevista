package tech.cameia.entrevista.domain.policy;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.CatalogOption;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.Modality;
import tech.cameia.entrevista.domain.model.Tone;

/**
 * Catálogo de personalización de la entrevista: modalidades, tonos, formas de respuesta e idiomas, con la
 * disponibilidad de cada uno.
 *
 * <p>Es la única fuente de esas opciones. La pantalla las muestra a partir de este catálogo y la creación de
 * la sesión las valida contra él, de modo que lo que se ofrece y lo que se acepta no pueden diferir. Las
 * listas siguen el orden de declaración de cada enumerado, que es el orden en que se muestran.</p>
 *
 * <p>Hoy solo la modalidad Entreno no está disponible: se muestra como «Próximamente» hasta que existan la
 * repregunta y el reintento de respuesta. Habilitarla exige cambiar este conjunto y desplegar.</p>
 */
public final class PersonalizationCatalog {

    /** Los cuatro enumerados cuyas opciones ofrece el catálogo. */
    private static final Set<Class<?>> CATALOG_ENUMS =
            Set.of(Modality.class, Tone.class, AnswerMode.class, InterviewLanguage.class);

    /** Opciones que se muestran pero todavía no se pueden elegir. */
    private static final Set<Enum<?>> UNAVAILABLE = Set.of(Modality.TRAINING);

    private final List<CatalogOption<Modality>> modes = optionsOf(Modality.values());
    private final List<CatalogOption<Tone>> tones = optionsOf(Tone.values());
    private final List<CatalogOption<AnswerMode>> answerFormats = optionsOf(AnswerMode.values());
    private final List<CatalogOption<InterviewLanguage>> languages = optionsOf(InterviewLanguage.values());

    /** @return las modalidades en orden, con su disponibilidad; lista inmodificable */
    public List<CatalogOption<Modality>> modes() {
        return modes;
    }

    /** @return los tonos en orden; lista inmodificable */
    public List<CatalogOption<Tone>> tones() {
        return tones;
    }

    /** @return las formas de respuesta en orden; lista inmodificable */
    public List<CatalogOption<AnswerMode>> answerFormats() {
        return answerFormats;
    }

    /** @return los idiomas de la sesión en orden; lista inmodificable */
    public List<CatalogOption<InterviewLanguage>> languages() {
        return languages;
    }

    /**
     * Indica si una opción se puede elegir hoy.
     *
     * @param option valor de cualquiera de los cuatro enumerados
     * @return {@code false} solo para las opciones marcadas como «Próximamente»
     * @throws NullPointerException si {@code option} es nulo
     * @throws IllegalArgumentException si {@code option} no pertenece a ninguno de los cuatro enumerados
     */
    public boolean isAvailable(Enum<?> option) {
        Objects.requireNonNull(option, "option");
        if (!CATALOG_ENUMS.contains(option.getDeclaringClass())) {
            throw new IllegalArgumentException(
                    "El valor no es una opción del catálogo: " + option.getDeclaringClass().getSimpleName());
        }
        return !UNAVAILABLE.contains(option);
    }

    // Arma la lista de un enumerado en su orden de declaración; List.copyOf la deja inmodificable.
    private <E extends Enum<E>> List<CatalogOption<E>> optionsOf(E[] values) {
        return List.copyOf(Arrays.stream(values).map(value -> new CatalogOption<>(value, isAvailable(value))).toList());
    }
}
