package tech.cameia.entrevista.spike.planificacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Pruebas sin conexión ni llaves de las piezas nuevas del banco de pruebas. */
class ModelosDePruebaTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final String LLAVE_FALSA = "gsk_FALSA1234567890FALSA1234567890";

    private static String cuerpoOk(String contenido) {
        return MAPPER.createObjectNode().<tools.jackson.databind.node.ObjectNode>set("choices",
                MAPPER.createArrayNode().add(MAPPER.createObjectNode().put("finish_reason", "stop")
                        .set("message", MAPPER.createObjectNode().put("content", contenido))))
                .set("usage", MAPPER.createObjectNode().put("prompt_tokens", 100).put("completion_tokens", 50))
                .toString();
    }

    @Test
    void respuestaSinRazonamientoSeEntregaTalCual() {
        ModeloDePrueba.Respuesta r = QwenGroq.interpretar(cuerpoOk("{\"idioma\":\"en\"}"), Map.of());
        assertThat(r.texto()).isEqualTo("{\"idioma\":\"en\"}");
        assertThat(r.tokensEntrada()).isEqualTo(100);
        assertThat(r.tokensSalida()).isEqualTo(50);
        assertThat(r.motivoDeCorte()).isEqualTo("stop");
        assertThat(QwenGroq.empiezaConRazonamiento(r.texto())).isFalse();
    }

    @Test
    void respuestaConRazonamientoDelanteSeGuardaSinLimpiarYSeMarca() {
        String texto = "<think>Voy a pensar</think>\n{\"idioma\":\"en\"}";
        ModeloDePrueba.Respuesta r = QwenGroq.interpretar(cuerpoOk(texto), Map.of());
        assertThat(r.texto()).isEqualTo(texto);
        assertThat(QwenGroq.empiezaConRazonamiento(r.texto())).isTrue();
    }

    @Test
    void elRazonamientoDelanteNoEsJsonPuroYFallaLaForma() {
        PlanChecker.Pedido pedido = new PlanChecker.Pedido("en", false, java.util.List.of(), java.util.List.of());
        PlanChecker.Resultado r = PlanChecker.evaluar("<think>x</think>{\"idioma\":\"en\",\"preguntas\":[]}", pedido);
        assertThat(r.valido()).isFalse();
        assertThat(r.criterios().get(0).id()).isEqualTo("a");
        assertThat(r.criterios().get(0).cumple()).isFalse();
    }

    @Test
    void respuestaSinContenidoEsFalloConMotivoDeCorte() {
        String cuerpo = "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"\"}}]}";
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> QwenGroq.interpretar(cuerpo, Map.of()))
                .isInstanceOf(ModeloDePrueba.FalloDelProveedor.class).hasMessageContaining("finish_reason=length");
    }

    @Test
    void laPeticionLlevaElModeloUnSoloMensajeYLaLlaveSoloEnElEncabezado() throws Exception {
        QwenGroq qwen = new QwenGroq(LLAVE_FALSA, 1.0, null, Duration.ofSeconds(5));
        JsonNode cuerpo = MAPPER.readTree(qwen.cuerpo("hola", 4096));
        assertThat(cuerpo.get("model").asString()).isEqualTo("qwen/qwen3.8-27b");
        assertThat(cuerpo.get("messages")).hasSize(1);
        assertThat(cuerpo.get("messages").get(0).get("role").asString()).isEqualTo("user");
        assertThat(cuerpo.get("temperature").asDouble()).isEqualTo(1.0);
        assertThat(cuerpo.get("max_completion_tokens").asInt()).isEqualTo(4096);
        assertThat(cuerpo.has("reasoning_effort")).isFalse();
        assertThat(qwen.cuerpo("hola", 4096)).doesNotContain(LLAVE_FALSA);
        assertThat(qwen.peticion("hola", 4096).uri().toString()).isEqualTo("https://api.groq.com/openai/v1/chat/completions");
        assertThat(qwen.peticion("hola", 4096).headers().firstValue("Authorization")).contains("Bearer " + LLAVE_FALSA);
    }

    @Test
    void elAjusteSinRazonamientoAgregaElParametro() throws Exception {
        QwenGroq qwen = new QwenGroq(LLAVE_FALSA, 1.0, "none", Duration.ofSeconds(5));
        assertThat(MAPPER.readTree(qwen.cuerpo("hola", 10)).get("reasoning_effort").asString()).isEqualTo("none");
    }

    @Test
    void clasificaLosFallosDelProveedor() {
        QwenGroq qwen = new QwenGroq(LLAVE_FALSA, 1.0, null, Duration.ofSeconds(5));
        String porMinuto = "{\"error\":{\"message\":\"Rate limit reached for model on tokens per minute (TPM). Please try again in 7.5s.\"}}";
        String porDia = "{\"error\":{\"message\":\"Rate limit reached on requests per day (RPD): Limit 1000. Please try again in 5m10s.\"}}";
        ModeloDePrueba.FalloDelProveedor minuto = qwen.clasificar(429, porMinuto, Map.of());
        assertThat(minuto.tipo()).isEqualTo(ModeloDePrueba.TipoDeFallo.LIMITE_POR_MINUTO);
        assertThat(minuto.esperaSugeridaS()).isEqualTo(8);
        ModeloDePrueba.FalloDelProveedor dia = qwen.clasificar(429, porDia, Map.of());
        assertThat(dia.tipo()).isEqualTo(ModeloDePrueba.TipoDeFallo.LIMITE_POR_DIA);
        assertThat(dia.esperaSugeridaS()).isEqualTo(310);
        assertThat(qwen.clasificar(429, porMinuto, Map.of("retry-after", "12")).esperaSugeridaS()).isEqualTo(12);
        assertThat(qwen.clasificar(503, "{\"error\":{\"message\":\"over capacity\"}}", Map.of()).tipo())
                .isEqualTo(ModeloDePrueba.TipoDeFallo.SOBRECARGA);
        assertThat(qwen.clasificar(400, "{\"error\":{\"message\":\"bad\"}}", Map.of()).tipo())
                .isEqualTo(ModeloDePrueba.TipoDeFallo.OTRO);
    }

    @Test
    void laCausaDeUnFalloNuncaLlevaLaLlave() {
        QwenGroq qwen = new QwenGroq(LLAVE_FALSA, 1.0, null, Duration.ofSeconds(5));
        String cuerpo = "{\"error\":{\"message\":\"Invalid API Key " + LLAVE_FALSA + "\"}}";
        assertThat(qwen.clasificar(401, cuerpo, Map.of()).getMessage()).doesNotContain(LLAVE_FALSA).contains("****");
    }

    @Test
    void enmascaraLlavesExactasYConFormaDeLlave() {
        String texto = "a " + LLAVE_FALSA + " b AIzaSyA1234567890abcdefghijklmnop c Bearer abcdefghijklmnopqrstuvwxyz0123";
        String limpio = Secretos.enmascarar(texto, "otra-llave-exacta-1234");
        assertThat(limpio).doesNotContain(LLAVE_FALSA).doesNotContain("AIzaSy").doesNotContain("abcdefghijklmnopqrstuvwxyz0123");
        assertThat(Secretos.enmascarar("x otra-llave-exacta-1234 y", "otra-llave-exacta-1234")).isEqualTo("x **** y");
        assertThat(Secretos.enmascarar(null)).isNull();
    }
}
