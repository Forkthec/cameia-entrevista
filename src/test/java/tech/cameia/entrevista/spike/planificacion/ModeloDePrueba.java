package tech.cameia.entrevista.spike.planificacion;

import java.util.Map;

/**
 * Modelo de lenguaje que el banco de pruebas del spike puede consultar.
 *
 * <p>Es código de pruebas del spike, no de producción. Cada implementación recibe el mismo prompt y
 * devuelve el texto tal como lo entregó el proveedor, sin limpiar.</p>
 */
interface ModeloDePrueba {

    /** Nombre corto con que se rotulan las salidas ({@code GEMINI_LITE} o {@code QWEN_GROQ}). */
    String alias();

    /** Identificador del modelo en el proveedor. */
    String nombreDelModelo();

    /**
     * Pide una respuesta al modelo, una sola vez y sin reintentos ocultos.
     *
     * @param prompt texto completo del prompt
     * @param maxTokensSalida tope de tokens de la respuesta
     * @return la respuesta con sus mediciones
     * @throws FalloDelProveedor si el proveedor rechaza o no atiende la llamada
     */
    Respuesta generar(String prompt, int maxTokensSalida);

    /** Respuesta cruda del modelo; los tokens valen {@code -1} si el proveedor no los informó. */
    record Respuesta(String texto, int tokensEntrada, int tokensSalida, String modeloInformado,
            Map<String, String> encabezadosDeLimites, String motivoDeCorte) {
    }

    /** Tipo de fallo del proveedor, que decide si se espera, se reintenta o se para. */
    enum TipoDeFallo {
        /** 503 u otro fallo transitorio: se espera 20 s. */
        SOBRECARGA,
        /** 429 por minuto: se espera lo que diga el proveedor, o 65 s. */
        LIMITE_POR_MINUTO,
        /** 429 por día: se para ese modelo. */
        LIMITE_POR_DIA,
        /** Cualquier otro fallo: no se reintenta. */
        OTRO
    }

    /** Fallo del proveedor con su causa ya enmascarada (sin llaves). */
    final class FalloDelProveedor extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final TipoDeFallo tipo;

        private final long esperaSugeridaS;

        private final transient Map<String, String> encabezados;

        FalloDelProveedor(TipoDeFallo tipo, String causa, long esperaSugeridaS, Map<String, String> encabezados) {
            super(causa);
            this.tipo = tipo;
            this.esperaSugeridaS = esperaSugeridaS;
            this.encabezados = encabezados;
        }

        TipoDeFallo tipo() {
            return tipo;
        }

        /** Segundos que el proveedor pidió esperar, o {@code -1} si no lo dijo. */
        long esperaSugeridaS() {
            return esperaSugeridaS;
        }

        Map<String, String> encabezados() {
            return encabezados;
        }
    }
}
