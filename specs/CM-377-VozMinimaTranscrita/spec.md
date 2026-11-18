# CM-377 · Rechazar la respuesta por voz con menos de 1,5 s de voz reconocida

| Campo | Valor |
|---|---|
| Tarea Jira | CM-377 «El servidor acepta como respuesta un audio con casi nada de voz si el archivo dura 1,5 s o más». Tipo Error, Sprint 5, severidad propuesta Baja. Relacionada con CM-309 (HU-5.8) y CM-345 |
| Historia | HU-5.8, criterio CA-5.8.10 (backlog v11.6, hoja HE-05) |
| Repositorio | `cameia-entrevista`. Base `origin/develop` en `25d0ba4` |
| Rama | `CM-377-voz-minima-transcrita`. Un PR hacia `develop`; no depende de ninguna CM sin fusionar |

## 1. Problema

`AudioAnswerPolicy` compara la duración **del archivo** con 1,5 s antes de transcribir. Un audio de 1,53 s en el que la persona
solo dijo «Sí.» pasa ese control, se transcribe, se registra como respuesta y la entrevista avanza (staging, 15-11-2026,
entrevista 49d1ba41). La app no lo permite porque el navegador exige 2 s de voz, pero un envío directo al endpoint sí.

## 2. Comportamiento esperado (CA-5.8.10)

Si llegan menos de 1,5 s de **voz**, medidos con la suma de la duración de las palabras que devuelve AssemblyAI, el servidor
responde 422 sin registrar la respuesta y deja la pregunta pendiente.

## 3. Decisiones

| # | Decisión | Por qué |
|---|---|---|
| D1 | La voz se mide como `Σ max(0, end − start)` de `words` de la transcripción de AssemblyAI, en milisegundos, sin los silencios entre palabras | Es lo que pide el criterio. Las marcas vienen en la misma respuesta que ya se consulta: no hay otra llamada |
| D2 | El código es el mismo `AUDIO_TOO_SHORT` (422) y el mismo mensaje «No detectamos tu voz, intenta de nuevo.» | El Cliente Web ya lo maneja; para la persona es el mismo caso. No hay código nuevo en el catálogo |
| D3 | El control previo por duración del archivo (< 1,5 s, sin transcribir) se mantiene | Ahorra la llamada a AssemblyAI en el caso evidente |
| D4 | Orden en `TranscriptionPolicy`: vacía → poca voz → idioma → largo | Un texto vacío sigue siendo `TRANSCRIPTION_EMPTY`; un «Yes.» corto en una entrevista en español es más útil como «no detectamos tu voz» |
| D5 | Si la respuesta del proveedor no trae `words`, no se rechaza por voz | Igual que el idioma sin confianza: no se puede afirmar. La duración del archivo ya se comprobó |
| D6 | Exactamente 1.500 ms de voz se aceptan; 1.499 ms se rechazan | «Menos de 1,5 s» |
| D7 | La transcripción ya se hizo, así que su ejecución `TRANSCRIBIR` queda registrada como exitosa; el turno se libera a `PENDING` como en los demás rechazos de la transcripción | Mismo camino que `TRANSCRIPTION_EMPTY` |

## 4. Fuera de alcance

- Cambiar el umbral de la interfaz (2 s).
- Medir la voz en el servidor sin el proveedor (detector de actividad de voz propio).
- Cambios de esquema, de contrato JSON o de colección de Postman: el código y el estado ya existían.

## 5. Pruebas

| Caso | Prueba |
|---|---|
| 1.499 ms de voz con texto → `AudioTooShortException` | `TranscriptionPolicyTest.accept_shouldRejectAsTooShort_whenSpeechIsUnderMinimum` |
| 1.500 ms → se acepta | `TranscriptionPolicyTest.accept_shouldAccept_whenSpeechIsExactlyMinimum` |
| Sin `words` → se acepta | `TranscriptionPolicyTest.accept_shouldAccept_whenSpeechIsNotReported` |
| Orden vacía → voz → idioma | `accept_shouldRejectAsEmptyBeforeSpeech`, `accept_shouldRejectAsTooShortBeforeLanguage` |
| Suma de palabras sin silencios; marcas inválidas no suman; sin `words` es `null` | `AssemblyAiTranscriptionAdapterTest` (3 pruebas nuevas) |
| Punta a punta: 422 `AUDIO_TOO_SHORT`, una transcripción, cero respuestas y turno `PENDING`; 1.500 ms registra | `AudioAnswerEndToEndTest.submit_shouldRejectTranscriptionWithLittleSpeech`, `submit_shouldAcceptExactlyMinimumSpeech` |
