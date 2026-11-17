package tech.cameia.entrevista.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import tech.cameia.entrevista.domain.exception.TranscriptionUnavailableException;
import tech.cameia.entrevista.domain.model.AudioAnswer;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.TimedWord;
import tech.cameia.entrevista.domain.model.Transcription;
import tech.cameia.entrevista.domain.port.SpeechTranscriptionPort;

/**
 * Base de las pruebas del Reporte de Evaluación: termina una entrevista de cinco preguntas (la 3 es conductual) respondiéndolas por texto o, algunas, por voz con palabras y tiempos programados, y consulta el reporte.
 */
abstract class ReportFlowSupport extends AnswerFlowSupport {

    /** Salida válida del modelo: contenido de 1, 2, 4 y 5 y STAR de la 3 (nivel 4). */
    static final String REPORT_OK = """
            {"contenido":[
              {"numero":1,"precision_claridad":4,"carisma_verbal":4,"lenguaje_adecuado":4},
              {"numero":2,"precision_claridad":2,"carisma_verbal":3,"lenguaje_adecuado":5},
              {"numero":4,"precision_claridad":3,"carisma_verbal":3,"lenguaje_adecuado":3},
              {"numero":5,"precision_claridad":3,"carisma_verbal":3,"lenguaje_adecuado":4}],
             "star":[
              {"numero":3,"situacion":true,"tarea":true,"accion":true,"resultado":true}]}""";

    /** Texto que dice la persona en cada respuesta por voz, con su muletilla. */
    static final String SPOKEN = "Eh, respuesta hablada";

    @Autowired
    ScriptedTranscriber transcriber;

    @BeforeEach
    void resetTranscriber() {
        transcriber.reset();
    }

    // ---- sesión terminada ----

    /** Termina una entrevista respondiendo las cinco preguntas por texto. */
    String finishedTextSession(String uid, UUID profile) {
        String id = startedReportSession(uid, profile, "TEXTO");
        for (int number = 1; number <= 5; number++) {
            answerByText(uid, id, number);
        }
        return finished(id);
    }

    /** Termina una entrevista por voz: las preguntas 2 y 4 se responden hablando y las otras por texto. */
    String finishedMixedSession(String uid, UUID profile) {
        String id = startedReportSession(uid, profile, "AUDIO");
        for (int number = 1; number <= 5; number++) {
            if (number == 2 || number == 4) {
                answerByVoice(uid, id, number);
            } else {
                answerByText(uid, id, number);
            }
        }
        return finished(id);
    }

    private String finished(String id) {
        assertThat(sessionStatus(id)).isEqualTo("FINALIZADA");
        model.reset();
        waiter.reset();
        return id;
    }

    String startedReportSession(String uid, UUID profile, String answerFormat) {
        String json = """
                {"profileId":"%s","targetRoleId":"%s","jobOffer":"Buscamos analista","mode":"SIMULACION",
                 "tone":"CALIDO","answerFormat":"%s","sessionLanguage":"es-CO"}""".formatted(profile, ROLE,
                answerFormat);
        ResponseEntity<String> created = call(HttpMethod.POST, BASE, uid, null, json);
        assertThat(created.getStatusCode().value()).isIn(200, 201);
        String id = JsonPath.read(created.getBody(), "$.sessionId");
        model.enqueue(() -> response(VALID_PLAN));
        assertThat(call(HttpMethod.POST, BASE + "/" + id + "/start", uid, null, null).getStatusCode().value())
                .isEqualTo(200);
        model.reset();
        return id;
    }

    void answerByText(String uid, String sessionId, int number) {
        model.enqueue(AnswerFlowSupport::evaluation);
        assertThat(status(answer(uid, sessionId, turnId(sessionId, number), ANSWER, "clave-" + number))).isEqualTo(200);
    }

    void answerByVoice(String uid, String sessionId, int number) {
        transcriber.enqueue(() -> new Transcription(SPOKEN, "es", 0.97, null,
                List.of(new TimedWord("Eh,", 0, 200), new TimedWord("respuesta", 800, 1100),
                        new TimedWord("hablada", 1150, 1500))));
        model.enqueue(AnswerFlowSupport::evaluation);
        assertThat(status(audio(uid, sessionId, turnId(sessionId, number)))).isEqualTo(200);
    }

    private ResponseEntity<String> audio(String uid, String sessionId, String turnId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-User-Id", uid);
        HttpHeaders audioHeaders = new HttpHeaders();
        audioHeaders.setContentType(MediaType.parseMediaType("audio/webm;codecs=opus"));
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("turnId", turnId);
        parts.add("audio", new HttpEntity<>(new ByteArrayResource(voiceFile()) {
            @Override
            public String getFilename() {
                return "respuesta.webm";
            }
        }, audioHeaders));
        return client.exchange(BASE + "/" + sessionId + "/answers/audio", HttpMethod.POST,
                new HttpEntity<>(parts, headers), String.class);
    }

    private static byte[] voiceFile() {
        try (var in = ReportFlowSupport.class.getResourceAsStream("/audio/voz.webm")) {
            return in.readAllBytes();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    // ---- consulta ----

    ResponseEntity<String> report(String uid, String sessionId) {
        return call(HttpMethod.GET, BASE + "/" + sessionId + "/evaluation-report", uid, null, null);
    }

    int reportExecutions(String sessionId, String status) {
        return count("SELECT count(*) FROM microentrevista.ejecucion_ia WHERE sesion_id = ?::uuid"
                + " AND tipo_operacion = 'GENERAR_REPORTE' AND estado = ?", sessionId, status);
    }

    // ---- dobles ----

    /** Transcriptor que responde lo que se le encola y cuenta las llamadas. */
    static class ScriptedTranscriber implements SpeechTranscriptionPort {

        private final Deque<Supplier<Transcription>> script = new ArrayDeque<>();

        private final AtomicInteger calls = new AtomicInteger();

        synchronized void enqueue(Supplier<Transcription> next) {
            script.add(next);
        }

        synchronized void reset() {
            script.clear();
            calls.set(0);
        }

        @Override
        public synchronized Transcription transcribe(AudioAnswer audio, InterviewLanguage expected) {
            calls.incrementAndGet();
            Supplier<Transcription> next = script.poll();
            if (next == null) {
                throw new TranscriptionUnavailableException("sin guion", null);
            }
            return next.get();
        }

        @Override
        public String provider() {
            return "simulado";
        }

        @Override
        public String model() {
            return "modelo-simulado";
        }
    }

    @TestConfiguration
    static class Transcribers {

        @Bean
        @Primary
        ScriptedTranscriber scriptedTranscriber() {
            return new ScriptedTranscriber();
        }
    }
}
