package tech.cameia.entrevista.infrastructure.media;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import tech.cameia.entrevista.domain.model.AudioFormat;
import tech.cameia.entrevista.domain.model.AudioInspection;

/**
 * Lee un archivo WebM (EBML) y lo acepta solo si es audio Opus sin video, midiendo su duración por las marcas de
 * tiempo de los bloques.
 *
 * <p>{@code MediaRecorder} escribe el segmento y los clústeres con tamaño desconocido y sin la duración en
 * {@code Info}, así que el recorrido es lineal: entra en los elementos contenedores que interesan (segmento, pistas,
 * clúster, grupo de bloque) sin saltarlos y salta todos los demás por su tamaño. Nunca usa el nombre del archivo ni
 * el tipo declarado.</p>
 */
final class WebmAudioReader {

    private static final long EBML = 0x1A45DFA3L;
    private static final long DOC_TYPE = 0x4282L;
    private static final long SEGMENT = 0x18538067L;
    private static final long INFO = 0x1549A966L;
    private static final long TIMESTAMP_SCALE = 0x2AD7B1L;
    private static final long INFO_DURATION = 0x4489L;
    private static final long TRACKS = 0x1654AE6BL;
    private static final long TRACK_ENTRY = 0xAEL;
    private static final long TRACK_NUMBER = 0xD7L;
    private static final long TRACK_TYPE = 0x83L;
    private static final long CODEC_ID = 0x86L;
    private static final long CLUSTER = 0x1F43B675L;
    private static final long CLUSTER_TIMESTAMP = 0xE7L;
    private static final long SIMPLE_BLOCK = 0xA3L;
    private static final long BLOCK_GROUP = 0xA0L;
    private static final long BLOCK = 0xA1L;

    /** Contenedores en los que se entra sin saltarlos: sus hijos se leen en el mismo recorrido. */
    private static final Set<Long> ENTERED = Set.of(SEGMENT, INFO, TRACKS, TRACK_ENTRY, CLUSTER, BLOCK_GROUP);

    private static final long TRACK_TYPE_AUDIO = 2;
    private static final String OPUS = "A_OPUS";
    private static final long DEFAULT_SCALE_NS = 1_000_000L;
    private static final long NANOS_PER_MICRO = 1000;

    /** Bits de agrupación (lacing) de las banderas de un bloque. */
    private static final int LACING_BITS = 0x06;

    /** Duración de una trama Opus, en microsegundos, por modo (RFC 6716, tabla 2). */
    private static final long[] SILK_FRAME_MICROS = {10_000, 20_000, 40_000, 60_000};
    private static final long[] HYBRID_FRAME_MICROS = {10_000, 20_000};
    private static final long[] CELT_FRAME_MICROS = {2_500, 5_000, 10_000, 20_000};

    /** Un bloque trae al menos el número de pista, la marca de tiempo relativa y las banderas. */
    private static final long MIN_BLOCK_BYTES = 4;

    /** Firma del encabezado EBML. */
    static boolean looksLikeWebm(byte[] data) {
        return data.length >= 4 && (data[0] & 0xFF) == 0x1A && (data[1] & 0xFF) == 0x45 && (data[2] & 0xFF) == 0xDF
                && (data[3] & 0xFF) == 0xA3;
    }

    private WebmAudioReader() {
    }

    /**
     * Lee el audio.
     *
     * @param data bytes del archivo
     * @return formato y duración, o vacío si no es WebM con solo audio Opus y al menos un bloque
     */
    static Optional<AudioInspection> read(byte[] data) {
        ByteCursor in = new ByteCursor(data);
        if (in.readId() != EBML) {
            return Optional.empty();
        }
        long headerSize = in.readSize();
        if (headerSize == ByteCursor.UNKNOWN_SIZE) {
            return Optional.empty();
        }
        int headerEnd = in.limitFor(headerSize);
        String docType = null;
        while (in.position() < headerEnd) {
            long id = in.readId();
            long size = in.readSize();
            if (id == DOC_TYPE) {
                docType = in.readString(size);
            } else {
                in.skip(size);
            }
        }
        if (!"webm".equals(docType)) {
            return Optional.empty();
        }
        return readSegment(in);
    }

    private static Optional<AudioInspection> readSegment(ByteCursor in) {
        State state = new State();
        while (in.remaining() >= 2) {
            long id = in.readId();
            long size = in.readSize();
            if (ENTERED.contains(id)) {
                if (id == TRACK_ENTRY) {
                    state.tracks.add(new Track());
                }
                continue;
            }
            if (size == ByteCursor.UNKNOWN_SIZE || size > in.remaining()) {
                // Un elemento truncado al final (grabación cortada) termina el recorrido sin invalidar lo leído.
                break;
            }
            readLeaf(in, id, size, state);
        }
        return state.result();
    }

    private static void readLeaf(ByteCursor in, long id, long size, State state) {
        int end = in.limitFor(size);
        Track current = state.tracks.isEmpty() ? null : state.tracks.get(state.tracks.size() - 1);
        if (id == TIMESTAMP_SCALE) {
            state.scaleNs = in.readUnsigned(size);
        } else if (id == INFO_DURATION) {
            state.declaredTicks = in.readFloat(size);
        } else if (id == TRACK_NUMBER && current != null) {
            current.number = in.readUnsigned(size);
        } else if (id == TRACK_TYPE && current != null) {
            current.type = in.readUnsigned(size);
        } else if (id == CODEC_ID && current != null) {
            current.codec = in.readString(size);
        } else if (id == CLUSTER_TIMESTAMP) {
            state.clusterTicks = in.readUnsigned(size);
        } else if ((id == SIMPLE_BLOCK || id == BLOCK) && size >= MIN_BLOCK_BYTES) {
            readBlock(in, end, state);
        }
        in.seek(end);
    }

    private static void readBlock(ByteCursor in, int end, State state) {
        long track = in.readSize();
        int relative = in.readInt16();
        int flags = in.readByte();
        if ((flags & LACING_BITS) != 0 || in.position() >= end) {
            // MediaRecorder no agrupa paquetes en un bloque; un bloque agrupado o sin datos no se puede medir.
            throw new MalformedMediaException("bloque WebM agrupado o vacío");
        }
        state.packetMicros = Math.addExact(state.packetMicros, opusPacketMicros(in, end));
        state.blockTracks.add(track);
        state.lastTicks = Math.max(state.lastTicks, state.clusterTicks + relative);
        state.blocks++;
    }

    /**
     * Duración de un paquete Opus según su byte TOC (RFC 6716, 3.1): la configuración da la duración de cada trama y
     * los dos bits bajos, cuántas tramas trae. Así la duración no depende solo de las marcas de tiempo, que un archivo
     * manipulado podría comprimir para esconder un audio más largo.
     */
    static long opusPacketMicros(ByteCursor in, int end) {
        int toc = in.readByte();
        int config = toc >> 3;
        long frameMicros;
        if (config < 12) {
            frameMicros = SILK_FRAME_MICROS[config % 4];
        } else if (config < 16) {
            frameMicros = HYBRID_FRAME_MICROS[config % 2];
        } else {
            frameMicros = CELT_FRAME_MICROS[config % 4];
        }
        int frames = switch (toc & 0x03) {
            case 0 -> 1;
            case 1, 2 -> 2;
            default -> {
                if (in.position() >= end) {
                    throw new MalformedMediaException("paquete Opus sin el número de tramas");
                }
                yield in.readByte() & 0x3F;
            }
        };
        return frameMicros * frames;
    }

    private static final class Track {
        private long number = -1;
        private long type = -1;
        private String codec = "";
    }

    private static final class State {
        private final List<Track> tracks = new ArrayList<>();
        private final Set<Long> blockTracks = new HashSet<>();
        private long scaleNs = DEFAULT_SCALE_NS;
        private double declaredTicks;
        private long clusterTicks;
        private long lastTicks;
        private long blocks;
        private long packetMicros;

        Optional<AudioInspection> result() {
            if (tracks.isEmpty() || blocks == 0 || scaleNs <= 0) {
                return Optional.empty();
            }
            Set<Long> audioTracks = new HashSet<>();
            for (Track track : tracks) {
                // Cualquier pista que no sea audio Opus (video, subtítulos, otro códec) invalida el archivo.
                if (track.type != TRACK_TYPE_AUDIO || !OPUS.equals(track.codec)) {
                    return Optional.empty();
                }
                audioTracks.add(track.number);
            }
            if (!audioTracks.containsAll(blockTracks)) {
                return Optional.empty();
            }
            // La mayor de tres medidas: la última marca de tiempo, la duración declarada y la suma de los paquetes.
            double ticks = Math.max(lastTicks, Double.isFinite(declaredTicks) ? declaredTicks : 0);
            double byTimestamps = Math.min(Long.MAX_VALUE, ticks * scaleNs);
            long nanos = (long) Math.max(byTimestamps, (double) packetMicros * NANOS_PER_MICRO);
            return Optional.of(new AudioInspection(AudioFormat.WEBM_OPUS, Duration.ofNanos(Math.max(0, nanos))));
        }
    }
}
