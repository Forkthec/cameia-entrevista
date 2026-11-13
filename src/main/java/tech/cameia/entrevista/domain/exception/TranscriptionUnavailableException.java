package tech.cameia.entrevista.domain.exception;

import java.util.Objects;

import tech.cameia.entrevista.domain.model.TranscriptionFailure;

/**
 * El servicio de transcripción falló, no respondió a tiempo o respondió algo inválido (CA-5.12.8): no se registra
 * la respuesta ni se avanza de pregunta y se descarta el audio.
 *
 * <p>Es {@code 503}: la causa está fuera del servicio y es pasajera. La causa técnica viaja como
 * {@link #getCause()} y solo llega al log, nunca con el audio ni el texto.</p>
 */
public class TranscriptionUnavailableException extends BusinessException {

    /** Mensaje que ve la persona, literal de CA-5.12.8. */
    public static final String MESSAGE = "No pudimos transcribir tu respuesta. Graba de nuevo o responde por texto.";

    private final String reason;
    private final TranscriptionFailure failure;

    /**
     * Crea la excepción con la razón técnica, para el log. La clase de fallo es {@link TranscriptionFailure#REJECTED}:
     * no se reintenta.
     *
     * @param reason razón corta y sin datos de la persona (por ejemplo {@code timeout} o {@code status=500})
     * @param cause causa técnica; puede ser {@code null}
     */
    public TranscriptionUnavailableException(String reason, Throwable cause) {
        this(TranscriptionFailure.REJECTED, reason, cause);
    }

    /**
     * Crea la excepción con la clase de fallo, que decide el reintento automático (HU-5.13), y la razón técnica.
     *
     * @param failure clase de fallo
     * @param reason razón corta y sin datos de la persona
     * @param cause causa técnica; puede ser {@code null}
     */
    public TranscriptionUnavailableException(TranscriptionFailure failure, String reason, Throwable cause) {
        super(ErrorCode.TRANSCRIPTION_UNAVAILABLE, MESSAGE);
        this.failure = Objects.requireNonNull(failure, "failure");
        this.reason = reason;
        if (cause != null) {
            initCause(cause);
        }
    }

    /** @return razón técnica del fallo, sin datos de la persona */
    public String getReason() {
        return reason;
    }

    /** @return clase de fallo: transitorio, tiempo agotado u otro */
    public TranscriptionFailure failure() {
        return failure;
    }
}
