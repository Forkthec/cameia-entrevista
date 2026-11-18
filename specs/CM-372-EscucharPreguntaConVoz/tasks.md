# Tarjetas · CM-372 Escuchar la pregunta con voz

- [x] T1 · Dominio: modelo del audio, regla de la pregunta, puertos, excepciones y códigos.
- [x] T2 · Persistencia: migración `V10` y adaptador de contadores con sentencias condicionadas.
- [x] T3 · Adaptador de Google Cloud Text-to-Speech (Chirp 3 HD, MP3) con credencial ADC o llave; configuración `cameia.speech.*`.
- [x] T4 · Caso de uso `ListenQuestionService`, controlador y voz en el estado de la sesión.
- [x] T5 · Pruebas: unitarias, adaptadores contra servidor simulado, punta a punta con PostgreSQL y prueba real manual (pendiente de credencial).
- [x] T6 · Postman: carpeta «Escuchar la pregunta» y voz en el simulador; Newman de toda la colección en 0 fallos.
- [x] T7 · ADR 0017, `docs/errores.md`, `CLAUDE.md`, `.env.example` y bitácora.
- [x] T8 · `./mvnw.cmd clean verify` en verde; cobertura de lo nuevo ≥ 90 %.
