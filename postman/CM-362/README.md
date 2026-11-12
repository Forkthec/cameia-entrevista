# CM-362 · Probar el reintento de la transcripción (HU-5.13) con Postman

| Archivo | Para qué |
|---|---|
| `CM-362.postman_collection.json` | Colección v2.1: 26 peticiones con sus pruebas, en el orden de los criterios |
| `semilla-sesion-en-curso.sql` | Deja una entrevista `EN_CURSO` en modo audio, con 5 preguntas y el turno 1 pendiente, para `cm362-postman`. Evita depender de RabbitMQ y de Gemini |
| `voz-simulada/mappings/*.json` | Escenarios del Voz simulado (WireMock), que el compose monta con el perfil `voz-simulada` |
| `audios/*.webm` | «Audios» de texto: su contenido elige el escenario del Voz simulado |

## Escenarios del Voz simulado

| Archivo | Voz responde | Lo que debe pasar en Entrevista |
|---|---|---|
| `exito.webm` | 200 con texto | 200, respuesta registrada (1 llamada a Voz) |
| `falla-una-vez.webm` | 503 y, al segundo intento, 200 | 200, una sola respuesta (2 llamadas) — CA-5.13.1/2 |
| `falla-siempre.webm` | 503 siempre | 503 `TRANSCRIPTION_UNAVAILABLE` (2 llamadas) — CA-5.13.3 |
| `lento.webm` | 200 a los 65 s | 503 al agotar `VOZ_TIMEOUT`, sin reintento (1 llamada) — CA-5.13.3 |
| `sin-voz.webm` | 200 con texto vacío | 422 `TRANSCRIPTION_EMPTY` |
| `rechazo.webm` | 422 | 503, sin reintento (1 llamada) |

El escenario `falla-una-vez` alterna entre fallo y éxito; la colección lo reinicia con `POST {{vozUrl}}/__admin/scenarios/reset`. El número de llamadas a Voz se ve con `POST {{vozUrl}}/__admin/requests/count` y se vacía con `DELETE {{vozUrl}}/__admin/requests`.

## Pasos

1. `docker network create cameia-net` (una sola vez) y, en `.env`, `VOZ_TIMEOUT=5s` para que el caso lento tarde 5 s y no 60.
2. `docker compose --profile voz-simulada up --build -d`.
3. `docker exec -i cameia-entrevista-db psql -U postgres -d DB-entrevista -v ON_ERROR_STOP=1 < postman/CM-362/semilla-sesion-en-curso.sql` (repítelo antes de cada corrida).
4. Importa la colección; en *Settings → Working directory* elige `postman/CM-362` (o vuelve a escoger cada archivo de `audios/`) y córrela completa con el *Runner*.
5. Por consola: `npx newman run postman/CM-362/CM-362.postman_collection.json --working-dir postman/CM-362`.

## Casos manuales

- **CA-5.13.8 (envío durante los intentos).** Con la semilla recién cargada, en dos pestañas: envía `lento.webm` al turno 1 y, antes de que responda, `exito.webm` al mismo turno. La segunda responde al instante `409 TURN_PROCESSING`; Voz recibe una sola llamada.
- **Conexión rechazada.** `docker stop cameia-entrevista-voz-simulada`, envía cualquier audio: 503 `TRANSCRIPTION_UNAVAILABLE` tras dos intentos (en el log: `intento=2, causa=TRANSIENT`). Luego `docker start cameia-entrevista-voz-simulada`.
- **Una sola respuesta en la base.** `docker exec cameia-entrevista-db psql -U postgres -d DB-entrevista -c "SELECT turno_id, origen_respuesta, es_definitiva FROM microentrevista.intento_respuesta"`.
