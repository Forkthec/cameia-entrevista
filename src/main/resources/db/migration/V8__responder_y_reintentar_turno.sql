-- ---------------------------------------------------------------------------
-- Responder una pregunta por escrito y reintentar su evaluacion.
--
-- 1) La clave anti-duplicados admite la operacion RESPONDER; mientras la respuesta se evalua, la fila
--    guarda estado_respuesta = 202.
-- 2) El turno cuenta los usos de «Reintentar» de la persona (de 0 a 4).
-- 3) Solo puede haber una evaluacion en curso por turno.
-- ---------------------------------------------------------------------------

-- Registrar la respuesta usa la clave anti-duplicados con su propia operacion.
ALTER TABLE microentrevista.clave_idempotencia
    DROP CONSTRAINT ck_clave_idempotencia_operacion,
    ADD CONSTRAINT ck_clave_idempotencia_operacion CHECK (operacion IN ('CREAR_SESION', 'RESPONDER'));
COMMENT ON COLUMN microentrevista.clave_idempotencia.operacion IS 'Operacion protegida por la clave. Valores: CREAR_SESION, RESPONDER.';
COMMENT ON COLUMN microentrevista.clave_idempotencia.estado_respuesta IS 'Estado HTTP guardado; 202 mientras la respuesta se evalua.';

-- Usos del boton Reintentar de la persona sobre un turno: 2 y, al volver mas tarde, 2 mas.
ALTER TABLE microentrevista.turno
    ADD COLUMN reintentos_manuales smallint NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_turno_reintentos_manuales CHECK (reintentos_manuales BETWEEN 0 AND 4);
COMMENT ON COLUMN microentrevista.turno.reintentos_manuales IS 'Usos de Reintentar que llegaron a evaluar, de 0 a 4.';

-- Una sola evaluacion en curso por turno: impide dos evaluaciones simultaneas de la misma respuesta.
CREATE UNIQUE INDEX uq_ejecucion_ia_evaluacion_en_curso
    ON microentrevista.ejecucion_ia (turno_id)
    WHERE tipo_operacion = 'EVALUAR' AND estado = 'EN_CURSO';
