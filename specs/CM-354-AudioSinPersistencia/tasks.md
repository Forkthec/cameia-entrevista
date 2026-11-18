# Tarjetas · CM-354 Audio de la respuesta sin persistencia

Reglas que aplican a todas:

- Repo `cameia-entrevista`, rama `CM-354-audio-sin-persistencia` desde `origin/develop` (`0470493` o posterior).
- Identificadores y nombres de métodos en inglés; `@DisplayName`, Javadoc y comentarios en español, sin `CM-NNN` ni rutas a otros archivos.
- Arrange-Act-Assert, sin `Thread.sleep`.
- **Prohibido:** tocar `src/main/**`, agregar dependencias, cambiar contratos o migraciones, registrar el audio o el texto.
- **Detenerse y reportar si:** una firma o un ayudante no existe como se describe, una prueba existente se rompe sin causa clara o hace falta algo que no está listado.
- Comando por tarjeta: `./mvnw.cmd -B test "-Dtest=<Clase>"`. Docker debe estar encendido.

---

## T0 · Línea base

- `git fetch`, `git status` limpio y la rama creada.
- `./mvnw.cmd -B clean verify`. Debe dar `BUILD SUCCESS`.
- Guardar el log en `ejecucion/evidencia/CM-354/00_linea-base.log` y anotar las cifras de `SubmitAudioAnswerService` y `AudioAnswerController` de `target/site/jacoco/jacoco.csv`.

## T1 · Guardia del umbral y directorio de multipart propio (REQ-HT06-01, REQ-HT06-05)

**Archivo:** `src/test/java/tech/cameia/entrevista/presentation/AudioAnswerEndToEndTest.java`.

**Agregar:**
- Importaciones: `java.nio.file.Files`, `java.nio.file.Path`, `java.io.UncheckedIOException`, `jakarta.servlet.MultipartConfigElement`, `org.springframework.test.context.DynamicPropertyRegistry`, `org.springframework.test.context.DynamicPropertySource`.
- Los campos y el método, debajo del contenedor `postgres`:

```java
    /** Directorio propio para las partes del multipart: si el contenedor escribiera el audio, quedaría aquí. */
    private static final Path MULTIPART_DIR = createMultipartDir();

    @Autowired
    private MultipartConfigElement multipartConfig;

    @DynamicPropertySource
    static void multipartLocation(DynamicPropertyRegistry registry) {
        registry.add("spring.servlet.multipart.location", MULTIPART_DIR::toString);
    }

    private static Path createMultipartDir() {
        try {
            return Files.createTempDirectory("cameia-multipart-");
        } catch (IOException cannotCreate) {
            throw new UncheckedIOException(cannotCreate);
        }
    }
```

- Ayudante:

```java
    /** Cuenta los archivos que el contenedor dejó en el directorio de partes. */
    private static long filesInMultipartDir() {
        try (var files = Files.list(MULTIPART_DIR)) {
            return files.count();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
```

- Prueba:

```java
    @Test
    @DisplayName("CA-5.8.18: el umbral de escritura en disco supera el máximo, así que las partes viven solo en memoria")
    void submit_shouldKeepMultipartPartsInMemory() {
        assertThat(multipartConfig.getMaxFileSize()).isEqualTo(12L * 1024 * 1024);
        assertThat((long) multipartConfig.getFileSizeThreshold()).isGreaterThan(multipartConfig.getMaxFileSize());
        assertThat(multipartConfig.getLocation()).isEqualTo(MULTIPART_DIR.toString());
    }
```

**Trampas:**
- `IOException` ya está importada (la usa `audio(...)`): no la importes dos veces.
- Si `MultipartConfigElement` no se puede inyectar (bean ausente), detente y repórtalo; no lo crees a mano.
- **Ensayo obligatorio (V3):** cambia a mano `spring.servlet.multipart.file-size-threshold=13MB` a `0` en `src/main/resources/application.properties` y corre la prueba. Debe **fallar**. Revierte con `git checkout -- src/main/resources/application.properties`, comprueba con `git diff` que no queda cambio en `src/main` y vuelve a correrla en verde. Pega las dos salidas en `tasks.md`.

**Terminado:** `./mvnw.cmd -B test "-Dtest=AudioAnswerEndToEndTest"` en verde, con el ensayo hecho.

## T2 · Ningún audio en disco, log ni memoria tras respuestas válidas y fallidas (REQ-HT06-01 a 04)

### T2a · Prueba de punta a punta

**Archivo:** el mismo de T1. Importaciones: `java.util.Base64`, `java.util.HexFormat`, `java.util.Arrays`, `org.springframework.boot.test.system.CapturedOutput` (ya está), `java.nio.charset.StandardCharsets` si hace falta.

```java
    private static final String HOSTILE_NAME = "../../respuesta final.webm";

    /**
     * Envía un caso de la lista y comprueba su estado y código; devuelve los bytes enviados para buscarlos en el log.
     */
    private byte[] sendCase(String uid, String sessionId, String turnId, byte[] audio, int status, String code) {
        ResponseEntity<String> response = sendAudio(uid, sessionId, turnId, audio, HOSTILE_NAME, "audio/webm", null);
        assertThat(response.getStatusCode().value()).as(code).isEqualTo(status);
        if (code != null) {
            assertThat(code(response)).isEqualTo(code);
        }
        return audio;
    }

    @Test
    @DisplayName("HT-06: tras respuestas válidas y fallidas no queda audio en disco, en la base ni en el log")
    void submit_shouldLeaveNoAudio_whenAnswersSucceedOrFail(CapturedOutput output) {
        List<byte[]> sent = new ArrayList<>();
        // Válido
        String[] ok = startInterview(UID_A, PROFILE_A, "AUDIO");
        sent.add(sendCase(UID_A, ok[0], ok[1], audio("tono-3s.webm"), 200, null));
        // Rechazos antes de transcribir
        String[] s = startInterview(UID_A, PROFILE_A, "AUDIO");
        sent.add(sendCase(UID_A, s[0], s[1], paddedWebm(10_485_761), 413, "AUDIO_TOO_LARGE"));
        sent.add(sendCase(UID_A, s[0], s[1], audio("tono-3s.mp3"), 415, "AUDIO_INVALID_FORMAT"));
        sent.add(sendCase(UID_A, s[0], s[1], audio("no-es-audio.webm"), 415, "AUDIO_INVALID_FORMAT"));
        sent.add(sendCase(UID_A, s[0], s[1], audio("tono-1s.webm"), 422, "AUDIO_TOO_SHORT"));
        // Fallos al transcribir
        transcriber.enqueue(() -> "   ");
        sent.add(sendCase(UID_A, s[0], s[1], audio("tono-3s.webm"), 422, "TRANSCRIPTION_EMPTY"));
        transcriber.enqueue(() -> {
            throw transient503();
        });
        transcriber.enqueue(() -> {
            throw transient503();
        });
        sent.add(sendCase(UID_A, s[0], s[1], audio("tono-3s.webm"), 503, "TRANSCRIPTION_UNAVAILABLE"));
        byte[] seenOnFailure = transcriber.lastAudio();
        // Ajena
        sent.add(sendCase(UID_B, s[0], s[1], audio("tono-3s.webm"), 404, "SESSION_NOT_FOUND"));
        // Entrevista por texto
        String[] text = startInterview(UID_B, PROFILE_B, "TEXTO");
        sent.add(sendCase(UID_B, text[0], text[1], audio("tono-3s.webm"), 422, "AUDIO_NOT_ALLOWED"));

        assertThat(filesInMultipartDir()).isZero();
        assertThat(seenOnFailure).containsOnly((byte) 0);
        assertThat(count("SELECT count(*) FROM information_schema.columns WHERE table_schema = 'microentrevista'"
                + " AND data_type IN ('bytea', 'oid')")).isZero();
        assertThat(turnStatus(s[1])).isEqualTo("PENDING");
        String log = output.getAll();
        assertThat(log).doesNotContain("respuesta final").doesNotContain("../../").doesNotContain(TRANSCRIPT);
        for (byte[] audio : sent) {
            byte[] head = Arrays.copyOf(audio, Math.min(48, audio.length));
            assertThat(log).doesNotContain(Base64.getEncoder().encodeToString(head))
                    .doesNotContain(HexFormat.of().formatHex(head));
        }
    }
```

**Trampas:**
- `transient503()` es un método `private static` que ya existe en la sección de HU-5.13 de la misma clase; no lo dupliques.
- `List` y `ArrayList` ya se importan (los usa `ScriptedTranscriber`); compruébalo antes.
- Copia el arreglo **antes** de enviarlo si la comparación del log falla por bytes en cero: el `finally` del servicio sobrescribe el arreglo que recibe el controlador, no el del cliente de prueba (`ByteArrayResource` envía una copia por la red). Así que `sent` conserva los bytes originales; no hace falta copiar.
- Si el caso «Ajena» responde otro código distinto de `SESSION_NOT_FOUND`, revisa `submit_shouldReturn404ForForeignOrMissingSession` y usa el mismo código que esa prueba afirma. Si son distintos, detente y repórtalo.
- Si `startInterview(UID_B, PROFILE_B, "TEXTO")` falla con 409 porque `UID_B` ya tiene sesión, detente y repórtalo; no cambies el orden de los usuarios sin decirlo.

### T2b · Unitarias de bytes en cero en los rechazos previos a la transcripción

**Archivo:** `src/test/java/tech/cameia/entrevista/application/service/SubmitAudioAnswerServiceTest.java`, debajo de `submit_shouldWipeAudioBytes_whenRequestFails`.

```java
    @Test
    void submit_shouldWipeAudioBytes_whenAudioIsTooLarge() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(1);
        byte[] audio = new byte[10_485_761];
        Arrays.fill(audio, (byte) 7);

        assertThatThrownBy(() -> service.submit(command(turnId, audio, null)))
                .isInstanceOf(AudioTooLargeException.class);
        assertThat(audio).containsOnly((byte) 0);
    }

    @Test
    void submit_shouldWipeAudioBytes_whenContentIsNotAudio() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(1);
        when(inspector.inspect(any())).thenReturn(Optional.empty());
        byte[] audio = audio();

        assertThatThrownBy(() -> service.submit(command(turnId, audio, null)))
                .isInstanceOf(AudioInvalidFormatException.class);
        assertThat(audio).containsOnly((byte) 0);
    }

    @Test
    void submit_shouldWipeAudioBytes_whenInterviewIsByText() {
        sessionWith(AnswerMode.TEXT, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.IN_PROGRESS);
        UUID turnId = openTurnAt(1);
        byte[] audio = audio();

        assertThatThrownBy(() -> service.submit(command(turnId, audio, null)))
                .isInstanceOf(AudioNotAllowedException.class);
        assertThat(audio).containsOnly((byte) 0);
    }

    @Test
    void submit_shouldWipeAudioBytes_whenSessionIsAbandoned() {
        sessionWith(AnswerMode.AUDIO, InterviewLanguage.SPANISH_COLOMBIA, SessionStatus.ABANDONED);
        byte[] audio = audio();

        assertThatThrownBy(() -> service.submit(command(UUID.randomUUID(), audio, null)))
                .isInstanceOf(SessionAbandonedException.class);
        assertThat(audio).containsOnly((byte) 0);
    }
```

**Trampas:**
- Comprueba el nombre real del valor de texto de `AnswerMode` (`TEXT` u otro) con `git grep -n "enum AnswerMode" -A6 src/main`. Usa el que exista.
- `inspector.inspect` devuelve `Optional<AudioInspection>`; si `Optional.empty()` no produce `AudioInvalidFormatException` con el `AudioAnswerPolicy` real, lee `AudioAnswerPolicy.checkContent` y usa la entrada que la produce. Si ninguna la produce, detente.
- Importa las excepciones de `tech.cameia.entrevista.domain.exception` y `java.util.Arrays`.
- Ensayo: comenta temporalmente el `Arrays.fill` del `finally` del servicio. Las 4 pruebas nuevas deben fallar. Revierte y comprueba con `git diff src/main` que no queda nada.

**Terminado:** `./mvnw.cmd -B test "-Dtest=AudioAnswerEndToEndTest,SubmitAudioAnswerServiceTest"` en verde.

**Commit (T1 y T2):** `CM-354 | test(audio): comprobar que no queda audio en disco ni en el log tras respuestas válidas y fallidas`.

## T3 · Guardia de arquitectura: el servicio no escribe archivos (REQ-HT06-05)

**Archivo:** `src/test/java/tech/cameia/entrevista/architecture/LayeredArchitectureTest.java`. Al final de la clase:

```java
    @Test
    @DisplayName("Ninguna clase del servicio escribe archivos: el audio y los datos de la persona no tocan el disco")
    void services_shouldNotWriteFiles() {
        ArchRule rule = noClasses()
                .that().resideInAPackage(BASE_PACKAGE + "..")
                .should().dependOnClassesThat().haveFullyQualifiedName("java.nio.file.Files")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.io.FileOutputStream")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.io.RandomAccessFile")
                .orShould().callMethod(java.io.File.class, "createTempFile", String.class, String.class)
                .because("el audio de la respuesta vive solo en memoria y se descarta al terminar la petición");

        rule.check(classes);
    }
```

**Trampas:**
- Si la regla falla en `origin/develop` sin cambios, **no la relajes**: detente y reporta qué clase la viola (contradice el hecho H6 de la spec).
- **Ensayo obligatorio (V5):** crea temporalmente `src/main/java/tech/cameia/entrevista/infrastructure/TmpProbe.java` con un método que llame a `java.nio.file.Files.write(Path.of("x"), new byte[0])`. Corre la prueba: debe fallar. Borra el archivo, comprueba `git status` limpio en `src/main` y vuelve a verde. Pega las salidas.

**Terminado:** `./mvnw.cmd -B test "-Dtest=LayeredArchitectureTest"` en verde.

**Commit:** `CM-354 | test(arquitectura): impedir el uso de archivos en el servicio`.

## T4 · Renumerar ADR y bitácoras y fijar 8 s en el ADR 0014 (D-354-4, D-354-5)

Sigue **literalmente** la spec §10:

1. Los seis `git mv` de la tabla de archivos.
2. La línea 1 de los dos ADR renumerados (`# ADR 0015 · …`, `# ADR 0016 · …`).
3. Cada fila de la tabla de referencias, con Edit y el texto exacto «Antes» → «Después».
4. `docs/bitacora-ia/README.md`: cuatro filas nuevas (14 a 17), con enlace, una frase de qué decidió cada una (sácala de la primera línea de **Tarea** o del título de cada archivo) y su salida. Mismo formato que las filas 11 a 13.
5. ADR 0014: las tres filas de la tabla de la spec §10.

**Trampas:**
- Las herramientas de edición pueden cambiar `≈` o `×`; verifica con `git diff` que solo cambian las cifras.
- No toques `CLAUDE.md` ni `specs/CM-216-*`: sus «ADR 0012/0013» son los de Paula.

**Verificación (V7):**
`git grep -nE "0012-respuesta-por-voz|0013-reintento-de-la-transcripcion|1[1-4]_(respuesta-por-voz|validacion-audio|integracion-respuesta|reintento-transcripcion)"` sin resultados.
`git grep -n "10,5 s" docs/adr/0014-reintentos-y-respaldo-de-la-ia.md` sin resultados.

**Commit:** `CM-354 | docs(adr): renumerar los ADR y bitácoras de la voz y fijar 8 s de evaluación en el ADR 0014`.

## T5 · Verificación final (una vez, sobre la punta)

- `./mvnw.cmd -B clean verify`; log en `ejecucion/evidencia/CM-354/01_clean-verify.log`.
- Cobertura de `SubmitAudioAnswerService` y `AudioAnswerController` desde `jacoco.csv`: no debe bajar respecto de T0.
- Newman con la colección y el ambiente local del repo (`postman/README.md`), contra la aplicación empaquetada y la base de `docker compose`; log en `ejecucion/evidencia/CM-354/02_newman.log`.
- `git rev-parse HEAD^{tree}` anotado.
- `ejecucion/evidencia/CM-354/resumen.md` con la tabla R1 a R14 y la lista V1 a V10 de la spec, con las salidas reales.
