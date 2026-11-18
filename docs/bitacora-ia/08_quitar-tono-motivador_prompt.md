# Entrada 08 · Quitar el tono Motivador

- **Fecha:** 2026-11-07 · **Herramienta:** Claude Code (Claude Sonnet 5.5) · **Responsable:** Paula Andrea Muñoz Delgado
- **Salida:** [Spec de CM-352](../../specs/CM-352-QuitarTonoMotivador/spec.md), sección 18 (decisiones D-0 a D-4)

## Prompt

```
Rol: Backend de CAMEIA, dueño del servicio de Entrevista.

Contexto: el catálogo aprobado tiene tres tonos del entrevistador (Profesional y neutro, Cálido y Estricto); el tono
Motivador sale del producto. El servicio ya guarda sesiones con ese tono en una columna con restricción CHECK, el enumerado
lo conoce y el catálogo lo ofrece. Crear la sesión guarda, bajo la clave anti-duplicados, la respuesta serializada con el
nombre del tono. El servicio corre en Cloud Run: la revisión nueva migra al arrancar mientras la anterior aún atiende.

Tarea: decidir qué se hace con las sesiones y las respuestas guardadas que ya tienen el tono retirado, cómo se cambia la
restricción de la base, qué responde el servicio a quien aún envía el tono y cómo se despliega sin romper a Frontend;
presentar cada punto con opciones, recomendación y consecuencias.

Condiciones: ningún error previsible termina en el 500 genérico; la migración es una sola transacción y se prueba contra una
base con datos; repetir una clave antigua nunca falla al leer la respuesta guardada; el cambio de contrato se avisa a
Frontend; ningún valor propuesto pasa a la spec sin respuesta.

Formato: tabla de decisiones en la spec, con lo que pasa si la persona elige otra opción.
```

## Decisiones con peso humano

- Las sesiones guardadas con Motivador pasan a Profesional y neutro, sin importar su estado (Paula, 2-nov-2026). La migración no tiene reversa: el tono original se pierde.
- Se acepta la ventana de despliegue en la que la revisión anterior puede recibir un Motivador antes de que la migración termine; un solo PR y un solo despliegue, en vez de dos pasos (se recomendó y Paula lo ratificó el 7-nov-2026).
