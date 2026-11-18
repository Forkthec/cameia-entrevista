package tech.cameia.entrevista.infrastructure.persistence.repository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import tech.cameia.entrevista.domain.port.SpeechUsageRepository;

/**
 * Contadores de la voz de las preguntas sobre {@code uso_audio_pregunta} y {@code consumo_voz_mensual}.
 *
 * <p>Cada operación es una sola sentencia condicionada, sin leer antes: así dos peticiones simultáneas no pueden pasar
 * el límite (la fila queda bloqueada por la primera hasta que confirma) ni dejar un contador por debajo de 0. Corre
 * fuera de toda transacción del caso de uso: cada sentencia confirma sola.</p>
 */
@Repository
class SpeechUsageRepositoryAdapter implements SpeechUsageRepository {

    private static final String RESERVE_PLAY = """
            INSERT INTO microentrevista.uso_audio_pregunta (pregunta_id, usos)
            SELECT ?, 1 WHERE 1 <= ?
            ON CONFLICT (pregunta_id) DO UPDATE SET usos = uso_audio_pregunta.usos + 1
            WHERE uso_audio_pregunta.usos < ?""";

    private static final String RELEASE_PLAY = """
            UPDATE microentrevista.uso_audio_pregunta SET usos = usos - 1 WHERE pregunta_id = ? AND usos > 0""";

    private static final String PLAYS_BY_SESSION = """
            SELECT u.pregunta_id, u.usos FROM microentrevista.uso_audio_pregunta u
            JOIN microentrevista.pregunta p ON p.id = u.pregunta_id WHERE p.sesion_id = ?""";

    private static final String RESERVE_CHARACTERS = """
            INSERT INTO microentrevista.consumo_voz_mensual (mes, caracteres)
            SELECT ?, ? WHERE 0 < ?
            ON CONFLICT (mes) DO UPDATE SET caracteres = consumo_voz_mensual.caracteres + EXCLUDED.caracteres
            WHERE consumo_voz_mensual.caracteres < ?""";

    private static final String RELEASE_CHARACTERS = """
            UPDATE microentrevista.consumo_voz_mensual SET caracteres = GREATEST(0, caracteres - ?) WHERE mes = ?""";

    private final JdbcTemplate jdbc;

    SpeechUsageRepositoryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean reservePlay(UUID questionId, int maxPlays) {
        return jdbc.update(RESERVE_PLAY, questionId, maxPlays, maxPlays) == 1;
    }

    @Override
    public void releasePlay(UUID questionId) {
        jdbc.update(RELEASE_PLAY, questionId);
    }

    @Override
    public Map<UUID, Integer> playsBySession(UUID sessionId) {
        Map<UUID, Integer> plays = new HashMap<>();
        jdbc.query(PLAYS_BY_SESSION, row -> {
            plays.put(row.getObject("pregunta_id", UUID.class), row.getInt("usos"));
        }, sessionId);
        return plays;
    }

    // El tope se mira sobre lo ya consumido: una pregunta que empieza por debajo del tope se envía completa
    // (CA-5.15.28: 899.999 acumulados todavía admite una pregunta; 900.000 ya no).
    @Override
    public boolean reserveCharacters(YearMonth month, int characters, long monthlyCap) {
        return jdbc.update(RESERVE_CHARACTERS, firstDay(month), characters, monthlyCap, monthlyCap) == 1;
    }

    @Override
    public void releaseCharacters(YearMonth month, int characters) {
        jdbc.update(RELEASE_CHARACTERS, characters, firstDay(month));
    }

    private static LocalDate firstDay(YearMonth month) {
        return month.atDay(1);
    }
}
