package tech.cameia.entrevista.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de una petición ya atendida, para que repetirla devuelva la misma respuesta.
 *
 * @param id identificador del registro
 * @param owner persona que hizo la petición
 * @param operation operación que se protege, por ejemplo la creación de una sesión
 * @param key clave que la persona envió
 * @param requestFingerprint huella del contenido de la petición original, en hexadecimal
 * @param responseStatus estado HTTP de la respuesta original
 * @param responseBodyJson cuerpo de la respuesta original, como JSON
 * @param createdAt fecha del registro
 */
public record IdempotencyKey(
        UUID id,
        FirebaseUid owner,
        String operation,
        String key,
        String requestFingerprint,
        int responseStatus,
        String responseBodyJson,
        Instant createdAt) {
}
