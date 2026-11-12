package tech.cameia.entrevista.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tech.cameia.entrevista.application.command.AudioAnswerCommand;
import tech.cameia.entrevista.application.service.AnswerResult;
import tech.cameia.entrevista.application.service.SubmitAudioAnswerService;
import tech.cameia.entrevista.domain.exception.ErrorCode;
import tech.cameia.entrevista.domain.exception.InvalidRequestException;
import tech.cameia.entrevista.domain.exception.TranscriptionEmptyException;
import tech.cameia.entrevista.domain.exception.TranscriptionLanguageMismatchException;
import tech.cameia.entrevista.domain.exception.TranscriptionTooLongException;
import tech.cameia.entrevista.domain.exception.TranscriptionUnavailableException;
import tech.cameia.entrevista.domain.model.FieldRejection;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.TurnStatus;
import tech.cameia.entrevista.presentation.advice.ProblemDetailTestSupport;

/**
 * Contrato HTTP de {@code POST …/answers/audio}: la respuesta 200 común que consume el Cliente Web y los códigos de
 * los rechazos de la transcripción (CA-5.12.7 a CA-5.12.10).
 */
class AudioAnswerControllerTest {

    private static final UUID SESSION = UUID.fromString("3f1c9a52-7d0e-4c41-9a56-0b7e2d8c1a10");
    private static final UUID TURN = UUID.fromString("9b2e6d44-5a3c-4f0b-8e71-2c4d6f8a0b12");
    private static final UUID NEXT = UUID.fromString("0c1d2e3f-4a5b-4c6d-8e7f-9a0b1c2d3e4f");
    private static final String PATH = "/api/v1/interviews/sessions/" + SESSION + "/answers/audio";
    private static final MockMultipartFile AUDIO = new MockMultipartFile("audio", "respuesta.webm", "audio/webm",
            new byte[] {1, 2, 3});

    private final SubmitAudioAnswerService service = mock(SubmitAudioAnswerService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AudioAnswerController(service))
            .setControllerAdvice(ProblemDetailTestSupport.errorHandler())
            .build();

    @Test
    void answerByVoice_shouldReturnCommonAnswerShape_whenRegistered() throws Exception {
        when(service.submit(any())).thenReturn(new AnswerResult(TURN, TurnStatus.COMPLETED, 2, 5,
                new AnswerResult.NextQuestion(NEXT, 2, "Pregunta 2"), false));

        mockMvc.perform(multipart(PATH).file(AUDIO).param("turnId", TURN.toString())
                        .header("X-User-Id", "uid-a").header("Idempotency-Key", "clave-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turn.turnId").value(TURN.toString()))
                .andExpect(jsonPath("$.turn.status").value("COMPLETED"))
                .andExpect(jsonPath("$.progress.currentQuestion").value(2))
                .andExpect(jsonPath("$.progress.totalQuestions").value(5))
                .andExpect(jsonPath("$.nextQuestion.turnId").value(NEXT.toString()))
                .andExpect(jsonPath("$.nextQuestion.number").value(2))
                .andExpect(jsonPath("$.nextQuestion.text").value("Pregunta 2"))
                .andExpect(jsonPath("$.interviewFinished").value(false));

        ArgumentCaptor<AudioAnswerCommand> command = ArgumentCaptor.forClass(AudioAnswerCommand.class);
        verify(service).submit(command.capture());
        assertThat(command.getValue().session().owner().value()).isEqualTo("uid-a");
        assertThat(command.getValue().session().sessionId()).isEqualTo(SESSION);
        assertThat(command.getValue().rawTurnId()).isEqualTo(TURN.toString());
        assertThat(command.getValue().idempotencyKey()).isEqualTo("clave-1");
    }

    @Test
    void answerByVoice_shouldReturnNullNextQuestion_whenLastQuestionIsAnswered() throws Exception {
        when(service.submit(any())).thenReturn(new AnswerResult(TURN, TurnStatus.COMPLETED, 5, 5, null, true));

        mockMvc.perform(multipart(PATH).file(AUDIO).param("turnId", TURN.toString()).header("X-User-Id", "uid-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextQuestion").doesNotExist())
                .andExpect(jsonPath("$.interviewFinished").value(true))
                .andExpect(jsonPath("$.progress.currentQuestion").value(5));
    }

    @Test
    void answerByVoice_shouldReturn401_whenIdentityIsMissing() throws Exception {
        mockMvc.perform(multipart(PATH).file(AUDIO).param("turnId", TURN.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));
    }

    @Test
    void answerByVoice_shouldReturn422TranscriptionEmpty() throws Exception {
        when(service.submit(any())).thenThrow(new TranscriptionEmptyException());

        mockMvc.perform(multipart(PATH).file(AUDIO).param("turnId", TURN.toString()).header("X-User-Id", "uid-a"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TRANSCRIPTION_EMPTY"))
                .andExpect(jsonPath("$.detail").value("No detectamos tu voz, intenta de nuevo."));
    }

    @Test
    void answerByVoice_shouldReturn422TranscriptionTooLong() throws Exception {
        when(service.submit(any())).thenThrow(new TranscriptionTooLongException());

        mockMvc.perform(multipart(PATH).file(AUDIO).param("turnId", TURN.toString()).header("X-User-Id", "uid-a"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TRANSCRIPTION_TOO_LONG"));
    }

    @Test
    void answerByVoice_shouldReturn422LanguageMismatch() throws Exception {
        when(service.submit(any())).thenThrow(new TranscriptionLanguageMismatchException(InterviewLanguage.ENGLISH));

        mockMvc.perform(multipart(PATH).file(AUDIO).param("turnId", TURN.toString()).header("X-User-Id", "uid-a"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TRANSCRIPTION_LANGUAGE_MISMATCH"))
                .andExpect(jsonPath("$.detail").value("Tu entrevista es en inglés. Responde en inglés."));
    }

    @Test
    void answerByVoice_shouldReturn503TranscriptionUnavailable() throws Exception {
        when(service.submit(any())).thenThrow(new TranscriptionUnavailableException("tiempo agotado", null));

        mockMvc.perform(multipart(PATH).file(AUDIO).param("turnId", TURN.toString()).header("X-User-Id", "uid-a"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("TRANSCRIPTION_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail")
                        .value("No pudimos transcribir tu respuesta. Graba de nuevo o responde por texto."));
    }

    @Test
    void answerByVoice_shouldReturn422WithFields_whenPartsAreMissing() throws Exception {
        when(service.submit(any())).thenThrow(new InvalidRequestException(List.of(
                new FieldRejection("turnId", ErrorCode.TURN_ID_REQUIRED, "Falta la pregunta a la que respondes."),
                new FieldRejection("audio", ErrorCode.AUDIO_REQUIRED, "Graba tu respuesta antes de enviarla."))));

        mockMvc.perform(multipart(PATH).header("X-User-Id", "uid-a"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("turnId"))
                .andExpect(jsonPath("$.errors[0].code").value("TURN_ID_REQUIRED"))
                .andExpect(jsonPath("$.errors[1].field").value("audio"))
                .andExpect(jsonPath("$.errors[1].code").value("AUDIO_REQUIRED"));
    }
}
