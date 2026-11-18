package tech.cameia.entrevista.domain.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Lo que se sabe de un audio tras leer su contenido real: el formato y la duración.
 *
 * @param format formato admitido que se reconoció
 * @param duration duración medida en el propio contenedor (marcas de tiempo de las muestras)
 */
public record AudioInspection(AudioFormat format, Duration duration) {

    /**
     * Valida que ninguno de los dos datos falte y que la duración no sea negativa.
     */
    public AudioInspection {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("La duración no puede ser negativa");
        }
    }
}
