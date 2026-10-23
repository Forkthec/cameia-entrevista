package tech.cameia.entrevista.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tech.cameia.entrevista.application.command.CreateSessionCommand;
import tech.cameia.entrevista.application.service.CreateSessionResult;
import tech.cameia.entrevista.application.service.CreateSessionService;
import tech.cameia.entrevista.application.service.OngoingSession;
import tech.cameia.entrevista.domain.exception.IdempotencyKeyConflictException;
import tech.cameia.entrevista.domain.exception.ProfileNotFoundException;
import tech.cameia.entrevista.domain.exception.ProfileSyncPendingException;
import tech.cameia.entrevista.domain.exception.SessionAlreadyExistsException;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.Modality;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.domain.model.ValidatedSessionRequest;
import tech.cameia.entrevista.domain.policy.PersonalizationCatalog;
import tech.cameia.entrevista.domain.policy.SessionConfigurationPolicy;
import tech.cameia.entrevista.presentation.advice.ProblemDetailTestSupport;

@ExtendWith(OutputCaptureExtension.class)
class CreateSessionControllerTest {

    private static final String PATH = "/api/v1/interviews/sessions";
    private static final UUID PROFILE = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final UUID ROLE = UUID.fromString("a0000001-0000-0000-0000-000000000002");
    private static final UUID SESSION = UUID.fromString("3b1f8f2a-6c1e-4d8b-9f0a-2c3d4e5f6a7b");
    private static final UUID RUNNING = UUID.fromString("0e2d4c1a-8b3f-4a6e-9d21-5c7b8a9f0e1d");
    private static final String VALID_BODY = """
            {"profileId":"7c9e6679-7425-40de-944b-e07fc1f90ae7","targetRoleId":"a0000001-0000-0000-0000-000000000002",
             "jobOffer":"Buscamos","mode":"SIMULACION","tone":"CALIDO","answerFormat":"TEXTO","sessionLanguage":"es-CO"}""";

    private final CreateSessionService service = mock(CreateSessionService.class);
    private final SessionConfigurationPolicy policy = new SessionConfigurationPolicy(new PersonalizationCatalog(),
            roleId -> roleId.equals(ROLE));
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new InterviewSessionController(service))
            .setControllerAdvice(ProblemDetailTestSupport.errorHandler())
            .build();

    private static ValidatedSessionRequest configuration() {
        return new ValidatedSessionRequest(PROFILE, ROLE, "Buscamos", Modality.SIMULATION, Tone.WARM,
                AnswerMode.TEXT, InterviewLanguage.SPANISH_COLOMBIA, null);
    }

    // El servicio simulado valida con la política real, para ejercitar los errores de campo de punta a punta.
    private void validateWithRealPolicy(int httpStatus) {
        when(service.create(any())).thenAnswer(call -> {
            CreateSessionCommand command = call.getArgument(0);
            ValidatedSessionRequest request = policy.validate(command.draft());
            return new CreateSessionResult(httpStatus, SESSION, SessionStatus.CONFIGURED, request, null);
        });
    }

    private MockHttpServletRequestBuilder request(String body) {
        return post(PATH).header("X-User-Id", "uid-a").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    @DisplayName("Una sesión nueva responde 201 con su configuración y ongoingSession en null")
    void create_shouldReturn201_whenNew() throws Exception {
        when(service.create(any())).thenReturn(
                new CreateSessionResult(201, SESSION, SessionStatus.CONFIGURED, configuration(), null));

        mockMvc.perform(request(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sessionId").value(SESSION.toString()))
                .andExpect(jsonPath("$.status").value("CONFIGURADA"))
                .andExpect(jsonPath("$.configuration.profileId").value(PROFILE.toString()))
                .andExpect(jsonPath("$.configuration.targetRoleId").value(ROLE.toString()))
                .andExpect(jsonPath("$.configuration.jobOffer").value("Buscamos"))
                .andExpect(jsonPath("$.configuration.mode").value("SIMULACION"))
                .andExpect(jsonPath("$.configuration.tone").value("CALIDO"))
                .andExpect(jsonPath("$.configuration.answerFormat").value("TEXTO"))
                .andExpect(jsonPath("$.configuration.sessionLanguage").value("es-CO"))
                .andExpect(jsonPath("$.ongoingSession").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"ongoingSession\":null")));
    }

    @Test
    @DisplayName("Una sesión reutilizada responde 200 e informa la entrevista en curso")
    void create_shouldReturn200_whenReused() throws Exception {
        when(service.create(any())).thenReturn(new CreateSessionResult(200, SESSION, SessionStatus.CONFIGURED,
                configuration(), new OngoingSession(RUNNING, 2)));

        mockMvc.perform(request(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(SESSION.toString()))
                .andExpect(jsonPath("$.ongoingSession.sessionId").value(RUNNING.toString()))
                .andExpect(jsonPath("$.ongoingSession.answersCount").value(2));
    }

    @Test
    @DisplayName("Una oferta ausente sale como null en la configuración")
    void create_shouldReturnNullOffer_whenAbsent() throws Exception {
        ValidatedSessionRequest noOffer = new ValidatedSessionRequest(PROFILE, ROLE, null, Modality.SIMULATION,
                Tone.WARM, AnswerMode.TEXT, InterviewLanguage.SPANISH_COLOMBIA, null);
        when(service.create(any())).thenReturn(
                new CreateSessionResult(201, SESSION, SessionStatus.CONFIGURED, noOffer, null));

        mockMvc.perform(request(VALID_BODY)).andExpect(status().isCreated())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"jobOffer\":null")));
    }

    @Test
    @DisplayName("El dueño sale del encabezado aunque el cuerpo traiga otro y campos que no existen")
    void create_shouldIgnoreUnexpectedFields_andUseHeaderOwner() throws Exception {
        when(service.create(any())).thenReturn(
                new CreateSessionResult(201, SESSION, SessionStatus.CONFIGURED, configuration(), null));
        String hostile = VALID_BODY.replace("{\"profileId\"",
                "{\"firebaseUid\":\"uid-b\",\"status\":\"EN_CURSO\",\"plan\":\"PREMIUM\",\"planningRetries\":2,"
                        + "\"profileId\"");

        mockMvc.perform(request(hostile).header("Idempotency-Key", "clave-1")).andExpect(status().isCreated());

        ArgumentCaptor<CreateSessionCommand> captor = ArgumentCaptor.forClass(CreateSessionCommand.class);
        verify(service).create(captor.capture());
        assertThat(captor.getValue().owner().value()).isEqualTo("uid-a");
        assertThat(captor.getValue().draft().idempotencyKey()).isEqualTo("clave-1");
        assertThat(captor.getValue().draft().tone()).isEqualTo("CALIDO");
    }

    @ParameterizedTest(name = "[{0}]")
    @NullSource
    @ValueSource(strings = {"", "   ", "uid\u0007"})
    @DisplayName("Sin identidad válida responde 401 y no llama al caso de uso")
    void create_shouldReturn401_whenNoIdentity(String identity) throws Exception {
        MockHttpServletRequestBuilder request = post(PATH).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY);
        if (identity != null) {
            request.header("X-User-Id", identity);
        }

        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"))
                .andExpect(jsonPath("$.detail").value("Tu sesión expiró. Inicia sesión de nuevo."));

        verify(service, never()).create(any());
    }

    @Test
    @DisplayName("Un cuerpo vacío {} da seis elementos en el orden del formulario")
    void create_shouldReturn422WithSixFields_whenEmptyBody() throws Exception {
        validateWithRealPolicy(201);

        mockMvc.perform(request("{}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentType("application/problem+json;charset=UTF-8"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Revisa los campos marcados."))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.errors.length()").value(6))
                .andExpect(jsonPath("$.errors[0].field").value("profileId"))
                .andExpect(jsonPath("$.errors[0].code").value("PROFILE_ID_REQUIRED"))
                .andExpect(jsonPath("$.errors[0].message").value("Elige un Perfil Profesional."))
                .andExpect(jsonPath("$.errors[1].field").value("targetRoleId"))
                .andExpect(jsonPath("$.errors[2].field").value("mode"))
                .andExpect(jsonPath("$.errors[3].field").value("tone"))
                .andExpect(jsonPath("$.errors[4].field").value("answerFormat"))
                .andExpect(jsonPath("$.errors[5].field").value("sessionLanguage"))
                .andExpect(jsonPath("$.errors[5].code").value("SESSION_LANGUAGE_REQUIRED"));
    }

    @Test
    @DisplayName("Cinco valores fuera del catálogo dan cinco elementos con su código")
    void create_shouldReturn422WithFiveFields_whenOutOfCatalog() throws Exception {
        validateWithRealPolicy(201);
        String body = """
                {"profileId":"7c9e6679-7425-40de-944b-e07fc1f90ae7","targetRoleId":"00000000-0000-0000-0000-000000000000",
                 "mode":"ENTRENO","tone":"AMABLE","answerFormat":"VIDEO","sessionLanguage":"fr"}""";

        mockMvc.perform(request(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.length()").value(5))
                .andExpect(jsonPath("$.errors[0].code").value("TARGET_ROLE_ID_INVALID_VALUE"))
                .andExpect(jsonPath("$.errors[0].message").value("Elige un rol objetivo del catálogo."))
                .andExpect(jsonPath("$.errors[1].code").value("MODE_NOT_ALLOWED"))
                .andExpect(jsonPath("$.errors[1].message").value("Esta modalidad estará disponible próximamente."))
                .andExpect(jsonPath("$.errors[2].code").value("TONE_INVALID_VALUE"))
                .andExpect(jsonPath("$.errors[3].code").value("ANSWER_FORMAT_INVALID_VALUE"))
                .andExpect(jsonPath("$.errors[4].code").value("SESSION_LANGUAGE_INVALID_VALUE"));
    }

    @Test
    @DisplayName("Un perfil que no es UUID da PROFILE_ID_INVALID_FORMAT y los demás campos válidos no aparecen")
    void create_shouldReturn422_whenProfileIdIsNotUuid() throws Exception {
        validateWithRealPolicy(201);

        mockMvc.perform(request(VALID_BODY.replace("7c9e6679-7425-40de-944b-e07fc1f90ae7", "perfil-1")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("profileId"))
                .andExpect(jsonPath("$.errors[0].code").value("PROFILE_ID_INVALID_FORMAT"))
                .andExpect(jsonPath("$.errors[0].message").value("El Perfil Profesional elegido no es válido."));
    }

    @Test
    @DisplayName("Una oferta de 3001 caracteres da JOB_OFFER_TOO_LONG y no se escribe en el registro")
    void create_shouldReturn422_whenOfferTooLong_andNotLogIt(CapturedOutput output) throws Exception {
        validateWithRealPolicy(201);
        String secret = "oferta-secreta-123" + "a".repeat(2990);

        mockMvc.perform(request(VALID_BODY.replace("Buscamos", secret)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("jobOffer"))
                .andExpect(jsonPath("$.errors[0].code").value("JOB_OFFER_TOO_LONG"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("La oferta laboral no puede superar los 3000 caracteres."));

        assertThat(output.getOut()).contains("campos=[jobOffer=JOB_OFFER_TOO_LONG]")
                .doesNotContain("oferta-secreta-123");
    }

    @Test
    @DisplayName("Una clave inválida sale como elemento del encabezado Idempotency-Key")
    void create_shouldReturn422_whenIdempotencyKeyInvalid() throws Exception {
        validateWithRealPolicy(201);

        mockMvc.perform(request(VALID_BODY).header("Idempotency-Key", "a b"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"))
                .andExpect(jsonPath("$.errors[0].code").value("IDEMPOTENCY_KEY_INVALID_FORMAT"))
                .andExpect(jsonPath("$.errors[0].message").value("Ocurrió un error. Inténtalo de nuevo."));
    }

    @Test
    @DisplayName("Un perfil de otra persona responde 404 PROFILE_NOT_FOUND")
    void create_shouldReturn404_whenProfileOfOtherUser() throws Exception {
        when(service.create(any())).thenThrow(new ProfileNotFoundException());

        mockMvc.perform(request(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROFILE_NOT_FOUND"))
                .andExpect(jsonPath("$.title").value("Perfil no encontrado"))
                .andExpect(jsonPath("$.detail").value("No encontramos lo que buscabas."));
    }

    @Test
    @DisplayName("Un perfil que aún no llegó responde 409 PROFILE_SYNC_PENDING")
    void create_shouldReturn409_whenSyncPending() throws Exception {
        when(service.create(any())).thenThrow(new ProfileSyncPendingException());

        mockMvc.perform(request(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_SYNC_PENDING"))
                .andExpect(jsonPath("$.title").value("Perfil en actualización"))
                .andExpect(jsonPath("$.detail").value("Tu perfil se está actualizando. Inténtalo en unos segundos."));
    }

    @Test
    @DisplayName("La misma clave con otra configuración responde 409 IDEMPOTENCY_KEY_CONFLICT")
    void create_shouldReturn409_whenKeyConflict() throws Exception {
        when(service.create(any())).thenThrow(new IdempotencyKeyConflictException());

        mockMvc.perform(request(VALID_BODY).header("Idempotency-Key", "clave-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"))
                .andExpect(jsonPath("$.title").value("Solicitud distinta"))
                .andExpect(jsonPath("$.detail")
                        .value("Esta solicitud no coincide con la que enviaste antes. Inténtalo de nuevo."));
    }

    @Test
    @DisplayName("Dos creaciones simultáneas sin clave responden 409 SESSION_ALREADY_EXISTS")
    void create_shouldReturn409_whenSessionAlreadyExists() throws Exception {
        when(service.create(any())).thenThrow(new SessionAlreadyExistsException());

        mockMvc.perform(request(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("JSON roto, cuerpo vacío o un campo con objeto responden 422 REQUEST_BODY_INVALID_FORMAT")
    void create_shouldReturn422_whenBodyUnreadable() throws Exception {
        for (String body : new String[] {"{\"mode\":", "", "[]", VALID_BODY.replace("\"CALIDO\"", "{\"a\":1}")}) {
            mockMvc.perform(request(body))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("REQUEST_BODY_INVALID_FORMAT"))
                    .andExpect(jsonPath("$.detail").value("Revisa el formato de los datos enviados."));
        }
        verify(service, never()).create(any());
    }

    @Test
    @DisplayName("Un Content-Type que no es JSON responde 415 MEDIA_TYPE_NOT_ALLOWED")
    void create_shouldReturn415_whenNotJson() throws Exception {
        mockMvc.perform(post(PATH).header("X-User-Id", "uid-a").contentType(MediaType.TEXT_PLAIN).content(VALID_BODY))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("MEDIA_TYPE_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("Otros métodos sobre la ruta responden 405 METHOD_NOT_ALLOWED")
    void create_shouldReturn405_whenGet() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(PATH)
                .header("X-User-Id", "uid-a"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("Un fallo imprevisto responde 500 INTERNAL_ERROR sin detalle del fallo")
    void create_shouldReturn500_whenUnexpectedFailure() throws Exception {
        when(service.create(any())).thenThrow(new IllegalStateException("detalle-interno-secreto"));

        mockMvc.perform(request(VALID_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("detalle-interno-secreto"))));
    }
}
