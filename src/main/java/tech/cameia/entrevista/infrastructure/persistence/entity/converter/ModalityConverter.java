package tech.cameia.entrevista.infrastructure.persistence.entity.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import tech.cameia.entrevista.domain.model.Modality;

/**
 * Modalidad: traduce entre el enumerado del dominio y el valor que guarda la base. Se declara en la
 * columna con {@code @Convert} y no se aplica sola.
 */
@Converter(autoApply = false)
public class ModalityConverter implements AttributeConverter<Modality, String> {

    /**
     * @param attribute valor del dominio, posiblemente {@code null}
     * @return el valor guardado, o {@code null} si no hay
     */
    @Override
    public String convertToDatabaseColumn(Modality attribute) {
        return attribute == null ? null : attribute.code();
    }

    /**
     * @param dbData valor guardado, posiblemente {@code null}
     * @return el valor del dominio, o {@code null} si no hay
     * @throws IllegalArgumentException si el valor guardado no existe en el dominio
     */
    @Override
    public Modality convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Modality.fromCode(dbData);
    }
}
