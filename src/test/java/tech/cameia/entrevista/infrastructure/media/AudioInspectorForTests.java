package tech.cameia.entrevista.infrastructure.media;

import java.util.Optional;

import tech.cameia.entrevista.domain.model.AudioInspection;

/**
 * Acceso de las pruebas de otros paquetes al inspector real, que es interno de {@code infrastructure.media}.
 */
public final class AudioInspectorForTests {

    private AudioInspectorForTests() {
    }

    /**
     * Inspecciona un audio con el inspector real.
     *
     * @param content bytes del audio
     * @return formato y duración, o vacío si no es un formato admitido
     */
    public static Optional<AudioInspection> inspect(byte[] content) {
        return new ContainerAudioInspector().inspect(content);
    }
}
