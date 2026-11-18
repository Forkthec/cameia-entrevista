# Entrada 12 · Cierre de la sesión y consolidación

- **Fecha:** 2026-11-07 · **Herramienta:** Claude Code (Claude Sonnet 5.5) · **Responsable:** Paula Andrea Muñoz Delgado
- **Salida:** [Spec de CM-220](../../specs/CM-220-CerrarSesion/spec.md), sección 16 (decisiones D-220-1 a D-220-4)

## Prompt

```
Rol: Backend de CAMEIA, dueño del servicio de Entrevista.

Contexto: al responder la quinta pregunta la entrevista termina y la sesión debe quedar finalizada con su fecha de fin,
reuniendo las cinco evaluaciones ya guardadas. El cierre no llama a la IA pero puede fallar (la base cae un instante, dos
peticiones cierran a la vez). Cloud Run limita la CPU cuando no hay una petición en curso. El criterio pide un 503 si el
cierre falla, sin decir qué código lleva, ni cuánto se espera entre intentos, ni quién ejecuta el cierre.

Tarea: proponer opciones con recomendación y consecuencias para: quién ejecuta la consolidación; qué código de error
devuelve cuando falla; y cuánto se espera entre los tres intentos.

Condiciones: ningún error previsible termina en el 500 genérico; sin transacción abierta mientras se espera; la respuesta
de la persona nunca se pierde; ninguna respuesta incluye la retroalimentación; cada cambio con su prueba, OpenAPI y Postman.

Formato: tabla de decisiones en la spec, con las alternativas descartadas.
```

## Decisiones con peso humano

- La consolidación se ejecuta en la misma petición de la quinta respuesta, justo después de confirmar la transición, con hasta tres intentos en transacciones propias; no en segundo plano ni con un proceso periódico (Paula, 7-nov-2026).
- Cada estado de la sesión tiene su código en `answers`: `SESSION_ABANDONED`, `SESSION_ALREADY_CLOSED` y `SESSION_TRANSITION_NOT_ALLOWED` (Paula, 7-nov-2026).
- El cierre fallido se llama `SESSION_CONSOLIDATION_FAILED` (Paula, 7-nov-2026).
- Las esperas entre intentos son de 200 ms y 400 ms (Paula, 7-nov-2026).
