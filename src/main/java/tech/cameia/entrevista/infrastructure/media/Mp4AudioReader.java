package tech.cameia.entrevista.infrastructure.media;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import tech.cameia.entrevista.domain.model.AudioFormat;
import tech.cameia.entrevista.domain.model.AudioInspection;

/**
 * Lee un archivo MP4 (ISO BMFF) y lo acepta solo si todas sus pistas son de sonido con muestras {@code mp4a} (AAC),
 * midiendo su duración.
 *
 * <p>Safari graba MP4 fragmentado: {@code moov} describe las pistas y cada {@code moof} trae la marca de tiempo de
 * inicio ({@code tfdt}) y la duración de sus muestras ({@code trun}, o los valores por defecto de {@code tfhd} y
 * {@code trex}). Un MP4 no fragmentado trae la duración en {@code mdhd} o en {@code stts}. Se toma la mayor de las
 * duraciones que se puedan medir.</p>
 */
final class Mp4AudioReader {

    /** Cajas en las que se entra: sus hijos son más cajas. */
    private static final Set<String> CONTAINERS = Set.of("moov", "trak", "mdia", "minf", "stbl", "mvex", "moof",
            "traf");

    private static final String SOUND = "soun";
    private static final String AAC = "mp4a";
    private static final int TFHD_DEFAULT_DURATION = 0x08;
    private static final int TRUN_DATA_OFFSET = 0x01;
    private static final int TRUN_FIRST_SAMPLE_FLAGS = 0x04;
    private static final int TRUN_SAMPLE_DURATION = 0x100;
    private static final int TRUN_SAMPLE_SIZE = 0x200;
    private static final int TRUN_SAMPLE_FLAGS = 0x400;
    private static final int TRUN_SAMPLE_CTO = 0x800;
    private static final int MAX_DEPTH = 8;

    /** Tramas de audio por muestra AAC-LC (ISO/IEC 14496-3). */
    private static final long AAC_FRAMES_PER_SAMPLE = 1024;

    private Mp4AudioReader() {
    }

    /** Firma de un MP4: la primera caja es {@code ftyp}. */
    static boolean looksLikeMp4(byte[] data) {
        return data.length >= 8 && data[4] == 'f' && data[5] == 't' && data[6] == 'y' && data[7] == 'p';
    }

    /**
     * Lee el audio.
     *
     * @param data bytes del archivo
     * @return formato y duración, o vacío si no es MP4 con solo pistas de sonido AAC y al menos una muestra
     */
    static Optional<AudioInspection> read(byte[] data) {
        if (!looksLikeMp4(data)) {
            return Optional.empty();
        }
        State state = new State();
        readBoxes(new ByteCursor(data), data.length, state, 0);
        return state.result();
    }

    private static void readBoxes(ByteCursor in, int end, State state, int depth) {
        if (depth > MAX_DEPTH) {
            throw new MalformedMediaException("cajas demasiado anidadas");
        }
        while (end - in.position() >= 8) {
            int start = in.position();
            long size = in.readUInt32();
            String type = in.readFourCc();
            if (size == 1) {
                size = in.readUInt64();
            } else if (size == 0) {
                size = (long) end - start;
            }
            if (size < in.position() - start || size > (long) end - start) {
                // Una caja final truncada (grabación cortada) termina la lectura de este nivel.
                return;
            }
            int boxEnd = (int) (start + size);
            if (CONTAINERS.contains(type)) {
                if ("trak".equals(type)) {
                    state.currentTrack = new Track();
                }
                readBoxes(in, boxEnd, state, depth + 1);
                if ("trak".equals(type)) {
                    state.closeTrack();
                }
            } else {
                readLeaf(in, type, boxEnd, state);
            }
            in.seek(boxEnd);
        }
    }

    private static void readLeaf(ByteCursor in, String type, int end, State state) {
        Track track = state.currentTrack;
        switch (type) {
            case "tkhd" -> {
                if (track != null) {
                    int version = in.readByte();
                    in.skip(3 + (version == 1 ? 16 : 8));
                    track.id = in.readUInt32();
                }
            }
            case "mdhd" -> {
                if (track != null) {
                    int version = in.readByte();
                    in.skip(3 + (version == 1 ? 16 : 8));
                    track.timescale = in.readUInt32();
                    track.declaredDuration = version == 1 ? in.readUInt64() : in.readUInt32();
                }
            }
            case "hdlr" -> {
                if (track != null) {
                    in.skip(8);
                    track.handler = in.readFourCc();
                }
            }
            case "stsd" -> {
                if (track != null) {
                    in.skip(4);
                    long entries = in.readUInt32();
                    if (entries > 0) {
                        in.skip(4);
                        track.sampleEntry = in.readFourCc();
                    }
                }
            }
            case "stts" -> {
                if (track != null) {
                    readSampleTable(in, end, track);
                }
            }
            case "trex" -> {
                in.skip(4);
                long id = in.readUInt32();
                in.skip(4);
                state.trexDefaults.put(id, in.readUInt32());
            }
            case "tfhd" -> readFragmentHeader(in, state);
            case "tfdt" -> {
                int version = in.readByte();
                in.skip(3);
                state.fragmentStart = version == 1 ? in.readUInt64() : in.readUInt32();
            }
            case "trun" -> readTrackRun(in, end, state);
            default -> {
                // Las demás cajas (mdat, free, udta, esds...) no cambian la decisión.
            }
        }
    }

    private static void readSampleTable(ByteCursor in, int end, Track track) {
        in.skip(4);
        long entries = in.readUInt32();
        if (entries * 8 > end - in.position()) {
            throw new MalformedMediaException("tabla stts fuera de la caja");
        }
        long total = 0;
        long samples = 0;
        for (long i = 0; i < entries; i++) {
            long count = in.readUInt32();
            long delta = in.readUInt32();
            total = Math.addExact(total, Math.multiplyExact(count, delta));
            samples = Math.addExact(samples, count);
        }
        track.tableDuration = total;
        track.tableSamples = samples;
    }

    private static void readFragmentHeader(ByteCursor in, State state) {
        in.readByte();
        int flags = (int) in.readUnsigned(3);
        state.fragmentTrack = in.readUInt32();
        if ((flags & 0x01) != 0) {
            in.skip(8);
        }
        if ((flags & 0x02) != 0) {
            in.skip(4);
        }
        state.fragmentDefault = (flags & TFHD_DEFAULT_DURATION) != 0 ? in.readUInt32()
                : state.trexDefaults.getOrDefault(state.fragmentTrack, 0L);
        state.fragmentStart = state.fragmentEnds.getOrDefault(state.fragmentTrack, 0L);
    }

    private static void readTrackRun(ByteCursor in, int end, State state) {
        in.readByte();
        int flags = (int) in.readUnsigned(3);
        long samples = in.readUInt32();
        if ((flags & TRUN_DATA_OFFSET) != 0) {
            in.skip(4);
        }
        if ((flags & TRUN_FIRST_SAMPLE_FLAGS) != 0) {
            in.skip(4);
        }
        int perSample = Integer.bitCount(flags & (TRUN_SAMPLE_DURATION | TRUN_SAMPLE_SIZE | TRUN_SAMPLE_FLAGS
                | TRUN_SAMPLE_CTO)) * 4;
        if (samples * perSample > end - in.position()) {
            throw new MalformedMediaException("trun fuera de la caja");
        }
        long total = 0;
        for (long i = 0; i < samples; i++) {
            if ((flags & TRUN_SAMPLE_DURATION) != 0) {
                total = Math.addExact(total, in.readUInt32());
                in.skip(perSample - 4L);
            } else {
                total = Math.addExact(total, state.fragmentDefault);
                in.skip(perSample);
            }
        }
        long runEnd = Math.addExact(state.fragmentStart, total);
        state.fragmentStart = runEnd;
        state.fragmentEnds.merge(state.fragmentTrack, runEnd, Math::max);
        state.fragmentSamples.merge(state.fragmentTrack, samples, Math::addExact);
    }

    private static final class Track {
        private long id = -1;
        private long timescale;
        private long declaredDuration;
        private long tableDuration;
        private long tableSamples;
        private String handler = "";
        private String sampleEntry = "";
    }

    private static final class State {
        private final Map<Long, Track> tracks = new HashMap<>();
        private final Map<Long, Long> trexDefaults = new HashMap<>();
        private final Map<Long, Long> fragmentEnds = new HashMap<>();
        private final Map<Long, Long> fragmentSamples = new HashMap<>();
        private Track currentTrack;
        private boolean invalidTrack;
        private long fragmentTrack;
        private long fragmentDefault;
        private long fragmentStart;

        void closeTrack() {
            Track track = currentTrack;
            currentTrack = null;
            // Cualquier pista que no sea sonido AAC (video, texto, otro códec) invalida el archivo.
            if (!SOUND.equals(track.handler) || !AAC.equals(track.sampleEntry) || track.timescale <= 0) {
                invalidTrack = true;
                return;
            }
            tracks.put(track.id, track);
        }

        Optional<AudioInspection> result() {
            if (invalidTrack || tracks.isEmpty()) {
                return Optional.empty();
            }
            double seconds = 0;
            long samples = 0;
            for (Track track : tracks.values()) {
                long trackSamples = Math.addExact(track.tableSamples, fragmentSamples.getOrDefault(track.id, 0L));
                samples = Math.addExact(samples, trackSamples);
                long ticks = Math.max(track.tableDuration, fragmentEnds.getOrDefault(track.id, 0L));
                if (track.declaredDuration != 0xFFFFFFFFL) {
                    ticks = Math.max(ticks, track.declaredDuration);
                }
                // Cada muestra AAC trae al menos 1024 tramas de audio: así la duración no depende solo de las
                // duraciones declaradas, que un archivo manipulado podría achicar para esconder un audio más largo.
                long byCount = Math.multiplyExact(trackSamples, AAC_FRAMES_PER_SAMPLE);
                seconds = Math.max(seconds, (double) Math.max(ticks, byCount) / track.timescale);
            }
            if (samples == 0) {
                return Optional.empty();
            }
            for (Long fragmentTrackId : fragmentEnds.keySet()) {
                if (!tracks.containsKey(fragmentTrackId)) {
                    return Optional.empty();
                }
            }
            return Optional.of(new AudioInspection(AudioFormat.MP4_AAC, Duration.ofMillis(Math.round(seconds * 1000))));
        }
    }
}
