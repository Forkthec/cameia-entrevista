# Ambigüedades y decisiones pendientes

Este documento es el registro de decisiones que pueden bloquear diseño o implementación. Una entrada deja de bloquear cuando tiene una respuesta explícita, responsable, fecha y evidencia del cambio aplicado.

## Cómo usarlo

- El agente debe revisar este archivo antes de editar.
- Las ambigüedades críticas bloquean cambios en la frontera o el dominio afectado.
- Cada resolución debe actualizar la pregunta, la decisión, el impacto y la spec relacionada.
- No borrar decisiones históricas; marcar su estado como `Resuelta` o `Reemplazada`.

## Registro

### A-001: Autenticación entre API Gateway y Entrevista

- Estado: `Resuelta`
- Responsable: Juan Vela
- Fecha: 2026-09-08
- Tema: seguridad y entrada de peticiones
- Decisión adoptada: **Dos capas de autenticación**

  **Capa 1 (IAM + OIDC en Cloud Run)** — Base obligatoria para todos los microservicios:
  - El microservicio se despliega con `--no-allow-unauthenticated`.
  - Solo la cuenta de servicio del API Gateway tiene rol de invocador (binding de IAM).
  - El Gateway obtiene un token OIDC del metadata server de Cloud Run (sin llaves en el código) y lo agrega a cada petición en `Authorization: Bearer <token>`.
  - Cloud Run valida el token automáticamente antes de enrutar al microservicio.
  - No requiere cambios en la lógica de negocio; es configuración de infraestructura.

  **Capa 2 (VPC e ingress interno)** — Aplica también a Entrevista, dado que procesa información sensible de la sesión de práctica (contexto profesional, prompts conversacionales, transcripciones):
  - Se agrega encima de la Capa 1.
  - El microservicio se despliega con `ingress: internal`, sin ruta pública desde internet.
  - El Gateway accede por un conector privado dentro de la VPC.
  - El Gateway sigue siendo público (acceso desde React).

- Impacto en código: mínimo. La validación de origen la hace Cloud Run; Entrevista solo recibe peticiones ya autenticadas.
- Especificación afectada: ninguna todavía; no hay endpoints implementados. Cuando se cree el primer endpoint que dependa de esta frontera, su spec en `specs/` debe referenciar esta decisión (A-001).

### A-002: Naturaleza del "perfil de debilidades"

- Estado: `Resuelta`
- Responsable: Juan Vela
- Fecha: 2026-09-08
- Tema: alcance y modos de sesión
- Decisión adoptada: El **perfil de debilidades es un reporte/output derivado** de entrevistas ya realizadas (modo entreno o simulación), **no es un tercer modo de sesión**. Su mecánica (qué datos lo alimentan, cuándo se genera, cómo se expone) se definirá en una spec futura cuando esta capacidad entre en alcance.
- Impacto en código: ninguno todavía. No crear un tercer modo de sesión ni lógica de generación de este perfil sin spec aprobada.
- Especificación afectada: pendiente de creación cuando esta capacidad entre en alcance.

### A-003: Máquina de estados de la sesión de entrevista

- Estado: `Pendiente`
- Tema: dominio y arquitectura
- Pregunta: ¿Cuáles son los estados, las transiciones válidas e inválidas, y las reglas específicas por modo (entreno/simulación) de la máquina de estados de sesión?
- Decisión: Se definirá en un plan técnico o spec futura antes de implementarse. No inventar estados, transiciones ni reglas de modo sin esa spec aprobada.
- Especificación: pendiente de creación.

## Decisiones confirmadas

- Idioma operativo de este documento: español.
- Payload mínimo del gateway: `firebase_uid`, `email`, `roles` y `request_id` obligatorios; `display_name`, `correlation_id` e `issued_at` opcionales.
- Este servicio no custodia contraseñas ni credenciales de autenticación.
- La documentación Spec-Driven se organiza bajo `specs/` (en la raíz del repositorio).
- El límite de 1000 líneas se mide sobre el diff total agregado y eliminado por solicitud.
- **Seguridad resuelta (A-001):** IAM + OIDC de Cloud Run como base; VPC + ingress interno también aplica a Entrevista.
- **Perfil de debilidades (A-002):** reporte derivado de entrevistas realizadas, no un modo de sesión.
- **Máquina de estados (A-003):** pendiente de spec; no implementar sin definición aprobada.
- **Arquitectura confirmada:** Estilo DDD con `domain`, `application`, `infrastructure`, `presentation`. Las reglas de dependencia se verificarán con la prueba ArchUnit `LayeredArchitectureTest` (`tech.cameia.entrevista.architecture`) cuando se implemente.
- **Paquete base:** `tech.cameia.entrevista` (groupId de Maven: `tech.cameia`).
