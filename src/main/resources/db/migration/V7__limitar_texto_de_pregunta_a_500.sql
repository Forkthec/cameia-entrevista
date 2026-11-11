-- El texto de una pregunta mide hasta 500 caracteres (maximo observado al planificar: 362, con margen).
-- El esquema repite el limite que valida el dominio. Es segura porque ninguna fila de pregunta existe todavia con
-- mas de 500 caracteres: hasta ahora ningun codigo escribia en esta tabla.

ALTER TABLE microentrevista.pregunta DROP CONSTRAINT ck_pregunta_texto_original;
ALTER TABLE microentrevista.pregunta ALTER COLUMN texto_original TYPE varchar(500);
ALTER TABLE microentrevista.pregunta ADD CONSTRAINT ck_pregunta_texto_original
    CHECK (char_length(btrim(texto_original)) BETWEEN 1 AND 500);
