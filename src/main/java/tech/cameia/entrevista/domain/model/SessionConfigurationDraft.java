package tech.cameia.entrevista.domain.model;

/**
 * Lo que la persona envió para configurar una sesión, sin validar. Cualquier valor puede ser {@code null}.
 *
 * @param profileId identificador del Perfil Profesional, en texto
 * @param targetRoleId identificador del rol objetivo, en texto
 * @param jobOffer texto libre de la oferta laboral
 * @param mode valor de la modalidad
 * @param tone valor del tono
 * @param answerFormat valor de la forma de respuesta
 * @param sessionLanguage valor del idioma
 * @param idempotencyKey clave anti-duplicados del encabezado
 */
public record SessionConfigurationDraft(
        String profileId,
        String targetRoleId,
        String jobOffer,
        String mode,
        String tone,
        String answerFormat,
        String sessionLanguage,
        String idempotencyKey) {
}
