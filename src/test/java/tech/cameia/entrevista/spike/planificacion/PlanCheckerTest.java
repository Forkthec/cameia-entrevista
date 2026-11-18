package tech.cameia.entrevista.spike.planificacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tech.cameia.entrevista.spike.planificacion.PlanChecker.Pedido;
import tech.cameia.entrevista.spike.planificacion.PlanChecker.Resultado;

/** Comprueba, sin llamar al modelo, que el verificador de la lista (a) a (g) decide como se espera. */
class PlanCheckerTest {

    private static final Pedido ES_CON_OFERTA = new Pedido("es-CO", true, List.of("api", "base de datos"), List.of("rabbitmq"));

    private static String plan(String idioma, String t1, String t2, String t3, String t4, String t5, String tipo2) {
        return """
                {"idioma":"%s","preguntas":[
                {"posicion":1,"tipo":"APERTURA","texto":"%s"},
                {"posicion":2,"tipo":"%s","texto":"%s"},
                {"posicion":3,"tipo":"CONDUCTUAL","texto":"%s"},
                {"posicion":4,"tipo":"TECNICA","texto":"%s"},
                {"posicion":5,"tipo":"SITUACIONAL","texto":"%s"}]}
                """.formatted(idioma, t1, tipo2, t2, t3, t4, t5);
    }

    private static String planBueno() {
        return plan("es-CO", "Cuéntame sobre ti y por qué quieres este rol.",
                "¿Cómo diseñas una API REST para que sea fácil de versionar?",
                "Describe una vez en que resolviste un conflicto con tu equipo.",
                "¿Cómo modelas una base de datos para evitar duplicados con RabbitMQ?",
                "¿Qué harías si una entrega falla en producción un viernes?", "TECNICA");
    }

    @Test
    @DisplayName("Un plan correcto cumple los siete criterios")
    void evaluar_shouldBeValid_whenPlanIsCorrect() {
        Resultado resultado = PlanChecker.evaluar(planBueno(), ES_CON_OFERTA);

        assertThat(resultado.valido()).as(resultado.criterios().toString()).isTrue();
        assertThat(resultado.largos()).hasSize(5);
    }

    @Test
    @DisplayName("Un texto que no es JSON falla el criterio a y deja los demás sin evaluar")
    void evaluar_shouldFailForm_whenTextIsNotJson() {
        Resultado resultado = PlanChecker.evaluar("```json\n{}\n```", ES_CON_OFERTA);

        assertThat(resultado.valido()).isFalse();
        assertThat(resultado.criterios()).hasSize(7);
        assertThat(resultado.criterios().get(0).cumple()).isFalse();
        assertThat(resultado.criterios().get(1).detalle()).contains("no evaluable");
    }

    @Test
    @DisplayName("Un tipo distinto al de la plantilla en una posición falla el criterio b")
    void evaluar_shouldFailTypes_whenPositionHasOtherType() {
        String mal = plan("es-CO", "Cuéntame sobre ti.", "¿Cómo usas una API?", "Describe un conflicto.",
                "¿Cómo modelas una base de datos con RabbitMQ?", "¿Qué harías si falla?", "SITUACIONAL");

        Resultado resultado = PlanChecker.evaluar(mal, ES_CON_OFERTA);

        assertThat(resultado.criterios().get(1).cumple()).isFalse();
        assertThat(resultado.criterios().get(1).detalle()).contains("posición 2");
    }

    @Test
    @DisplayName("Dos preguntas casi idénticas fallan el criterio e")
    void evaluar_shouldFailRepeated_whenTwoQuestionsAreAlmostEqual() {
        String repetido = plan("es-CO", "Cuéntame sobre ti.", "¿Cómo diseñas una API REST versionada para clientes?",
                "Describe un conflicto en tu equipo.", "¿Cómo diseñas una API REST versionada para clientes nuevos?",
                "¿Qué harías si falla RabbitMQ?", "TECNICA");

        Resultado resultado = PlanChecker.evaluar(repetido, ES_CON_OFERTA);

        assertThat(resultado.criterios().get(4).cumple()).isFalse();
    }

    @Test
    @DisplayName("Preguntas en inglés cuando se pidió español fallan el criterio d")
    void evaluar_shouldFailLanguage_whenQuestionsAreInEnglish() {
        String ingles = plan("es-CO", "Tell me about yourself and why you want the role.",
                "How do you design a REST API for the clients?", "Describe a time you solved a conflict with your team.",
                "How would you model a database with RabbitMQ?", "What would you do if a release fails on Friday?", "TECNICA");

        Resultado resultado = PlanChecker.evaluar(ingles, ES_CON_OFERTA);

        assertThat(resultado.criterios().get(3).cumple()).isFalse();
    }

    @Test
    @DisplayName("Sin oferta enviada, el criterio f no aplica y cumple")
    void evaluar_shouldPassOffer_whenNoOfferWasSent() {
        Pedido sinOferta = new Pedido("es-CO", false, List.of("api", "base de datos"), List.of("rabbitmq"));
        String sin = plan("es-CO", "Cuéntame sobre ti.", "¿Cómo diseñas una API REST versionada?",
                "Describe un conflicto en tu equipo.", "¿Cómo modelas una base de datos para evitar duplicados?",
                "¿Qué harías si falla una entrega?", "TECNICA");

        Resultado resultado = PlanChecker.evaluar(sin, sinOferta);

        assertThat(resultado.criterios().get(5).cumple()).isTrue();
    }

    @Test
    @DisplayName("Una pregunta que supera el largo máximo vigente falla el criterio g")
    void evaluar_shouldFailLength_whenQuestionIsTooLong() {
        String larga = "¿Cómo diseñas una API? " + "a".repeat(PlanChecker.LARGO_MAXIMO_VIGENTE);
        String mal = plan("es-CO", "Cuéntame sobre ti.", larga, "Describe un conflicto en tu equipo.",
                "¿Cómo modelas una base de datos con RabbitMQ?", "¿Qué harías si falla?", "TECNICA");

        Resultado resultado = PlanChecker.evaluar(mal, ES_CON_OFERTA);

        assertThat(resultado.criterios().get(6).cumple()).isFalse();
    }

    private static final Map<String, List<String>> TEMAS = Map.of(
            "mensajería", List.of("cola", "rabbitmq", "mensaj"),
            "base de datos", List.of("base de datos", "sql"));

    @Test
    @DisplayName("Un tema que aparece en dos de las preguntas 2 a 5 falla el criterio h")
    void temasDistintos_shouldFail_whenSameTopicAppearsTwice() {
        List<String> textos = List.of("Cuéntame de ti.", "¿Cómo usas colas de mensajes?", "Describe un conflicto.",
                "¿Cómo modelas una base de datos?", "¿Qué harías si falla RabbitMQ?");

        PlanChecker.Criterio h = PlanChecker.temasDistintos(textos, TEMAS);

        assertThat(h.cumple()).isFalse();
        assertThat(h.detalle()).contains("mensajería").contains("[2, 5]");
    }

    @Test
    @DisplayName("Cada tema en una sola pregunta, y la apertura sin contar, cumple el criterio h")
    void temasDistintos_shouldPass_whenEachTopicAppearsOnce() {
        List<String> textos = List.of("Cuéntame por qué te interesa trabajar con colas.", "¿Cómo usas colas de mensajes?",
                "Describe un conflicto.", "¿Cómo modelas una base de datos?", "¿Qué harías si falla una entrega?");

        assertThat(PlanChecker.temasDistintos(textos, TEMAS).cumple()).isTrue();
    }

    @Test
    @DisplayName("Una palabra que solo contiene el fragmento corto no cuenta como el tema")
    void temasDistintos_shouldNotMatchInsideOtherWords() {
        List<String> textos = List.of("Cuéntame de ti.", "¿Cómo usas colas de mensajes?", "Háblame de la colaboración con tu equipo.",
                "¿Cómo modelas una base de datos?", "¿Qué harías si falla una entrega?");

        PlanChecker.Criterio h = PlanChecker.temasDistintos(textos, TEMAS);

        assertThat(h.cumple()).isTrue();
    }

    @Test
    @DisplayName("Un fragmento corto en plural sí cuenta como el tema")
    void temasDistintos_shouldMatchPluralOfShortFragment() {
        List<String> textos = List.of("Cuéntame de ti.", "¿Cómo configuras las colas?", "Describe un conflicto.",
                "¿Cómo modelas una base de datos?", "¿Qué harías si cae la cola de pagos?");

        assertThat(PlanChecker.temasDistintos(textos, TEMAS).cumple()).isFalse();
    }

    @Test
    @DisplayName("El texto de un plan mal formado no entrega preguntas")
    void textosDelPlan_shouldBeNull_whenFormIsWrong() {
        assertThat(PlanChecker.textosDelPlan("no es json")).isNull();
        assertThat(PlanChecker.textosDelPlan(planBueno())).hasSize(5);
    }

    @Test
    @DisplayName("Una técnica sobre mensajería con RabbitMQ cuenta como término del rol cuando la lista lo trae")
    void evaluar_shouldAcceptMessaging_whenRoleTermsIncludeIt() {
        Pedido pedido = new Pedido("es-CO", false, List.of("api", "rabbitmq", "mensaj"), List.of());
        String plan = plan("es-CO", "Cuéntame sobre ti y por qué quieres este rol.",
                "¿Cómo diseñas una API REST para que sea fácil de versionar?",
                "Describe una vez en que resolviste un conflicto con tu equipo.",
                "¿Qué estrategia usas para que los mensajes no se pierdan si cae el consumidor?",
                "¿Qué harías si una entrega falla en producción un viernes?", "TECNICA");

        assertThat(PlanChecker.evaluar(plan, pedido).criterios().get(2).cumple()).isTrue();
    }
}
