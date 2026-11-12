package tech.cameia.entrevista.domain.model;

/**
 * Quién atiende una llamada a la IA: el modelo, la versión del prompt y el proveedor que se registran en la ejecución.
 *
 * @param model nombre del modelo
 * @param promptVersion versión del prompt que se envía
 * @param provider código del proveedor
 */
public record ExecutionSource(String model, String promptVersion, String provider) {
}
