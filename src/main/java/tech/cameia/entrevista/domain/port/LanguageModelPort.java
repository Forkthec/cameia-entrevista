package tech.cameia.entrevista.domain.port;

import tech.cameia.entrevista.domain.exception.LlmLimitReachedException;
import tech.cameia.entrevista.domain.exception.LlmUnavailableException;
import tech.cameia.entrevista.domain.model.AiProvider;
import tech.cameia.entrevista.domain.model.LanguageModelPrompt;
import tech.cameia.entrevista.domain.model.LanguageModelResponse;

/**
 * Pide texto a un modelo de lenguaje, sin saber qué proveedor ni qué modelo responde.
 *
 * <p>Devuelve el texto crudo del modelo: cada caso de uso lo valida antes de cambiar un estado
 * de la entrevista (IA-01). Junto al texto llegan los tokens gastados, para que el caso de uso
 * registre el consumo. Una llamada hace un solo intento; si el caso de uso necesita reintentar,
 * lo decide él, con el ciclo de intentos de la aplicación, que elige el proveedor de cada uno.</p>
 */
public interface LanguageModelPort {

    /**
     * Envía el prompt al proveedor dado, con el tiempo máximo de la operación del prompt, y devuelve su respuesta.
     *
     * @param prompt texto que se envía y tope de tokens de la respuesta
     * @param provider proveedor que atiende la llamada
     * @return texto de la respuesta, nunca en blanco, con los tokens gastados y el modelo que respondió
     * @throws LlmUnavailableException si el modelo no responde dentro del tiempo de espera, si el
     *                                 proveedor falla o si la respuesta llega vacía o inutilizable
     * @throws LlmLimitReachedException si el proveedor rechaza la llamada por cuota agotada
     */
    LanguageModelResponse generate(LanguageModelPrompt prompt, AiProvider provider);

    /**
     * Indica si el proveedor puede usarse; el de respaldo puede no tener llave.
     *
     * @param provider proveedor consultado
     * @return {@code true} si una llamada a ese proveedor puede hacerse
     */
    boolean isAvailable(AiProvider provider);
}
