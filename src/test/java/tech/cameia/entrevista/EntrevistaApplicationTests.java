package tech.cameia.entrevista;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.Client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.retry.RetryTemplate;

@SpringBootTest(properties = "cameia.llm.model=modelo-de-prueba")
class EntrevistaApplicationTests {

	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoads() {
		// Con todas las autoconfiguraciones activas, ninguna crea un segundo cliente ni otra plantilla de reintentos.
		assertThat(context.getBeansOfType(Client.class)).containsOnlyKeys("languageModelClient");
		assertThat(context.getBeansOfType(RetryTemplate.class)).containsOnlyKeys("languageModelRetryTemplate");
	}

}
