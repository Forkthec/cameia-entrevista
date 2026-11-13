package tech.cameia.entrevista.domain.model;

/** Proveedor de IA dentro de un ciclo de intentos. */
public enum AiProvider {

    /** Gemini: proveedor principal. */
    PRIMARY("gemini"),

    /** Qwen en Groq: proveedor de respaldo. */
    FALLBACK("groq");

    private final String code;

    AiProvider(String code) {
        this.code = code;
    }

    /**
     * Valor que se guarda en {@code ejecucion_ia.proveedor}.
     *
     * @return código corto del proveedor
     */
    public String code() {
        return code;
    }
}
