-- Copia local del catalogo de roles profesionales.
--
-- El rol objetivo de una sesion se valida contra los roles activos del catalogo, que vive en
-- Perfil. Entrevista no llama a Perfil: guarda su propia copia, que mantiene el consumidor del
-- evento perfil.catalogo-roles-actualizado. Un rol nuevo se crea solo en Perfil y llega por el
-- evento; no exige una migracion aqui. Perfil nunca borra un rol, lo desactiva, y por eso el
-- consumidor tampoco borra los roles que una instantanea no trae.
--
-- id es de Perfil y no tiene clave foranea: ningun servicio referencia las tablas de otro. Los
-- largos de nombre_es y nombre_en repiten los del contrato del evento. Los instantes son
-- timestamptz (UTC).

CREATE TABLE microentrevista.rol_profesional (
    id                 uuid          NOT NULL,
    nombre_es          varchar(255)  NOT NULL,
    nombre_en          varchar(255)  NOT NULL,
    estado             varchar(8)    NOT NULL DEFAULT 'ACTIVE',
    actualizado_en     timestamptz   NOT NULL,
    fecha_replicacion  timestamptz   NOT NULL,
    CONSTRAINT pk_rol_profesional PRIMARY KEY (id),
    CONSTRAINT ck_rol_profesional_nombre_es CHECK (char_length(btrim(nombre_es)) > 0),
    CONSTRAINT ck_rol_profesional_nombre_en CHECK (char_length(btrim(nombre_en)) > 0),
    CONSTRAINT ck_rol_profesional_estado CHECK (estado IN ('ACTIVE', 'INACTIVE'))
);

-- El Inbox registra ahora los mensajes de los dos eventos de Perfil.
ALTER TABLE microentrevista.evento_procesado
    DROP CONSTRAINT ck_evento_procesado_tipo,
    ADD CONSTRAINT ck_evento_procesado_tipo
        CHECK (tipo IN ('perfil.profesional-actualizado', 'perfil.catalogo-roles-actualizado'));

COMMENT ON TABLE microentrevista.rol_profesional IS 'Copia del catalogo de roles profesionales que mantiene el consumidor de perfil.catalogo-roles-actualizado. Permite validar el rol objetivo de una sesion sin llamar a Perfil. Sin datos personales.';
COMMENT ON COLUMN microentrevista.rol_profesional.estado IS 'Valores: ACTIVE, INACTIVE. Solo un rol ACTIVE puede ser el rol objetivo de una sesion nueva.';
COMMENT ON COLUMN microentrevista.rol_profesional.actualizado_en IS 'actualizadoEn de la ultima instantanea aplicada a esta fila; una instantanea anterior no la modifica.';
