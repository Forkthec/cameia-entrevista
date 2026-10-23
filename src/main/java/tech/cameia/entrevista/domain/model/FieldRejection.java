package tech.cameia.entrevista.domain.model;

import tech.cameia.entrevista.domain.exception.ErrorCode;

/**
 * Un campo rechazado por la validación: su nombre en el contrato, el código estable y el mensaje para la persona.
 *
 * @param field nombre del campo como lo ve el cliente
 * @param code código estable de la causa
 * @param message mensaje en español para la persona
 */
public record FieldRejection(String field, ErrorCode code, String message) {
}
