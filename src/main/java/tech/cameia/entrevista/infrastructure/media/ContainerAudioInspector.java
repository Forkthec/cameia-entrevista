package tech.cameia.entrevista.infrastructure.media;

import java.util.Optional;

import org.springframework.stereotype.Component;

import tech.cameia.entrevista.domain.model.AudioInspection;
import tech.cameia.entrevista.domain.port.AudioInspector;

/**
 * Implementa {@link AudioInspector} leyendo el contenedor: la firma de los primeros bytes decide qué lector se usa,
 * y el lector exige que todas las pistas sean de audio con el códec esperado. Un archivo que no se puede leer (un
 * ejecutable, un texto, un audio truncado o manipulado) da vacío, nunca un error del servidor.
 */
@Component
class ContainerAudioInspector implements AudioInspector {

    @Override
    public Optional<AudioInspection> inspect(byte[] content) {
        try {
            if (WebmAudioReader.looksLikeWebm(content)) {
                return WebmAudioReader.read(content);
            }
            if (Mp4AudioReader.looksLikeMp4(content)) {
                return Mp4AudioReader.read(content);
            }
            return Optional.empty();
        } catch (MalformedMediaException | ArithmeticException unreadable) {
            return Optional.empty();
        }
    }
}
