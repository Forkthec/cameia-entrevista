package tech.cameia.entrevista.domain.exception;

import java.util.Objects;

import tech.cameia.entrevista.domain.model.TranscriptionFailure;

/**
 * No se pudo transcribir el audio. La lanza el adaptador de Voz en cada intento fallido, con la causa clasificada,
 * y la lanza el caso de uso al agotar los intentos (CA-5.13.3): la pregunta queda pendiente y la persona ve el
 * mensaje con «Grabar de nuevo» y «Responder por texto».
 */
public class TranscriptionUnavailableException extends BusinessException {

    /** Mensaje que ve la persona (CA-5.13.3). */
    public static final String MESSAGE = "No pudimos transcribir tu respuesta. Graba de nuevo o responde por texto.";

    private final TranscriptionFailure failure;

    /**
     * Crea la excepción con la causa clasificada.
     *
     * @param failure clase de fallo, que decide si se reintenta
     */
    public TranscriptionUnavailableException(TranscriptionFailure failure) {
        this(failure, null);
    }

    /**
     * Crea la excepción con la causa clasificada y la excepción técnica que la originó.
     *
     * @param failure clase de fallo, que decide si se reintenta
     * @param cause excepción técnica, para el registro; puede ser {@code null}
     */
    public TranscriptionUnavailableException(TranscriptionFailure failure, Throwable cause) {
        super(ErrorCode.TRANSCRIPTION_UNAVAILABLE, MESSAGE);
        this.failure = Objects.requireNonNull(failure, "failure");
        if (cause != null) {
            initCause(cause);
        }
    }

    /** @return clase de fallo */
    public TranscriptionFailure failure() {
        return failure;
    }
}
