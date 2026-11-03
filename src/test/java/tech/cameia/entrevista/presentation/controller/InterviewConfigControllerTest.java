package tech.cameia.entrevista.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.cameia.entrevista.support.CatalogTestData.EXPECTED_BODY;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tech.cameia.entrevista.application.service.GetPersonalizationCatalogService;
import tech.cameia.entrevista.domain.policy.PersonalizationCatalog;
import tech.cameia.entrevista.presentation.advice.ProblemDetailTestSupport;

class InterviewConfigControllerTest {

    private static final String PATH = "/api/v1/interviews/config/catalog";

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new InterviewConfigController(
                    new GetPersonalizationCatalogService(new PersonalizationCatalog())))
            .setControllerAdvice(ProblemDetailTestSupport.errorHandler())
            .build();

    private String bodyOf(RequestBuilder request) throws Exception {
        return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("Con identidad devuelve el catálogo exacto, en su orden")
    void catalog_shouldReturnExactBody_whenIdentityPresent() throws Exception {
        mockMvc.perform(get(PATH).header("X-User-Id", "uid-ñandú"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(EXPECTED_BODY, JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("Una identidad de 128 caracteres es válida")
    void catalog_shouldReturn200_whenIdentityHas128Chars() throws Exception {
        mockMvc.perform(get(PATH).header("X-User-Id", "u".repeat(128)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest(name = "[{0}]")
    @NullSource
    @ValueSource(strings = {"", "   ", "\t", "uid\u0007"})
    @DisplayName("Una identidad ausente, vacía, en blanco o con carácter de control responde 401 sin catálogo")
    void catalog_shouldReturn401_whenIdentityInvalid(String identity) throws Exception {
        MockHttpServletRequestBuilder request = get(PATH);
        if (identity != null) {
            request.header("X-User-Id", identity);
        }

        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"))
                .andExpect(jsonPath("$.detail").value("Tu sesión expiró. Inicia sesión de nuevo."))
                .andExpect(jsonPath("$.modes").doesNotExist());
    }

    @Test
    @DisplayName("Una identidad de 129 caracteres responde 401 IDENTITY_REQUIRED sin catálogo")
    void catalog_shouldReturn401_whenIdentityHas129Chars() throws Exception {
        mockMvc.perform(get(PATH).header("X-User-Id", "u".repeat(129)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"))
                .andExpect(jsonPath("$.modes").doesNotExist());
    }

    @Test
    @DisplayName("El catálogo es el mismo para cualquier plan y cualquier persona")
    void catalog_shouldBeIdenticalForEveryPlan_whenPlanHeaderDiffers() throws Exception {
        String free = bodyOf(get(PATH).header("X-User-Id", "uid-a").header("X-User-Plan", "FREE"));
        String premium = bodyOf(get(PATH).header("X-User-Id", "uid-a").header("X-User-Plan", "PREMIUM"));
        String noPlan = bodyOf(get(PATH).header("X-User-Id", "uid-b"));

        assertThat(free).isEqualTo(premium).isEqualTo(noPlan);
        mockMvc.perform(get(PATH).header("X-User-Id", "uid-a").header("X-User-Plan", "FREE"))
                .andExpect(content().json(EXPECTED_BODY, JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("Los parámetros de consulta inesperados se ignoran")
    void catalog_shouldIgnoreQueryParameters_whenUnexpected() throws Exception {
        String plain = bodyOf(get(PATH).header("X-User-Id", "uid-a"));
        String withParams = bodyOf(get(PATH + "?plan=PREMIUM&modality=ENTRENO&lang=en&firebaseUid=otro")
                .header("X-User-Id", "uid-a"));
        String hostile = bodyOf(get(PATH + "?q=%3Cscript%3E&r=%25").header("X-User-Id", "uid-a"));

        assertThat(withParams).isEqualTo(plain);
        assertThat(hostile).isEqualTo(plain).doesNotContain("script");
    }

    @Test
    @DisplayName("Un cuerpo enviado en el GET no se lee")
    void catalog_shouldIgnoreBody_whenSent() throws Exception {
        mockMvc.perform(get(PATH).header("X-User-Id", "uid-a")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"modes\":[]}"))
                .andExpect(status().isOk())
                .andExpect(content().json(EXPECTED_BODY, JsonCompareMode.STRICT));
    }

    @Test
    @DisplayName("Un fallo imprevisto responde 500 INTERNAL_ERROR sin detalle interno")
    void catalog_shouldReturn500Generic_whenUnexpectedFailure() throws Exception {
        GetPersonalizationCatalogService failing = mock(GetPersonalizationCatalogService.class);
        when(failing.execute()).thenThrow(new IllegalStateException("detalle interno"));
        MockMvc failingMvc = MockMvcBuilders.standaloneSetup(new InterviewConfigController(failing))
                .setControllerAdvice(ProblemDetailTestSupport.errorHandler())
                .build();

        String body = failingMvc.perform(get(PATH).header("X-User-Id", "uid-a"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Ocurrió un error. Inténtalo de nuevo."))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("detalle interno").doesNotContain("IllegalStateException");
    }
}
