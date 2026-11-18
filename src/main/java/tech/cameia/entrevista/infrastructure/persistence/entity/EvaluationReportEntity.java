package tech.cameia.entrevista.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Fila de la tabla {@code reporte_evaluacion}: una por sesión; las columnas nulas significan «sin resultado». */
@Entity
@Table(name = "reporte_evaluacion")
public class EvaluationReportEntity {

    @Id
    @Column(name = "sesion_id", nullable = false)
    private UUID sessionId;

    @Column(name = "precision_claridad", precision = 3, scale = 2)
    private BigDecimal precision;

    @Column(name = "carisma_verbal", precision = 3, scale = 2)
    private BigDecimal charisma;

    @Column(name = "lenguaje_adecuado", precision = 3, scale = 2)
    private BigDecimal language;

    @Column(name = "ritmo_habla")
    private Integer speechRate;

    @Column(name = "tiempo_pausas", precision = 4, scale = 1)
    private BigDecimal pauseTime;

    @Column(name = "tasa_vacilacion", precision = 4, scale = 1)
    private BigDecimal hesitationRate;

    @Column(name = "fecha_emision", nullable = false)
    private Instant issuedAt;

    /** Constructor que exige JPA. */
    protected EvaluationReportEntity() {
    }

    /**
     * Crea la fila con todas sus columnas; el entusiasmo no se mapea y queda nulo.
     *
     * @param sessionId sesión evaluada
     * @param precision promedio de precisión y claridad
     * @param charisma promedio de carisma verbal
     * @param language promedio de lenguaje adecuado
     * @param speechRate palabras por minuto
     * @param pauseTime porcentaje de tiempo en pausas
     * @param hesitationRate porcentaje de muletillas
     * @param issuedAt momento de emisión
     */
    public EvaluationReportEntity(UUID sessionId, BigDecimal precision, BigDecimal charisma, BigDecimal language,
            Integer speechRate, BigDecimal pauseTime, BigDecimal hesitationRate, Instant issuedAt) {
        this.sessionId = sessionId;
        this.precision = precision;
        this.charisma = charisma;
        this.language = language;
        this.speechRate = speechRate;
        this.pauseTime = pauseTime;
        this.hesitationRate = hesitationRate;
        this.issuedAt = issuedAt;
    }

    /** @return sesión evaluada */
    public UUID getSessionId() {
        return sessionId;
    }

    /** @return promedio de precisión y claridad, o nulo */
    public BigDecimal getPrecision() {
        return precision;
    }

    /** @return promedio de carisma verbal, o nulo */
    public BigDecimal getCharisma() {
        return charisma;
    }

    /** @return promedio de lenguaje adecuado, o nulo */
    public BigDecimal getLanguage() {
        return language;
    }

    /** @return palabras por minuto, o nulo */
    public Integer getSpeechRate() {
        return speechRate;
    }

    /** @return porcentaje de tiempo en pausas, o nulo */
    public BigDecimal getPauseTime() {
        return pauseTime;
    }

    /** @return porcentaje de muletillas, o nulo */
    public BigDecimal getHesitationRate() {
        return hesitationRate;
    }

    /** @return momento de emisión */
    public Instant getIssuedAt() {
        return issuedAt;
    }
}
