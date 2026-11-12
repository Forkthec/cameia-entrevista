package tech.cameia.entrevista.spike.planificacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tech.cameia.entrevista.spike.planificacion.InformeEvaluacion.Latencia;
import tech.cameia.entrevista.spike.planificacion.InformeEvaluacion.Resultado;

/** Cálculo de percentiles, reglas de destino y escritura del informe, sin red ni llaves. */
class InformeEvaluacionTest {

    private static Resultado conTexto(long ms) {
        return new Resultado("QA", "GEMINI_LITE", "QA-P1-A", 1, ms, 700, 150,
                "{\"accion\":\"AVANZAR\",\"clasificacion\":\"COMPLETA\",\"motivo\":\"m\",\"follow_up_texto\":\"\","
                        + "\"feedback\":\"f\"}", "", List.of(), "APERTURA", "AVANZAR", "COMPLETA", "COMPLETA", true,
                "AVANZAR", "COMPLETA");
    }

    private static Resultado cortada() {
        return new Resultado("QA", "GEMINI_LITE", "QA-P1-A", 1, 30_000, -1, -1, "", "SOBRECARGA",
                List.of("SOBRECARGA"), "APERTURA", "AVANZAR", "COMPLETA", "COMPLETA", false, "", "");
    }

    private static List<Resultado> tiempos(long... ms) {
        List<Resultado> lista = new ArrayList<>();
        for (long t : ms) {
            lista.add(conTexto(t));
        }
        return lista;
    }

    private static long[] de100a2000() {
        long[] valores = new long[20];
        for (int i = 0; i < 20; i++) {
            valores[i] = (i + 1) * 100L;
        }
        return valores;
    }

    @Test
    @DisplayName("Los percentiles por rango más cercano coinciden con valores conocidos")
    void percentiles_shouldMatchKnownValues() {
        Latencia latencia = InformeEvaluacion.latencia(tiempos(de100a2000()));

        assertThat(latencia.p50()).isEqualTo(1000);
        assertThat(latencia.p95()).isEqualTo(1900);
        assertThat(latencia.maximo()).isEqualTo(2000);
    }

    @Test
    @DisplayName("El p99 solo existe con 100 llamadas o más")
    void percentiles_shouldOmitP99_whenFewerThan100() {
        assertThat(InformeEvaluacion.latencia(tiempos(de100a2000())).p99()).isNull();

        long[] cien = new long[100];
        for (int i = 0; i < 100; i++) {
            cien[i] = i + 1;
        }
        assertThat(InformeEvaluacion.latencia(tiempos(cien)).p99()).isEqualTo(99);
    }

    @Test
    @DisplayName("Las llamadas cortadas por tiempo no entran en los percentiles y se cuentan aparte")
    void percentiles_shouldExcludeTimeouts() {
        List<Resultado> llamadas = new ArrayList<>(tiempos(new long[19]).stream().map(r -> conTexto(1000)).toList());
        llamadas.add(cortada());

        Latencia latencia = InformeEvaluacion.latencia(llamadas);

        assertThat(latencia.conTexto()).isEqualTo(19);
        assertThat(latencia.p95()).isEqualTo(1000);
        assertThat(latencia.cortadas()).isEqualTo(1);
    }

    @Test
    @DisplayName("Solo cuenta como sobre 3 s lo que pasa de 3000 ms")
    void report_shouldCountOverThreeSeconds() {
        assertThat(InformeEvaluacion.latencia(tiempos(2999, 3000, 3001)).sobreTresSegundos()).isEqualTo(1);
    }

    @Test
    @DisplayName("Máximo × 1,5 sobre 3 s marca la regla de latencia aunque el p95 esté bajo")
    void rules_shouldFlagMaxTimesOneAndHalf() {
        List<Resultado> llamadas = new ArrayList<>(tiempos(new long[19]).stream().map(r -> conTexto(1500)).toList());
        llamadas.add(conTexto(2100));

        Latencia latencia = InformeEvaluacion.latencia(llamadas);

        assertThat(latencia.p95()).isEqualTo(1500);
        assertThat(latencia.maximoPorUnoYMedio()).isEqualTo(3150);
        assertThat(InformeEvaluacion.reglaLatencia(latencia)).isTrue();
    }

    @Test
    @DisplayName("Con el máximo en 1900 (× 1,5 = 2850) la regla de latencia no se marca")
    void rules_shouldNotFlag_whenWithinLimits() {
        List<Resultado> llamadas = new ArrayList<>(tiempos(new long[19]).stream().map(r -> conTexto(1500)).toList());
        llamadas.add(conTexto(1900));

        Latencia latencia = InformeEvaluacion.latencia(llamadas);

        assertThat(latencia.maximoPorUnoYMedio()).isEqualTo(2850);
        assertThat(InformeEvaluacion.reglaLatencia(latencia)).isFalse();
    }

    @Test
    @DisplayName("El CSV no lleva texto de la salida del modelo y el informe lista lo no corrido como NO VERIFICADO")
    void report_shouldNotWriteAnswerText(@TempDir Path carpeta) throws Exception {
        Resultado r = new Resultado("QA", "GEMINI_LITE", "QA-P1-A", 1, 900, 700, 150,
                "{\"accion\":\"AVANZAR\",\"clasificacion\":\"COMPLETA\",\"motivo\":\"m\",\"follow_up_texto\":\"\","
                        + "\"feedback\":\"TEXTO-DE-RETROALIMENTACION-UNICO\"}", "", List.of(), "APERTURA", "AVANZAR",
                "COMPLETA", "COMPLETA", true, "AVANZAR", "COMPLETA");

        InformeEvaluacion.escribir(List.of(r), carpeta, "12-nov-2026");

        String csv = Files.readString(carpeta.resolve("resultados.csv"));
        assertThat(csv).contains("grupo,modelo,caso,corrida,ms,tokensEntrada,tokensSalida,jsonValido,accion,"
                + "clasificacion,decisionEsperada,coincide,fallo").contains("QA,GEMINI_LITE,QA-P1-A,1,900,700,150,true,"
                        + "AVANZAR,COMPLETA,AVANZAR,true,").doesNotContain("TEXTO-DE-RETROALIMENTACION-UNICO");
        String informe = Files.readString(carpeta.resolve("informe.md"));
        assertThat(informe).contains("NO VERIFICADO").contains("## 7. Hallazgos con destino");
    }

    @Test
    @DisplayName("El idioma del feedback se reconoce por palabras vacías")
    void idioma_shouldDetectEnglishSpanishAndMixed() {
        assertThat(InformeEvaluacion.idiomaDelTexto("The answer is complete and your example is good")).isEqualTo("EN");
        assertThat(InformeEvaluacion.idiomaDelTexto("La respuesta es completa y el ejemplo es bueno")).isEqualTo("ES");
        assertThat(InformeEvaluacion.idiomaDelTexto("La respuesta es buena, but the answer is too short and your example is weak")).isEqualTo("MEZCLADO");
    }

    @Test
    @DisplayName("La respuesta generada tiene exactamente los puntos de código pedidos")
    void generatedAnswer_shouldHaveExactCodePoints() {
        InformeEvaluacion.Caso caso = new InformeEvaluacion.Caso("X", "BORDES", "TECNICA", "p", "media", null,
                "AVANZAR", "COMPLETA", "COMPLETA", 4000, "Párrafo técnico con tildes y emoji 😀. ");

        String respuesta = caso.respuestaDelCandidato();

        assertThat(respuesta.codePointCount(0, respuesta.length())).isEqualTo(4000);
    }
}
