package tech.cameia.entrevista.infrastructure.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tech.cameia.entrevista.domain.model.AudioFormat;
import tech.cameia.entrevista.domain.model.AudioInspection;

/**
 * Lectura del contenido real de los audios. Los archivos de {@code src/test/resources/audio} se generaron con
 * ffmpeg (tono, silencio y voz sintetizada); el WebM «en vivo» se arma aquí byte a byte como lo escribe
 * {@code MediaRecorder}: segmento y clústeres de tamaño desconocido y sin duración en {@code Info}.
 */
class ContainerAudioInspectorTest {

    private final ContainerAudioInspector inspector = new ContainerAudioInspector();

    static byte[] fixture(String name) {
        try (InputStream in = ContainerAudioInspectorTest.class.getResourceAsStream("/audio/" + name)) {
            return in.readAllBytes();
        } catch (IOException | NullPointerException missing) {
            throw new IllegalStateException("Falta el audio de prueba " + name, missing);
        }
    }

    private static void assertDuration(Optional<AudioInspection> result, AudioFormat format, long expectedMs,
            long toleranceMs) {
        assertThat(result).isPresent();
        assertThat(result.get().format()).isEqualTo(format);
        assertThat(result.get().duration().toMillis()).isBetween(expectedMs - toleranceMs, expectedMs + toleranceMs);
    }

    @Test
    @DisplayName("WebM con Opus (Chrome, Edge, Firefox) se reconoce y se mide")
    void inspect_shouldReadWebmOpus() {
        assertDuration(inspector.inspect(fixture("tono-3s.webm")), AudioFormat.WEBM_OPUS, 3000, 100);
        assertDuration(inspector.inspect(fixture("voz.webm")), AudioFormat.WEBM_OPUS, 13_000, 7000);
    }

    @Test
    @DisplayName("MP4 fragmentado con AAC (Safari) se reconoce y se mide")
    void inspect_shouldReadFragmentedMp4() {
        assertDuration(inspector.inspect(fixture("voz-safari.mp4")), AudioFormat.MP4_AAC, 13_000, 7000);
        assertDuration(inspector.inspect(fixture("silencio-180s-safari.mp4")), AudioFormat.MP4_AAC, 180_000, 300);
        assertDuration(inspector.inspect(fixture("silencio-182s-safari.mp4")), AudioFormat.MP4_AAC, 182_000, 300);
    }

    @Test
    @DisplayName("MP4 no fragmentado con AAC se reconoce y se mide")
    void inspect_shouldReadPlainMp4() {
        assertDuration(inspector.inspect(fixture("tono-3s.mp4")), AudioFormat.MP4_AAC, 3000, 100);
    }

    @Test
    @DisplayName("CA-5.8.7 y CA-5.8.8: 3:00 y 3:02 de WebM se miden con precisión")
    void inspect_shouldMeasureLongWebm() {
        assertDuration(inspector.inspect(fixture("silencio-180s.webm")), AudioFormat.WEBM_OPUS, 180_000, 100);
        assertDuration(inspector.inspect(fixture("silencio-182s.webm")), AudioFormat.WEBM_OPUS, 182_000, 100);
        assertDuration(inspector.inspect(fixture("tono-1s.webm")), AudioFormat.WEBM_OPUS, 1000, 100);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tono-3s.mp3", "tono-3s.wav", "tono-3s-con-video.webm", "tono-3s-con-video.mp4",
        "tono-3s-vorbis.webm"})
    @DisplayName("CA-5.8.14: MP3, WAV, Vorbis y archivos con pista de video no se admiten")
    void inspect_shouldRejectOtherFormats(String name) {
        assertThat(inspector.inspect(fixture(name))).isEmpty();
    }

    @Test
    @DisplayName("CA-5.8.15: un texto o un ejecutable que se presentan como WebM no se admiten")
    void inspect_shouldRejectFakeAudio() {
        assertThat(inspector.inspect("hola, no soy audio".getBytes(StandardCharsets.UTF_8))).isEmpty();
        byte[] executable = new byte[4096];
        executable[0] = 'M';
        executable[1] = 'Z';
        assertThat(inspector.inspect(executable)).isEmpty();
        assertThat(inspector.inspect(new byte[0])).isEmpty();
        assertThat(inspector.inspect("#!/bin/sh\nrm -rf /\n".getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    @DisplayName("Un encabezado EBML válido seguido de basura no se admite ni rompe la lectura")
    void inspect_shouldRejectEbmlHeaderWithGarbage() {
        byte[] real = fixture("tono-3s.webm");
        byte[] forged = Arrays.copyOf(real, 64);
        byte[] withGarbage = Arrays.copyOf(forged, 4096);
        Arrays.fill(withGarbage, 64, withGarbage.length, (byte) 0xFF);
        assertThat(inspector.inspect(forged)).isEmpty();
        assertThat(inspector.inspect(withGarbage)).isEmpty();
    }

    @Test
    @DisplayName("Un MP4 truncado o con tamaños de caja imposibles no rompe la lectura")
    void inspect_shouldSurviveCorruptMp4() {
        byte[] real = fixture("voz-safari.mp4");
        assertThat(inspector.inspect(Arrays.copyOf(real, 40))).isEmpty();
        byte[] corrupt = real.clone();
        // Tamaño de la segunda caja inflado a 2^32-1.
        int second = ((corrupt[0] & 0xFF) << 24) | ((corrupt[1] & 0xFF) << 16) | ((corrupt[2] & 0xFF) << 8)
                | (corrupt[3] & 0xFF);
        corrupt[second] = (byte) 0xFF;
        corrupt[second + 1] = (byte) 0xFF;
        corrupt[second + 2] = (byte) 0xFF;
        corrupt[second + 3] = (byte) 0xFF;
        assertThat(inspector.inspect(corrupt)).isEmpty();
    }

    @Test
    @DisplayName("Una grabación WebM cortada al final se mide hasta el último bloque completo")
    void inspect_shouldTolerateTruncatedWebm() {
        byte[] live = LiveWebm.opus(180_000);
        Optional<AudioInspection> result = inspector.inspect(Arrays.copyOf(live, live.length / 2 + 3));
        assertThat(result).isPresent();
        assertThat(result.get().duration()).isLessThan(Duration.ofSeconds(180));
    }

    // ---- WebM «en vivo», como lo escribe MediaRecorder ----

    @Test
    @DisplayName("WebM con segmento y clústeres de tamaño desconocido y sin duración: se mide por los bloques")
    void inspect_shouldMeasureLiveWebmByBlockTimestamps() {
        // El último paquete dura 20 ms: la suma de los paquetes da la marca del último bloque más 20 ms.
        assertDuration(inspector.inspect(LiveWebm.opus(180_000)), AudioFormat.WEBM_OPUS, 180_020, 0);
        assertDuration(inspector.inspect(LiveWebm.opus(182_000)), AudioFormat.WEBM_OPUS, 182_020, 0);
        assertDuration(inspector.inspect(LiveWebm.opus(2_000)), AudioFormat.WEBM_OPUS, 2_020, 0);
    }

    @Test
    @DisplayName("Marcas de tiempo comprimidas no esconden un audio largo: cuenta la duración de los paquetes Opus")
    void inspect_shouldMeasureForgedTimestampsByPackets() {
        // 10 000 paquetes de 20 ms (200 s) con todas las marcas de tiempo en 0.
        Optional<AudioInspection> result = inspector.inspect(LiveWebm.build("A_OPUS", 2, 199_980, true));
        assertDuration(result, AudioFormat.WEBM_OPUS, 200_000, 0);
    }

    @Test
    @DisplayName("WebM en vivo con un códec distinto de Opus, una pista de video o sin bloques no se admite")
    void inspect_shouldRejectLiveWebmWithWrongTracks() {
        assertThat(inspector.inspect(LiveWebm.build("A_VORBIS", 2, 3000))).isEmpty();
        assertThat(inspector.inspect(LiveWebm.build("V_VP8", 1, 3000))).isEmpty();
        assertThat(inspector.inspect(LiveWebm.build("A_OPUS", 2, 0))).isEmpty();
    }

    /** Arma un WebM mínimo con la forma que produce {@code MediaRecorder}. */
    static final class LiveWebm {

        private static final int BLOCK_MS = 20;
        private static final int CLUSTER_MS = 10_000;

        static byte[] opus(int durationMs) {
            return build("A_OPUS", 2, durationMs);
        }

        static byte[] build(String codec, int trackType, int lastBlockMs) {
            return build(codec, trackType, lastBlockMs, false);
        }

        static byte[] build(String codec, int trackType, int lastBlockMs, boolean forgeTimestamps) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            element(out, 0x1A45DFA3, concat(element(0x4282, "webm".getBytes(StandardCharsets.US_ASCII))));
            write(out, new byte[] {0x18, 0x53, (byte) 0x80, 0x67, 0x01, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
            element(out, 0x1549A966, element(0x2AD7B1, new byte[] {0x0F, 0x42, 0x40}));
            byte[] entry = concat(element(0xD7, new byte[] {1}), element(0x83, new byte[] {(byte) trackType}),
                    element(0x86, codec.getBytes(StandardCharsets.US_ASCII)));
            element(out, 0x1654AE6B, element(0xAE, entry));
            if (lastBlockMs > 0) {
                for (int cluster = 0; cluster <= lastBlockMs; cluster += CLUSTER_MS) {
                    // Clúster de tamaño desconocido, como en una grabación en curso.
                    write(out, new byte[] {0x1F, 0x43, (byte) 0xB6, 0x75, (byte) 0xFF});
                    element(out, 0xE7, uint(forgeTimestamps ? 0 : cluster));
                    int end = Math.min(cluster + CLUSTER_MS - BLOCK_MS, lastBlockMs);
                    for (int t = cluster; t <= end; t += BLOCK_MS) {
                        int rel = forgeTimestamps ? 0 : t - cluster;
                        element(out, 0xA3, new byte[] {(byte) 0x81, (byte) (rel >> 8), (byte) rel, (byte) 0x80,
                            (byte) 0xF8, 0x22, 0x33});
                    }
                }
            }
            return out.toByteArray();
        }

        private static byte[] uint(int value) {
            return new byte[] {(byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value};
        }

        private static byte[] element(long id, byte[] payload) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            element(out, id, payload);
            return out.toByteArray();
        }

        private static void element(ByteArrayOutputStream out, long id, byte[] payload) {
            int idBytes = id > 0xFFFFFF ? 4 : id > 0xFFFF ? 3 : id > 0xFF ? 2 : 1;
            for (int i = idBytes - 1; i >= 0; i--) {
                out.write((int) (id >> (8 * i)));
            }
            // Tamaño en 4 bytes (marcador 0x10).
            int size = payload.length;
            write(out, new byte[] {(byte) (0x10 | (size >> 24)), (byte) (size >> 16), (byte) (size >> 8),
                (byte) size});
            write(out, payload);
        }

        private static byte[] concat(byte[]... parts) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (byte[] part : parts) {
                write(out, part);
            }
            return out.toByteArray();
        }

        private static void write(ByteArrayOutputStream out, byte[] bytes) {
            out.write(bytes, 0, bytes.length);
        }
    }
}
