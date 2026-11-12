package tech.cameia.entrevista.infrastructure.media;

/**
 * El archivo recibido no se puede leer como el contenedor que dice ser. Es interna del paquete: el inspector la
 * convierte en «formato no admitido» y nunca llega al cliente ni lleva datos del archivo.
 */
final class MalformedMediaException extends RuntimeException {

    MalformedMediaException(String reason) {
        super(reason, null, false, false);
    }
}
