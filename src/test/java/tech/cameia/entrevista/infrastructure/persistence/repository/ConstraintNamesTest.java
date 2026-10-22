package tech.cameia.entrevista.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** La búsqueda de la restricción violada recorre la cadena de causas sin distinguir mayúsculas y con tope. */
class ConstraintNamesTest {

    private static boolean violates(Throwable failure, String constraint) {
        return ConstraintNames.violates(failure, constraint);
    }

    private static ConstraintViolationException named(String name) {
        return new ConstraintViolationException("m", new SQLException("m", "23505"), name);
    }

    @Test
    @DisplayName("Encuentra la restricción en una causa anidada, sin distinguir mayúsculas")
    void violates_shouldMatch_whenNestedAndDifferentCase() {
        var failure = new DataIntegrityViolationException("x", named("UQ_Algo"));

        assertThat(violates(failure, "uq_algo")).isTrue();
    }

    @Test
    @DisplayName("No coincide cuando la restricción es otra o el fallo no trae restricción")
    void violates_shouldNotMatch_whenOtherConstraint() {
        assertThat(violates(new DataIntegrityViolationException("x", named("uq_otra")), "uq_algo")).isFalse();
        assertThat(violates(new DataIntegrityViolationException("x"), "uq_algo")).isFalse();
        assertThat(violates(null, "uq_algo")).isFalse();
    }

    @Test
    @DisplayName("Una cadena de más de 32 causas se corta sin recorrerla entera")
    void violates_shouldStop_whenChainIsLongerThanLimit() {
        Throwable chain = named("uq_algo");
        for (int i = 0; i < 40; i++) {
            chain = new RuntimeException("capa " + i, chain);
        }

        assertThat(violates(chain, "uq_algo")).isFalse();
    }
}
