package tech.cameia.entrevista.application.command;

/**
 * Pedido de responder la pregunta pendiente de una sesión con texto escrito.
 *
 * @param session sesión y dueño; el dueño viene de la identidad del Gateway
 * @param turnId identificador del turno al que se responde, tal como llegó en el cuerpo
 * @param answerText texto de la respuesta, tal como llegó
 * @param idempotencyKey clave del encabezado {@code Idempotency-Key}, tal como llegó
 */
public record AnswerCommand(SessionCommand session, String turnId, String answerText, String idempotencyKey) {

    /** El texto de la respuesta es de la persona: nunca se imprime. */
    @Override
    public String toString() {
        return "AnswerCommand[session=" + session + ", turnId=" + turnId + "]";
    }
}
