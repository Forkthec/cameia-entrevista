-- ---------------------------------------------------------------------------
-- Respuesta por voz transcrita (CM-349, HU-5.12).
--
-- La respuesta por voz reutiliza tablas que ya existian:
--   * intento_respuesta guarda el texto transcrito con origen_respuesta = 'AUDIO' y el idioma detectado.
--   * ejecucion_ia guarda cada llamada al servicio de transcripcion con tipo_operacion = 'TRANSCRIBIR',
--     el turno al que pertenece y su resultado (CA-5.12.3).
-- El audio nunca se guarda: no hay columna ni tabla para el (CA-5.12.4).
--
-- Lo unico que cambia es la operacion anti-duplicados (se suma a RESPONDER de V8): el envio del audio usa la cabecera Idempotency-Key
-- para que reenviar la misma grabacion tras perder la conexion devuelva la misma respuesta.
-- ---------------------------------------------------------------------------
ALTER TABLE microentrevista.clave_idempotencia
    DROP CONSTRAINT ck_clave_idempotencia_operacion,
    ADD CONSTRAINT ck_clave_idempotencia_operacion CHECK (operacion IN ('CREAR_SESION', 'RESPONDER', 'RESPONDER_AUDIO'));

COMMENT ON COLUMN microentrevista.clave_idempotencia.operacion IS 'Operacion protegida por la clave. Valores: CREAR_SESION, RESPONDER, RESPONDER_AUDIO.';
