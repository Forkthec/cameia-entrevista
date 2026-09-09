package tech.cameia.entrevista.presentation.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import tech.cameia.entrevista.presentation.dto.HealthResponse;

/**
 * Adaptador de entrada HTTP que expone el estado de salud del microservicio de Entrevista.
 *
 * <p>Lo consumen el API Gateway y las sondas de disponibilidad de la plataforma para
 * decidir si el servicio puede recibir tráfico. El endpoint no requiere contexto de
 * sesión ni datos del usuario.</p>
 */
@RestController
class HealthController {

    /**
     * Comprueba que el microservicio está operativo y puede atender peticiones.
     *
     * <p>Es una comprobación superficial: confirma que la aplicación responde, pero no
     * verifica dependencias externas como la base de datos, la mensajería o los
     * proveedores LLM. No admite parámetros de consulta.</p>
     *
     * @return respuesta HTTP {@code 200 OK} cuyo cuerpo JSON contiene el estado del
     *         servicio; por ejemplo, el campo {@code status} con el valor {@code "UP"}
     */
    @GetMapping(path = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<HealthResponse> checkHealth() {
        return ResponseEntity.ok(HealthResponse.up());
    }
}
