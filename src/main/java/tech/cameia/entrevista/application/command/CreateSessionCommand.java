package tech.cameia.entrevista.application.command;

import java.util.Objects;

import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.SessionConfigurationDraft;

/**
 * Entrada del caso de uso que crea o reutiliza la sesión configurada.
 *
 * @param owner persona autenticada, tomada solo de la identidad que pone el Gateway
 * @param draft configuración enviada, sin validar
 */
public record CreateSessionCommand(FirebaseUid owner, SessionConfigurationDraft draft) {

    /**
     * @throws NullPointerException si falta alguno de los dos
     */
    public CreateSessionCommand {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(draft, "draft");
    }
}
