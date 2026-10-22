package tech.cameia.entrevista;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.Client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.core.retry.RetryTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Prueba de arranque del contexto completo contra PostgreSQL 16 real.
 *
 * <p>Que el contexto cargue demuestra que Flyway aplica las migraciones sobre una base vacía
 * y que Hibernate valida su mapeo contra el esquema resultante ({@code ddl-auto=validate}).
 * Se omite si no hay Docker disponible.</p>
 */
@SpringBootTest(properties = "cameia.llm.model=modelo-de-prueba")
@Testcontainers(disabledWithoutDocker = true)
class EntrevistaApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

	@Autowired
	private ApplicationContext context;

	@Test
	@DisplayName("El contexto arranca con las migraciones aplicadas")
	void contextLoads() {
		// Con todas las autoconfiguraciones activas, ninguna crea un segundo cliente ni otra plantilla de reintentos.
		assertThat(context.getBeansOfType(Client.class)).containsOnlyKeys("languageModelClient");
		assertThat(context.getBeansOfType(RetryTemplate.class)).containsOnlyKeys("languageModelRetryTemplate");
	}

}
