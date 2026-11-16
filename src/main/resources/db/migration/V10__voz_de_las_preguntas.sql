-- ---------------------------------------------------------------------------
-- Escuchar la pregunta con voz (CM-372, HU-5.15).
--
-- Solo se guardan dos contadores; el audio y el texto enviado a la voz nunca se guardan (CA-5.15.15):
--   * uso_audio_pregunta: usos de audio gastados por pregunta (CA-5.15.6). La tabla pregunta no se modifica (la
--     protege un disparador), por eso el contador vive aparte. Cada reserva es un solo INSERT ... ON CONFLICT
--     condicionado al maximo, y cada devolucion un UPDATE que no baja de 0 (CA-5.15.7, CA-5.15.23).
--   * consumo_voz_mensual: caracteres enviados a la voz por mes calendario UTC, para el tope mensual (CA-5.15.28).
-- ---------------------------------------------------------------------------
CREATE TABLE microentrevista.uso_audio_pregunta (
    pregunta_id  uuid      CONSTRAINT pk_uso_audio_pregunta PRIMARY KEY
                           CONSTRAINT fk_uso_audio_pregunta_pregunta REFERENCES microentrevista.pregunta (id) ON DELETE CASCADE,
    usos         smallint  NOT NULL,
    CONSTRAINT ck_uso_audio_pregunta_usos CHECK (usos BETWEEN 0 AND 10)
);

COMMENT ON TABLE microentrevista.uso_audio_pregunta IS 'Usos de audio gastados por pregunta (HU-5.15). Sin audio ni texto.';
COMMENT ON COLUMN microentrevista.uso_audio_pregunta.usos IS 'Veces que el servicio genero el audio de la pregunta; el maximo lo fija SPEECH_MAX_PLAYS (3).';

CREATE TABLE microentrevista.consumo_voz_mensual (
    mes          date    CONSTRAINT pk_consumo_voz_mensual PRIMARY KEY,
    caracteres   bigint  NOT NULL,
    CONSTRAINT ck_consumo_voz_mensual_mes CHECK (extract(day FROM mes) = 1),
    CONSTRAINT ck_consumo_voz_mensual_caracteres CHECK (caracteres >= 0)
);

COMMENT ON TABLE microentrevista.consumo_voz_mensual IS 'Caracteres enviados a la voz por mes calendario UTC (HU-5.15).';
COMMENT ON COLUMN microentrevista.consumo_voz_mensual.mes IS 'Primer dia del mes, en UTC.';
