package tech.cameia.entrevista.domain.model;

import tech.cameia.entrevista.domain.exception.IdentityRequiredException;

/**
 * Identidad de la persona que hace la petición: el {@code firebaseUid} que pone el Gateway.
 *
 * @param value identificador tal como llegó, sin recortar
 */
public record FirebaseUid(String value) {

    /** Largo máximo del identificador, igual al de las columnas que lo guardan. */
    public static final int MAX_LENGTH = 128;

    /**
     * Valida el identificador recibido en el encabezado de identidad.
     *
     * @param raw valor del encabezado, posiblemente ausente
     * @return la identidad, con el mismo valor recibido
     * @throws IdentityRequiredException si falta, está en blanco, pasa de {@value #MAX_LENGTH}
     *         caracteres o contiene un carácter de control, de formato (como el de dirección
     *         de texto) o un separador de línea o de párrafo
     */
    public static FirebaseUid required(String raw) {
        if (raw == null || raw.isBlank()
                || raw.codePointCount(0, raw.length()) > MAX_LENGTH
                || raw.codePoints().anyMatch(FirebaseUid::isUnsafeCharacter)) {
            throw new IdentityRequiredException();
        }
        return new FirebaseUid(raw);
    }

    /**
     * Indica si el carácter no puede formar parte de una identidad: los de control, los de formato
     * de Unicode (dirección de texto, ancho cero) y los separadores de línea y de párrafo, que
     * alterarían una línea del registro.
     */
    private static boolean isUnsafeCharacter(int codePoint) {
        return Character.isISOControl(codePoint) || switch (Character.getType(codePoint)) {
            case Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true;
            default -> false;
        };
    }
}
