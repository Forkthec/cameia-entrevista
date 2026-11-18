package tech.cameia.entrevista.infrastructure.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StarResultEntityKeyTest {

    private static StarResultEntity.Key key(UUID sessionId, UUID questionId) throws ReflectiveOperationException {
        StarResultEntity.Key key = new StarResultEntity.Key();
        set(key, "sessionId", sessionId);
        set(key, "questionId", questionId);
        return key;
    }

    private static void set(Object target, String field, Object value) throws ReflectiveOperationException {
        Field declared = target.getClass().getDeclaredField(field);
        declared.setAccessible(true);
        declared.set(target, value);
    }

    @Test
    @DisplayName("Dos claves con la misma sesión y pregunta son iguales y tienen el mismo código")
    void sameValues_shouldBeEqualWithSameHash() throws ReflectiveOperationException {
        UUID session = UUID.randomUUID();
        UUID question = UUID.randomUUID();

        assertThat(key(session, question)).isEqualTo(key(session, question))
                .hasSameHashCodeAs(key(session, question));
    }

    @Test
    @DisplayName("Una clave con otra pregunta, otra sesión, nula u otro tipo no es igual")
    void differentValues_shouldNotBeEqual() throws ReflectiveOperationException {
        UUID session = UUID.randomUUID();
        UUID question = UUID.randomUUID();
        StarResultEntity.Key base = key(session, question);

        assertThat(base).isNotEqualTo(key(session, UUID.randomUUID()))
                .isNotEqualTo(key(UUID.randomUUID(), question)).isNotEqualTo(null).isNotEqualTo("clave");
    }
}
