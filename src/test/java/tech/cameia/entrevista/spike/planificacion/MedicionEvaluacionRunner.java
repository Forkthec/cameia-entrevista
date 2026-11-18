package tech.cameia.entrevista.spike.planificacion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import tech.cameia.entrevista.application.service.EvaluationPromptBuilder;
import tech.cameia.entrevista.domain.port.LanguageModelPort;
import tech.cameia.entrevista.infrastructure.config.ia.LanguageModelProperties;
import tech.cameia.entrevista.spike.planificacion.InformeEvaluacion.Caso;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Medición del evaluador de respuestas (HU-5.2) contra Gemini (por el puerto real del servicio) y Qwen en Groq.
 *
 * <p>Manual: solo corre con {@code SPIKE_EVALUACION=true}. Arma cada prompt con {@link EvaluationPromptBuilder} y lee
 * cada salida con el lector real del servicio. Variables: {@code SPIKE_GRUPOS} (lista separada por comas; vacío =
 * todos), {@code SPIKE_PRESUPUESTO_GEMINI} (200 por defecto) y {@code SPIKE_PRESUPUESTO_QWEN} (400 por defecto): máximo
 * de llamadas por modelo, contando todas las hechas. Las llaves salen solo del entorno del proceso
 * ({@code GOOGLE_GENAI_API_KEY}, {@code LLM_MODEL}, {@code GROQ_API_KEY}); nunca se imprimen ni se guardan. Una llamada
 * ya guardada se salta, de modo que la corrida se puede reanudar. Al terminar reescribe el informe con todas las
 * salidas de la carpeta.</p>
 */
@EnabledIfEnvironmentVariable(named = "SPIKE_EVALUACION", matches = "true")
class MedicionEvaluacionRunner {

    private static final Path BASE = Path.of("docs", "mediciones", "CM-216-evaluacion");

    private static final Path SALIDAS = BASE.resolve("salidas");

    private static final Path CASOS = Path.of("src", "test", "resources", "spike", "evaluacion", "casos.v1.json");

    private static final JsonMapper MAPPER = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private static final int CORRIDAS = 3;

    private static final int VENTANA_GEMINI = 15;

    private static final Duration TIEMPO_LIMITE = Duration.ofSeconds(30);

    /** Una llamada pendiente: caso, corrida (en {@code RAFAGA_10}, la ronda) y si abre una ronda. */
    private record Trabajo(String grupo, Caso caso, int corrida, boolean abreRonda) {
    }

    @Test
    void ejecutar() throws Exception {
        List<Caso> casos = InformeEvaluacion.casos(CASOS);
        Set<String> pedidos = Arrays.stream(System.getenv().getOrDefault("SPIKE_GRUPOS", "").split(","))
                .map(String::strip).filter(g -> !g.isEmpty()).collect(Collectors.toSet());
        Set<String> grupos = pedidos.isEmpty() ? InformeEvaluacion.ESPERADAS.keySet() : pedidos;
        for (String grupo : grupos) {
            if (!InformeEvaluacion.ESPERADAS.containsKey(grupo)) {
                throw new IllegalArgumentException("Grupo desconocido en SPIKE_GRUPOS: " + grupo);
            }
        }
        String llaveGemini = exigir("GOOGLE_GENAI_API_KEY");
        String modeloGemini = exigir("LLM_MODEL");
        String llaveGroq = exigir("GROQ_API_KEY");
        int topeGemini = Integer.parseInt(System.getenv().getOrDefault("SPIKE_PRESUPUESTO_GEMINI", "200"));
        int topeQwen = Integer.parseInt(System.getenv().getOrDefault("SPIKE_PRESUPUESTO_QWEN", "400"));
        String esquema = new EvaluationPromptBuilder().build("pregunta", "respuesta").jsonSchema();
        Files.createDirectories(SALIDAS);

        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
                        ToolCallingAutoConfiguration.class, GoogleGenAiChatAutoConfiguration.class))
                .withUserConfiguration(ClienteReal.class)
                .withPropertyValues("cameia.llm.api-key=" + llaveGemini, "cameia.llm.model=" + modeloGemini,
                        "cameia.llm.timeout=30s", "cameia.llm.temperature=1.0", "cameia.llm.thinking-level=minimal")
                .run(ctx -> {
                    ModeloDePrueba gemini = new GeminiLite(ctx.getBean(LanguageModelPort.class), modeloGemini,
                            llaveGemini, esquema);
                    ModeloDePrueba qwen = new QwenGroq(llaveGroq, 1.0, "none", TIEMPO_LIMITE).conEsquema(esquema);
                    List<Trabajo> trabajos = trabajos(casos, grupos);
                    ExecutorService hilos = Executors.newFixedThreadPool(2);
                    try {
                        Future<?> a = hilos.submit(() -> new Ejecucion(gemini, topeGemini, 5_000, 5_000, true,
                                llaveGemini, llaveGroq).correr(trabajos));
                        Future<?> b = hilos.submit(() -> new Ejecucion(qwen, topeQwen, 8_000, 18_000, false,
                                llaveGemini, llaveGroq).correr(trabajos));
                        a.get();
                        b.get();
                    } finally {
                        hilos.shutdown();
                    }
                });

        InformeEvaluacion.escribir(InformeEvaluacion.cargar(SALIDAS, casos), BASE,
                LocalDate.now(ZoneId.of("America/Bogota")).toString());
        System.out.println("Informe reescrito en " + BASE.resolve("informe.md"));
    }

    private static String exigir(String variable) {
        String valor = System.getenv(variable);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta " + variable + " en el entorno del proceso");
        }
        return valor;
    }

    /** Las llamadas de cada grupo, en el orden en que se corren. */
    private static List<Trabajo> trabajos(List<Caso> casos, Set<String> grupos) {
        Map<String, List<Caso>> porGrupo = casos.stream().collect(Collectors.groupingBy(Caso::grupo,
                LinkedHashMap::new, Collectors.toList()));
        List<Trabajo> trabajos = new ArrayList<>();
        for (String grupo : InformeEvaluacion.ESPERADAS.keySet()) {
            if (!grupos.contains(grupo)) {
                continue;
            }
            switch (grupo) {
                case "ES_SIN_DIFICULTAD", "ES_CON_DIFICULTAD" -> porGrupo.get("ES").forEach(
                        c -> agregarCorridas(trabajos, grupo, c));
                case "EN", "QA", "BORDES" -> porGrupo.get(grupo).forEach(c -> agregarCorridas(trabajos, grupo, c));
                case "RAFAGA_10" -> {
                    for (int ronda = 1; ronda <= CORRIDAS; ronda++) {
                        boolean primera = true;
                        for (Caso c : porGrupo.get("ES")) {
                            if (c.id().endsWith("-A") || c.id().endsWith("-B")) {
                                trabajos.add(new Trabajo(grupo, c, ronda, primera));
                                primera = false;
                            }
                        }
                    }
                }
                default -> throw new IllegalStateException(grupo);
            }
        }
        return trabajos;
    }

    private static void agregarCorridas(List<Trabajo> trabajos, String grupo, Caso caso) {
        for (int corrida = 1; corrida <= CORRIDAS; corrida++) {
            trabajos.add(new Trabajo(grupo, caso, corrida, false));
        }
    }

    /** Corre las llamadas de un modelo respetando su presupuesto, su ritmo y sus límites. */
    private static final class Ejecucion {

        private final ModeloDePrueba modelo;

        private final int tope;

        private final long pausaMs;

        private final long pausaBordesMs;

        private final boolean conVentana;

        private final String[] llaves;

        private final AtomicInteger usadas = new AtomicInteger();

        private final Deque<Long> ultimas = new ArrayDeque<>();

        private final EvaluationPromptBuilder constructor = new EvaluationPromptBuilder();

        private boolean detenido;

        Ejecucion(ModeloDePrueba modelo, int tope, long pausaMs, long pausaBordesMs, boolean conVentana,
                String... llaves) {
            this.modelo = modelo;
            this.tope = tope;
            this.pausaMs = pausaMs;
            this.pausaBordesMs = pausaBordesMs;
            this.conVentana = conVentana;
            this.llaves = llaves;
        }

        void correr(List<Trabajo> trabajos) {
            for (Trabajo trabajo : trabajos) {
                if (detenido) {
                    return;
                }
                boolean rafaga = trabajo.grupo().equals("RAFAGA_10");
                if (rafaga && trabajo.abreRonda() && trabajo.corrida() > 1) {
                    dormir(65_000);
                }
                boolean llamo = ejecutar(trabajo);
                if (llamo && !rafaga) {
                    dormir(trabajo.grupo().equals("BORDES") ? pausaBordesMs : pausaMs);
                }
            }
        }

        /** @return {@code true} si hizo al menos una llamada al proveedor */
        private boolean ejecutar(Trabajo trabajo) {
            Path archivo = SALIDAS.resolve(trabajo.grupo()).resolve(modelo.alias())
                    .resolve(trabajo.caso().id() + "-" + trabajo.corrida() + ".json");
            if (Files.exists(archivo) && !terminoPorLimiteDiario(archivo)) {
                return false;
            }
            String prompt = prompt(trabajo);
            List<String> fallos = new ArrayList<>();
            for (int intento = 1; intento <= 2; intento++) {
                if (usadas.get() >= tope) {
                    detenido = true;
                    System.out.println(modelo.alias() + ": se alcanzó el tope de " + tope + " llamadas");
                    return intento > 1;
                }
                esperarVentana(trabajo);
                usadas.incrementAndGet();
                long inicio = System.nanoTime();
                try {
                    ModeloDePrueba.Respuesta respuesta = modelo.generar(prompt, EvaluationPromptBuilder.MAX_OUTPUT_TOKENS);
                    long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
                    guardar(archivo, trabajo, ms, respuesta, null, null, fallos);
                    System.out.println(describir(trabajo, ms, "ok"));
                    return true;
                } catch (ModeloDePrueba.FalloDelProveedor fallo) {
                    long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
                    fallos.add(fallo.tipo().name());
                    boolean reintentar = fallo.tipo() == ModeloDePrueba.TipoDeFallo.LIMITE_POR_MINUTO && intento == 1;
                    if (reintentar) {
                        System.out.println(describir(trabajo, ms, "límite por minuto: espera y repite"));
                        dormir(fallo.esperaSugeridaS() > 0 ? (fallo.esperaSugeridaS() + 1) * 1000 : 65_000);
                        continue;
                    }
                    guardar(archivo, trabajo, ms, null, fallo.tipo().name(), fallo.getMessage(), fallos);
                    System.out.println(describir(trabajo, ms, "fallo " + fallo.tipo()));
                    detenido = fallo.tipo() == ModeloDePrueba.TipoDeFallo.LIMITE_POR_DIA;
                    return true;
                }
            }
            return true;
        }

        private String prompt(Trabajo trabajo) {
            String texto = constructor.build(trabajo.caso().pregunta(), trabajo.caso().respuestaDelCandidato()).text();
            if (!trabajo.grupo().equals("ES_CON_DIFICULTAD")) {
                return texto;
            }
            return texto.replace("Pregunta:", "Dificultad: " + trabajo.caso().dificultad() + "\nPregunta:");
        }

        private void esperarVentana(Trabajo trabajo) {
            if (!conVentana || trabajo.grupo().equals("RAFAGA_10")) {
                return;
            }
            long ahora = System.currentTimeMillis();
            while (!ultimas.isEmpty() && ahora - ultimas.peekFirst() > 60_000) {
                ultimas.pollFirst();
            }
            if (ultimas.size() >= VENTANA_GEMINI) {
                dormir(60_000 - (ahora - ultimas.peekFirst()) + 500);
            }
            ultimas.addLast(System.currentTimeMillis());
        }

        private boolean terminoPorLimiteDiario(Path archivo) {
            try {
                return "LIMITE_POR_DIA".equals(MAPPER.readTree(Files.readString(archivo, StandardCharsets.UTF_8))
                        .path("tipoDeFallo").asString(""));
            } catch (IOException ilegible) {
                throw new UncheckedIOException(ilegible);
            }
        }

        private void guardar(Path archivo, Trabajo trabajo, long ms, ModeloDePrueba.Respuesta respuesta, String tipo,
                String causa, List<String> fallos) {
            ObjectNode nodo = MAPPER.createObjectNode();
            nodo.put("grupo", trabajo.grupo()).put("modelo", modelo.alias()).put("caso", trabajo.caso().id())
                    .put("corrida", trabajo.corrida()).put("ms", ms);
            nodo.put("tokensEntrada", respuesta == null ? -1 : respuesta.tokensEntrada())
                    .put("tokensSalida", respuesta == null ? -1 : respuesta.tokensSalida())
                    .put("texto", respuesta == null ? "" : Secretos.enmascarar(respuesta.texto(), llaves))
                    .put("tipoDeFallo", tipo == null ? "" : tipo)
                    .put("causa", causa == null ? "" : Secretos.enmascarar(causa, llaves));
            fallos.forEach(f -> nodo.withArray("fallos").add(f));
            try {
                Files.createDirectories(archivo.getParent());
                Files.writeString(archivo, MAPPER.writeValueAsString(nodo), StandardCharsets.UTF_8);
            } catch (IOException fallo) {
                throw new UncheckedIOException(fallo);
            }
        }

        private String describir(Trabajo trabajo, long ms, String estado) {
            return modelo.alias() + " " + trabajo.grupo() + " " + trabajo.caso().id() + " c" + trabajo.corrida() + " "
                    + ms + " ms " + estado + " (llamadas " + usadas.get() + "/" + tope + ")";
        }

        private static void dormir(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException interrumpido) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** El adaptador y la configuración real del cliente, sin el resto de la aplicación. */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = LanguageModelProperties.class,
            basePackages = "tech.cameia.entrevista.infrastructure.ia")
    static class ClienteReal {
    }
}
