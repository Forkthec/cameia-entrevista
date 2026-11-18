package tech.cameia.entrevista.spike.planificacion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Comprueba la lista (a) a (g) de CA-4.1.1 sobre el texto crudo que devolvió el modelo.
 *
 * <p>Es código del banco de pruebas del spike, no de producción. Lo que se puede decidir con una
 * regla exacta (a, b, g) se decide aquí. Lo que exige criterio (c pertinencia al rol, d idioma,
 * e repetidas, f refleja la oferta) se aproxima con una regla simple y el resultado se marca como
 * {@code aproximado}: una persona debe confirmarlo leyendo el plan.</p>
 */
final class PlanChecker {

    /** Plantilla de competencias: tipo de cada posición (1 a 5). */
    static final List<String> PLANTILLA = List.of("APERTURA", "TECNICA", "CONDUCTUAL", "TECNICA", "SITUACIONAL");

    /** Largo máximo vigente de una pregunta principal, en caracteres. */
    static final int LARGO_MAXIMO_VIGENTE = 500;

    /** Desde este parecido (palabras compartidas / palabras totales) dos preguntas se consideran casi idénticas. */
    private static final double UMBRAL_REPETIDAS = 0.6;

    private static final Set<String> PALABRAS_ES = Set.of("el", "la", "los", "las", "de", "del", "que", "en", "un", "una",
            "para", "con", "por", "como", "su", "sus", "cómo", "qué", "cuál", "me", "te", "se", "es", "y", "o", "al",
            "cuando", "si", "has", "ha", "tu", "usted", "ti", "puedes", "describe", "cuéntame");

    private static final Set<String> PALABRAS_EN = Set.of("the", "of", "and", "to", "in", "a", "an", "for", "with", "how",
            "what", "which", "your", "you", "me", "is", "are", "do", "does", "would", "that", "about", "can", "tell",
            "describe", "have", "has", "when", "if", "on", "at", "by", "or");

    private static final Pattern SEPARADOR = Pattern.compile("[^\\p{L}\\p{N}]+");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private PlanChecker() {
    }

    /** Resultado de un criterio: si pasa, el detalle y si es una aproximación que una persona debe confirmar. */
    record Criterio(String id, boolean cumple, boolean aproximado, String detalle) {
    }

    /** Resultado de las siete comprobaciones y los largos observados. */
    record Resultado(List<Criterio> criterios, List<Integer> largos) {

        boolean valido() {
            return criterios.stream().allMatch(Criterio::cumple);
        }
    }

    /** Lo que se necesita saber de la entrevista pedida para juzgar el plan. */
    record Pedido(String idioma, boolean conOferta, List<String> terminosTecnicos, List<String> terminosOferta) {
    }

    /**
     * Evalúa el texto crudo del modelo.
     *
     * @param crudo texto tal como lo devolvió el modelo, sin limpiar
     * @param pedido idioma pedido, si se envió oferta y los términos con que se aproximan c y f
     * @return los siete criterios (a a g) y los largos de las preguntas
     */
    static Resultado evaluar(String crudo, Pedido pedido) {
        List<Criterio> criterios = new ArrayList<>();
        List<Integer> largos = new ArrayList<>();
        JsonNode plan = leer(crudo);
        String falloForma = plan == null ? "el texto no es un JSON válido" : falloDeForma(plan, pedido.idioma());
        criterios.add(new Criterio("a", falloForma == null, false,
                falloForma == null ? "5 preguntas y forma conforme al esquema" : falloForma));
        if (falloForma != null) {
            for (String id : List.of("b", "c", "d", "e", "f", "g")) {
                criterios.add(new Criterio(id, false, false, "no evaluable: falla la forma (a)"));
            }
            return new Resultado(criterios, largos);
        }
        List<String> textos = new ArrayList<>();
        JsonNode preguntas = plan.get("preguntas");
        for (int i = 0; i < 5; i++) {
            textos.add(preguntas.get(i).get("texto").asString());
            largos.add(textos.get(i).codePointCount(0, textos.get(i).length()));
        }
        criterios.add(tipos(preguntas));
        criterios.add(pertinencia(textos, pedido));
        criterios.add(idioma(textos, pedido.idioma()));
        criterios.add(repetidas(textos));
        criterios.add(oferta(textos, pedido));
        criterios.add(largo(textos, largos));
        return new Resultado(criterios, largos);
    }

    /**
     * Criterio adicional "h" (no es de CA-4.1.1): cada tema del rol aparece, como mucho, en una de las
     * preguntas 2 a 5. La apertura (posición 1) no cuenta, porque habla de la persona y del rol. Es una
     * aproximación por fragmentos de palabra y una persona debe confirmarla. No cambia {@code valido()}.
     *
     * @param textos los cinco textos del plan
     * @param temas nombre del tema y fragmentos que lo delatan
     * @return el criterio h, con los temas repetidos y las posiciones donde aparecen
     */
    static Criterio temasDistintos(List<String> textos, Map<String, List<String>> temas) {
        List<String> repetidos = new ArrayList<>();
        temas.forEach((tema, fragmentos) -> {
            List<Integer> posiciones = new ArrayList<>();
            for (int i = 1; i < textos.size(); i++) {
                String texto = textos.get(i).toLowerCase(Locale.ROOT);
                if (fragmentos.stream().anyMatch(fragmento -> menciona(texto, fragmento))) {
                    posiciones.add(i + 1);
                }
            }
            if (posiciones.size() > 1) {
                repetidos.add(tema + " en las preguntas " + posiciones);
            }
        });
        return new Criterio("h", repetidos.isEmpty(), true, repetidos.isEmpty()
                ? "ningún tema se repite entre las preguntas 2 a 5 (aproximado)"
                : "tema repetido: " + String.join("; ", repetidos));
    }

    /**
     * Indica si el texto menciona el fragmento sin confundir palabras distintas: un fragmento de hasta
     * 4 letras ({@code cola}, {@code rest}, {@code api}) debe ser una palabra completa, con plural
     * opcional, para que {@code cola} no coincida con «colaboración»; uno más largo ({@code mensaj})
     * debe empezar una palabra.
     */
    private static boolean menciona(String texto, String fragmento) {
        String inicio = "(?<![\\p{L}])" + Pattern.quote(fragmento);
        String patron = fragmento.length() <= 4 ? inicio + "(s|es)?(?![\\p{L}])" : inicio;
        return Pattern.compile(patron).matcher(texto).find();
    }

    /** Los cinco textos de un plan bien formado, o {@code null} si el texto crudo no trae esa forma. */
    static List<String> textosDelPlan(String crudo) {
        JsonNode plan = leer(crudo);
        if (plan == null || !plan.has("preguntas") || !plan.get("preguntas").isArray() || plan.get("preguntas").size() != 5) {
            return null;
        }
        List<String> textos = new ArrayList<>();
        for (JsonNode pregunta : plan.get("preguntas").values()) {
            if (!pregunta.has("texto") || !pregunta.get("texto").isString()) {
                return null;
            }
            textos.add(pregunta.get("texto").asString());
        }
        return textos;
    }

    private static JsonNode leer(String crudo) {
        try {
            return MAPPER.readTree(crudo.strip());
        } catch (RuntimeException ilegible) {
            return null;
        }
    }

    /** Devuelve el motivo por el que el plan no cumple el esquema, o {@code null} si lo cumple. */
    private static String falloDeForma(JsonNode plan, String idiomaPedido) {
        if (!plan.isObject() || plan.size() != 2 || !plan.has("idioma") || !plan.has("preguntas")) {
            return "la raíz no tiene exactamente los campos idioma y preguntas";
        }
        if (!plan.get("idioma").isString() || !idiomaPedido.equals(plan.get("idioma").asString())) {
            return "el campo idioma no es " + idiomaPedido;
        }
        JsonNode preguntas = plan.get("preguntas");
        if (!preguntas.isArray() || preguntas.size() != 5) {
            return "preguntas no es una lista de exactamente 5 elementos";
        }
        for (int i = 0; i < 5; i++) {
            JsonNode p = preguntas.get(i);
            if (!p.isObject() || p.size() != 3 || !p.has("posicion") || !p.has("tipo") || !p.has("texto")) {
                return "la pregunta " + (i + 1) + " no tiene exactamente posicion, tipo y texto";
            }
            if (!p.get("posicion").isIntegralNumber() || p.get("posicion").asInt() != i + 1) {
                return "la pregunta " + (i + 1) + " no trae posicion " + (i + 1);
            }
            if (!p.get("tipo").isString() || !p.get("texto").isString()) {
                return "la pregunta " + (i + 1) + " trae tipo o texto que no es texto";
            }
            if (!Set.of("APERTURA", "TECNICA", "CONDUCTUAL", "SITUACIONAL").contains(p.get("tipo").asString())) {
                return "la pregunta " + (i + 1) + " trae un tipo fuera del catálogo";
            }
        }
        return null;
    }

    private static Criterio tipos(JsonNode preguntas) {
        for (int i = 0; i < 5; i++) {
            String tipo = preguntas.get(i).get("tipo").asString();
            if (!PLANTILLA.get(i).equals(tipo)) {
                return new Criterio("b", false, false,
                        "posición " + (i + 1) + ": vino " + tipo + " y la plantilla pide " + PLANTILLA.get(i));
            }
        }
        return new Criterio("b", true, false, "los 5 tipos coinciden con la plantilla");
    }

    private static Criterio pertinencia(List<String> textos, Pedido pedido) {
        for (int posicion : new int[] {1, 3}) {
            String texto = textos.get(posicion).toLowerCase(Locale.ROOT);
            if (pedido.terminosTecnicos().stream().noneMatch(texto::contains)) {
                return new Criterio("c", false, true,
                        "la pregunta técnica de la posición " + (posicion + 1) + " no menciona ningún término del rol");
            }
        }
        return new Criterio("c", true, true, "las dos técnicas mencionan términos del rol (aproximado)");
    }

    private static Criterio idioma(List<String> textos, String idioma) {
        boolean esperadoEs = idioma.startsWith("es");
        List<String> dudosas = new ArrayList<>();
        for (int i = 0; i < textos.size(); i++) {
            long es = palabras(textos.get(i)).stream().filter(PALABRAS_ES::contains).count();
            long en = palabras(textos.get(i)).stream().filter(PALABRAS_EN::contains).count();
            boolean bien = esperadoEs ? es > en : en > es;
            if (!bien) {
                dudosas.add("pos " + (i + 1) + " (es=" + es + ", en=" + en + ")");
            }
        }
        return new Criterio("d", dudosas.isEmpty(), true, dudosas.isEmpty()
                ? "las 5 preguntas parecen estar en " + idioma + " (aproximado)"
                : "no parecen estar en " + idioma + ": " + String.join("; ", dudosas));
    }

    private static Criterio repetidas(List<String> textos) {
        for (int i = 0; i < textos.size(); i++) {
            for (int j = i + 1; j < textos.size(); j++) {
                double parecido = parecido(palabrasLargas(textos.get(i)), palabrasLargas(textos.get(j)));
                if (parecido >= UMBRAL_REPETIDAS) {
                    return new Criterio("e", false, true, "las preguntas " + (i + 1) + " y " + (j + 1)
                            + " se parecen " + Math.round(parecido * 100) + " %");
                }
            }
        }
        return new Criterio("e", true, true, "ningún par supera el " + Math.round(UMBRAL_REPETIDAS * 100) + " % de parecido (aproximado)");
    }

    private static Criterio oferta(List<String> textos, Pedido pedido) {
        if (!pedido.conOferta()) {
            return new Criterio("f", true, false, "no se envió oferta: no aplica");
        }
        boolean refleja = textos.stream().map(t -> t.toLowerCase(Locale.ROOT))
                .anyMatch(t -> pedido.terminosOferta().stream().anyMatch(t::contains));
        return new Criterio("f", refleja, true, refleja
                ? "al menos una pregunta menciona un término de la oferta (aproximado)"
                : "ninguna pregunta menciona un término de la oferta");
    }

    private static Criterio largo(List<String> textos, List<Integer> largos) {
        for (int i = 0; i < textos.size(); i++) {
            if (textos.get(i).isBlank()) {
                return new Criterio("g", false, false, "la pregunta " + (i + 1) + " está en blanco");
            }
            if (largos.get(i) > LARGO_MAXIMO_VIGENTE) {
                return new Criterio("g", false, false, "la pregunta " + (i + 1) + " mide " + largos.get(i)
                        + " caracteres y el máximo vigente es " + LARGO_MAXIMO_VIGENTE);
            }
        }
        return new Criterio("g", true, false, "todas tienen texto; la más larga mide "
                + largos.stream().max(Integer::compare).orElse(0) + " caracteres");
    }

    private static List<String> palabras(String texto) {
        return List.of(SEPARADOR.split(texto.toLowerCase(Locale.ROOT)));
    }

    private static Set<String> palabrasLargas(String texto) {
        Set<String> resultado = new HashSet<>();
        for (String palabra : palabras(texto)) {
            if (palabra.length() >= 4) {
                resultado.add(palabra);
            }
        }
        return resultado;
    }

    private static double parecido(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> comunes = new HashSet<>(a);
        comunes.retainAll(b);
        Set<String> todas = new HashSet<>(a);
        todas.addAll(b);
        return (double) comunes.size() / todas.size();
    }
}
