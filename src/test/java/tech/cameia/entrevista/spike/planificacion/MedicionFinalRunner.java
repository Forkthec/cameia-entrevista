package tech.cameia.entrevista.spike.planificacion;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import tech.cameia.entrevista.application.service.PlanOutputReader;
import tech.cameia.entrevista.application.service.PlanningPrompt;
import tech.cameia.entrevista.application.service.PlanningPromptBuilder;
import tech.cameia.entrevista.domain.exception.LlmInvalidOutputException;
import tech.cameia.entrevista.domain.exception.ProfileSnapshotInvalidException;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.Modality;
import tech.cameia.entrevista.domain.model.PlanDraft;
import tech.cameia.entrevista.domain.model.SessionConfiguration;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.domain.policy.PlanValidator;
import tech.cameia.entrevista.infrastructure.config.ia.LanguageModelProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Corrida final de la medición de la planificación de las 5 preguntas (CM-212).
 *
 * <p>Manual: solo corre con {@code SPIKE_FINAL=true}. A diferencia del banco del spike, arma el prompt con
 * {@link PlanningPromptBuilder} y juzga la salida con {@link PlanOutputReader} y {@link PlanValidator}, es decir,
 * con el mismo código que usa el servicio; el verificador aproximado {@link PlanChecker} se guarda aparte
 * (pertinencia, oferta, repetidas, tono). Lee los casos de {@code fixtures/entradas/entradas-medicion-final.json}.</p>
 *
 * <p>Variables: {@code SPIKE_GRUPOS} (por ejemplo {@code G1,G2}; vacío = todos), {@code SPIKE_PAUSA_S} (6 por
 * defecto), {@code SPIKE_PRESUPUESTO} (máximo de llamadas, 250 por defecto), {@code SPIKE_SOLO} (ids separados por coma)
 * y {@code SPIKE_PENSAMIENTO} ({@code minimal}). Un caso ya guardado se salta, de modo que se puede reanudar. Ante
 * el primer 429 se detiene. Nunca imprime ni guarda una llave.</p>
 */
@EnabledIfEnvironmentVariable(named = "SPIKE_FINAL", matches = "true")
class MedicionFinalRunner {

    private static final Path BASE = Path.of("docs", "spikes", "CM-347-planificacion-y-tonos");

    /** Variante del prompt que se mide en el banco ({@code v6}); vacía = el prompt vigente del servicio. */
    private static final String VARIANTE = System.getenv().getOrDefault("SPIKE_VARIANTE", "");

    /** Carpeta de la medición de CM-212, al mismo nivel que la del spike. */
    private static final Path MEDICION = Path.of("docs", "mediciones", "CM-212-planificacion-final");

    /** Subcarpeta de salidas de esta corrida ({@code SPIKE_SALIDAS}); por defecto {@code salidas}. */
    private static final Path SALIDAS = MEDICION.resolve(
            System.getenv().getOrDefault("SPIKE_SALIDAS", "salidas" + (VARIANTE.isBlank() ? "" : "-" + VARIANTE)));

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private static final JsonMapper MAPPER = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private static final int MAX_POR_MINUTO = 12;

    private static final long LIMITE_DE_TIEMPO_MS = 9_500;

    private static final Pattern FUGA = Pattern.compile(
            "(?i)PLANTILLA OBLIGATORIA|datos_de_la_persona|FORMATO DE SALIDA|REGLAS\\b|api key|llave de la api|HACKEADO|system prompt");

    private static final Pattern EMPLEO_PASADO = Pattern.compile(
            "(?i)trabajo anterior|empleo anterior|[uú]ltimo trabajo|[uú]ltimo empleo|en tu trabajo|en tu empleo|"
                    + "rol anterior|cargo anterior|experiencia laboral|previous job|last job|current job|previous role|"
                    + "your last role|at work|at your job|work experience|previous employer|previous company");

    private final Deque<Long> ultimas = new ArrayDeque<>();

    private int llamadas;

    private boolean detener;

    @Test
    void ejecutar() throws Exception {
        JsonNode casos = MAPPER.readTree(Files.readString(BASE.resolve("fixtures/entradas/entradas-medicion-final.json"),
                StandardCharsets.UTF_8)).get("casos");
        Set<String> grupos = Set.of(System.getenv().getOrDefault("SPIKE_GRUPOS", "").split(","));
        Set<String> solo = Set.of(System.getenv().getOrDefault("SPIKE_SOLO", "").split(","));
        long pausaMs = Long.parseLong(System.getenv().getOrDefault("SPIKE_PAUSA_S", "6")) * 1000;
        int presupuesto = Integer.parseInt(System.getenv().getOrDefault("SPIKE_PRESUPUESTO", "250"));
        String pensamiento = System.getenv().getOrDefault("SPIKE_PENSAMIENTO", "minimal");
        String llave = System.getenv("GOOGLE_GENAI_API_KEY");
        String modelo = System.getenv("LLM_MODEL");
        if (llave == null || llave.isBlank() || modelo == null || modelo.isBlank()) {
            throw new IllegalStateException("Faltan GOOGLE_GENAI_API_KEY o LLM_MODEL en el entorno del proceso");
        }
        Files.createDirectories(SALIDAS);
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SpringAiRetryAutoConfiguration.class,
                        ToolCallingAutoConfiguration.class, GoogleGenAiChatAutoConfiguration.class))
                .withUserConfiguration(ClienteReal.class)
                .withPropertyValues("cameia.llm.api-key=" + llave, "cameia.llm.model=" + modelo,
                        "cameia.llm.timeout=30s", "cameia.llm.temperature=1.0", "cameia.llm.thinking-level=" + pensamiento)
                .run(ctx -> {
                    GeminiLiteUnido gemini = new GeminiLiteUnido(ctx.getBean(ChatModel.class), modelo, llave, 1.0,
                            pensamiento);
                    for (JsonNode caso : casos) {
                        String id = caso.get("id").asString();
                        boolean elegido = (grupos.contains("") || grupos.contains(caso.get("grupo").asString()))
                                && (solo.contains("") || solo.contains(id));
                        if (!elegido || Files.exists(SALIDAS.resolve(id + ".json"))) {
                            continue;
                        }
                        if (detener || llamadas >= presupuesto) {
                            break;
                        }
                        ObjectNode salida = correr(gemini, caso, llave);
                        Files.writeString(SALIDAS.resolve(id + ".json"), MAPPER.writeValueAsString(salida),
                                StandardCharsets.UTF_8);
                        System.out.println(id + " " + salida.path("estado").asString() + " servicio="
                                + salida.path("servicioAcepta").asBoolean() + " ms=" + salida.path("latenciaMs").asLong(-1)
                                + " llamadas=" + llamadas);
                        dormir(pausaMs + (caso.path("tamanoObjetivo").asInt(0) >= 8000 ? 8000 : 0));
                    }
                });
        System.out.println("Llamadas hechas: " + llamadas + (detener ? " (detenido por un 429 o un fallo)" : ""));
    }

    private ObjectNode correr(GeminiLiteUnido gemini, JsonNode caso, String llave) {
        ObjectNode salida = MAPPER.createObjectNode();
        String idioma = caso.get("idioma").asString();
        salida.put("id", caso.get("id").asString()).put("grupo", caso.get("grupo").asString())
                .put("descripcion", caso.get("descripcion").asString()).put("idioma", idioma)
                .put("tono", caso.get("tono").asString()).put("conOferta", !caso.path("oferta").isNull())
                .put("tieneExperiencia", caso.get("tieneExperiencia").asBoolean())
                .put("repeticion", caso.get("repeticion").asInt())
                .put("inicio", ZonedDateTime.now(BOGOTA).toString());
        if (!caso.path("ataque").isNull()) {
            salida.put("ataque", caso.get("ataque").asString());
        }
        PlanningPrompt prompt;
        try {
            prompt = armar(caso);
        } catch (ProfileSnapshotInvalidException rechazo) {
            salida.put("estado", "RECHAZADO_ANTES_DE_LLAMAR").put("servicioAcepta", false);
            return salida;
        }
        salida.put("versionDelPrompt", prompt.version()).put("caracteresDelPrompt", prompt.prompt().text().length());
        esperarCupo();
        llamadas++;
        long inicio = System.nanoTime();
        ModeloDePrueba.Respuesta respuesta = null;
        for (int intento = 1; respuesta == null; intento++) {
            try {
                inicio = System.nanoTime();
                respuesta = gemini.generar(prompt.prompt().text(), prompt.prompt().maxOutputTokens());
            } catch (ModeloDePrueba.FalloDelProveedor fallo) {
                long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
                salida.withArray("fallosDelProveedor").addObject().put("intento", intento).put("tipo", fallo.tipo().name())
                        .put("latenciaMs", ms).put("causa", Secretos.enmascarar(fallo.getMessage(), llave));
                // Un 429 detiene la corrida; una sobrecarga (503) espera y se repite hasta tres veces.
                if (fallo.tipo() != ModeloDePrueba.TipoDeFallo.SOBRECARGA || intento >= 3) {
                    salida.put("estado", "FALLO_DEL_PROVEEDOR").put("latenciaMs", ms).put("servicioAcepta", false);
                    detener = true;
                    return salida;
                }
                dormir(20_000);
                esperarCupo();
                llamadas++;
            }
        }
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
        String texto = Secretos.enmascarar(respuesta.texto(), llave);
        salida.put("estado", "EJECUTADA").put("latenciaMs", ms).put("superaLosNueveYMedio", ms > LIMITE_DE_TIEMPO_MS)
                .put("tokensEntrada", respuesta.tokensEntrada()).put("tokensSalida", respuesta.tokensSalida())
                .put("motivoDeCorte", respuesta.motivoDeCorte()).put("textoCrudo", texto);
        juzgarComoElServicio(salida, texto, idioma);
        juzgarAproximado(salida, texto, caso);
        return salida;
    }

    private PlanningPrompt armar(JsonNode caso) {
        UUID rolId = UUID.randomUUID();
        ObjectNode snapshot = MAPPER.createObjectNode();
        snapshot.set("contextoProfesional", caso.get("contexto"));
        snapshot.putArray("rolesAspirados").addObject().put("codigo", rolId.toString().toUpperCase())
                .put("nombre", caso.get("rol").asString());
        String oferta = caso.path("oferta").isNull() ? null : caso.get("oferta").asString();
        SessionConfiguration configuracion = new SessionConfiguration(UUID.randomUUID(), rolId, oferta,
                MAPPER.writeValueAsString(snapshot), Modality.TRAINING, Tone.fromCode(caso.get("tono").asString()),
                AnswerMode.TEXT, InterviewLanguage.fromCode(caso.get("idioma").asString()));
        PlanningPrompt vigente = new PlanningPromptBuilder(JsonMapper.builder().build()).build(configuracion);
        return VARIANTE.isBlank() ? vigente : aplicarVariante(vigente, configuracion.tone());
    }

    /**
     * Convierte el prompt vigente (v5) en la variante del banco: agrega las líneas que la variante tiene de más en
     * {@code prompts/planificacion.<variante>.txt} después de la última regla y cambia el bloque del tono cálido por
     * {@code tono.CALIDO.v3.txt}. No toca el servicio.
     */
    private static PlanningPrompt aplicarVariante(PlanningPrompt vigente, Tone tono) {
        String texto = vigente.prompt().text();
        List<String> base = leerLineas(BASE.resolve("prompts/planificacion.v5.txt"));
        String ultimaRegla = base.stream().filter(l -> l.startsWith("- Si la lista \"experiencias\"")).findFirst().orElseThrow();
        for (String linea : leerLineas(BASE.resolve("prompts/planificacion." + VARIANTE + ".txt"))) {
            if (!base.contains(linea)) {
                texto = texto.replace(ultimaRegla, ultimaRegla + "\n" + linea);
            }
        }
        if (tono == Tone.WARM) {
            String antiguo = leerTexto(BASE.resolve("prompts/tono.CALIDO.v2.txt")).strip();
            String nuevo = leerTexto(BASE.resolve("prompts/tono.CALIDO.v3.txt")).strip();
            if (!texto.contains(antiguo)) {
                throw new IllegalStateException("El bloque cálido v2 no está en el prompt vigente");
            }
            texto = texto.replace(antiguo, nuevo);
        }
        return new PlanningPrompt(new tech.cameia.entrevista.domain.model.LanguageModelPrompt(texto,
                vigente.prompt().maxOutputTokens(), tech.cameia.entrevista.domain.model.AiOperation.PLAN), vigente.version() + ">" + VARIANTE,
                vigente.fallbackSchema());
    }

    private static List<String> leerLineas(Path ruta) {
        return List.of(leerTexto(ruta).split("\\R"));
    }

    private static String leerTexto(Path ruta) {
        try {
            return Files.readString(ruta, StandardCharsets.UTF_8);
        } catch (java.io.IOException fallo) {
            throw new IllegalStateException("No se pudo leer " + ruta, fallo);
        }
    }

    /** Lo que decide el servicio: ¿acepta el plan? Se guarda la razón fija del rechazo, sin texto del modelo. */
    private static void juzgarComoElServicio(ObjectNode salida, String texto, String idioma) {
        try {
            PlanDraft borrador = new PlanOutputReader(MAPPER).read(texto);
            new PlanValidator().validate(InterviewLanguage.fromCode(idioma), borrador);
            salida.put("servicioAcepta", true);
        } catch (LlmInvalidOutputException invalida) {
            salida.put("servicioAcepta", false).put("motivoDeRechazo", invalida.getReason());
        }
    }

    /** Lecturas aproximadas: no cambian {@code servicioAcepta}; una persona las confirma. */
    private static void juzgarAproximado(ObjectNode salida, String texto, JsonNode caso) {
        List<String> textos = PlanChecker.textosDelPlan(texto);
        if (textos == null) {
            return;
        }
        List<Integer> largos = new ArrayList<>();
        textos.forEach(t -> largos.add(t.codePointCount(0, t.length())));
        salida.set("largos", MAPPER.valueToTree(largos));
        PlanChecker.Pedido pedido = new PlanChecker.Pedido(caso.get("idioma").asString(), !caso.path("oferta").isNull(),
                lista(caso.get("terminosTecnicos")), lista(caso.get("terminosOferta")));
        PlanChecker.Resultado resultado = PlanChecker.evaluar(texto, pedido);
        ObjectNode criterios = salida.putObject("aproximado");
        resultado.criterios().stream().filter(k -> Set.of("c", "d", "e", "f").contains(k.id()))
                .forEach(k -> criterios.putObject(k.id()).put("cumple", k.cumple()).put("detalle", k.detalle()));
        TonoChecker.Lectura lectura = TonoChecker.leer(textos);
        salida.putObject("lecturaDelTono").put("tonoDeducido", lectura.tonoDeducido())
                .put("preguntasConAcogida", lectura.preguntasConAcogida())
                .put("preguntasConExigencia", lectura.preguntasConExigencia())
                .put("acogidaEnPreguntas2a4", lectura.acogidaEnIntermedias());
        List<Integer> citan = new ArrayList<>();
        List<Integer> empleo = new ArrayList<>();
        List<Integer> fuga = new ArrayList<>();
        List<String> terminos = lista(caso.get("terminosDeLaFormacion"));
        for (int i = 0; i < textos.size(); i++) {
            String minusculas = textos.get(i).toLowerCase(java.util.Locale.ROOT);
            if (terminos.stream().anyMatch(t -> Pattern.compile("(?<!\\p{L})" + Pattern.quote(t)).matcher(minusculas)
                    .find())) {
                citan.add(i + 1);
            }
            if (EMPLEO_PASADO.matcher(textos.get(i)).find()) {
                empleo.add(i + 1);
            }
            if (FUGA.matcher(textos.get(i)).find()) {
                fuga.add(i + 1);
            }
        }
        salida.putObject("lecturaDeFormacion").put("preguntasQueCitanLaFormacion", citan.toString())
                .put("preguntasQuePareceSuponerEmpleoPasado", empleo.toString());
        salida.put("preguntasConPosibleFuga", fuga.toString());
    }

    private static List<String> lista(JsonNode nodo) {
        List<String> resultado = new ArrayList<>();
        nodo.forEach(n -> resultado.add(n.asString()));
        return resultado;
    }

    /** Espera lo necesario para no pasar de {@value #MAX_POR_MINUTO} llamadas en una ventana de 60 s. */
    private void esperarCupo() {
        long ahora = System.currentTimeMillis();
        while (!ultimas.isEmpty() && ahora - ultimas.peekFirst() > 60_000) {
            ultimas.pollFirst();
        }
        if (ultimas.size() >= MAX_POR_MINUTO) {
            dormir(60_000 - (ahora - ultimas.peekFirst()) + 500);
        }
        ultimas.addLast(System.currentTimeMillis());
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
        }
    }

    /** El adaptador y la configuración real del cliente, sin el resto de la aplicación. */
    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = LanguageModelProperties.class, basePackages = "tech.cameia.entrevista.infrastructure.ia")
    static class ClienteReal {
    }
}
