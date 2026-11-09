package tech.cameia.entrevista.spike.planificacion;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Regla simple y reproducible para decidir si un plan "suena" al tono pedido (CA-4.1.3).
 *
 * <p>Código del banco de pruebas del spike. Es una aproximación por palabras, no un juicio humano:
 * lo que mide queda marcado como aproximado y una persona debe confirmarlo. Se miden tres señales
 * sobre las 5 preguntas: acogida (frases de bienvenida, cercanía o agradecimiento), exigencia (pedir
 * cifras, métricas o precisión) y frases que dan por hecha una respuesta anterior de la persona.
 * Reglas del tono deducido: ESTRICTO si hay exigencia en al menos 3 preguntas y acogida en 1 o
 * menos; CALIDO si hay acogida en al menos 1 pregunta y exigencia en 2 o menos; NEUTRO si no hay
 * acogida y la exigencia es de 2 o menos; el resto es AMBIGUO.</p>
 *
 * <p>Además, para el tono cálido se vigilan dos defectos de naturalidad, porque cada pregunta se
 * muestra sola y con su texto original: saludos o agradecimientos en las preguntas 2, 3 y 4, y
 * cualquier frase que suponga una respuesta previa. Solo la pregunta 1 puede abrir con bienvenida y
 * la 5 puede cerrar con una frase breve. Las palabras se afinaron leyendo las salidas guardadas de
 * la versión 2 del prompt; la regla se valida con salidas nuevas que no se usaron para afinarla.</p>
 */
final class TonoChecker {

    private static final Pattern ACOGIDA = Pattern.compile(
            "gusto|encantad|bienvenid|cómod|comod|tranquil|gracias|sin prisa|con calma|saludarte|tenerte|hola|"
                    + "me alegra|qué bueno|que bueno|agradec|"
                    + "welcome|glad|comfortable|pleasure|happy|no rush|take your time|feel free|thank|"
                    + "great to have|thrilled|hello|excited|delighted|appreciate|wonderful|nice to",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EXIGENCIA = Pattern.compile(
            "métric|metric|cifra|número|numero|medible|measurable|exact|específic|especific|specific|evidenc|"
                    + "concret|porcentaj|percent|cuantific|quantif|detall|precis",
            Pattern.CASE_INSENSITIVE);

    /** Frases que solo tendrían sentido si la persona ya hubiera contestado algo antes. */
    private static final Pattern SUPONE_RESPUESTA = Pattern.compile(
            "gracias por compartir|gracias por contar|gracias por esa|qué interesante|que interesante|"
                    + "conocer más de tu|conocer mejor tu|avanzando muy bien|"
                    + "thanks for sharing|thank you for sharing|thanks for that|great answer|good answer|"
                    + "how interesting|that is interesting|that's interesting|going well|going great",
            Pattern.CASE_INSENSITIVE);

    private TonoChecker() {
    }

    /**
     * Señales contadas y el tono que la regla deduce.
     *
     * @param preguntasConAcogida preguntas (de 5) con frase de acogida
     * @param preguntasConExigencia preguntas (de 5) que piden cifras, métricas o precisión
     * @param tonoDeducido tono que deduce la regla
     * @param acogidaEnIntermedias preguntas 2, 3 y 4 con frase de acogida (defecto para el tono cálido)
     * @param suponenRespuestaPrevia preguntas (de 5) que dan por hecha una respuesta anterior
     */
    record Lectura(long preguntasConAcogida, long preguntasConExigencia, String tonoDeducido,
            long acogidaEnIntermedias, long suponenRespuestaPrevia) {

        /** El tono cálido natural: bienvenida en la 1, nada de cortesías en la 2 a la 4 y sin suponer respuestas. */
        boolean calidezNatural() {
            return acogidaEnIntermedias == 0 && suponenRespuestaPrevia == 0;
        }
    }

    static Lectura leer(List<String> textos) {
        long acogida = textos.stream().filter(t -> ACOGIDA.matcher(minusculas(t)).find()).count();
        long exigencia = textos.stream().filter(t -> EXIGENCIA.matcher(minusculas(t)).find()).count();
        long intermedias = textos.subList(1, Math.min(4, textos.size())).stream()
                .filter(t -> ACOGIDA.matcher(minusculas(t)).find()).count();
        long suponen = textos.stream().filter(t -> SUPONE_RESPUESTA.matcher(minusculas(t)).find()).count();
        String tono;
        if (exigencia >= 3 && acogida <= 1) {
            tono = "ESTRICTO";
        } else if (acogida >= 1 && exigencia <= 2) {
            tono = "CALIDO";
        } else if (acogida == 0 && exigencia <= 2) {
            tono = "PROFESIONAL_NEUTRO";
        } else {
            tono = "AMBIGUO";
        }
        return new Lectura(acogida, exigencia, tono, intermedias, suponen);
    }

    private static String minusculas(String texto) {
        return texto.toLowerCase(Locale.ROOT);
    }
}
