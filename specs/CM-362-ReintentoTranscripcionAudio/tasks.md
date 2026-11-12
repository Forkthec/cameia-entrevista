# CM-362 · Tareas

- [x] T1 · Dominio: liberar el turno (`PROCESSING/RETRYING → PENDING`), reserva vigente, actividad de la sesión.
- [x] T2 · Dominio: audio, transcripción, clase de fallo, respuesta registrada, política de reintento, puertos y excepciones con sus códigos.
- [x] T3 · Infraestructura: adaptador HTTP de Voz y su configuración validada.
- [x] T4 · Persistencia de `intento_respuesta` (sin migración).
- [x] T5 · Caso de uso con reserva, reintento, registro y liberación.
- [x] T6 · Endpoint `POST …/answers/audio`, respuesta común, OpenAPI y multipart en memoria.
- [x] T7 · `docs/errores.md`, `CLAUDE.md`, ADR 0012 y bitácora.
- [x] T8 · Pruebas de unidad, del adaptador y de punta a punta; `./mvnw.cmd clean verify` en verde.
- [x] T9 · Compose local con un Voz simulado (WireMock) y guía de prueba en Postman.
- [ ] T10 · Revisión humana de las decisiones D-362-1 a D-362-10.
