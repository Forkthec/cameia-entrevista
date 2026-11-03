package tech.cameia.entrevista.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Copia de un rol del catálogo de roles profesionales de Perfil.
 *
 * <p>El contenido ya viene validado contra el contrato del evento; el dominio repite las reglas de
 * nombre para que ninguna copia inválida llegue a la base.</p>
 *
 * @param id     identificador del rol en Perfil
 * @param nameEs nombre en español, de 1 a {@value #MAX_NAME_LENGTH} puntos de código y no en blanco
 * @param nameEn nombre en inglés, con las mismas reglas
 * @param status estado publicado
 */
public record ProfessionalRoleReplica(UUID id, String nameEs, String nameEn, RoleStatus status) {

    /** Largo máximo de cada nombre, igual al de la columna y al del contrato del evento. */
    public static final int MAX_NAME_LENGTH = 255;

    /**
     * @throws NullPointerException     si falta algún componente
     * @throws IllegalArgumentException si un nombre está en blanco o pasa de {@value #MAX_NAME_LENGTH}
     *                                  puntos de código
     */
    public ProfessionalRoleReplica {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nameEs, "nameEs");
        Objects.requireNonNull(nameEn, "nameEn");
        Objects.requireNonNull(status, "status");
        requireName(nameEs, "nameEs");
        requireName(nameEn, "nameEn");
    }

    /**
     * Indica si el rol puede ser el rol objetivo de una sesión.
     *
     * @return {@code true} solo si el rol está activo
     */
    public boolean isActive() {
        return status == RoleStatus.ACTIVE;
    }

    private static void requireName(String value, String field) {
        if (value.isBlank() || value.codePointCount(0, value.length()) > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    field + " debe tener de 1 a " + MAX_NAME_LENGTH + " puntos de código y no estar en blanco");
        }
    }
}
