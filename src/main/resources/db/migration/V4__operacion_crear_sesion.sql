-- ---------------------------------------------------------------------------
-- Operacion anti-duplicados de la creacion de la sesion.
-- La tabla clave_idempotencia aceptaba cualquier operacion no vacia; se fija
-- el valor permitido de la operacion que hoy la usa.
-- ---------------------------------------------------------------------------
ALTER TABLE microentrevista.clave_idempotencia
    DROP CONSTRAINT ck_clave_idempotencia_operacion,
    ADD CONSTRAINT ck_clave_idempotencia_operacion CHECK (operacion IN ('CREAR_SESION'));

COMMENT ON COLUMN microentrevista.clave_idempotencia.operacion IS 'Operacion protegida por la clave. Valores: CREAR_SESION.';
