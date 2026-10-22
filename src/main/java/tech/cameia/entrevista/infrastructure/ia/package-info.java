/**
 * Adaptadores de los proveedores de modelos de lenguaje, construidos con Spring AI.
 *
 * <p>Hoy hay uno solo, {@code GeminiLanguageModelAdapter}, que implementa
 * {@link tech.cameia.entrevista.domain.port.LanguageModelPort}.</p>
 *
 * <p><strong>Router LLM (pendiente).</strong> El diagrama de componentes C3 de Entrevista
 * prevé un Router que elige proveedor y modelo según el caso de uso y el costo, con respaldo
 * entre Gemini y Kimi (patrón Strategy). No se construye todavía porque hay una sola
 * estrategia (spec de CM-312, D-01). Cuando entre Kimi:</p>
 * <ul>
 *   <li>el Router implementa {@code LanguageModelPort}, que no cambia, y los casos de uso
 *       siguen dependiendo solo del puerto;</li>
 *   <li>cada proveedor pasa a ser una estrategia del Router, y el adaptador de Gemini es la
 *       primera;</li>
 *   <li>el Router no reintenta (los reintentos son de HU-5.2 y HU-5.6) ni publica el consumo de
 *       tokens, que lo publica el caso de uso a partir de
 *       {@link tech.cameia.entrevista.domain.model.LanguageModelResponse} (hallazgo H-2).</li>
 * </ul>
 */
package tech.cameia.entrevista.infrastructure.ia;
