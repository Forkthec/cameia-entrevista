# Plan · CM-377 Voz mínima transcrita

## Archivos

| Archivo | Cambio |
|---|---|
| `domain/model/Transcription.java` | Campo nuevo `speechDuration` (nulo si el proveedor no informa las palabras); el constructor de tres argumentos se conserva |
| `domain/policy/TranscriptionPolicy.java` | `MIN_SPEECH = 1500 ms`; regla nueva entre «vacía» e «idioma» |
| `domain/exception/AudioTooShortException.java` | Javadoc: ahora cubre también la voz reconocida |
| `infrastructure/client/assemblyai/AssemblyAiTranscriptionAdapter.java` | `speechDuration(words)` y `vozMs` en el registro de la transcripción completada (sin texto) |
| `presentation/controller/AudioAnswerController.java` | Descripción del 422 en OpenAPI |
| `docs/errores.md`, `CLAUDE.md` | Fila de `AUDIO_TOO_SHORT` y párrafo de la respuesta por voz |
| Pruebas | `TranscriptionPolicyTest`, `AssemblyAiTranscriptionAdapterTest`, `AudioAnswerEndToEndTest` |

## Riesgos

- Si AssemblyAI dejara de devolver `words`, la regla no aplica (D5) y el comportamiento vuelve al de hoy, sin romper nada.
- La suma de palabras subestima un poco la voz frente al detector del navegador; por eso el umbral del servidor (1,5 s) es menor
  que el de la interfaz (2 s).
