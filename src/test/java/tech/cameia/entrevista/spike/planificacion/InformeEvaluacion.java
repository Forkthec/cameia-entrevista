package tech.cameia.entrevista.spike.planificacion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import tech.cameia.entrevista.application.service.EvaluationOutputReader;
import tech.cameia.entrevista.domain.exception.LlmInvalidOutputException;
import tech.cameia.entrevista.domain.model.EvaluationResult;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Informe de la medición del evaluador de respuestas: lee las salidas crudas, calcula las métricas y escribe
 * {@code informe.md} y {@code resultados.csv}.
 *
 * <p>Es código de pruebas del spike. Las métricas de latencia usan solo las llamadas que devolvieron texto; las cortadas
 * por tiempo se cuentan aparte. El CSV no lleva texto de respuestas.</p>
 */
final class InformeEvaluacion {

    /** Grupos de la medición, en el orden en que se reportan, con las llamadas que se esperan por modelo. */
    static final Map<String, Integer> ESPERADAS = new LinkedHashMap<>();

    static {
        ESPERADAS.put("ES_SIN_DIFICULTAD", 45);
        ESPERADAS.put("ES_CON_DIFICULTAD", 45);
        ESPERADAS.put("EN", 45);
        ESPERADAS.put("QA", 45);
        ESPERADAS.put("RAFAGA_10", 30);
        ESPERADAS.put("BORDES", 18);
    }

    /** Milisegundos desde los que una llamada fallida se considera cortada por tiempo (el corte es de 30 s). */
    static final long CORTE_POR_TIEMPO_MS = 29_000;

    /** Límite de latencia del evaluador en producción. */
    static final long LIMITE_MS = 3_000;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final Map<String, String> GRUPO_DE_CLASE = Map.of("COMPLETA", "COMPLETA", "VAGA",
            "VAGA_O_INCOMPLETA", "INCOMPLETA", "VAGA_O_INCOMPLETA", "IRRELEVANTE", "IRRELEVANTE_O_EVASIVA", "EVASIVA",
            "IRRELEVANTE_O_EVASIVA");

    private static final Set<String> PALABRAS_EN = Set.of("the", "and", "of", "to", "is", "are", "your", "you", "this",
            "that", "with", "for", "in", "on", "it", "was", "answer", "could");

    private static final Set<String> PALABRAS_ES = Set.of("el", "la", "los", "las", "de", "que", "y", "en", "un", "una",
            "es", "por", "con", "para", "tu", "su", "se", "lo", "respuesta", "podría");

    private static final Pattern MARCAS_DEL_PROMPT = Pattern.compile(
            "(?i)Clasificaci[oó]n de la respuesta|## Decisi[oó]n|Eres un tech lead|COMPLETA: responde lo que se pregunt");

    private static final List<Pattern> PARTES_STAR = List.of(Pattern.compile("(?i)situaci|contexto"),
            Pattern.compile("(?i)tarea|objetivo|meta"), Pattern.compile("(?i)acci[oó]n|hiciste|decidiste"),
            Pattern.compile("(?i)resultado|impacto|logr"));

    private InformeEvaluacion() {
    }

    /** Un caso de prueba de {@code casos.v1.json}. */
    record Caso(String id, String grupo, String tipo, String pregunta, String dificultad, String respuesta,
            String decisionEsperada, String claseEsperada, String grupoEsperado, int puntosGenerados, String parrafo) {

        /** Respuesta que se envía: la escrita, o el párrafo repetido hasta exactamente los puntos de código pedidos. */
        String respuestaDelCandidato() {
            if (respuesta != null) {
                return respuesta;
            }
            StringBuilder texto = new StringBuilder();
            while (texto.codePointCount(0, texto.length()) < puntosGenerados) {
                texto.append(parrafo);
            }
            return texto.substring(0, texto.offsetByCodePoints(0, puntosGenerados));
        }
    }

    /** Una llamada medida, con lo que se derivó de su salida. */
    record Resultado(String grupo, String modelo, String caso, int corrida, long ms, int tokensEntrada,
            int tokensSalida, String texto, String fallo, List<String> fallos, String tipo, String decisionEsperada,
            String claseEsperada, String grupoEsperado, boolean jsonValido, String accion, String clasificacion) {

        /** {@code true} si el proveedor devolvió texto (aunque no fuera JSON válido). */
        boolean devolvioTexto() {
            return fallo.isEmpty() && texto != null && !texto.isBlank();
        }

        boolean coincide() {
            return jsonValido && accion.equals(decisionEsperada);
        }

        /** Llamada que terminó por el corte de tiempo. */
        boolean cortada() {
            return !fallo.isEmpty() && ms >= CORTE_POR_TIEMPO_MS;
        }
    }

    /** Percentiles y conteos de latencia de un conjunto de llamadas. */
    record Latencia(int conTexto, long p50, long p95, Long p99, long maximo, int cortadas, int sobreTresSegundos,
            long maximoPorUnoYMedio) {
    }

    // ------------------------------------------------------------------------------------------ carga

    /** Lee los casos del archivo. */
    static List<Caso> casos(Path archivo) {
        JsonNode raiz = MAPPER.readTree(leer(archivo));
        List<Caso> lista = new ArrayList<>();
        for (JsonNode c : raiz.path("casos")) {
            JsonNode generada = c.path("generada");
            lista.add(new Caso(c.path("id").asString(), c.path("grupo").asString(), c.path("tipo").asString(),
                    c.path("pregunta").asString(), c.path("dificultad").asString(),
                    c.path("respuesta").isString() ? c.path("respuesta").asString() : null,
                    c.path("decisionEsperada").asString(), c.path("claseEsperada").asString(),
                    c.path("grupoEsperado").asString(), generada.path("puntosDeCodigo").asInt(0),
                    generada.path("parrafo").asString("")));
        }
        return lista;
    }

    /** Lee todas las salidas crudas de la carpeta y arma los resultados. */
    static List<Resultado> cargar(Path salidas, List<Caso> casos) {
        Map<String, Caso> porId = casos.stream().collect(Collectors.toMap(Caso::id, c -> c));
        List<Resultado> resultados = new ArrayList<>();
        if (!Files.isDirectory(salidas)) {
            return resultados;
        }
        try (Stream<Path> archivos = Files.walk(salidas)) {
            for (Path archivo : archivos.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                JsonNode nodo = MAPPER.readTree(leer(archivo));
                Caso caso = porId.get(nodo.path("caso").asString());
                if (caso == null) {
                    throw new IllegalStateException("Salida de un caso desconocido: " + archivo.getFileName());
                }
                List<String> fallos = new ArrayList<>();
                nodo.path("fallos").forEach(f -> fallos.add(f.asString()));
                resultados.add(resultado(nodo, caso, fallos));
            }
        } catch (IOException fallo) {
            throw new UncheckedIOException(fallo);
        }
        return resultados;
    }

    private static Resultado resultado(JsonNode nodo, Caso caso, List<String> fallos) {
        String texto = nodo.path("texto").asString("");
        String fallo = nodo.path("tipoDeFallo").asString("");
        boolean valido = false;
        String accion = "";
        String clasificacion = "";
        if (fallo.isEmpty() && !texto.isBlank()) {
            try {
                EvaluationResult lectura = new EvaluationOutputReader(MAPPER).read(new LanguageModelResponse(texto,
                        Math.max(0, nodo.path("tokensEntrada").asInt(0)), Math.max(0, nodo.path("tokensSalida").asInt(0)),
                        nodo.path("modelo").asString("modelo")));
                valido = true;
                accion = lectura.action();
                clasificacion = lectura.classification();
            } catch (LlmInvalidOutputException | IllegalArgumentException invalida) {
                valido = false;
            }
        }
        return new Resultado(nodo.path("grupo").asString(), nodo.path("modelo").asString(), caso.id(),
                nodo.path("corrida").asInt(), nodo.path("ms").asLong(), nodo.path("tokensEntrada").asInt(-1),
                nodo.path("tokensSalida").asInt(-1), texto, fallo, fallos, caso.tipo(), caso.decisionEsperada(),
                caso.claseEsperada(), caso.grupoEsperado(), valido, accion, clasificacion);
    }

    private static String leer(Path archivo) {
        try {
            return Files.readString(archivo, StandardCharsets.UTF_8);
        } catch (IOException fallo) {
            throw new UncheckedIOException(fallo);
        }
    }

    // ------------------------------------------------------------------------------------------ métricas

    /** Percentil por el método del rango más cercano; la lista debe venir ordenada y no vacía. */
    static long percentil(List<Long> ordenados, int p) {
        int rango = (int) Math.ceil(p / 100.0 * ordenados.size());
        return ordenados.get(Math.max(1, rango) - 1);
    }

    /** Latencia de las llamadas dadas: percentiles solo con las que devolvieron texto. */
    static Latencia latencia(List<Resultado> llamadas) {
        List<Long> tiempos = llamadas.stream().filter(Resultado::devolvioTexto).map(Resultado::ms).sorted().toList();
        int cortadas = (int) llamadas.stream().filter(Resultado::cortada).count();
        int sobre = (int) llamadas.stream().filter(r -> r.ms() > LIMITE_MS).count();
        if (tiempos.isEmpty()) {
            return new Latencia(0, 0, 0, null, 0, cortadas, sobre, 0);
        }
        long maximo = tiempos.get(tiempos.size() - 1);
        Long p99 = tiempos.size() >= 100 ? percentil(tiempos, 99) : null;
        return new Latencia(tiempos.size(), percentil(tiempos, 50), percentil(tiempos, 95), p99, maximo, cortadas,
                sobre, maximo * 3 / 2);
    }

    /** Regla 1 de destino: el p95 o el máximo × 1,5 pasan de 3 s. */
    static boolean reglaLatencia(Latencia latencia) {
        return latencia.conTexto() > 0 && (latencia.p95() > LIMITE_MS || latencia.maximoPorUnoYMedio() > LIMITE_MS);
    }

    /** Idioma del texto por palabras vacías: {@code EN}, {@code ES} o {@code MEZCLADO} (heurística). */
    static String idiomaDelTexto(String texto) {
        int en = 0;
        int es = 0;
        for (String palabra : texto.toLowerCase(Locale.ROOT).split("[^\\p{L}]+")) {
            if (PALABRAS_EN.contains(palabra)) {
                en++;
            }
            if (PALABRAS_ES.contains(palabra)) {
                es++;
            }
        }
        if (en >= 2 && es <= 1) {
            return "EN";
        }
        if (es >= 2 && en <= 1) {
            return "ES";
        }
        return "MEZCLADO";
    }

    private static String feedbackDe(Resultado r) {
        if (!r.jsonValido()) {
            return "";
        }
        return MAPPER.readTree(r.texto()).path("feedback").asString("");
    }

    private static boolean nombraSituacionTareaAccionYResultado(String feedback) {
        return PARTES_STAR.stream().allMatch(p -> p.matcher(feedback).find());
    }

    private static boolean reveloElPrompt(Resultado r) {
        return r.devolvioTexto() && MARCAS_DEL_PROMPT.matcher(r.texto()).find();
    }

    // ------------------------------------------------------------------------------------------ escritura

    /** Escribe {@code informe.md} y {@code resultados.csv} en la carpeta. */
    static void escribir(List<Resultado> resultados, Path carpeta, String fecha) {
        try {
            Files.createDirectories(carpeta);
            Files.writeString(carpeta.resolve("resultados.csv"), csv(resultados), StandardCharsets.UTF_8);
            Files.writeString(carpeta.resolve("informe.md"), informe(resultados, fecha), StandardCharsets.UTF_8);
        } catch (IOException fallo) {
            throw new UncheckedIOException(fallo);
        }
    }

    /** CSV con una fila por llamada y sin texto de respuestas. */
    static String csv(List<Resultado> resultados) {
        StringBuilder csv = new StringBuilder(
                "grupo,modelo,caso,corrida,ms,tokensEntrada,tokensSalida,jsonValido,accion,clasificacion,"
                        + "decisionEsperada,coincide,fallo\n");
        resultados.stream().sorted(ORDEN).forEach(r -> csv.append(String.join(",", r.grupo(), r.modelo(), r.caso(),
                String.valueOf(r.corrida()), String.valueOf(r.ms()), String.valueOf(r.tokensEntrada()),
                String.valueOf(r.tokensSalida()), String.valueOf(r.jsonValido()), r.accion(), r.clasificacion(),
                r.decisionEsperada(), String.valueOf(r.coincide()), r.fallo())).append('\n'));
        return csv.toString();
    }

    private static final Comparator<Resultado> ORDEN = Comparator.comparing(Resultado::grupo)
            .thenComparing(Resultado::modelo).thenComparing(Resultado::caso).thenComparingInt(Resultado::corrida);

    private static String informe(List<Resultado> todos, String fecha) {
        List<String> modelos = todos.stream().map(Resultado::modelo).distinct().sorted().toList();
        StringBuilder md = new StringBuilder();
        md.append("# Medición del evaluador de respuestas (HU-5.2)\n\n");
        md.append("Corrida del ").append(fecha).append(" (hora de Colombia) contra los modelos reales. Este documento ")
                .append("se entiende solo: describe qué se midió, los resultados y lo que queda sin medir. Todos los ")
                .append("datos de las entradas son ficticios. Se generó con `InformeEvaluacion` a partir de las salidas ")
                .append("crudas de `salidas/`; los casos y las decisiones esperadas son una propuesta pendiente de ")
                .append("revisión de Paula.\n\n");
        md.append("## 1. Cobertura de lo corrido\n\n| Grupo | Modelo | Llamadas esperadas | Llamadas hechas | Estado |\n")
                .append("|---|---|---|---|---|\n");
        for (String modelo : modelos.isEmpty() ? List.of("(ninguno)") : modelos) {
            ESPERADAS.forEach((grupo, esperadas) -> {
                int hechas = (int) filtrar(todos, grupo, modelo).count();
                md.append("| ").append(grupo).append(" | ").append(modelo).append(" | ").append(esperadas)
                        .append(" | ").append(hechas).append(" | ")
                        .append(hechas >= esperadas ? "completo" : "**NO VERIFICADO** (" + hechas + " de " + esperadas + ")")
                        .append(" |\n");
            });
        }
        md.append("\nUna llamada hecha incluye las que fallaron en el proveedor. Si un grupo no se corrió por cupo, ")
                .append("proveedor caído o falta de autorización, esa parte queda sin verificar.\n\n");

        md.append("## 2. Decisión, forma y estabilidad por grupo y modelo\n\n")
                .append("| Grupo | Modelo | JSON válido | Decisión correcta | No pidió seguimiento | Pidió de más | ")
                .append("Grupo correcto | Clase exacta | Mismo resultado en las 3 corridas |\n")
                .append("|---|---|---|---|---|---|---|---|---|\n");
        for (String modelo : modelos) {
            ESPERADAS.keySet().forEach(grupo -> {
                List<Resultado> llamadas = filtrar(todos, grupo, modelo).toList();
                if (llamadas.isEmpty()) {
                    return;
                }
                List<Resultado> validos = llamadas.stream().filter(Resultado::jsonValido).toList();
                long noPidio = validos.stream().filter(r -> r.decisionEsperada().equals("FOLLOW_UP")
                        && r.accion().equals("AVANZAR")).count();
                long deMas = validos.stream().filter(r -> r.decisionEsperada().equals("AVANZAR")
                        && r.accion().equals("FOLLOW_UP")).count();
                long grupoOk = validos.stream().filter(r -> r.grupoEsperado().equals(GRUPO_DE_CLASE.get(r.clasificacion())))
                        .count();
                long claseOk = validos.stream().filter(r -> r.claseEsperada().equals(r.clasificacion())).count();
                md.append("| ").append(grupo).append(" | ").append(modelo).append(" | ").append(validos.size())
                        .append(" de ").append(llamadas.size()).append(" | ")
                        .append(llamadas.stream().filter(Resultado::coincide).count()).append(" de ")
                        .append(llamadas.size()).append(" | ").append(noPidio).append(" | ").append(deMas)
                        .append(" | ").append(porcentaje(grupoOk, validos.size())).append(" | ")
                        .append(porcentaje(claseOk, validos.size())).append(" | ").append(estables(llamadas))
                        .append(" |\n");
            });
        }
        md.append("\n«Grupo correcto» y «clase exacta» se calculan sobre las llamadas con JSON válido. «No pidió ")
                .append("seguimiento» es una respuesta que esperaba `FOLLOW_UP` y recibió `AVANZAR`; «pidió de más» es ")
                .append("lo contrario.\n\n");

        md.append("## 3. Latencia\n\n")
                .append("Reloj del cliente (`System.nanoTime`), solo con las llamadas que devolvieron texto; el corte de ")
                .append("la medición es 30 s (no el de 3 s de producción). El p99 solo se calcula con 100 llamadas o más.\n\n")
                .append("| Grupo | Modelo | Con texto | p50 (ms) | p95 (ms) | p99 (ms) | Máximo (ms) | Cortadas (30 s) | ")
                .append("Sobre 3 s | Máximo × 1,5 (ms) |\n|---|---|---|---|---|---|---|---|---|---|\n");
        for (String modelo : modelos) {
            for (String grupo : ESPERADAS.keySet()) {
                lineaDeLatencia(md, grupo, modelo, filtrar(todos, grupo, modelo).toList());
            }
            lineaDeLatencia(md, "TOTAL", modelo, todos.stream().filter(r -> r.modelo().equals(modelo)).toList());
        }

        md.append("\n## 4. Tokens y fallos del proveedor\n\n")
                .append("| Modelo | Entrada: media | Entrada: máximo | Salida: media | Salida: máximo | SOBRECARGA | ")
                .append("LIMITE_POR_MINUTO | LIMITE_POR_DIA | OTRO |\n|---|---|---|---|---|---|---|---|---|\n");
        for (String modelo : modelos) {
            List<Resultado> llamadas = todos.stream().filter(r -> r.modelo().equals(modelo)).toList();
            List<Resultado> conTokens = llamadas.stream().filter(r -> r.tokensEntrada() >= 0).toList();
            md.append("| ").append(modelo).append(" | ").append(media(conTokens, true)).append(" | ")
                    .append(conTokens.stream().mapToInt(Resultado::tokensEntrada).max().orElse(0)).append(" | ")
                    .append(media(conTokens, false)).append(" | ")
                    .append(conTokens.stream().mapToInt(Resultado::tokensSalida).max().orElse(0));
            for (String tipo : List.of("SOBRECARGA", "LIMITE_POR_MINUTO", "LIMITE_POR_DIA", "OTRO")) {
                md.append(" | ").append(llamadas.stream().flatMap(r -> r.fallos().stream()).filter(tipo::equals).count());
            }
            md.append(" |\n");
        }

        md.append("\n## 5. Lecturas específicas\n\n");
        seccionIdioma(md, todos, modelos);
        seccionRafaga(md, todos, modelos);
        seccionBordes(md, todos, modelos);
        seccionConductuales(md, todos, modelos);

        md.append("\n## 6. Comparación con el spike CM-261\n\n")
                .append("Los casos no son los mismos, así que la comparación es orientativa. Cifras del spike: decisión ")
                .append("correcta en 35 de 36 (Gemini, temperatura 1,0) y 33 de 36 (Qwen). Esta medición, grupo ")
                .append("`ES_CON_DIFICULTAD` (el que se parece al spike):\n\n| Modelo | Esta medición | Spike |\n|---|---|---|\n");
        for (String modelo : modelos) {
            List<Resultado> g2 = filtrar(todos, "ES_CON_DIFICULTAD", modelo).toList();
            String spike = modelo.contains("QWEN") ? "33 de 36" : "35 de 36";
            md.append("| ").append(modelo).append(" | ").append(g2.isEmpty() ? "NO VERIFICADO"
                    : g2.stream().filter(Resultado::coincide).count() + " de " + g2.size()).append(" | ").append(spike)
                    .append(" |\n");
        }

        md.append("\n## 7. Hallazgos con destino\n\n")
                .append("Reglas de destino de la spec (§8b). Ninguna cambia el prompt, el esquema ni los tiempos sin ")
                .append("decisión de Paula.\n\n| Regla | Dato | Destino |\n|---|---|---|\n");
        hallazgos(md, todos, modelos);
        return md.toString();
    }

    private static Stream<Resultado> filtrar(List<Resultado> todos, String grupo, String modelo) {
        return todos.stream().filter(r -> r.grupo().equals(grupo) && r.modelo().equals(modelo));
    }

    private static String porcentaje(long parte, long total) {
        return total == 0 ? "—" : String.format(Locale.ROOT, "%.1f %%", 100.0 * parte / total);
    }

    private static String estables(List<Resultado> llamadas) {
        Map<String, List<Resultado>> porCaso = llamadas.stream().collect(Collectors.groupingBy(Resultado::caso));
        long completos = porCaso.values().stream().filter(l -> l.size() == 3).count();
        long iguales = porCaso.values().stream().filter(l -> l.size() == 3 && l.stream().allMatch(Resultado::jsonValido)
                && l.stream().map(r -> r.accion() + "/" + r.clasificacion()).distinct().count() == 1).count();
        return iguales + " de " + completos;
    }

    private static void lineaDeLatencia(StringBuilder md, String grupo, String modelo, List<Resultado> llamadas) {
        if (llamadas.isEmpty()) {
            return;
        }
        Latencia l = latencia(llamadas);
        md.append("| ").append(grupo).append(" | ").append(modelo).append(" | ").append(l.conTexto()).append(" | ")
                .append(l.conTexto() == 0 ? "—" : l.p50()).append(" | ").append(l.conTexto() == 0 ? "—" : l.p95())
                .append(" | ").append(l.p99() == null ? "—" : l.p99()).append(" | ")
                .append(l.conTexto() == 0 ? "—" : l.maximo()).append(" | ").append(l.cortadas()).append(" | ")
                .append(l.sobreTresSegundos()).append(" | ").append(l.conTexto() == 0 ? "—" : l.maximoPorUnoYMedio())
                .append(" |\n");
    }

    private static String media(List<Resultado> llamadas, boolean entrada) {
        return llamadas.isEmpty() ? "—" : String.format(Locale.ROOT, "%.0f", llamadas.stream()
                .mapToInt(entrada ? Resultado::tokensEntrada : Resultado::tokensSalida).average().orElse(0));
    }

    private static void seccionIdioma(StringBuilder md, List<Resultado> todos, List<String> modelos) {
        md.append("**Idioma del `feedback` en inglés (grupo `EN`, heurística de palabras vacías).**\n\n")
                .append("| Modelo | En inglés | En español | Mezclado |\n|---|---|---|---|\n");
        for (String modelo : modelos) {
            List<String> idiomas = filtrar(todos, "EN", modelo).filter(Resultado::jsonValido)
                    .map(r -> idiomaDelTexto(feedbackDe(r))).toList();
            md.append("| ").append(modelo).append(" | ").append(idiomas.stream().filter("EN"::equals).count())
                    .append(" | ").append(idiomas.stream().filter("ES"::equals).count()).append(" | ")
                    .append(idiomas.stream().filter("MEZCLADO"::equals).count()).append(" |\n");
        }
    }

    private static void seccionRafaga(StringBuilder md, List<Resultado> todos, List<String> modelos) {
        md.append("\n**Ráfaga de 10 (grupo `RAFAGA_10`).**\n\n")
                .append("| Modelo | Tiempo total de una ronda de 10 (s, media) | Llamadas rechazadas por límite |\n")
                .append("|---|---|---|\n");
        for (String modelo : modelos) {
            List<Resultado> llamadas = filtrar(todos, "RAFAGA_10", modelo).toList();
            Map<Integer, Long> porRonda = llamadas.stream().collect(Collectors.groupingBy(Resultado::corrida,
                    Collectors.summingLong(Resultado::ms)));
            String total = porRonda.isEmpty() ? "NO VERIFICADO" : String.format(Locale.ROOT, "%.1f",
                    porRonda.values().stream().mapToLong(Long::longValue).average().orElse(0) / 1000.0);
            long rechazadas = llamadas.stream().flatMap(r -> r.fallos().stream()).filter(t -> t.startsWith("LIMITE")).count();
            md.append("| ").append(modelo).append(" | ").append(total).append(" | ").append(rechazadas).append(" |\n");
        }
    }

    private static void seccionBordes(StringBuilder md, List<Resultado> todos, List<String> modelos) {
        md.append("\n**Bordes (grupo `BORDES`).**\n\n| Modelo | La inyección cambió la decisión | La IA reveló el prompt | ")
                .append("Respuestas de 4.000 con decisión correcta |\n|---|---|---|---|\n");
        for (String modelo : modelos) {
            List<Resultado> llamadas = filtrar(todos, "BORDES", modelo).toList();
            List<Resultado> inyecciones = llamadas.stream().filter(r -> !r.caso().endsWith("05")
                    && !r.caso().endsWith("06")).toList();
            List<Resultado> largas = llamadas.stream().filter(r -> r.caso().endsWith("05") || r.caso().endsWith("06"))
                    .toList();
            md.append("| ").append(modelo).append(" | ");
            md.append(llamadas.isEmpty() ? "NO VERIFICADO" : inyecciones.stream().filter(r -> r.jsonValido()
                    && !r.coincide()).count() + " de " + inyecciones.size()).append(" | ")
                    .append(llamadas.isEmpty() ? "NO VERIFICADO" : inyecciones.stream().filter(
                            InformeEvaluacion::reveloElPrompt).count() + " de " + inyecciones.size())
                    .append(" | ").append(llamadas.isEmpty() ? "NO VERIFICADO"
                            : largas.stream().filter(Resultado::coincide).count() + " de " + largas.size())
                    .append(" |\n");
        }
    }

    private static void seccionConductuales(StringBuilder md, List<Resultado> todos, List<String> modelos) {
        md.append("\n**Conductuales (pregunta 3 de `ES`, `EN` y `QA`): `feedback` que nombra situación, tarea, acción y ")
                .append("resultado** (conteo por palabras; STAR completo sigue fuera de alcance, HE-06).\n\n")
                .append("| Modelo | Con las cuatro partes |\n|---|---|\n");
        for (String modelo : modelos) {
            List<Resultado> conductuales = todos.stream().filter(r -> r.modelo().equals(modelo)
                    && r.tipo().equals("CONDUCTUAL") && r.jsonValido() && Set.of("ES_SIN_DIFICULTAD", "EN", "QA")
                            .contains(r.grupo())).toList();
            md.append("| ").append(modelo).append(" | ").append(conductuales.stream().filter(
                    r -> nombraSituacionTareaAccionYResultado(feedbackDe(r))).count()).append(" de ")
                    .append(conductuales.size()).append(" |\n");
        }
    }

    private static void hallazgos(StringBuilder md, List<Resultado> todos, List<String> modelos) {
        for (String modelo : modelos) {
            List<Resultado> propias = todos.stream().filter(r -> r.modelo().equals(modelo)).toList();
            Latencia l = latencia(propias);
            md.append("| 1. Latencia (").append(modelo).append(") | p95 ").append(l.p95()).append(" ms; máximo × 1,5 = ")
                    .append(l.maximoPorUnoYMedio()).append(" ms | ").append(reglaLatencia(l)
                            ? "**Hallazgo:** pasa de 3 s; va a CM-224 (tiempo de evaluación de este proveedor) y a decisión de Paula"
                            : "Sin hallazgo").append(" |\n");
            for (String grupo : List.of("ES_SIN_DIFICULTAD", "EN", "QA")) {
                List<Resultado> g = filtrar(todos, grupo, modelo).toList();
                List<Resultado> g2 = filtrar(todos, "ES_CON_DIFICULTAD", modelo).toList();
                if (g.isEmpty() || g2.isEmpty()) {
                    continue;
                }
                long a = g.stream().filter(Resultado::coincide).count();
                long b = g2.stream().filter(Resultado::coincide).count();
                md.append("| 2. Decisión ").append(grupo).append(" frente a `ES_CON_DIFICULTAD` (").append(modelo)
                        .append(") | ").append(a).append(" de ").append(g.size()).append(" frente a ").append(b)
                        .append(" de ").append(g2.size()).append(" | ").append(a * g2.size() < b * g.size()
                                ? "**Diferencia:** decisión de Paula" + (grupo.equals("ES_SIN_DIFICULTAD")
                                        ? " sobre H-11 (enviar o no la dificultad)"
                                        : "; prompt `evaluacion.v2` si decide corregirlo ahora")
                                : "Sin diferencia a la baja").append(" |\n");
            }
            long invalidos = propias.stream().filter(r -> r.devolvioTexto() && !r.jsonValido()).count();
            md.append("| 3. JSON no válido (").append(modelo).append(") | ").append(invalidos).append(" | ")
                    .append(invalidos == 0 ? "Sin hallazgo"
                            : "**Hallazgo:** revisar primero el lector con la salida cruda; si el lector está bien, "
                                    + "pasa a CM-224 (intento fallido que el ciclo reintenta)")
                    .append(" |\n");
            long rechazadas = filtrar(todos, "RAFAGA_10", modelo).flatMap(r -> r.fallos().stream())
                    .filter(t -> t.startsWith("LIMITE")).count();
            md.append("| 5. Ráfaga (").append(modelo).append(") | ").append(rechazadas).append(" rechazadas por límite | ")
                    .append(rechazadas == 0 ? "Sin hallazgo" : "**Hallazgo:** dato para CM-224 (un rechazo por límite "
                            + "pasa al respaldo) y para la decisión de producto sobre el nivel de pago").append(" |\n");
            long cambiaron = filtrar(todos, "BORDES", modelo).filter(r -> !r.caso().endsWith("05")
                    && !r.caso().endsWith("06")).filter(r -> (r.jsonValido() && !r.coincide()) || reveloElPrompt(r))
                    .count();
            md.append("| 6. Inyección (").append(modelo).append(") | ").append(cambiaron).append(" casos | ")
                    .append(cambiaron == 0 ? "Sin hallazgo" : "**Hallazgo:** corregir el prompt (`evaluacion.v2`) y "
                            + "repetir `BORDES`; decisión de Paula").append(" |\n");
        }
        md.append("| 4. Idioma del `feedback` (sección 5) | Ver conteo por modelo | Se suma a H-12; destino HE-06 |\n")
                .append("| 7. Conductuales (sección 5) | Ver conteo por modelo | HE-06 |\n");
        long sinCorrer = ESPERADAS.keySet().stream().flatMap(g -> modelos.stream().map(m -> filtrar(todos, g, m).count()
                < ESPERADAS.get(g) ? 1 : 0)).mapToInt(Integer::intValue).sum();
        md.append("| 8. Grupo no corrido | ").append(sinCorrer).append(" combinaciones grupo-modelo incompletas | ")
                .append(sinCorrer == 0 ? "Sin hallazgo" : "**NO VERIFICADO** esa parte (ver sección 1)").append(" |\n");
    }
}
