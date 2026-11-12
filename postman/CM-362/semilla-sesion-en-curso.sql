-- CM-362 · Semilla SOLO PARA PRUEBAS LOCALES: deja una entrevista EN_CURSO, en modo audio, con sus 5 preguntas y
-- el turno 1 pendiente, para la persona 'cm362-postman'. Evita depender de RabbitMQ (copia del perfil) y de Gemini
-- (planificar las preguntas). Se puede correr las veces que haga falta: borra primero lo que dejó la corrida anterior.
--
-- Uso (con el compose de Entrevista levantado):
--   docker exec -i cameia-entrevista-db psql -U postgres -d DB-entrevista -v ON_ERROR_STOP=1 < postman/CM-362/semilla-sesion-en-curso.sql
--
-- Identificadores fijos para pegarlos en Postman:
--   sesión  3620c362-0000-4000-8000-000000000001
--   turno 1 3620c362-0000-4000-8000-0000000000a1

BEGIN;

-- La cascada borra preguntas, turnos, respuestas y ejecuciones de la corrida anterior.
DELETE FROM microentrevista.sesion WHERE firebase_uid = 'cm362-postman';

INSERT INTO microentrevista.sesion (id, firebase_uid, perfil_id, rol_objetivo_id, oferta_laboral,
    contexto_perfil_snapshot, modalidad, tono, forma_respuesta, idioma, estado, reintentos_planificacion,
    fecha_creacion, fecha_actualizacion, fecha_ultima_actividad, version)
VALUES ('3620c362-0000-4000-8000-000000000001', 'cm362-postman', '3620c362-0000-4000-8000-0000000000f1',
    'a0000001-0000-0000-0000-000000000002', 'Buscamos analista de datos',
    '{"perfilId":"3620c362-0000-4000-8000-0000000000f1","nombre":"Analista de datos"}'::jsonb,
    'SIMULACION', 'CALIDO', 'AUDIO', 'es-CO', 'EN_CURSO', 0, now(), now(), now(), 0);

INSERT INTO microentrevista.ejecucion_ia (id, sesion_id, turno_id, tipo_operacion, numero_intento, idempotency_key,
    estado, modelo, version_prompt, proveedor, tokens_entrada, tokens_salida, fecha_inicio, fecha_fin)
VALUES ('3620c362-0000-4000-8000-0000000000e1', '3620c362-0000-4000-8000-000000000001', NULL, 'PLANIFICAR', 1,
    '3620c362-0000-4000-8000-0000000000e2', 'EXITOSA', 'semilla-cm362', 'planificacion.v6', 'semilla', 0, 0,
    now(), now());

INSERT INTO microentrevista.pregunta (id, sesion_id, orden, tipo, texto_original, ejecucion_ia_id, fecha_creacion)
VALUES
    ('3620c362-0000-4000-8000-0000000000b1', '3620c362-0000-4000-8000-000000000001', 1, 'APERTURA',
     'Cuéntame brevemente quién eres y por qué te interesa este rol.', '3620c362-0000-4000-8000-0000000000e1', now()),
    ('3620c362-0000-4000-8000-0000000000b2', '3620c362-0000-4000-8000-000000000001', 2, 'TECNICA',
     '¿Cómo optimizarías una consulta SQL lenta?', '3620c362-0000-4000-8000-0000000000e1', now()),
    ('3620c362-0000-4000-8000-0000000000b3', '3620c362-0000-4000-8000-000000000001', 3, 'CONDUCTUAL',
     'Háblame de un desacuerdo con un compañero y cómo lo resolviste.', '3620c362-0000-4000-8000-0000000000e1', now()),
    ('3620c362-0000-4000-8000-0000000000b4', '3620c362-0000-4000-8000-000000000001', 4, 'TECNICA',
     '¿Cómo validarías la calidad de un conjunto de datos?', '3620c362-0000-4000-8000-0000000000e1', now()),
    ('3620c362-0000-4000-8000-0000000000b5', '3620c362-0000-4000-8000-000000000001', 5, 'SITUACIONAL',
     'Si un informe tuyo tuviera un error en producción, ¿qué harías?', '3620c362-0000-4000-8000-0000000000e1', now());

INSERT INTO microentrevista.turno (id, pregunta_id, estado, fecha_creacion, fecha_actualizacion, version)
VALUES ('3620c362-0000-4000-8000-0000000000a1', '3620c362-0000-4000-8000-0000000000b1', 'PENDING', now(), now(), 0);

COMMIT;
