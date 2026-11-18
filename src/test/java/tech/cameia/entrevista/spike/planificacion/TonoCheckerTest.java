package tech.cameia.entrevista.spike.planificacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Comprueba, sin llamar al modelo, que la regla de tono deduce lo esperado. */
class TonoCheckerTest {

    @Test
    @DisplayName("Preguntas con bienvenida y sin pedir cifras se leen como CALIDO")
    void leer_shouldBeCalido_whenManyWelcomes() {
        List<String> textos = List.of("Qué gusto saludarte, cuéntame de ti.", "¿Cómo usas Spring?",
                "Describe un reto.", "¿Cómo usas colas?", "¿Qué harías ante un fallo?");

        assertThat(TonoChecker.leer(textos).tonoDeducido()).isEqualTo("CALIDO");
    }

    @Test
    @DisplayName("Bienvenida solo en la 1 y cierre en la 5 es calidez natural")
    void leer_shouldBeNaturalWarmth_whenWelcomeOnlyInFirstAndLast() {
        List<String> textos = List.of("Qué gusto saludarte, cuéntame de ti.", "¿Cómo usas Spring?",
                "Me gustaría que me contaras un reto.", "¿Cómo usas colas?", "Para cerrar, gracias por tu tiempo: ¿qué harías ante un fallo?");

        TonoChecker.Lectura lectura = TonoChecker.leer(textos);

        assertThat(lectura.tonoDeducido()).isEqualTo("CALIDO");
        assertThat(lectura.acogidaEnIntermedias()).isZero();
        assertThat(lectura.calidezNatural()).isTrue();
    }

    @Test
    @DisplayName("Saludos o agradecimientos en las preguntas 2 a 4 se cuentan como defecto")
    void leer_shouldFlagRepeatedWelcomes_whenMiddleQuestionsGreet() {
        List<String> textos = List.of("Welcome, tell me about yourself.", "It is a pleasure to meet you; how do you use Spring?",
                "Thank you for taking the time; describe a challenge.", "We appreciate your enthusiasm; how do you use queues?",
                "Thanks again for joining us; what would you do if a deployment fails?");

        TonoChecker.Lectura lectura = TonoChecker.leer(textos);

        assertThat(lectura.acogidaEnIntermedias()).isEqualTo(3);
        assertThat(lectura.calidezNatural()).isFalse();
    }

    @Test
    @DisplayName("Una frase que da por hecha una respuesta anterior se cuenta como defecto")
    void leer_shouldFlagAssumedAnswer_whenQuestionThanksForSharing() {
        List<String> textos = List.of("Welcome, tell me about yourself.", "How do you use Spring?",
                "Thanks for sharing that, describe a challenge.", "How do you use queues?", "What would you do if a deployment fails?");

        TonoChecker.Lectura lectura = TonoChecker.leer(textos);

        assertThat(lectura.suponenRespuestaPrevia()).isEqualTo(1);
        assertThat(lectura.calidezNatural()).isFalse();
    }

    @Test
    @DisplayName("Preguntas que piden cifras y evidencia se leen como ESTRICTO")
    void leer_shouldBeEstricto_whenManyDemands() {
        List<String> textos = List.of("Indica cifras de tu experiencia.", "Dame una métrica concreta.",
                "Describe con evidencia el resultado.", "¿Cómo usas colas?", "¿Qué harías ante un fallo?");

        assertThat(TonoChecker.leer(textos).tonoDeducido()).isEqualTo("ESTRICTO");
    }

    @Test
    @DisplayName("Preguntas directas, sin bienvenida ni exigencia, se leen como PROFESIONAL_NEUTRO")
    void leer_shouldBeNeutro_whenNoSignals() {
        List<String> textos = List.of("¿Qué experiencia tiene?", "¿Cómo usa Spring?", "Describa un reto.", "¿Cómo usa colas?",
                "¿Qué haría ante un fallo?");

        assertThat(TonoChecker.leer(textos).tonoDeducido()).isEqualTo("PROFESIONAL_NEUTRO");
    }
}
