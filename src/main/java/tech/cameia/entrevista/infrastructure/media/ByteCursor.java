package tech.cameia.entrevista.infrastructure.media;

import java.nio.charset.StandardCharsets;

/**
 * Cursor de solo lectura sobre los bytes de un archivo recibido, con todas las lecturas acotadas: un valor que se
 * sale del arreglo lanza {@link MalformedMediaException} en lugar de leer de más. Los enteros son big-endian, como en
 * EBML y en MP4.
 */
final class ByteCursor {

    /** Tamaño EBML «desconocido» (todos los bits del valor en 1). */
    static final long UNKNOWN_SIZE = -1;

    private static final int MAX_ID_BYTES = 4;
    private static final int MAX_SIZE_BYTES = 8;

    private final byte[] data;
    private int position;

    ByteCursor(byte[] data) {
        this.data = data;
    }

    int position() {
        return position;
    }

    int remaining() {
        return data.length - position;
    }

    int length() {
        return data.length;
    }

    /** Mueve el cursor a una posición dentro del arreglo. */
    void seek(int target) {
        if (target < 0 || target > data.length) {
            throw new MalformedMediaException("posición fuera del archivo");
        }
        position = target;
    }

    /** Salta {@code count} bytes. */
    void skip(long count) {
        seek(limitFor(count));
    }

    /**
     * Posición final de un elemento de {@code size} bytes que empieza en la posición actual.
     *
     * @throws MalformedMediaException si el elemento se sale del archivo o el tamaño es desconocido
     */
    int limitFor(long size) {
        if (size < 0 || size > remaining()) {
            throw new MalformedMediaException("elemento fuera del archivo");
        }
        return position + (int) size;
    }

    int readByte() {
        if (position >= data.length) {
            throw new MalformedMediaException("fin del archivo");
        }
        return data[position++] & 0xFF;
    }

    /** Identificador EBML de 1 a 4 bytes, con su bit marcador incluido. */
    long readId() {
        int first = readByte();
        int length = Integer.numberOfLeadingZeros(first) - 24 + 1;
        if (first == 0 || length > MAX_ID_BYTES) {
            throw new MalformedMediaException("identificador EBML inválido");
        }
        long value = first;
        for (int i = 1; i < length; i++) {
            value = (value << 8) | readByte();
        }
        return value;
    }

    /** Tamaño EBML de 1 a 8 bytes, sin el bit marcador; {@link #UNKNOWN_SIZE} si todos sus bits valen 1. */
    long readSize() {
        int first = readByte();
        int length = Integer.numberOfLeadingZeros(first) - 24 + 1;
        if (first == 0 || length > MAX_SIZE_BYTES) {
            throw new MalformedMediaException("tamaño EBML inválido");
        }
        long value = first & (0xFF >> length);
        boolean allOnes = value == (0xFF >> length);
        for (int i = 1; i < length; i++) {
            int next = readByte();
            allOnes &= next == 0xFF;
            value = (value << 8) | next;
        }
        return allOnes ? UNKNOWN_SIZE : value;
    }

    /** Entero sin signo big-endian de 0 a 8 bytes. */
    long readUnsigned(long size) {
        if (size < 0 || size > MAX_SIZE_BYTES) {
            throw new MalformedMediaException("entero demasiado largo");
        }
        long value = 0;
        for (int i = 0; i < size; i++) {
            value = (value << 8) | readByte();
        }
        return value;
    }

    /** Número de coma flotante EBML de 4 u 8 bytes (o 0 bytes, que vale 0). */
    double readFloat(long size) {
        if (size == 0) {
            return 0;
        }
        if (size == 4) {
            return Float.intBitsToFloat((int) readUnsigned(4));
        }
        if (size == 8) {
            return Double.longBitsToDouble(readUnsigned(8));
        }
        throw new MalformedMediaException("número de coma flotante inválido");
    }

    /** Entero con signo de 16 bits. */
    int readInt16() {
        return (short) readUnsigned(2);
    }

    long readUInt32() {
        return readUnsigned(4);
    }

    long readUInt64() {
        long value = readUnsigned(8);
        if (value < 0) {
            throw new MalformedMediaException("entero de 64 bits fuera de rango");
        }
        return value;
    }

    /** Texto ASCII de {@code size} bytes, sin los ceros de relleno. */
    String readString(long size) {
        int end = limitFor(size);
        String text = new String(data, position, end - position, StandardCharsets.US_ASCII).replace("\0", "");
        position = end;
        return text;
    }

    /** Código de cuatro caracteres de una caja MP4. */
    String readFourCc() {
        return readString(4);
    }
}
