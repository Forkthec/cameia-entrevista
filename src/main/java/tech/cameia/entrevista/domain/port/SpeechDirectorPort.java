package tech.cameia.entrevista.domain.port;

import java.util.Optional;

import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.domain.policy.SpeechDirectionPolicy;

/**
 * Puerto del modelo de lenguaje que decide cómo debe sonar una pregunta: dónde van las etiquetas de voz y qué estilo
 * tiene la lectura.
 *
 * <p>Es una mejora, no un requisito: si el modelo no responde a tiempo o responde algo inútil, devuelve vacío y la
 * pregunta se lee tal cual. Nunca lanza una excepción.</p>
 */
public interface SpeechDirectorPort {

    /**
     * Pide la dirección de la pregunta.
     *
     * @param request pregunta y contexto de la entrevista
     * @return el texto con etiquetas y el estilo propuestos, sin validar; vacío si el modelo falló
     */
    Optional<Proposal> direct(Request request);

    /**
     * Lo que se le pasa al modelo.
     *
     * @param question texto de la pregunta
     * @param number posición de la pregunta, de 1 a 5
     * @param tone tono de la entrevista
     * @param language idioma de la entrevista
     * @param rules estilos y etiquetas que admite el tono
     */
    record Request(String question, int number, Tone tone, InterviewLanguage language,
            SpeechDirectionPolicy.Rules rules) {

        /** No imprime el texto de la pregunta. */
        @Override
        public String toString() {
            return "Request[number=" + number + ", tone=" + tone + ", language=" + language + "]";
        }
    }

    /**
     * Lo que propone el modelo.
     *
     * @param text texto con etiquetas
     * @param style estilo de la lectura
     */
    record Proposal(String text, String style) {

        /** No imprime el texto. */
        @Override
        public String toString() {
            return "Proposal[style=" + style + "]";
        }
    }
}
