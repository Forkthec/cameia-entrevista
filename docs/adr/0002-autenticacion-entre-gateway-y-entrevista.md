# ADR 0002 · Autenticación entre el Gateway y Entrevista

## Estado

Aceptada. Decisión de Product Owner, 8 de septiembre de 2026.

## Contexto

Entrevista procesa información sensible de la práctica: contexto profesional, prompts conversacionales y transcripciones. Solo debe aceptar peticiones que vengan del API Gateway, que es el punto de entrada público para la aplicación web.

## Decisión

Dos capas de autenticación.

**Capa 1: IAM con OIDC en Cloud Run (base obligatoria para todos los microservicios).**

- El servicio se despliega con `--no-allow-unauthenticated`.
- Solo la cuenta de servicio del Gateway tiene rol de invocador.
- El Gateway obtiene un token OIDC del servidor de metadatos de Cloud Run, sin llaves en el código, y lo agrega a cada petición en `Authorization: Bearer <token>`.
- Cloud Run valida el token antes de enrutar la petición al servicio.

**Capa 2: VPC e ingreso interno (aplica a Entrevista por la sensibilidad de sus datos).**

- Se agrega encima de la capa 1.
- El servicio se despliega con `ingress: internal`, sin ruta pública desde internet.
- El Gateway llega por un conector privado dentro de la VPC y sigue siendo público.

## Consecuencias

- El impacto en el código es mínimo: la validación de origen la hace Cloud Run y el servicio solo recibe peticiones ya autenticadas.
- Es configuración de infraestructura, a cargo de DevOps.
- La primera spec de un endpoint que dependa de esta frontera referencia este ADR.
