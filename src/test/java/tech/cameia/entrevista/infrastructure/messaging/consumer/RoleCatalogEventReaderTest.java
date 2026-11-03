package tech.cameia.entrevista.infrastructure.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import tech.cameia.entrevista.application.command.ReplicateRoleCatalogCommand;
import tech.cameia.entrevista.domain.model.RoleStatus;
import tech.cameia.entrevista.infrastructure.messaging.payload.RoleCatalogPayloadV1;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

class RoleCatalogEventReaderTest {

    private static final String MESSAGE_ID = "3b6f0c2e-9a41-4f55-8d11-0c5a7b2e9d10";
    private static final String TYPE = "perfil.catalogo-roles-actualizado";
    private static final String CODE_1 = "a0000001-0000-0000-0000-000000000001";
    private static final String CODE_2 = "a0000001-0000-0000-0000-000000000002";

    /** Ejemplo del contrato v1. */
    private static final String VALID_CATALOG_BODY = """
            {"actualizadoEn":"2026-10-24T12:00:00.123Z","roles":[\
            {"codigo":"a0000001-0000-0000-0000-000000000001","nombreEs":"Desarrollador Frontend",\
            "nombreEn":"Frontend Developer","estado":"ACTIVE"},\
            {"codigo":"a0000001-0000-0000-0000-000000000002","nombreEs":"Desarrollador Backend",\
            "nombreEn":"Backend Developer","estado":"INACTIVE"}]}""";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private RoleCatalogEventReader reader;

    @BeforeEach
    void setUp() {
        reader = new RoleCatalogEventReader(Validation.buildDefaultValidatorFactory().getValidator());
    }

    private ObjectNode tree() {
        return (ObjectNode) mapper.readTree(VALID_CATALOG_BODY);
    }

    private RoleCatalogPayloadV1 payload(ObjectNode tree) {
        return mapper.treeToValue(tree, RoleCatalogPayloadV1.class);
    }

    private ObjectNode role(ObjectNode root, int index) {
        return (ObjectNode) root.get("roles").get(index);
    }

    private ReplicateRoleCatalogCommand read(ObjectNode root) {
        return reader.read(payload(root), MESSAGE_ID, TYPE, 1);
    }

    private void assertViolation(ObjectNode root, String reason, String field) {
        assertThatThrownBy(() -> read(root)).isInstanceOfSatisfying(ContractViolation.class, violation -> {
            assertThat(violation.reason()).isEqualTo(reason);
            assertThat(violation.field()).isEqualTo(field);
        });
    }

    private void assertContractViolation(ObjectNode root, String field) {
        assertViolation(root, ContractViolation.PAYLOAD_CONTRACT_VIOLATION, field);
    }

    @Test
    @DisplayName("El ejemplo del contrato produce el comando con sus dos roles")
    void read_shouldBuildCommand_whenExampleValid() {
        ReplicateRoleCatalogCommand command = read(tree());

        assertThat(command.messageId()).isEqualTo(UUID.fromString(MESSAGE_ID));
        assertThat(command.snapshotAt()).isEqualTo(Instant.parse("2026-10-24T12:00:00.123Z"));
        assertThat(command.roles()).hasSize(2);
        assertThat(command.roles().get(0).id()).isEqualTo(UUID.fromString(CODE_1));
        assertThat(command.roles().get(0).nameEs()).isEqualTo("Desarrollador Frontend");
        assertThat(command.roles().get(0).nameEn()).isEqualTo("Frontend Developer");
        assertThat(command.roles().get(0).status()).isEqualTo(RoleStatus.ACTIVE);
        assertThat(command.roles().get(1).status()).isEqualTo(RoleStatus.INACTIVE);
    }

    // ------------------------------------------------------------------ propiedades del mensaje

    @ParameterizedTest(name = "message_id [{0}]")
    @NullSource
    @ValueSource(strings = { "", "abc", "3b6f0c2e9a414f558d110c5a7b2e9d10", "3b6f0c2e-9a41-4f55-8d11-0c5a7b2e9d1",
            "3b6f0c2e-9a41-4f55-8d11-0c5a7b2e9d100", "3b6f0c2e-9a41-4f55-8d11-0c5a7b2e9d1g" })
    @DisplayName("Un message_id ausente o que no es un UUID canónico se rechaza")
    void read_shouldReject_whenMessageIdInvalid(String messageId) {
        assertThatThrownBy(() -> reader.read(payload(tree()), messageId, TYPE, 1))
                .isInstanceOfSatisfying(ContractViolation.class,
                        violation -> assertThat(violation.reason()).isEqualTo("MESSAGE_ID_INVALID"));
    }

    @ParameterizedTest(name = "type [{0}]")
    @NullSource
    @ValueSource(strings = { "", "perfil.profesional-actualizado", "Perfil.Catalogo-Roles-Actualizado" })
    @DisplayName("Un type ausente o distinto del evento se rechaza")
    void read_shouldReject_whenTypeInvalid(String type) {
        assertThatThrownBy(() -> reader.read(payload(tree()), MESSAGE_ID, type, 1))
                .isInstanceOfSatisfying(ContractViolation.class,
                        violation -> assertThat(violation.reason()).isEqualTo("EVENT_TYPE_INVALID"));
    }

    @Test
    @DisplayName("Una versión ausente, distinta de 1 o que no es un entero se rechaza; cualquier entero 1 pasa")
    void read_shouldCheckVersion() {
        for (Object bad : new Object[] { null, 2, "1", 2L, 0, 1.0 }) {
            assertThatThrownBy(() -> reader.read(payload(tree()), MESSAGE_ID, TYPE, bad))
                    .as(String.valueOf(bad))
                    .isInstanceOfSatisfying(ContractViolation.class,
                            violation -> assertThat(violation.reason()).isEqualTo("EVENT_VERSION_UNSUPPORTED"));
        }
        for (Object good : new Object[] { 1, 1L, (short) 1, (byte) 1 }) {
            assertThatCode(() -> reader.read(payload(tree()), MESSAGE_ID, TYPE, good)).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("Un cuerpo vacío se rechaza como formato inválido")
    void read_shouldReject_whenBodyNull() {
        assertThatThrownBy(() -> reader.read(null, MESSAGE_ID, TYPE, 1))
                .isInstanceOfSatisfying(ContractViolation.class,
                        violation -> assertThat(violation.reason()).isEqualTo("PAYLOAD_INVALID_FORMAT"));
    }

    @Test
    @DisplayName("El orden de las comprobaciones es message_id, type, versión y cuerpo")
    void read_shouldCheckInFixedOrder() {
        assertThatThrownBy(() -> reader.read(null, "x", "otro", 2)).isInstanceOfSatisfying(ContractViolation.class,
                violation -> assertThat(violation.reason()).isEqualTo("MESSAGE_ID_INVALID"));
        assertThatThrownBy(() -> reader.read(null, MESSAGE_ID, "otro", 2)).isInstanceOfSatisfying(
                ContractViolation.class, violation -> assertThat(violation.reason()).isEqualTo("EVENT_TYPE_INVALID"));
        assertThatThrownBy(() -> reader.read(null, MESSAGE_ID, TYPE, 2)).isInstanceOfSatisfying(
                ContractViolation.class,
                violation -> assertThat(violation.reason()).isEqualTo("EVENT_VERSION_UNSUPPORTED"));
    }

    // ------------------------------------------------------------------ actualizadoEn

    @Test
    @DisplayName("actualizadoEn ausente o null se rechaza")
    void read_shouldReject_whenUpdatedAtMissing() {
        ObjectNode absent = tree();
        absent.remove("actualizadoEn");
        assertContractViolation(absent, "actualizadoEn");

        ObjectNode nulled = tree();
        nulled.putNull("actualizadoEn");
        assertContractViolation(nulled, "actualizadoEn");
    }

    @ParameterizedTest(name = "actualizadoEn [{0}]")
    @ValueSource(strings = { "2026-10-24", "2026-10-24T12:00:00+02:00", "2026-10-24T12:00:00Z",
            "2026-10-24T12:00:00.123", "2026-10-24 12:00:00.123Z", "", "ayer" })
    @DisplayName("actualizadoEn debe ser UTC con milisegundos y Z")
    void read_shouldReject_whenUpdatedAtMalformed(String value) {
        ObjectNode root = tree();
        root.put("actualizadoEn", value);

        assertContractViolation(root, "actualizadoEn");
    }

    @Test
    @DisplayName("Una fecha con forma válida pero inexistente se rechaza")
    void read_shouldReject_whenUpdatedAtDoesNotExist() {
        ObjectNode root = tree();
        root.put("actualizadoEn", "2026-02-30T12:00:00.123Z");

        assertContractViolation(root, "actualizadoEn");
    }

    // ------------------------------------------------------------------ roles

    @Test
    @DisplayName("roles ausente, null o vacío se rechaza")
    void read_shouldReject_whenRolesMissingOrEmpty() {
        ObjectNode absent = tree();
        absent.remove("roles");
        assertContractViolation(absent, "roles");

        ObjectNode nulled = tree();
        nulled.putNull("roles");
        assertContractViolation(nulled, "roles");

        ObjectNode empty = tree();
        empty.putArray("roles");
        assertContractViolation(empty, "roles");
    }

    @Test
    @DisplayName("E-10: 200 roles pasan y 201 se rechazan")
    void read_shouldEnforceRoleCountLimit() {
        assertThat(read(withRoles(200)).roles()).hasSize(200);

        assertContractViolation(withRoles(201), "roles");
    }

    private ObjectNode withRoles(int count) {
        ObjectNode root = tree();
        ArrayNode roles = root.putArray("roles");
        for (int i = 0; i < count; i++) {
            roles.addObject().put("codigo", new UUID(1L, i).toString()).put("nombreEs", "Rol " + i)
                    .put("nombreEn", "Role " + i).put("estado", "ACTIVE");
        }
        return root;
    }

    @Test
    @DisplayName("Un elemento null de roles se rechaza con su índice")
    void read_shouldReject_whenRoleItemNull() {
        ObjectNode root = tree();
        ((ArrayNode) root.get("roles")).addNull();

        assertContractViolation(root, "roles[2]");
    }

    // ------------------------------------------------------------------ roles[i].codigo

    @ParameterizedTest(name = "codigo [{0}]")
    @ValueSource(strings = { "abc", "", "a0000001-0000-0000-0000-00000000000", "a0000001-0000-0000-0000-0000000000011",
            "a0000001000000000000000000000001", "g0000001-0000-0000-0000-000000000001" })
    @DisplayName("Un codigo que no es un UUID canónico se rechaza")
    void read_shouldReject_whenCodeMalformed(String code) {
        ObjectNode root = tree();
        role(root, 1).put("codigo", code);

        assertContractViolation(root, "roles[1].codigo");
    }

    @Test
    @DisplayName("Un codigo ausente o null se rechaza")
    void read_shouldReject_whenCodeMissing() {
        ObjectNode absent = tree();
        role(absent, 0).remove("codigo");
        assertContractViolation(absent, "roles[0].codigo");

        ObjectNode nulled = tree();
        role(nulled, 0).putNull("codigo");
        assertContractViolation(nulled, "roles[0].codigo");
    }

    @Test
    @DisplayName("Dos roles con el mismo codigo se rechazan con la ruta del segundo")
    void read_shouldReject_whenCodeDuplicated() {
        ObjectNode root = tree();
        role(root, 1).put("codigo", CODE_1);

        assertViolation(root, ContractViolation.CATALOG_ROLE_DUPLICATED, "roles[1].codigo");
    }

    @Test
    @DisplayName("El mismo codigo en mayúsculas y minúsculas cuenta como repetido")
    void read_shouldReject_whenCodeDuplicatedWithDifferentCase() {
        ObjectNode root = tree();
        role(root, 0).put("codigo", "A0000001-0000-0000-0000-00000000000A");
        role(root, 1).put("codigo", "a0000001-0000-0000-0000-00000000000a");

        assertViolation(root, ContractViolation.CATALOG_ROLE_DUPLICATED, "roles[1].codigo");
    }

    @Test
    @DisplayName("Una violación de forma gana a un codigo repetido")
    void read_shouldReportContractViolationBeforeDuplicates() {
        ObjectNode root = tree();
        role(root, 1).put("codigo", CODE_1);
        role(root, 1).put("estado", "DELETED");

        assertContractViolation(root, "roles[1].estado");
    }

    // ------------------------------------------------------------------ nombres

    @Test
    @DisplayName("nombreEs y nombreEn ausentes, null, vacíos o en blanco se rechazan")
    void read_shouldReject_whenNamesBlankOrMissing() {
        for (String field : new String[] { "nombreEs", "nombreEn" }) {
            ObjectNode absent = tree();
            role(absent, 0).remove(field);
            assertContractViolation(absent, "roles[0]." + field);

            ObjectNode nulled = tree();
            role(nulled, 0).putNull(field);
            assertContractViolation(nulled, "roles[0]." + field);

            for (String blank : new String[] { "", " ", "   ", "\t" }) {
                ObjectNode root = tree();
                role(root, 0).put(field, blank);
                assertContractViolation(root, "roles[0]." + field);
            }
        }
    }

    @Test
    @DisplayName("255 puntos de código pasan (también con emoji) y 256 se rechazan")
    void read_shouldEnforceNameLengthInCodePoints() {
        for (String field : new String[] { "nombreEs", "nombreEn" }) {
            for (String unit : new String[] { "x", "😀" }) {
                ObjectNode ok = tree();
                role(ok, 0).put(field, unit.repeat(255));
                assertThatCode(() -> read(ok)).as(field + " 255 " + unit).doesNotThrowAnyException();

                ObjectNode tooLong = tree();
                role(tooLong, 0).put(field, unit.repeat(256));
                assertContractViolation(tooLong, "roles[0]." + field);
            }
        }
    }

    @Test
    @DisplayName("El carácter nulo en un nombre se rechaza porque PostgreSQL no lo guarda")
    void read_shouldReject_whenNameHasNullCharacter() {
        ObjectNode root = tree();
        role(root, 0).put("nombreEs", "Dev\u0000");

        assertContractViolation(root, "roles[0].nombreEs");
    }

    @Test
    @DisplayName("E-09: un nombre con marcado pasa y se conserva literal")
    void read_shouldKeepMarkupLiterally() {
        ObjectNode root = tree();
        role(root, 0).put("nombreEs", "<script>alert(1)</script>");

        assertThat(read(root).roles().get(0).nameEs()).isEqualTo("<script>alert(1)</script>");
    }

    // ------------------------------------------------------------------ estado

    @ParameterizedTest(name = "estado [{0}]")
    @ValueSource(strings = { "active", "Active", "", "DELETED", "ACTIVE ", "INACTIVE\n" })
    @DisplayName("Un estado distinto de ACTIVE o INACTIVE se rechaza")
    void read_shouldReject_whenStatusInvalid(String status) {
        ObjectNode root = tree();
        role(root, 0).put("estado", status);

        assertContractViolation(root, "roles[0].estado");
    }

    @Test
    @DisplayName("Un estado null o ausente se rechaza")
    void read_shouldReject_whenStatusMissing() {
        ObjectNode absent = tree();
        role(absent, 1).remove("estado");
        assertContractViolation(absent, "roles[1].estado");

        ObjectNode nulled = tree();
        role(nulled, 1).putNull("estado");
        assertContractViolation(nulled, "roles[1].estado");
    }

    // ------------------------------------------------------------------ campos desconocidos y orden

    @Test
    @DisplayName("Los campos desconocidos se ignoran y el mensaje pasa")
    void read_shouldIgnoreUnknownFields() {
        ObjectNode root = tree();
        root.put("extra", "x");
        role(root, 0).put("categoria", "TECH");

        assertThatCode(() -> read(root)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Con varias violaciones se informa la primera en orden alfabético de los nombres del código")
    void read_shouldReportFirstPathAlphabetically() {
        ObjectNode root = tree();
        role(root, 1).put("estado", "DELETED");
        role(root, 0).put("nombreEn", "");
        root.put("actualizadoEn", "mal");

        // «roles[0].nameEn» va antes que «roles[1].status» y que «updatedAt»
        assertContractViolation(root, "roles[0].nombreEn");
    }

    @Test
    @DisplayName("El valor recibido nunca aparece en la excepción")
    void read_shouldNotLeakReceivedValue() {
        ObjectNode root = tree();
        role(root, 0).put("nombreEs", "");
        role(root, 0).put("estado", "SECRETO");

        assertThatThrownBy(() -> read(root)).isInstanceOfSatisfying(ContractViolation.class,
                violation -> assertThat(violation.toString() + violation.getMessage()).doesNotContain("SECRETO"));
    }
}
