package tech.cameia.entrevista.spike.planificacion;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Cliente mínimo de Groq (formato compatible con OpenAI) para el modelo {@code qwen/qwen3.8-27b}.
 *
 * <p>Un solo mensaje de usuario con el prompt, la misma temperatura que Gemini y ningún reintento.
 * Por defecto no envía parámetros de razonamiento (el proveedor devuelve el razonamiento dentro de
 * {@code <think>} en el contenido); con {@code reasoningEffort = "none"} se pide el modo sin
 * razonamiento. Es código del spike, no de producción.</p>
 */
final class QwenGroq implements ModeloDePrueba {

    static final String URL = "https://api.groq.com/openai/v1/chat/completions";

    static final String MODELO = "qwen/qwen3.8-27b";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final Pattern ESPERA_EN_MENSAJE = Pattern.compile("try again in\\s+(?:(\\d+)m)?\\s*([0-9.]+)s",
            Pattern.CASE_INSENSITIVE);

    private final String llave;

    private final double temperatura;

    private final String reasoningEffort;

    private final HttpClient cliente;

    private final Duration timeout;

    /**
     * @param llave llave de Groq; nunca se imprime
     * @param temperatura temperatura de la llamada
     * @param reasoningEffort {@code null} para no enviar el parámetro, o p. ej. {@code "none"}
     * @param timeout espera máxima por llamada
     */
    QwenGroq(String llave, double temperatura, String reasoningEffort, Duration timeout) {
        this.llave = llave;
        this.temperatura = temperatura;
        this.reasoningEffort = reasoningEffort;
        this.timeout = timeout;
        this.cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    @Override
    public String alias() {
        return "QWEN_GROQ";
    }

    @Override
    public String nombreDelModelo() {
        return MODELO;
    }

    /** Arma el cuerpo JSON de la petición; no incluye la llave (va en un encabezado). */
    String cuerpo(String prompt, int maxTokensSalida) {
        ObjectNode raiz = MAPPER.createObjectNode();
        raiz.put("model", MODELO);
        raiz.putArray("messages").addObject().put("role", "user").put("content", prompt);
        raiz.put("temperature", temperatura);
        raiz.put("max_completion_tokens", maxTokensSalida);
        if (reasoningEffort != null) {
            raiz.put("reasoning_effort", reasoningEffort);
        }
        return MAPPER.writeValueAsString(raiz);
    }

    /** Arma la petición HTTP completa. */
    HttpRequest peticion(String prompt, int maxTokensSalida) {
        return HttpRequest.newBuilder(URI.create(URL)).timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + llave)
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo(prompt, maxTokensSalida))).build();
    }

    @Override
    public Respuesta generar(String prompt, int maxTokensSalida) {
        HttpResponse<String> http;
        try {
            http = cliente.send(peticion(prompt, maxTokensSalida), HttpResponse.BodyHandlers.ofString());
        } catch (IOException fallo) {
            throw new FalloDelProveedor(TipoDeFallo.SOBRECARGA,
                    "Conexión: " + fallo.getClass().getSimpleName() + ": " + Secretos.enmascarar(fallo.getMessage(), llave),
                    -1, Map.of());
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
            throw new FalloDelProveedor(TipoDeFallo.OTRO, "Interrumpida", -1, Map.of());
        }
        Map<String, String> limites = encabezadosDeLimites(http);
        if (http.statusCode() != 200) {
            throw clasificar(http.statusCode(), http.body(), limites);
        }
        return interpretar(http.body(), limites);
    }

    /** Guarda solo los encabezados {@code x-ratelimit-*} y {@code retry-after}. */
    private static Map<String, String> encabezadosDeLimites(HttpResponse<String> http) {
        Map<String, String> limites = new LinkedHashMap<>();
        http.headers().map().forEach((nombre, valores) -> {
            String n = nombre.toLowerCase();
            if (n.startsWith("x-ratelimit-") || n.equals("retry-after")) {
                limites.put(n, String.join(",", valores));
            }
        });
        return limites;
    }

    /** Traduce un estado HTTP distinto de 200 al tipo de fallo que corresponde, con la causa enmascarada. */
    FalloDelProveedor clasificar(int estado, String cuerpo, Map<String, String> limites) {
        String mensaje = mensajeDeError(cuerpo);
        String causa = Secretos.enmascarar("HTTP " + estado + ": " + mensaje, llave);
        if (causa.length() > 1500) {
            causa = causa.substring(0, 1500);
        }
        long espera = -1;
        String retry = limites.get("retry-after");
        if (retry != null) {
            try {
                espera = (long) Math.ceil(Double.parseDouble(retry.trim()));
            } catch (NumberFormatException ignorado) {
                espera = -1;
            }
        }
        if (espera < 0) {
            Matcher m = ESPERA_EN_MENSAJE.matcher(mensaje);
            if (m.find()) {
                long minutos = m.group(1) == null ? 0 : Long.parseLong(m.group(1));
                espera = (long) Math.ceil(minutos * 60 + Double.parseDouble(m.group(2)));
            }
        }
        if (estado == 429) {
            String minuscula = mensaje.toLowerCase();
            boolean porDia = minuscula.contains("per day") || minuscula.contains("(rpd)") || minuscula.contains("(tpd)");
            return new FalloDelProveedor(porDia ? TipoDeFallo.LIMITE_POR_DIA : TipoDeFallo.LIMITE_POR_MINUTO, causa, espera,
                    limites);
        }
        boolean transitorio = estado == 500 || estado == 502 || estado == 503 || estado == 504;
        return new FalloDelProveedor(transitorio ? TipoDeFallo.SOBRECARGA : TipoDeFallo.OTRO, causa, espera, limites);
    }

    private static String mensajeDeError(String cuerpo) {
        try {
            JsonNode raiz = MAPPER.readTree(cuerpo);
            JsonNode mensaje = raiz.path("error").path("message");
            return mensaje.isString() ? mensaje.asString() : cuerpo;
        } catch (RuntimeException ilegible) {
            return cuerpo == null ? "" : cuerpo;
        }
    }

    /**
     * Interpreta una respuesta 200: el texto es el contenido tal cual, incluido un {@code <think>}
     * delante si el modelo lo devolvió.
     */
    static Respuesta interpretar(String cuerpo, Map<String, String> limites) {
        JsonNode raiz;
        try {
            raiz = MAPPER.readTree(cuerpo);
        } catch (RuntimeException ilegible) {
            throw new FalloDelProveedor(TipoDeFallo.OTRO, "Cuerpo de respuesta ilegible", -1, limites);
        }
        JsonNode eleccion = raiz.path("choices").path(0);
        JsonNode contenido = eleccion.path("message").path("content");
        if (!contenido.isString() || contenido.asString().isBlank()) {
            throw new FalloDelProveedor(TipoDeFallo.OTRO,
                    "Respuesta sin contenido [finish_reason=" + eleccion.path("finish_reason").asString("?") + "]", -1,
                    limites);
        }
        JsonNode uso = raiz.path("usage");
        return new Respuesta(contenido.asString(), uso.path("prompt_tokens").asInt(-1),
                uso.path("completion_tokens").asInt(-1), raiz.path("model").asString(MODELO), limites,
                eleccion.path("finish_reason").asString(null));
    }

    /** {@code true} si el texto empieza con un bloque {@code <think>} (razonamiento delante del JSON). */
    static boolean empiezaConRazonamiento(String texto) {
        return texto != null && texto.stripLeading().startsWith("<think>");
    }
}
