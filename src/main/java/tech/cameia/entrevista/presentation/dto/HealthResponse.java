package tech.cameia.entrevista.presentation.dto;

/**
 * Respuesta del endpoint de salud del microservicio de Entrevista.
 *
 * <p>Forma parte del contrato público y la consumen el API Gateway y las sondas de
 * disponibilidad de la plataforma para decidir si el servicio puede recibir tráfico.
 * No incluye información de sesión ni datos del usuario.</p>
 *
 * @param status estado del servicio; vale {@code "UP"} cuando la aplicación responde con normalidad
 */
public record HealthResponse(String status) {

    /** Valor de {@code status} cuando el servicio está operativo. */
    private static final String STATUS_UP = "UP";

    /**
     * Crea una respuesta que indica que el servicio está operativo.
     *
     * @return respuesta con {@code status} igual a {@code "UP"}
     */
    public static HealthResponse up() {
        return new HealthResponse(STATUS_UP);
    }
}
