package tech.cameia.entrevista.domain.model;

import java.util.Objects;

/**
 * Palabra transcrita con su inicio y su fin en milisegundos desde el comienzo del audio. Solo vive en memoria
 * durante la petición que registra la respuesta: nunca se guarda ni se registra.
 *
 * @param text texto de la palabra tal como lo devolvió el servicio de transcripción
 * @param startMs inicio, en milisegundos
 * @param endMs fin, en milisegundos; mayor o igual que el inicio
 */
public record TimedWord(String text, long startMs, long endMs) {

    /** Valida que el texto exista y que los tiempos sean coherentes. */
    public TimedWord {
        Objects.requireNonNull(text, "text");
        if (startMs < 0 || endMs < startMs) {
            throw new IllegalArgumentException("Tiempos de palabra inválidos");
        }
    }

    /** No imprime el texto: una palabra nunca llega a un registro. */
    @Override
    public String toString() {
        return "TimedWord[startMs=" + startMs + ", endMs=" + endMs + "]";
    }
}
