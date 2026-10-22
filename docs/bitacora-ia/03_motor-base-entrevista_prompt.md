# Entrada 03 · Motor base de la entrevista

- **Fecha:** 2026-10-17 al 2026-10-22 · **Herramienta:** Claude Code (Claude Sonnet 5.5) · **Responsable:** Paula Andrea Muñoz Delgado
- **Salida:** [Spec de CM-311](../../specs/CM-311-MotorBaseEntrevista/spec.md), sección 15 (decisiones)

## Prompt

```
Rol: Backend de CAMEIA, dueño del servicio de Entrevista.

Contexto: la tarea del motor base de la entrevista pide el esquema inicial de la base, los estados de la sesión y del turno con sus
transiciones, el manejador de errores completo y la identidad que llega del Gateway. El servicio solo tiene el endpoint de salud y el
cliente del modelo de lenguaje (CM-312).

Tarea: proponer el esquema, las máquinas de estados y los errores, y señalar cada punto donde la spec, el backlog y el código no
coincidan, con opciones y una recomendación.

Condiciones: no asumir nada que no esté decidido; preguntar los valores, los textos y los códigos; cada restricción de la base repite
una regla del código; todo error previsible tiene su código.

Formato: spec con requisitos en EARS, tabla de decisiones y lista de verificación por requisito.
```

## Decisiones con peso humano

- La base admite la modalidad `ENTRENO` aunque la recomendación era admitir solo `SIMULACION`; el código la rechaza hasta la historia de configuración.
- El límite de la pregunta (1000 caracteres) queda provisional hasta el estudio de la historia de preguntas.
- El campo `idioma_detectado` se valida de forma exacta cuando exista el contrato de voz.
- Se conserva el perfil `production`; renombrarlo le toca a DevOps.
- Los errores que el contenedor resuelve por su cuenta salen en el formato común: un `multipart/form-data` ilegible responde 415, una ruta con codificación inválida responde 400 y `GET /error` responde 404, cada uno con su código estable (decisiones P20 a P22 de la spec).
- Los identificadores de Java del manejador de errores pasan a inglés, también los que dejó CM-312, aunque Cuentas conserva los nombres en español (decisión P23).
