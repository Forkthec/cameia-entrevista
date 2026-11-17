package tech.cameia.entrevista.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Fila de la tabla {@code reporte_star}: los cuatro componentes STAR de una pregunta conductual y su nivel. */
@Entity
@Table(name = "reporte_star")
@IdClass(StarResultEntity.Key.class)
public class StarResultEntity {

    @Id
    @Column(name = "sesion_id", nullable = false)
    private UUID sessionId;

    @Id
    @Column(name = "pregunta_id", nullable = false)
    private UUID questionId;

    @Column(name = "situacion")
    private Boolean situation;

    @Column(name = "tarea")
    private Boolean task;

    @Column(name = "accion")
    private Boolean action;

    @Column(name = "resultado")
    private Boolean result;

    @Column(name = "nivel")
    private Short level;

    /** Constructor que exige JPA. */
    protected StarResultEntity() {
    }

    /**
     * Crea la fila.
     *
     * @param sessionId reporte al que pertenece
     * @param questionId pregunta conductual evaluada
     * @param situation componente situación, o nulo
     * @param task componente tarea, o nulo
     * @param action componente acción, o nulo
     * @param result componente resultado, o nulo
     * @param level cantidad de componentes presentes, o nulo si no se evaluó
     */
    public StarResultEntity(UUID sessionId, UUID questionId, Boolean situation, Boolean task, Boolean action,
            Boolean result, Short level) {
        this.sessionId = sessionId;
        this.questionId = questionId;
        this.situation = situation;
        this.task = task;
        this.action = action;
        this.result = result;
        this.level = level;
    }

    /** Clave compuesta de la fila. */
    public static class Key implements Serializable {

        private static final long serialVersionUID = 1L;

        private UUID sessionId;

        private UUID questionId;

        /** Constructor que exige JPA. */
        public Key() {
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(sessionId, key.sessionId)
                    && Objects.equals(questionId, key.questionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionId, questionId);
        }
    }
}
