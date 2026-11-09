package tech.cameia.entrevista.spike.planificacion;

import java.util.Map;

import tech.cameia.entrevista.domain.exception.BusinessException;
import tech.cameia.entrevista.domain.exception.LlmLimitReachedException;
import tech.cameia.entrevista.domain.model.LanguageModelPrompt;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;
import tech.cameia.entrevista.domain.port.LanguageModelPort;

/**
 * Gemini 3.5 Flash Lite por el mismo cliente que usará el servicio ({@link LanguageModelPort}).
 *
 * <p>El modelo lo fija la variable {@code LLM_MODEL} del proceso de prueba. El puerto no expone
 * encabezados de límites ni el motivo de corte, así que esos datos quedan vacíos.</p>
 */
final class GeminiLite implements ModeloDePrueba {

    private final LanguageModelPort puerto;

    private final String modelo;

    private final String llave;

    GeminiLite(LanguageModelPort puerto, String modelo, String llave) {
        this.puerto = puerto;
        this.modelo = modelo;
        this.llave = llave;
    }

    @Override
    public String alias() {
        return "GEMINI_LITE";
    }

    @Override
    public String nombreDelModelo() {
        return modelo;
    }

    @Override
    public Respuesta generar(String prompt, int maxTokensSalida) {
        try {
            LanguageModelResponse r = puerto.generate(new LanguageModelPrompt(prompt, maxTokensSalida));
            return new Respuesta(r.text(), r.inputTokens(), r.outputTokens(), r.model(), Map.of(), null);
        } catch (BusinessException fallo) {
            String causa = Secretos.enmascarar(causaRaiz(fallo), llave);
            TipoDeFallo tipo;
            if (fallo instanceof LlmLimitReachedException) {
                tipo = causa.contains("PerDay") ? TipoDeFallo.LIMITE_POR_DIA : TipoDeFallo.LIMITE_POR_MINUTO;
            } else {
                tipo = TipoDeFallo.SOBRECARGA;
            }
            throw new FalloDelProveedor(tipo, fallo.getErrorCode().name() + " | " + causa, -1, Map.of());
        }
    }

    private static String causaRaiz(Throwable fallo) {
        Throwable raiz = fallo;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        String texto = raiz.getClass().getSimpleName() + ": " + raiz.getMessage();
        return texto.length() > 1500 ? texto.substring(0, 1500) : texto;
    }
}
