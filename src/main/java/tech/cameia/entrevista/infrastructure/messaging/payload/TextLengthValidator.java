package tech.cameia.entrevista.infrastructure.messaging.payload;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Valida {@link TextLength}: largo en puntos de código, sin texto en blanco cuando el mínimo es 1 o
 * más y sin el carácter nulo, que PostgreSQL no puede guardar en un texto.
 */
public class TextLengthValidator implements ConstraintValidator<TextLength, String> {

    private int min;
    private int max;

    @Override
    public void initialize(TextLength constraint) {
        this.min = constraint.min();
        this.max = constraint.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        int length = value.codePointCount(0, value.length());
        if (length < min || length > max) {
            return false;
        }
        if (min >= 1 && value.isBlank()) {
            return false;
        }
        return value.indexOf('\u0000') < 0;
    }
}
