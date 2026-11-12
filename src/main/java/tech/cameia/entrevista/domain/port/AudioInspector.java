package tech.cameia.entrevista.domain.port;

import java.util.Optional;

import tech.cameia.entrevista.domain.model.AudioInspection;

/**
 * Lee el contenido real de un audio para reconocer su formato y medir su duración, sin confiar en el tipo declarado
 * ni en el nombre del archivo (CA-5.8.15, CA-5.8.16).
 */
public interface AudioInspector {

    /**
     * Inspecciona el audio.
     *
     * @param content bytes recibidos
     * @return el formato y la duración, o vacío si el contenido no es un formato admitido, trae una pista de video,
     *         no tiene muestras de audio o no se puede leer
     */
    Optional<AudioInspection> inspect(byte[] content);
}
