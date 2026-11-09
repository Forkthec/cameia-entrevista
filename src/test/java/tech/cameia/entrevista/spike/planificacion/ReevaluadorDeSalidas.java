package tech.cameia.entrevista.spike.planificacion;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Vuelve a evaluar, sin llamar a ningún modelo, las salidas crudas ya guardadas con las reglas
 * vigentes de {@link PlanChecker} y {@link TonoChecker}.
 *
 * <p>Manual: solo corre con {@code SPIKE_REEVALUAR=true}. Nunca modifica una salida original: escribe
 * {@code fixtures/reevaluaciones/<carpeta>.json} con el resultado de la regla anterior (el guardado en la
 * salida) y el de la regla vigente, para poder ver qué cambió.</p>
 */
@EnabledIfEnvironmentVariable(named = "SPIKE_REEVALUAR", matches = "true")
class ReevaluadorDeSalidas {

    private static final Path BASE = Path.of("docs", "spikes", "CM-347-planificacion-y-tonos", "fixtures");

    private static final JsonMapper MAPPER = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    @Test
    void reevaluar() throws IOException {
        JsonNode entradas = MAPPER.readTree(Files.readString(BASE.resolve("entradas/entradas-ficticias.json")));
        Files.createDirectories(BASE.resolve("reevaluaciones"));
        try (Stream<Path> carpetas = Files.list(BASE.resolve("salidas"))) {
            for (Path carpeta : carpetas.filter(Files::isDirectory).toList()) {
                reevaluarCarpeta(carpeta, entradas);
            }
        }
    }

    private void reevaluarCarpeta(Path carpeta, JsonNode entradas) throws IOException {
        ArrayNode filas = MAPPER.createArrayNode();
        try (Stream<Path> archivos = Files.list(carpeta)) {
            for (Path archivo : archivos.filter(p -> p.getFileName().toString().matches("[AB]\\d+\\.json")).sorted().toList()) {
                JsonNode salida = MAPPER.readTree(Files.readString(archivo, StandardCharsets.UTF_8));
                if (!"EJECUTADA".equals(salida.path("estado").asString())) {
                    continue;
                }
                filas.add(fila(salida, entradas));
            }
        }
        if (!filas.isEmpty()) {
            Files.writeString(BASE.resolve("reevaluaciones/" + carpeta.getFileName() + ".json"),
                    MAPPER.writeValueAsString(filas), StandardCharsets.UTF_8);
        }
    }

    private ObjectNode fila(JsonNode salida, JsonNode entradas) {
        String idioma = salida.get("idioma").asString();
        JsonNode rol = rolPorNombre(entradas, salida.get("rol").asString(), idioma);
        PlanChecker.Pedido pedido = new PlanChecker.Pedido(idioma, salida.get("conOferta").asBoolean(),
                lista(rol.get("terminosTecnicosDelRol")), lista(rol.get("terminosDeLaOferta")));
        PlanChecker.Resultado nuevo = PlanChecker.evaluar(salida.get("textoCrudo").asString(), pedido);
        ObjectNode fila = MAPPER.createObjectNode();
        fila.put("id", salida.get("id").asString()).put("tono", salida.get("tono").asString())
                .put("validoReglaAnterior", salida.path("planValido").asBoolean())
                .put("validoReglaVigente", nuevo.valido());
        ArrayNode fallos = fila.putArray("letrasQueFallan");
        nuevo.criterios().stream().filter(k -> !k.cumple()).forEach(k -> fallos.add(k.id()));
        List<String> textos = PlanChecker.textosDelPlan(salida.get("textoCrudo").asString());
        if (nuevo.valido() && textos != null) {
            TonoChecker.Lectura lectura = TonoChecker.leer(textos);
            fila.put("tonoDeducido", lectura.tonoDeducido()).put("preguntasConAcogida", lectura.preguntasConAcogida())
                    .put("preguntasConExigencia", lectura.preguntasConExigencia())
                    .put("acogidaEnPreguntas2a4", lectura.acogidaEnIntermedias())
                    .put("preguntasQueSuponenRespuestaPrevia", lectura.suponenRespuestaPrevia())
                    .put("calidezNatural", lectura.calidezNatural());
        }
        if (textos != null && rol.has("temasDelRol")) {
            PlanChecker.Criterio h = PlanChecker.temasDistintos(textos, PlanificacionSpikeRunner.temasDelRol(rol));
            fila.put("temasDistintos", h.cumple()).put("detalleDeTemas", h.detalle());
        }
        return fila;
    }

    private static JsonNode rolPorNombre(JsonNode entradas, String nombre, String idioma) {
        for (JsonNode rol : entradas.get("roles").values()) {
            if (nombre.equals(rol.get("nombre").get(idioma).asString())) {
                return rol;
            }
        }
        throw new IllegalStateException("Rol desconocido: " + nombre);
    }

    private static List<String> lista(JsonNode nodo) {
        List<String> resultado = new ArrayList<>();
        nodo.forEach(n -> resultado.add(n.asString()));
        return resultado;
    }
}
