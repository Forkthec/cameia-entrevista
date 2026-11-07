-- ---------------------------------------------------------------------------
-- El tono Motivador sale del catalogo. Las sesiones guardadas con ese tono pasan a
-- Profesional y neutro; la respuesta guardada de las claves anti-duplicados se ajusta
-- para que repetir una clave antigua nunca falle al leerla.
-- ---------------------------------------------------------------------------
UPDATE microentrevista.sesion
   SET tono = 'PROFESIONAL_NEUTRO', version = version + 1, fecha_actualizacion = now()
 WHERE tono = 'MOTIVADOR';

UPDATE microentrevista.clave_idempotencia
   SET cuerpo_respuesta = jsonb_set(cuerpo_respuesta, '{configuration,tone}', '"PROFESSIONAL_NEUTRAL"')
 WHERE cuerpo_respuesta #>> '{configuration,tone}' = 'MOTIVATING';

ALTER TABLE microentrevista.sesion
    DROP CONSTRAINT ck_sesion_tono,
    ADD CONSTRAINT ck_sesion_tono CHECK (tono IN ('PROFESIONAL_NEUTRO', 'CALIDO', 'ESTRICTO'));

COMMENT ON COLUMN microentrevista.sesion.tono IS 'Valores: PROFESIONAL_NEUTRO, CALIDO, ESTRICTO.';
