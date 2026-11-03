package tech.cameia.entrevista.support;

/** Datos compartidos por las pruebas del catálogo de personalización. */
public final class CatalogTestData {

    /** Cuerpo exacto que debe responder el catálogo, en el orden en que se muestra. */
    public static final String EXPECTED_BODY = """
            {"modes":[{"value":"SIMULACION","available":true},{"value":"ENTRENO","available":false}],\
            "tones":[{"value":"PROFESIONAL_NEUTRO","available":true},{"value":"CALIDO","available":true},\
            {"value":"ESTRICTO","available":true},{"value":"MOTIVADOR","available":true}],\
            "answerFormats":[{"value":"AUDIO","available":true},{"value":"TEXTO","available":true}],\
            "languages":[{"value":"es-CO","available":true},{"value":"en","available":true}]}""";

    private CatalogTestData() {
    }
}
