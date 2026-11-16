package tech.cameia.entrevista.domain.model;

import java.util.Objects;

/**
 * Cómo debe sonar una pregunta: el texto que lee la voz, con las etiquetas de voz ya insertadas, y el estilo de toda
 * la lectura.
 *
 * <p>Las etiquetas se escriben en inglés y entre ángulos ({@code <short pause>}); el adaptador del servicio de voz
 * las traduce si su modelo usa otra sintaxis.</p>
 *
 * @param text texto con etiquetas; al quitarlas queda la pregunta original
 * @param style estilo de la lectura, en inglés (por ejemplo {@code confident})
 * @param directed {@code true} si un modelo colocó las etiquetas; {@code false} si es la pregunta tal cual con el
 *        estilo por defecto del tono
 */
public record SpeechDirection(String text, String style, boolean directed) {

    /** Valida que no falten datos. */
    public SpeechDirection {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(style, "style");
    }

    /** No imprime el texto: el texto de una pregunta no llega a un registro. */
    @Override
    public String toString() {
        return "SpeechDirection[text=<" + text.length() + " caracteres>, style=" + style + ", directed=" + directed
                + "]";
    }
}
