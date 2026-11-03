-- Copia local del Perfil Profesional y registro de mensajes ya aplicados.
--
-- Entrevista no llama a Perfil para crear una sesion: guarda su propia copia del perfil, que
-- mantiene el consumidor del evento perfil.profesional-actualizado. La copia conserva el perfil
-- completo en dos columnas jsonb porque Entrevista solo lo lee entero (lo copia a la sesion y
-- arma el prompt) y nunca filtra por sus datos internos.
--
-- perfil_id es de Perfil y no tiene clave foranea: ningun servicio referencia las tablas de otro.
-- Los largos de firebase_uid y nombre repiten los del dominio y los del contrato del evento. Los
-- instantes son timestamptz (UTC).

CREATE TABLE microentrevista.contexto_profesional_replica (
    perfil_id             uuid          NOT NULL,
    firebase_uid          varchar(128)  NOT NULL,
    nombre                varchar(255)  NOT NULL,
    estado                varchar(20)   NOT NULL,
    fecha_archivado       timestamptz   NULL,
    contexto_profesional  jsonb         NOT NULL,
    roles_aspirados       jsonb         NOT NULL,
    actualizado_en        timestamptz   NOT NULL,
    fecha_replicacion     timestamptz   NOT NULL,
    CONSTRAINT pk_contexto_profesional_replica PRIMARY KEY (perfil_id),
    CONSTRAINT ck_contexto_profesional_replica_firebase_uid CHECK (char_length(btrim(firebase_uid)) > 0),
    CONSTRAINT ck_contexto_profesional_replica_nombre CHECK (char_length(btrim(nombre)) > 0),
    CONSTRAINT ck_contexto_profesional_replica_estado
        CHECK (estado IN ('PENDING', 'IN_PROGRESS', 'IN_REVIEW', 'COMPLETED')),
    CONSTRAINT ck_contexto_profesional_replica_contexto CHECK (jsonb_typeof(contexto_profesional) = 'object'),
    CONSTRAINT ck_contexto_profesional_replica_roles
        CHECK (CASE WHEN jsonb_typeof(roles_aspirados) = 'array'
                    THEN jsonb_array_length(roles_aspirados) BETWEEN 1 AND 5
                    ELSE false END)
);

-- Inbox: un mensaje ya aplicado no vuelve a tener efecto cuando el broker lo entrega otra vez.
CREATE TABLE microentrevista.evento_procesado (
    message_id    uuid         NOT NULL,
    tipo          varchar(40)  NOT NULL,
    procesado_en  timestamptz  NOT NULL,
    CONSTRAINT pk_evento_procesado PRIMARY KEY (message_id),
    CONSTRAINT ck_evento_procesado_tipo CHECK (tipo IN ('perfil.profesional-actualizado'))
);

COMMENT ON TABLE microentrevista.contexto_profesional_replica IS 'Copia del Perfil Profesional que mantiene el consumidor de perfil.profesional-actualizado. Permite crear la sesion sin llamar a Perfil. Datos personales: solo lo que la entrevista usa.';
COMMENT ON COLUMN microentrevista.contexto_profesional_replica.estado IS 'Valores: PENDING, IN_PROGRESS, IN_REVIEW, COMPLETED. Solo COMPLETED sin fecha_archivado sirve para una entrevista.';
COMMENT ON COLUMN microentrevista.contexto_profesional_replica.contexto_profesional IS 'Bloque contextoProfesional del evento v1 (resumen, experiencias, formaciones, habilidades), sin campos desconocidos.';
COMMENT ON COLUMN microentrevista.contexto_profesional_replica.roles_aspirados IS 'Lista rolesAspirados del evento v1 (codigo UUID del catalogo de Perfil y nombre).';
COMMENT ON COLUMN microentrevista.contexto_profesional_replica.actualizado_en IS 'actualizadoEn del ultimo evento aplicado; un evento anterior se descarta.';
COMMENT ON TABLE microentrevista.evento_procesado IS 'Inbox: identificadores de mensajes ya aplicados, para que una reentrega no tenga efecto. Sin datos personales.';
