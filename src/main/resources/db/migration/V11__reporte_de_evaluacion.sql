-- ---------------------------------------------------------------------------
-- Reporte de Evaluacion de la sesion de entrevista.
--
--   * intento_respuesta: cuatro enteros de voz por respuesta (palabras, tramo, pausas y muletillas). Nunca se guarda
--     la lista de palabras ni el audio.
--   * ejecucion_ia: operacion nueva GENERAR_REPORTE, sin turno, y una sola ejecucion en curso por sesion.
--   * reporte_evaluacion: un reporte por sesion con las metricas de contenido y de voz; no se modifica una vez emitido.
--   * reporte_star: una fila por pregunta conductual con los cuatro componentes del metodo STAR y su nivel.
-- ---------------------------------------------------------------------------

-- 1) Datos de voz por respuesta: 4 numeros, nunca la lista de palabras ni el audio.
ALTER TABLE microentrevista.intento_respuesta
    ADD COLUMN voz_palabras    integer NULL,
    ADD COLUMN voz_tramo_ms    integer NULL,
    ADD COLUMN voz_pausas_ms   integer NULL,
    ADD COLUMN voz_muletillas  integer NULL,
    ADD CONSTRAINT ck_intento_respuesta_voz_completa CHECK (
        (voz_palabras IS NULL AND voz_tramo_ms IS NULL AND voz_pausas_ms IS NULL AND voz_muletillas IS NULL)
        OR (voz_palabras IS NOT NULL AND voz_tramo_ms IS NOT NULL AND voz_pausas_ms IS NOT NULL AND voz_muletillas IS NOT NULL)),
    ADD CONSTRAINT ck_intento_respuesta_voz_valores CHECK (
        voz_palabras IS NULL OR (voz_palabras >= 1 AND voz_tramo_ms > 0 AND voz_pausas_ms >= 0
            AND voz_pausas_ms <= voz_tramo_ms AND voz_muletillas >= 0 AND voz_muletillas <= voz_palabras)),
    ADD CONSTRAINT ck_intento_respuesta_voz_origen CHECK (origen_respuesta = 'AUDIO' OR voz_palabras IS NULL);

COMMENT ON COLUMN microentrevista.intento_respuesta.voz_palabras IS 'Palabras transcritas de la respuesta por voz; nulo si la respuesta no tiene datos de voz.';
COMMENT ON COLUMN microentrevista.intento_respuesta.voz_tramo_ms IS 'Milisegundos entre el inicio de la primera palabra y el fin de la ultima; nulo si la respuesta no tiene datos de voz.';
COMMENT ON COLUMN microentrevista.intento_respuesta.voz_pausas_ms IS 'Suma de los silencios de 500 ms o mas entre palabras seguidas; nulo si la respuesta no tiene datos de voz.';
COMMENT ON COLUMN microentrevista.intento_respuesta.voz_muletillas IS 'Muletillas detectadas en la transcripcion; nulo si la respuesta no tiene datos de voz.';

-- 2) Operacion nueva de la IA: generar el reporte, sin turno.
ALTER TABLE microentrevista.ejecucion_ia
    DROP CONSTRAINT ck_ejecucion_ia_tipo_operacion,
    ADD CONSTRAINT ck_ejecucion_ia_tipo_operacion
        CHECK (tipo_operacion IN ('PLANIFICAR', 'EVALUAR', 'TRANSCRIBIR', 'GENERAR_REPORTE')),
    DROP CONSTRAINT ck_ejecucion_ia_turno,
    ADD CONSTRAINT ck_ejecucion_ia_turno
        CHECK ((tipo_operacion IN ('PLANIFICAR', 'GENERAR_REPORTE')) = (turno_id IS NULL));

-- 3) Marca de generacion: una sola ejecucion GENERAR_REPORTE en curso por sesion.
CREATE UNIQUE INDEX uq_ejecucion_ia_reporte_en_curso
    ON microentrevista.ejecucion_ia (sesion_id)
    WHERE tipo_operacion = 'GENERAR_REPORTE' AND estado = 'EN_CURSO';

-- 4) Reporte de Evaluacion: uno por sesion, inmutable.
CREATE TABLE microentrevista.reporte_evaluacion (
    sesion_id           uuid          CONSTRAINT pk_reporte_evaluacion PRIMARY KEY
                                      CONSTRAINT fk_reporte_evaluacion_sesion REFERENCES microentrevista.sesion (id) ON DELETE CASCADE,
    precision_claridad  numeric(3,2)  NULL,
    carisma_verbal      numeric(3,2)  NULL,
    lenguaje_adecuado   numeric(3,2)  NULL,
    ritmo_habla         integer       NULL,
    tiempo_pausas       numeric(4,1)  NULL,
    tasa_vacilacion     numeric(4,1)  NULL,
    entusiasmo          numeric(5,2)  NULL,
    fecha_emision       timestamptz   NOT NULL,
    CONSTRAINT ck_reporte_evaluacion_precision_claridad CHECK (precision_claridad IS NULL OR precision_claridad BETWEEN 1 AND 5),
    CONSTRAINT ck_reporte_evaluacion_carisma_verbal CHECK (carisma_verbal IS NULL OR carisma_verbal BETWEEN 1 AND 5),
    CONSTRAINT ck_reporte_evaluacion_lenguaje_adecuado CHECK (lenguaje_adecuado IS NULL OR lenguaje_adecuado BETWEEN 1 AND 5),
    CONSTRAINT ck_reporte_evaluacion_ritmo_habla CHECK (ritmo_habla IS NULL OR ritmo_habla >= 0),
    CONSTRAINT ck_reporte_evaluacion_tiempo_pausas CHECK (tiempo_pausas IS NULL OR tiempo_pausas BETWEEN 0 AND 100),
    CONSTRAINT ck_reporte_evaluacion_tasa_vacilacion CHECK (tasa_vacilacion IS NULL OR tasa_vacilacion BETWEEN 0 AND 100),
    CONSTRAINT ck_reporte_evaluacion_entusiasmo CHECK (entusiasmo IS NULL),
    CONSTRAINT ck_reporte_evaluacion_voz_completa CHECK (
        (ritmo_habla IS NULL) = (tiempo_pausas IS NULL) AND (ritmo_habla IS NULL) = (tasa_vacilacion IS NULL))
);

COMMENT ON TABLE microentrevista.reporte_evaluacion IS 'Reporte de Evaluacion de la sesion: uno por sesion, inmutable una vez emitido.';
COMMENT ON COLUMN microentrevista.reporte_evaluacion.sesion_id IS 'Sesion evaluada; es la clave primaria porque cada sesion tiene un solo reporte.';
COMMENT ON COLUMN microentrevista.reporte_evaluacion.precision_claridad IS 'Promedio de 1 a 5 de la precision y claridad de las respuestas; nulo si no hubo respuestas evaluables.';
COMMENT ON COLUMN microentrevista.reporte_evaluacion.carisma_verbal IS 'Promedio de 1 a 5 del carisma verbal de las respuestas; nulo si no hubo respuestas evaluables.';
COMMENT ON COLUMN microentrevista.reporte_evaluacion.lenguaje_adecuado IS 'Promedio de 1 a 5 de lo adecuado del lenguaje; nulo si no hubo respuestas evaluables.';
COMMENT ON COLUMN microentrevista.reporte_evaluacion.ritmo_habla IS 'Palabras por minuto de las respuestas por voz; nulo si ninguna respuesta tiene datos de voz.';
COMMENT ON COLUMN microentrevista.reporte_evaluacion.tiempo_pausas IS 'Porcentaje (0 a 100, un decimal) del tiempo hablado que fue silencio; nulo si no hay datos de voz.';
COMMENT ON COLUMN microentrevista.reporte_evaluacion.tasa_vacilacion IS 'Porcentaje (0 a 100, un decimal) de palabras que son muletillas; nulo si no hay datos de voz.';
COMMENT ON COLUMN microentrevista.reporte_evaluacion.entusiasmo IS 'Reservado: siempre nulo mientras no exista una medicion de entusiasmo.';
COMMENT ON COLUMN microentrevista.reporte_evaluacion.fecha_emision IS 'Momento en que se emitio el reporte, en UTC.';

-- 5) Metodo STAR: una fila por pregunta conductual del reporte.
CREATE TABLE microentrevista.reporte_star (
    sesion_id    uuid      NOT NULL CONSTRAINT fk_reporte_star_reporte REFERENCES microentrevista.reporte_evaluacion (sesion_id) ON DELETE CASCADE,
    pregunta_id  uuid      NOT NULL CONSTRAINT fk_reporte_star_pregunta REFERENCES microentrevista.pregunta (id) ON DELETE CASCADE,
    situacion    boolean   NULL,
    tarea        boolean   NULL,
    accion       boolean   NULL,
    resultado    boolean   NULL,
    nivel        smallint  NULL,
    CONSTRAINT pk_reporte_star PRIMARY KEY (sesion_id, pregunta_id),
    CONSTRAINT ck_reporte_star_completo CHECK (
        (situacion IS NULL AND tarea IS NULL AND accion IS NULL AND resultado IS NULL AND nivel IS NULL)
        OR (situacion IS NOT NULL AND tarea IS NOT NULL AND accion IS NOT NULL AND resultado IS NOT NULL AND nivel IS NOT NULL)),
    CONSTRAINT ck_reporte_star_nivel CHECK (nivel IS NULL OR nivel =
        (situacion::int + tarea::int + accion::int + resultado::int))
);
CREATE INDEX ix_reporte_star_pregunta_id ON microentrevista.reporte_star (pregunta_id);

COMMENT ON TABLE microentrevista.reporte_star IS 'Metodo STAR por pregunta conductual del reporte; inmutable una vez emitido.';
COMMENT ON COLUMN microentrevista.reporte_star.sesion_id IS 'Reporte al que pertenece la fila.';
COMMENT ON COLUMN microentrevista.reporte_star.pregunta_id IS 'Pregunta conductual evaluada.';
COMMENT ON COLUMN microentrevista.reporte_star.situacion IS 'La respuesta describe la situacion; nulo si la pregunta no se pudo evaluar.';
COMMENT ON COLUMN microentrevista.reporte_star.tarea IS 'La respuesta describe la tarea; nulo si la pregunta no se pudo evaluar.';
COMMENT ON COLUMN microentrevista.reporte_star.accion IS 'La respuesta describe la accion; nulo si la pregunta no se pudo evaluar.';
COMMENT ON COLUMN microentrevista.reporte_star.resultado IS 'La respuesta describe el resultado; nulo si la pregunta no se pudo evaluar.';
COMMENT ON COLUMN microentrevista.reporte_star.nivel IS 'Cantidad de componentes presentes (0 a 4); debe coincidir con la suma de los cuatro.';

-- 6) Inmutabilidad: ningun UPDATE sobre el reporte ni sobre su STAR. El borrado solo ocurre en cascada desde la sesion.
CREATE FUNCTION microentrevista.fn_rechazar_cambio_reporte() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'reporte de evaluacion inmutable' USING ERRCODE = 'P0001', CONSTRAINT = 'trg_reporte_inmutable';
END $$;

CREATE TRIGGER trg_reporte_evaluacion_inmutable BEFORE UPDATE ON microentrevista.reporte_evaluacion
    FOR EACH ROW EXECUTE FUNCTION microentrevista.fn_rechazar_cambio_reporte();
CREATE TRIGGER trg_reporte_star_inmutable BEFORE UPDATE ON microentrevista.reporte_star
    FOR EACH ROW EXECUTE FUNCTION microentrevista.fn_rechazar_cambio_reporte();
