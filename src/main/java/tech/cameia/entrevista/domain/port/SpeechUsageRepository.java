package tech.cameia.entrevista.domain.port;

import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;

/**
 * Contadores de la voz de las preguntas: los usos de audio de cada pregunta y los caracteres enviados a la voz en el
 * mes. Es lo único que queda guardado de escuchar una pregunta (CA-5.15.15).
 *
 * <p>Cada reserva y cada devolución es una sola sentencia condicionada: dos peticiones simultáneas nunca pasan el
 * límite ni dejan el contador por debajo de 0 (CA-5.15.7, CA-5.15.23).</p>
 */
public interface SpeechUsageRepository {

    /**
     * Reserva un uso de audio de la pregunta si le queda alguno.
     *
     * @param questionId pregunta
     * @param maxPlays usos de audio por pregunta
     * @return {@code true} si se reservó; {@code false} si ya gastó todos
     */
    boolean reservePlay(UUID questionId, int maxPlays);

    /**
     * Devuelve un uso de audio reservado. No baja de 0.
     *
     * @param questionId pregunta
     */
    void releasePlay(UUID questionId);

    /**
     * Usos de audio gastados por las preguntas de una sesión.
     *
     * @param sessionId sesión
     * @return usos por pregunta; una pregunta sin usos no aparece
     */
    Map<UUID, Integer> playsBySession(UUID sessionId);

    /**
     * Suma los caracteres al consumo del mes si el consumo todavía no llegó al tope.
     *
     * @param month mes calendario en UTC
     * @param characters caracteres que se envían
     * @param monthlyCap tope de caracteres del mes
     * @return {@code true} si se sumaron; {@code false} si el mes ya llegó al tope
     */
    boolean reserveCharacters(YearMonth month, int characters, long monthlyCap);

    /**
     * Resta caracteres reservados que no se llegaron a enviar. No baja de 0.
     *
     * @param month mes de la reserva
     * @param characters caracteres que se devuelven
     */
    void releaseCharacters(YearMonth month, int characters);
}
