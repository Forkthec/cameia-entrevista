-- Esquema inicial del microservicio de Entrevista.
--
-- Guarda las sesiones de practica y simulacion de entrevista, sus preguntas, los turnos de
-- respuesta, los intentos del usuario, la retroalimentacion, cada llamada al modelo de
-- lenguaje y las claves que evitan duplicados. Las reglas de negocio viven en el dominio;
-- este esquema las repite (NOT NULL, CHECK, UNIQUE, claves foraneas e indices) para que una
-- fila invalida nunca llegue a guardarse aunque el error este en el codigo.
--
-- perfil_id y rol_objetivo_id pertenecen a Perfil y no tienen clave foranea: ningun servicio
-- referencia las tablas de otro. Los instantes son timestamptz (UTC). Las funciones de los
-- disparadores califican las tablas con el esquema porque corren con el search_path de quien
-- actualiza.

CREATE SCHEMA IF NOT EXISTS microentrevista;

-- ---------------------------------------------------------------------------
-- Catalogo de tipos de pregunta
-- ---------------------------------------------------------------------------
CREATE TABLE microentrevista.tipo_pregunta (
    codigo       varchar(20) NOT NULL CONSTRAINT pk_tipo_pregunta PRIMARY KEY,
    aplica_star  boolean     NOT NULL,
    CONSTRAINT ck_tipo_pregunta_codigo CHECK (codigo IN ('APERTURA', 'TECNICA', 'CONDUCTUAL', 'SITUACIONAL'))
);

INSERT INTO microentrevista.tipo_pregunta (codigo, aplica_star) VALUES
    ('APERTURA', false),
    ('TECNICA', false),
    ('CONDUCTUAL', true),
    ('SITUACIONAL', false);

-- ---------------------------------------------------------------------------
-- Sesion
-- ---------------------------------------------------------------------------
CREATE TABLE microentrevista.sesion (
    id                         uuid         CONSTRAINT pk_sesion PRIMARY KEY,
    firebase_uid               varchar(128) NOT NULL,
    perfil_id                  uuid         NOT NULL,
    rol_objetivo_id            uuid         NOT NULL,
    oferta_laboral             varchar(3000) NULL,
    contexto_perfil_snapshot   jsonb        NOT NULL,
    modalidad                  varchar(20)  NOT NULL,
    tono                       varchar(20)  NOT NULL,
    forma_respuesta            varchar(10)  NOT NULL,
    idioma                     varchar(5)   NOT NULL,
    estado                     varchar(20)  NOT NULL DEFAULT 'CONFIGURADA',
    reintentos_planificacion   smallint     NOT NULL DEFAULT 0,
    fecha_creacion             timestamptz  NOT NULL,
    fecha_actualizacion        timestamptz  NOT NULL,
    fecha_ultima_actividad     timestamptz  NULL,
    fecha_fin                  timestamptz  NULL,
    version                    bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ck_sesion_firebase_uid CHECK (char_length(btrim(firebase_uid)) > 0),
    CONSTRAINT ck_sesion_oferta_laboral CHECK (oferta_laboral IS NULL OR char_length(btrim(oferta_laboral)) BETWEEN 1 AND 3000),
    CONSTRAINT ck_sesion_contexto_perfil_snapshot CHECK (jsonb_typeof(contexto_perfil_snapshot) = 'object'),
    CONSTRAINT ck_sesion_modalidad CHECK (modalidad IN ('SIMULACION', 'ENTRENO')),
    CONSTRAINT ck_sesion_tono CHECK (tono IN ('PROFESIONAL_NEUTRO', 'CALIDO', 'ESTRICTO', 'MOTIVADOR')),
    CONSTRAINT ck_sesion_forma_respuesta CHECK (forma_respuesta IN ('AUDIO', 'TEXTO')),
    CONSTRAINT ck_sesion_idioma CHECK (idioma IN ('es-CO', 'en')),
    CONSTRAINT ck_sesion_estado CHECK (estado IN ('CONFIGURADA', 'EN_CURSO', 'EVALUANDO', 'FINALIZADA', 'ABANDONADA')),
    CONSTRAINT ck_sesion_reintentos_planificacion CHECK (reintentos_planificacion BETWEEN 0 AND 2),
    CONSTRAINT ck_sesion_fecha_fin CHECK ((estado IN ('ABANDONADA', 'FINALIZADA')) = (fecha_fin IS NOT NULL))
);

-- Una sola sesion configurada y una sola en curso por usuario.
CREATE UNIQUE INDEX uq_sesion_configurada_por_usuario ON microentrevista.sesion (firebase_uid) WHERE estado = 'CONFIGURADA';
CREATE UNIQUE INDEX uq_sesion_en_curso_por_usuario ON microentrevista.sesion (firebase_uid) WHERE estado = 'EN_CURSO';
CREATE INDEX ix_sesion_firebase_uid ON microentrevista.sesion (firebase_uid);
-- Atiende la busqueda de sesiones en curso sin actividad reciente, para abandonarlas.
CREATE INDEX ix_sesion_en_curso_ultima_actividad ON microentrevista.sesion (fecha_ultima_actividad) WHERE estado = 'EN_CURSO';

-- ---------------------------------------------------------------------------
-- Pregunta
-- ejecucion_ia_id (procedencia de la IA) apunta a ejecucion_ia, que a su vez apunta a turno y
-- este a pregunta: hay un ciclo, asi que su clave foranea se agrega al final.
-- ---------------------------------------------------------------------------
CREATE TABLE microentrevista.pregunta (
    id               uuid          CONSTRAINT pk_pregunta PRIMARY KEY,
    sesion_id        uuid          NOT NULL CONSTRAINT fk_pregunta_sesion REFERENCES microentrevista.sesion (id) ON DELETE CASCADE,
    orden            smallint      NOT NULL,
    tipo             varchar(20)   NOT NULL CONSTRAINT fk_pregunta_tipo_pregunta REFERENCES microentrevista.tipo_pregunta (codigo) ON DELETE RESTRICT,
    texto_original   varchar(1000) NOT NULL,
    ejecucion_ia_id  uuid          NOT NULL,
    fecha_creacion   timestamptz   NOT NULL,
    CONSTRAINT ck_pregunta_orden CHECK (orden BETWEEN 1 AND 5),
    -- Limite provisional: se ajusta con el esquema del spike de planificacion.
    CONSTRAINT ck_pregunta_texto_original CHECK (char_length(btrim(texto_original)) BETWEEN 1 AND 1000),
    CONSTRAINT uq_pregunta_sesion_orden UNIQUE (sesion_id, orden)
);

CREATE INDEX ix_pregunta_tipo ON microentrevista.pregunta (tipo);

-- ---------------------------------------------------------------------------
-- Turno
-- ---------------------------------------------------------------------------
CREATE TABLE microentrevista.turno (
    id                   uuid        CONSTRAINT pk_turno PRIMARY KEY,
    pregunta_id          uuid        NOT NULL CONSTRAINT fk_turno_pregunta REFERENCES microentrevista.pregunta (id) ON DELETE CASCADE,
    estado               varchar(20) NOT NULL DEFAULT 'PENDING',
    fecha_creacion       timestamptz NOT NULL,
    fecha_actualizacion  timestamptz NOT NULL,
    version              bigint      NOT NULL DEFAULT 0,
    CONSTRAINT uq_turno_pregunta UNIQUE (pregunta_id),
    CONSTRAINT ck_turno_estado CHECK (estado IN ('PENDING', 'PROCESSING', 'COMPLETED', 'RETRYING'))
);

-- ---------------------------------------------------------------------------
-- Intento de respuesta
-- ---------------------------------------------------------------------------
CREATE TABLE microentrevista.intento_respuesta (
    id                 uuid          CONSTRAINT pk_intento_respuesta PRIMARY KEY,
    turno_id           uuid          NOT NULL CONSTRAINT fk_intento_respuesta_turno REFERENCES microentrevista.turno (id) ON DELETE CASCADE,
    tipo_interaccion   varchar(20)   NOT NULL,
    es_definitiva      boolean       NOT NULL,
    origen_respuesta   varchar(10)   NOT NULL,
    respuesta_texto    varchar(4000) NOT NULL,
    pregunta_mostrada  varchar(1000) NOT NULL,
    idioma_detectado   varchar(10)   NULL,
    fecha_creacion     timestamptz   NOT NULL,
    CONSTRAINT ck_intento_respuesta_tipo_interaccion CHECK (tipo_interaccion IN ('INICIAL')),
    CONSTRAINT ck_intento_respuesta_origen_respuesta CHECK (origen_respuesta IN ('TEXTO', 'AUDIO')),
    CONSTRAINT ck_intento_respuesta_respuesta_texto CHECK (char_length(btrim(respuesta_texto)) BETWEEN 1 AND 4000),
    CONSTRAINT ck_intento_respuesta_pregunta_mostrada CHECK (char_length(btrim(pregunta_mostrada)) BETWEEN 1 AND 1000),
    CONSTRAINT ck_intento_respuesta_idioma_detectado CHECK (idioma_detectado IS NULL OR char_length(btrim(idioma_detectado)) BETWEEN 2 AND 10),
    CONSTRAINT ck_intento_respuesta_idioma_origen CHECK (origen_respuesta = 'AUDIO' OR idioma_detectado IS NULL)
);

CREATE INDEX ix_intento_respuesta_turno_id ON microentrevista.intento_respuesta (turno_id);
-- Un turno tiene a lo sumo un intento definitivo.
CREATE UNIQUE INDEX uq_intento_definitivo_por_turno ON microentrevista.intento_respuesta (turno_id) WHERE es_definitiva;

-- ---------------------------------------------------------------------------
-- Retroalimentacion del turno
-- ---------------------------------------------------------------------------
CREATE TABLE microentrevista.feedback_turno (
    id               uuid        CONSTRAINT pk_feedback_turno PRIMARY KEY,
    turno_id         uuid        NOT NULL CONSTRAINT fk_feedback_turno_turno REFERENCES microentrevista.turno (id) ON DELETE CASCADE,
    feedback         jsonb       NOT NULL,
    feedback_star    jsonb       NULL,
    ejecucion_ia_id  uuid        NOT NULL,
    fecha_creacion   timestamptz NOT NULL,
    CONSTRAINT uq_feedback_turno_turno UNIQUE (turno_id),
    CONSTRAINT ck_feedback_turno_feedback CHECK (jsonb_typeof(feedback) = 'object'),
    CONSTRAINT ck_feedback_turno_feedback_star CHECK (feedback_star IS NULL OR jsonb_typeof(feedback_star) = 'object')
);

-- ---------------------------------------------------------------------------
-- Ejecucion de la IA: una fila por cada llamada al modelo
-- ---------------------------------------------------------------------------
CREATE TABLE microentrevista.ejecucion_ia (
    id               uuid         CONSTRAINT pk_ejecucion_ia PRIMARY KEY,
    sesion_id        uuid         NOT NULL CONSTRAINT fk_ejecucion_ia_sesion REFERENCES microentrevista.sesion (id) ON DELETE CASCADE,
    turno_id         uuid         NULL CONSTRAINT fk_ejecucion_ia_turno REFERENCES microentrevista.turno (id) ON DELETE CASCADE,
    tipo_operacion   varchar(20)  NOT NULL,
    numero_intento   smallint     NOT NULL,
    idempotency_key  uuid         NOT NULL CONSTRAINT uq_ejecucion_ia_idempotency_key UNIQUE,
    estado           varchar(20)  NOT NULL,
    modelo           varchar(120) NOT NULL,
    version_prompt   varchar(80)  NULL,
    error_codigo     varchar(80)  NULL,
    proveedor        varchar(60)  NULL,
    tokens_entrada   bigint       NULL,
    tokens_salida    bigint       NULL,
    fecha_inicio     timestamptz  NOT NULL,
    fecha_fin        timestamptz  NULL,
    CONSTRAINT ck_ejecucion_ia_tipo_operacion CHECK (tipo_operacion IN ('PLANIFICAR', 'EVALUAR', 'TRANSCRIBIR')),
    CONSTRAINT ck_ejecucion_ia_numero_intento CHECK (numero_intento BETWEEN 1 AND 3),
    CONSTRAINT ck_ejecucion_ia_estado CHECK (estado IN ('EN_CURSO', 'EXITOSA', 'FALLIDA')),
    CONSTRAINT ck_ejecucion_ia_fecha_fin CHECK ((estado = 'EN_CURSO') = (fecha_fin IS NULL)),
    CONSTRAINT ck_ejecucion_ia_turno CHECK ((tipo_operacion = 'PLANIFICAR') = (turno_id IS NULL)),
    CONSTRAINT ck_ejecucion_ia_modelo CHECK (char_length(btrim(modelo)) > 0),
    CONSTRAINT ck_ejecucion_ia_version_prompt CHECK ((tipo_operacion = 'TRANSCRIBIR') = (version_prompt IS NULL) AND (version_prompt IS NULL OR char_length(btrim(version_prompt)) > 0)),
    CONSTRAINT ck_ejecucion_ia_error_codigo CHECK ((estado = 'FALLIDA') = (error_codigo IS NOT NULL) AND (error_codigo IS NULL OR char_length(btrim(error_codigo)) > 0)),
    CONSTRAINT ck_ejecucion_ia_proveedor CHECK (proveedor IS NULL OR char_length(btrim(proveedor)) > 0),
    CONSTRAINT ck_ejecucion_ia_tokens_entrada CHECK (tokens_entrada IS NULL OR tokens_entrada >= 0),
    CONSTRAINT ck_ejecucion_ia_tokens_salida CHECK (tokens_salida IS NULL OR tokens_salida >= 0)
);

CREATE INDEX ix_ejecucion_ia_sesion_id ON microentrevista.ejecucion_ia (sesion_id);
CREATE INDEX ix_ejecucion_ia_turno_id ON microentrevista.ejecucion_ia (turno_id);

-- ---------------------------------------------------------------------------
-- Clave de idempotencia: evita duplicar una operacion repetida por el cliente
-- ---------------------------------------------------------------------------
CREATE TABLE microentrevista.clave_idempotencia (
    id                uuid         CONSTRAINT pk_clave_idempotencia PRIMARY KEY,
    firebase_uid      varchar(128) NOT NULL,
    operacion         varchar(40)  NOT NULL,
    clave             varchar(64)  NOT NULL,
    huella_peticion   char(64)     NOT NULL,
    estado_respuesta  smallint     NOT NULL,
    cuerpo_respuesta  jsonb        NOT NULL,
    fecha_creacion    timestamptz  NOT NULL,
    CONSTRAINT uq_clave_idempotencia_usuario_operacion_clave UNIQUE (firebase_uid, operacion, clave),
    CONSTRAINT ck_clave_idempotencia_clave CHECK (clave ~ '^[A-Za-z0-9._-]{1,64}$'),
    CONSTRAINT ck_clave_idempotencia_operacion CHECK (char_length(btrim(operacion)) > 0),
    CONSTRAINT ck_clave_idempotencia_huella_peticion CHECK (huella_peticion ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_clave_idempotencia_estado_respuesta CHECK (estado_respuesta BETWEEN 200 AND 599)
);

-- ---------------------------------------------------------------------------
-- Procedencia de la IA: cierra el ciclo pregunta - turno - ejecucion_ia
-- ---------------------------------------------------------------------------
ALTER TABLE microentrevista.pregunta ADD CONSTRAINT fk_pregunta_ejecucion_ia
    FOREIGN KEY (ejecucion_ia_id) REFERENCES microentrevista.ejecucion_ia (id) ON DELETE CASCADE;
CREATE INDEX ix_pregunta_ejecucion_ia_id ON microentrevista.pregunta (ejecucion_ia_id);

ALTER TABLE microentrevista.feedback_turno ADD CONSTRAINT fk_feedback_turno_ejecucion_ia
    FOREIGN KEY (ejecucion_ia_id) REFERENCES microentrevista.ejecucion_ia (id) ON DELETE CASCADE;
CREATE INDEX ix_feedback_turno_ejecucion_ia_id ON microentrevista.feedback_turno (ejecucion_ia_id);

-- ---------------------------------------------------------------------------
-- Disparadores
-- ---------------------------------------------------------------------------

-- Rechaza cambiar el texto, el tipo, la posicion o la sesion de una pregunta cuando su sesion ya no esta
-- CONFIGURADA. Las preguntas se insertan en la misma transaccion que pasa la sesion a EN_CURSO,
-- asi que con la sesion iniciada quedan fijas para siempre.
CREATE FUNCTION microentrevista.fn_rechazar_cambio_pregunta() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.texto_original, NEW.tipo, NEW.orden, NEW.sesion_id)
           IS DISTINCT FROM (OLD.texto_original, OLD.tipo, OLD.orden, OLD.sesion_id)
       AND (SELECT s.estado FROM microentrevista.sesion s WHERE s.id = OLD.sesion_id) <> 'CONFIGURADA' THEN
        RAISE EXCEPTION 'pregunta inmutable' USING ERRCODE = 'P0001', CONSTRAINT = 'trg_pregunta_inmutable';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_pregunta_inmutable BEFORE UPDATE ON microentrevista.pregunta
    FOR EACH ROW EXECUTE FUNCTION microentrevista.fn_rechazar_cambio_pregunta();

-- Tampoco se borra una pregunta de una sesion iniciada. En la cascada desde la sesion, la fila de la sesion
-- ya no existe y la consulta no devuelve nada: el borrado de la sesion completa sigue permitido.
CREATE FUNCTION microentrevista.fn_rechazar_borrado_pregunta() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (SELECT s.estado FROM microentrevista.sesion s WHERE s.id = OLD.sesion_id) <> 'CONFIGURADA' THEN
        RAISE EXCEPTION 'pregunta inmutable' USING ERRCODE = 'P0001', CONSTRAINT = 'trg_pregunta_inmutable';
    END IF;
    RETURN OLD;
END $$;
CREATE TRIGGER trg_pregunta_inmutable_borrado BEFORE DELETE ON microentrevista.pregunta
    FOR EACH ROW EXECUTE FUNCTION microentrevista.fn_rechazar_borrado_pregunta();

-- Un intento de respuesta solo se inserta: es la evidencia de lo que la persona respondio.
CREATE FUNCTION microentrevista.fn_rechazar_cambio_intento() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'intento de respuesta de solo insercion' USING ERRCODE = 'P0001', CONSTRAINT = 'trg_intento_append_only';
END $$;
CREATE TRIGGER trg_intento_append_only BEFORE UPDATE ON microentrevista.intento_respuesta
    FOR EACH ROW EXECUTE FUNCTION microentrevista.fn_rechazar_cambio_intento();

-- ---------------------------------------------------------------------------
-- Comentarios
-- ---------------------------------------------------------------------------
COMMENT ON TABLE microentrevista.sesion IS 'Sesion de practica o simulacion de entrevista de un usuario. La identidad es el firebase_uid que pone el Gateway; perfil_id y rol_objetivo_id son de Perfil y no tienen clave foranea.';
COMMENT ON TABLE microentrevista.pregunta IS 'Pregunta de una sesion. Texto, tipo y orden no cambian una vez iniciada la sesion (disparador trg_pregunta_inmutable).';
COMMENT ON TABLE microentrevista.turno IS 'Avance de una pregunta; la misma maquina de estados sirve para texto y voz.';
COMMENT ON TABLE microentrevista.intento_respuesta IS 'Respuesta del usuario a un turno. Solo se inserta: no admite cambios posteriores (disparador trg_intento_append_only).';
COMMENT ON TABLE microentrevista.feedback_turno IS 'Retroalimentacion de un turno. Se guarda, pero no se expone hasta que la sesion finaliza.';
COMMENT ON TABLE microentrevista.ejecucion_ia IS 'Cada llamada al modelo: operacion, intento, modelo, version del prompt, estado y causa del fallo. Base de la medicion de procedencia de la IA.';
COMMENT ON TABLE microentrevista.clave_idempotencia IS 'Clave anti-duplicados por usuario y operacion, con la huella de la peticion y la respuesta guardada.';
COMMENT ON TABLE microentrevista.tipo_pregunta IS 'Catalogo de tipos de pregunta.';

COMMENT ON COLUMN microentrevista.sesion.estado IS 'CONFIGURADA al crearla, EN_CURSO al iniciarla, EVALUANDO al terminar las preguntas, FINALIZADA con la evaluacion lista, ABANDONADA si se deja. Una sesion configurada se elimina con DELETE: no tiene estado propio.';
COMMENT ON COLUMN microentrevista.sesion.version IS 'Contador de control de concurrencia optimista.';
COMMENT ON COLUMN microentrevista.sesion.contexto_perfil_snapshot IS 'Copia minima del perfil al crear la sesion; evita consultar Perfil en cada turno.';
COMMENT ON COLUMN microentrevista.turno.estado IS 'PENDING al crearse, PROCESSING al recibir la respuesta, COMPLETED al evaluarse, RETRYING si el modelo falla.';

COMMENT ON COLUMN microentrevista.sesion.modalidad IS 'Valores: SIMULACION, ENTRENO.';
COMMENT ON COLUMN microentrevista.sesion.tono IS 'Valores: PROFESIONAL_NEUTRO, CALIDO, ESTRICTO, MOTIVADOR.';
COMMENT ON COLUMN microentrevista.sesion.forma_respuesta IS 'Valores: AUDIO, TEXTO.';
COMMENT ON COLUMN microentrevista.sesion.idioma IS 'Valores: es-CO, en.';
COMMENT ON COLUMN microentrevista.pregunta.tipo IS 'Valores: APERTURA, TECNICA, CONDUCTUAL, SITUACIONAL.';
COMMENT ON COLUMN microentrevista.intento_respuesta.tipo_interaccion IS 'Valores: INICIAL.';
COMMENT ON COLUMN microentrevista.intento_respuesta.origen_respuesta IS 'Valores: TEXTO, AUDIO.';
COMMENT ON COLUMN microentrevista.ejecucion_ia.tipo_operacion IS 'Valores: PLANIFICAR, EVALUAR, TRANSCRIBIR.';
COMMENT ON COLUMN microentrevista.ejecucion_ia.estado IS 'Valores: EN_CURSO, EXITOSA, FALLIDA.';
