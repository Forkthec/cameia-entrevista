package tech.cameia.entrevista.spike.planificacion;

import java.util.regex.Pattern;

/** Enmascara llaves en cualquier texto del proveedor antes de guardarlo o mostrarlo. */
final class Secretos {

    /** Formas conocidas de llave: Google ({@code AIza...}), Groq ({@code gsk_...}) y portadores genéricos. */
    private static final Pattern FORMAS = Pattern.compile(
            "AIza[0-9A-Za-z_\\-]{20,}|gsk_[0-9A-Za-z]{20,}|(?i:bearer)\\s+[0-9A-Za-z._\\-]{20,}");

    private Secretos() {
    }

    /**
     * Reemplaza por {@code ****} las llaves dadas y cualquier texto con forma de llave.
     *
     * @param texto texto original, puede ser {@code null}
     * @param llaves valores exactos de las llaves del proceso; los vacíos se ignoran
     * @return el texto sin llaves
     */
    static String enmascarar(String texto, String... llaves) {
        if (texto == null) {
            return null;
        }
        String limpio = texto;
        for (String llave : llaves) {
            if (llave != null && !llave.isBlank()) {
                limpio = limpio.replace(llave, "****");
            }
        }
        return FORMAS.matcher(limpio).replaceAll("****");
    }
}
