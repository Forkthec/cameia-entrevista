package tech.cameia.entrevista.domain.model;

import java.util.Objects;

/**
 * Una opción de personalización de la entrevista y si hoy se puede elegir.
 *
 * <p>Una opción no disponible se muestra en la interfaz deshabilitada con «Próximamente»; no se puede
 * usar al crear una sesión.</p>
 *
 * @param value     valor del enumerado que representa la opción
 * @param available {@code true} si hoy se puede elegir
 * @param <E>       tipo del enumerado (modalidad, tono, forma de respuesta o idioma)
 */
public record CatalogOption<E extends Enum<E>>(E value, boolean available) {

    /** Exige el valor: una opción sin valor no significa nada. */
    public CatalogOption {
        Objects.requireNonNull(value, "value");
    }
}
