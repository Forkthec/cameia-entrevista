package tech.cameia.entrevista.infrastructure.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import tech.cameia.entrevista.application.command.ReplicateProfileCommand;
import tech.cameia.entrevista.domain.model.ReplicatedProfileStatus;
import tech.cameia.entrevista.infrastructure.messaging.payload.ProfileUpdatedPayloadV1;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class ProfileUpdatedEventReaderTest {

    private static final String MESSAGE_ID = "3b6f0c2e-9a41-4f55-8d11-0c5a7b2e9d10";
    private static final String TYPE = "perfil.profesional-actualizado";
    private static final Object REMOVE = new Object();

    /** Ejemplo del contrato v1. */
    private static final String VALID_BODY = """
            {
              "usuarioId": "6f1d2c3b4a5e4f60718293a4b5c6d7e8",
              "contextoProfesional": {
                "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
                "nombre": "Analista de datos",
                "estado": "COMPLETED",
                "fecha_archivado": null,
                "resumen": "Analista con tres años de experiencia en SQL y Python.",
                "experiencias": [
                  {"empresa": "Acme", "cargo": "Analista", "descripcion": "Reportes", "inicio": "2022-01",
                   "fin": "2023-12", "estadoEmpleo": "ENDED"}
                ],
                "formaciones": [
                  {"institucion": "Universidad del Cauca", "titulo": "Ingeniería de Sistemas",
                   "nivel": "UNDERGRADUATE", "inicio": "2018-02", "fin": null, "enCurso": true}
                ],
                "habilidades": [{"nombre": "SQL", "nivel": "ADVANCED"}]
              },
              "rolesAspirados": [
                {"codigo": "a0000001-0000-0000-0000-000000000002", "nombre": "Desarrollador Backend"}
              ],
              "actualizadoEn": "2026-10-15T12:00:00.123Z"
            }
            """;

    private final JsonMapper mapper = JsonMapper.builder().build();
    private ProfileUpdatedEventReader reader;

    @BeforeEach
    void setUp() {
        reader = new ProfileUpdatedEventReader(Validation.buildDefaultValidatorFactory().getValidator(), mapper);
    }

    private ObjectNode tree() {
        return (ObjectNode) mapper.readTree(VALID_BODY);
    }

    private ProfileUpdatedPayloadV1 payload(ObjectNode tree) {
        return mapper.treeToValue(tree, ProfileUpdatedPayloadV1.class);
    }

    /** Cambia un solo valor del ejemplo; {@code pointer} es un JSON Pointer a una propiedad. */
    private ProfileUpdatedPayloadV1 with(String pointer, Object value) {
        ObjectNode root = tree();
        int cut = pointer.lastIndexOf('/');
        ObjectNode parent = (ObjectNode) root.at(pointer.substring(0, cut));
        String key = pointer.substring(cut + 1);
        switch (value) {
            case null -> parent.putNull(key);
            case String text -> parent.put(key, text);
            case Boolean flag -> parent.put(key, flag);
            case JsonNode node -> parent.set(key, node);
            default -> parent.remove(key);
        }
        return payload(root);
    }

    private ArrayNode copies(String listPointer, int count) {
        ArrayNode list = mapper.createArrayNode();
        JsonNode element = tree().at(listPointer).get(0);
        for (int i = 0; i < count; i++) {
            list.add(element.deepCopy());
        }
        return list;
    }

    private ReplicateProfileCommand read(ProfileUpdatedPayloadV1 payload) {
        return reader.read(payload, MESSAGE_ID, TYPE, 1);
    }

    private void assertRejectedAt(ProfileUpdatedPayloadV1 payload, String field) {
        assertThatThrownBy(() -> read(payload)).isInstanceOfSatisfying(ContractViolation.class, violation -> {
            assertThat(violation.reason()).isEqualTo(ContractViolation.PAYLOAD_CONTRACT_VIOLATION);
            assertThat(violation.field()).isEqualTo(field);
        });
    }

    private void assertRejectedReason(Runnable call, String reason) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ContractViolation.class, violation -> {
            assertThat(violation.reason()).isEqualTo(reason);
            assertThat(violation.field()).isNull();
        });
    }

    // ---------------------------------------------------------------- caso válido

    @Test
    @DisplayName("El ejemplo del contrato se convierte en la copia completa")
    void read_shouldBuildReplica_whenValid() {
        ReplicateProfileCommand command = read(payload(tree()));

        assertThat(command.messageId()).isEqualTo(UUID.fromString(MESSAGE_ID));
        var replica = command.replica();
        assertThat(replica.profileId()).isEqualTo(UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7"));
        assertThat(replica.owner().value()).isEqualTo("6f1d2c3b4a5e4f60718293a4b5c6d7e8");
        assertThat(replica.name()).isEqualTo("Analista de datos");
        assertThat(replica.status()).isEqualTo(ReplicatedProfileStatus.COMPLETED);
        assertThat(replica.archivedAt()).isNull();
        assertThat(replica.updatedAt()).isEqualTo(Instant.parse("2026-10-15T12:00:00.123Z"));
        assertThat(replica.professionalContextJson()).contains("\"resumen\"", "\"fecha_archivado\"")
                .doesNotContain("embedding");
        assertThat(replica.aspiredRolesJson()).contains("\"codigo\":\"a0000001-0000-0000-0000-000000000002\"",
                "\"nombre\":\"Desarrollador Backend\"");
    }

    @Test
    @DisplayName("Los campos desconocidos se ignoran y no se guardan")
    void read_shouldIgnoreUnknownFields_whenPresent() {
        ObjectNode root = tree();
        root.putArray("embedding").add(0.1);
        root.put("plan", "PREMIUM");
        ((ObjectNode) root.get("contextoProfesional")).put("plan", "PREMIUM").putArray("embedding").add(0.1);

        var replica = read(payload(root)).replica();

        assertThat(replica.professionalContextJson()).doesNotContain("embedding", "PREMIUM");
        assertThat(replica.aspiredRolesJson()).doesNotContain("embedding", "PREMIUM");
    }

    @Test
    @DisplayName("El texto hostil se guarda literal")
    void read_shouldKeepTextLiteral_whenHostile() {
        String hostile = "<script>alert(1)</script> ' \" % _ \\ 😀";

        var replica = read(with("/contextoProfesional/nombre", hostile)).replica();

        assertThat(replica.name()).isEqualTo(hostile);
        assertThat(mapper.readTree(replica.professionalContextJson()).get("nombre").asString()).isEqualTo(hostile);
    }

    @Test
    @DisplayName("Un cuerpo de 1 MiB en el resumen se rechaza por pasar de 2000")
    void read_shouldReject_whenBodyHuge() {
        assertRejectedAt(with("/contextoProfesional/resumen", "a".repeat(1_048_576)),
                "contextoProfesional.resumen");
    }

    @Test
    @DisplayName("Con varios campos inválidos se informa el primero en orden alfabético")
    void read_shouldReportFirstFieldAlphabetically_whenSeveralInvalid() {
        ObjectNode root = tree();
        ((ObjectNode) root.get("contextoProfesional")).put("resumen", "").put("nombre", "");

        assertRejectedAt(payload(root), "contextoProfesional.nombre");
    }

    // ---------------------------------------------------------------- propiedades del mensaje

    @ParameterizedTest(name = "message_id={0}")
    @ValueSource(strings = {"abc", "1-1-1-1-1", "3b6f0c2e-9a41-4f55-8d11-0c5a7b2e9d1", " 3b6f0c2e-9a41-4f55-8d11-0c5a7b2e9d10"})
    @DisplayName("Un message_id que no es un UUID canónico se rechaza")
    void read_shouldReject_whenMessageIdInvalid(String messageId) {
        assertRejectedReason(() -> reader.read(payload(tree()), messageId, TYPE, 1),
                ContractViolation.MESSAGE_ID_INVALID);
    }

    @Test
    @DisplayName("Un message_id ausente se rechaza")
    void read_shouldReject_whenMessageIdMissing() {
        assertRejectedReason(() -> reader.read(payload(tree()), null, TYPE, 1), ContractViolation.MESSAGE_ID_INVALID);
    }

    @Test
    @DisplayName("Un message_id en mayúsculas se acepta")
    void read_shouldAccept_whenMessageIdUppercase() {
        var command = reader.read(payload(tree()), MESSAGE_ID.toUpperCase(), TYPE, 1);

        assertThat(command.messageId()).isEqualTo(UUID.fromString(MESSAGE_ID));
    }

    @ParameterizedTest(name = "type={0}")
    @ValueSource(strings = {"cuenta.creada", "", "PERFIL.PROFESIONAL-ACTUALIZADO"})
    @DisplayName("Un type distinto del esperado se rechaza")
    void read_shouldReject_whenTypeInvalid(String type) {
        assertRejectedReason(() -> reader.read(payload(tree()), MESSAGE_ID, type, 1),
                ContractViolation.EVENT_TYPE_INVALID);
    }

    @Test
    @DisplayName("Un type ausente se rechaza")
    void read_shouldReject_whenTypeMissing() {
        assertRejectedReason(() -> reader.read(payload(tree()), MESSAGE_ID, null, 1),
                ContractViolation.EVENT_TYPE_INVALID);
    }

    @ParameterizedTest(name = "versión={0}")
    @MethodSource("badVersions")
    @DisplayName("Una versión que no es un entero igual a 1 se rechaza")
    void read_shouldReject_whenVersionNotOne(Object version) {
        assertRejectedReason(() -> reader.read(payload(tree()), MESSAGE_ID, TYPE, version),
                ContractViolation.EVENT_VERSION_UNSUPPORTED);
    }

    static Stream<Object> badVersions() {
        return Stream.of(2, "1", 2L, 0, -1, 1.0, 1.0f);
    }

    @ParameterizedTest(name = "versión={0}")
    @MethodSource("integerOnes")
    @DisplayName("El entero 1 se acepta sea cual sea su tipo AMQP")
    void read_shouldAccept_whenVersionIsAnyIntegerOne(Object version) {
        assertThat(reader.read(payload(tree()), MESSAGE_ID, TYPE, version)).isNotNull();
    }

    static Stream<Object> integerOnes() {
        return Stream.of(1, 1L, (short) 1, (byte) 1);
    }

    @Test
    @DisplayName("Una versión ausente se rechaza")
    void read_shouldReject_whenVersionMissing() {
        assertRejectedReason(() -> reader.read(payload(tree()), MESSAGE_ID, TYPE, null),
                ContractViolation.EVENT_VERSION_UNSUPPORTED);
    }

    @Test
    @DisplayName("Un cuerpo vacío se rechaza como formato inválido")
    void read_shouldReject_whenPayloadNull() {
        assertRejectedReason(() -> reader.read(null, MESSAGE_ID, TYPE, 1), ContractViolation.PAYLOAD_INVALID_FORMAT);
    }

    @Test
    @DisplayName("Las propiedades se comprueban antes que el cuerpo")
    void read_shouldCheckProperties_beforeBody() {
        assertRejectedReason(() -> reader.read(null, "abc", "otro", 2), ContractViolation.MESSAGE_ID_INVALID);
        assertRejectedReason(() -> reader.read(null, MESSAGE_ID, "otro", 2), ContractViolation.EVENT_TYPE_INVALID);
        assertRejectedReason(() -> reader.read(null, MESSAGE_ID, TYPE, 2),
                ContractViolation.EVENT_VERSION_UNSUPPORTED);
    }

    // ---------------------------------------------------------------- campos de la raíz

    @ParameterizedTest(name = "usuarioId={0}")
    @MethodSource("badUserIds")
    @DisplayName("Un usuarioId inválido se rechaza")
    void read_shouldReject_whenUserIdInvalid(Object userId) {
        assertRejectedAt(with("/usuarioId", userId), "usuarioId");
    }

    static Stream<Object> badUserIds() {
        return Stream.of(null, REMOVE, "", "   ", "u".repeat(129), "uid\u0007", "uid\u0000", "uid​", "uid\n");
    }

    @Test
    @DisplayName("Un usuarioId de 128 puntos de código se acepta")
    void read_shouldAccept_whenUserIdAtLimit() {
        var replica = read(with("/usuarioId", "u".repeat(128))).replica();

        assertThat(replica.owner().value()).hasSize(128);
    }

    @Test
    @DisplayName("Un bloque contextoProfesional nulo o ausente se rechaza")
    void read_shouldReject_whenContextMissing() {
        assertRejectedAt(with("/contextoProfesional", null), "contextoProfesional");
        assertRejectedAt(with("/contextoProfesional", REMOVE), "contextoProfesional");
    }

    @ParameterizedTest(name = "id={0}")
    @MethodSource("badProfileIds")
    @DisplayName("Un id de perfil que no es UUID se rechaza")
    void read_shouldReject_whenProfileIdInvalid(Object id) {
        assertRejectedAt(with("/contextoProfesional/id", id), "contextoProfesional.id");
    }

    static Stream<Object> badProfileIds() {
        return Stream.of(null, "no-uuid", "7c9e6679-7425-40de-944b-e07fc1f90ae");
    }

    @ParameterizedTest(name = "nombre de {1} caracteres")
    @MethodSource("badNames")
    @DisplayName("Un nombre inválido se rechaza")
    void read_shouldReject_whenNameInvalid(Object name, String description) {
        assertRejectedAt(with("/contextoProfesional/nombre", name), "contextoProfesional.nombre");
    }

    static Stream<Arguments> badNames() {
        return Stream.of(Arguments.of(null, "null"), Arguments.of("", "0"), Arguments.of("   ", "3 espacios"),
                Arguments.of("é".repeat(256), "256"), Arguments.of("\u0000", "NUL"));
    }

    @Test
    @DisplayName("255 emojis (510 unidades UTF-16) se aceptan como nombre")
    void read_shouldAccept_whenNameIs255CodePoints() {
        assertThat(read(with("/contextoProfesional/nombre", "😀".repeat(255))).replica().name())
                .hasSize(510);
    }

    @ParameterizedTest(name = "estado={0}")
    @MethodSource("badStatuses")
    @DisplayName("Un estado fuera de los cuatro valores se rechaza")
    void read_shouldReject_whenStatusInvalid(Object status) {
        assertRejectedAt(with("/contextoProfesional/estado", status), "contextoProfesional.estado");
    }

    static Stream<Object> badStatuses() {
        return Stream.of(null, "completed", "ARCHIVED", "");
    }

    @ParameterizedTest(name = "estado={0}")
    @ValueSource(strings = {"PENDING", "IN_PROGRESS", "IN_REVIEW", "COMPLETED"})
    @DisplayName("Los cuatro estados del contrato se aceptan")
    void read_shouldAccept_whenStatusValid(String status) {
        assertThat(read(with("/contextoProfesional/estado", status)).replica().status().name()).isEqualTo(status);
    }

    @ParameterizedTest(name = "fecha_archivado={0}")
    @ValueSource(strings = {"ayer", "2026-02-31T00:00:00Z", "", "2026-10-15"})
    @DisplayName("Una fecha de archivado que no es un instante real se rechaza")
    void read_shouldReject_whenArchivedAtInvalid(String archivedAt) {
        assertRejectedAt(with("/contextoProfesional/fecha_archivado", archivedAt),
                "contextoProfesional.fecha_archivado");
    }

    @ParameterizedTest(name = "fecha_archivado={0}")
    @ValueSource(strings = {"2026-10-15T12:00:00.123Z", "2026-10-15T12:00:00Z"})
    @DisplayName("Una fecha de archivado ISO con o sin milisegundos se acepta")
    void read_shouldAccept_whenArchivedAtValid(String archivedAt) {
        var replica = read(with("/contextoProfesional/fecha_archivado", archivedAt)).replica();

        assertThat(replica.archivedAt()).isEqualTo(Instant.parse(archivedAt));
        assertThat(replica.isUsableForInterview()).isFalse();
    }

    @ParameterizedTest(name = "resumen de {1} caracteres")
    @MethodSource("badSummaries")
    @DisplayName("Un resumen inválido se rechaza")
    void read_shouldReject_whenSummaryInvalid(Object summary, int length) {
        assertRejectedAt(with("/contextoProfesional/resumen", summary), "contextoProfesional.resumen");
    }

    static Stream<Arguments> badSummaries() {
        return Stream.of(Arguments.of(null, 0), Arguments.of("", 0), Arguments.of("a".repeat(2001), 2001));
    }

    @Test
    @DisplayName("Un resumen de 2000 puntos de código se acepta")
    void read_shouldAccept_whenSummaryAtLimit() {
        assertThat(read(with("/contextoProfesional/resumen", "a".repeat(2000))).replica()).isNotNull();
    }

    // ---------------------------------------------------------------- experiencias

    @Test
    @DisplayName("Una lista de experiencias nula o de 5 elementos se rechaza")
    void read_shouldReject_whenExperiencesInvalid() {
        assertRejectedAt(with("/contextoProfesional/experiencias", null), "contextoProfesional.experiencias");
        assertRejectedAt(with("/contextoProfesional/experiencias", copies("/contextoProfesional/experiencias", 5)),
                "contextoProfesional.experiencias");
    }

    @ParameterizedTest(name = "experiencias de {0}")
    @ValueSource(ints = {0, 1, 4})
    @DisplayName("Las experiencias de 0, 1 y 4 elementos se aceptan")
    void read_shouldAccept_whenExperiencesWithinLimits(int count) {
        var list = copies("/contextoProfesional/experiencias", count);

        assertThat(read(with("/contextoProfesional/experiencias", list)).replica()).isNotNull();
    }

    @ParameterizedTest(name = "{0}={1}")
    @MethodSource("badExperienceTexts")
    @DisplayName("Un texto de experiencia fuera de rango se rechaza")
    void read_shouldReject_whenExperienceTextInvalid(String property, String value) {
        assertRejectedAt(with("/contextoProfesional/experiencias/0/" + property, value),
                "contextoProfesional.experiencias[0]." + property);
    }

    static Stream<Arguments> badExperienceTexts() {
        return Stream.of(Arguments.of("empresa", ""), Arguments.of("empresa", "e".repeat(101)),
                Arguments.of("cargo", ""), Arguments.of("cargo", "c".repeat(101)),
                Arguments.of("descripcion", ""), Arguments.of("descripcion", "d".repeat(501)));
    }

    @Test
    @DisplayName("La descripción nula y los límites de empresa, cargo y descripción se aceptan")
    void read_shouldAccept_whenExperienceTextAtLimits() {
        var base = "/contextoProfesional/experiencias/0/";

        assertThat(read(with(base + "descripcion", null)).replica()).isNotNull();
        assertThat(read(with(base + "descripcion", "d".repeat(500))).replica()).isNotNull();
        assertThat(read(with(base + "empresa", "e".repeat(100))).replica()).isNotNull();
        assertThat(read(with(base + "cargo", "c".repeat(100))).replica()).isNotNull();
    }

    @ParameterizedTest(name = "{0}={1}")
    @MethodSource("badYearMonths")
    @DisplayName("Un mes que no es AAAA-MM se rechaza")
    void read_shouldReject_whenYearMonthInvalid(String property, String value, String basePointer, String field) {
        assertRejectedAt(with(basePointer + property, value), field + property);
    }

    static Stream<Arguments> badYearMonths() {
        String experience = "/contextoProfesional/experiencias/0/";
        String education = "/contextoProfesional/formaciones/0/";
        String experienceField = "contextoProfesional.experiencias[0].";
        String educationField = "contextoProfesional.formaciones[0].";
        return Stream.of(
                Arguments.of("inicio", "2022-13", experience, experienceField),
                Arguments.of("inicio", "2022-1", experience, experienceField),
                Arguments.of("fin", "2022-00", experience, experienceField),
                Arguments.of("fin", "22-01", experience, experienceField),
                Arguments.of("inicio", "2022-13", education, educationField),
                Arguments.of("fin", "2022-1", education, educationField));
    }

    @Test
    @DisplayName("El inicio y el fin de una experiencia pueden ser nulos")
    void read_shouldAccept_whenExperienceDatesNull() {
        var base = "/contextoProfesional/experiencias/0/";

        assertThat(read(with(base + "inicio", null)).replica()).isNotNull();
        assertThat(read(with(base + "fin", null)).replica()).isNotNull();
    }

    @ParameterizedTest(name = "estadoEmpleo={0}")
    @ValueSource(strings = {"current", "WORKING", ""})
    @DisplayName("Un estado de empleo fuera de los tres valores se rechaza")
    void read_shouldReject_whenEmploymentStatusInvalid(String status) {
        assertRejectedAt(with("/contextoProfesional/experiencias/0/estadoEmpleo", status),
                "contextoProfesional.experiencias[0].estadoEmpleo");
    }

    // ---------------------------------------------------------------- formaciones

    @Test
    @DisplayName("Una lista de formaciones vacía, nula o de 6 elementos se rechaza")
    void read_shouldReject_whenEducationsInvalid() {
        assertRejectedAt(with("/contextoProfesional/formaciones", mapper.createArrayNode()),
                "contextoProfesional.formaciones");
        assertRejectedAt(with("/contextoProfesional/formaciones", null), "contextoProfesional.formaciones");
        assertRejectedAt(with("/contextoProfesional/formaciones", copies("/contextoProfesional/formaciones", 6)),
                "contextoProfesional.formaciones");
    }

    @ParameterizedTest(name = "formaciones de {0}")
    @ValueSource(ints = {1, 5})
    @DisplayName("Las formaciones de 1 y 5 elementos se aceptan")
    void read_shouldAccept_whenEducationsWithinLimits(int count) {
        var list = copies("/contextoProfesional/formaciones", count);

        assertThat(read(with("/contextoProfesional/formaciones", list)).replica()).isNotNull();
    }

    @ParameterizedTest(name = "{0}={1}")
    @MethodSource("badEducationFields")
    @DisplayName("Un campo de formación inválido se rechaza")
    void read_shouldReject_whenEducationFieldInvalid(String property, Object value) {
        assertRejectedAt(with("/contextoProfesional/formaciones/0/" + property, value),
                "contextoProfesional.formaciones[0]." + property);
    }

    static Stream<Arguments> badEducationFields() {
        return Stream.of(Arguments.of("institucion", "i".repeat(151)), Arguments.of("institucion", ""),
                Arguments.of("titulo", "t".repeat(151)), Arguments.of("titulo", null),
                Arguments.of("nivel", "PHD"), Arguments.of("nivel", null),
                Arguments.of("inicio", null), Arguments.of("enCurso", null));
    }

    @Test
    @DisplayName("Una formación con 150 puntos de código en institución y título, sin fin y con enCurso falso se acepta")
    void read_shouldAccept_whenEducationAtLimits() {
        var base = "/contextoProfesional/formaciones/0/";

        assertThat(read(with(base + "institucion", "i".repeat(150))).replica()).isNotNull();
        assertThat(read(with(base + "titulo", "t".repeat(150))).replica()).isNotNull();
        assertThat(read(with(base + "enCurso", false)).replica()).isNotNull();
    }

    // ---------------------------------------------------------------- habilidades

    @Test
    @DisplayName("Una lista de habilidades vacía, nula o de 21 elementos se rechaza")
    void read_shouldReject_whenSkillsInvalid() {
        assertRejectedAt(with("/contextoProfesional/habilidades", mapper.createArrayNode()),
                "contextoProfesional.habilidades");
        assertRejectedAt(with("/contextoProfesional/habilidades", null), "contextoProfesional.habilidades");
        assertRejectedAt(with("/contextoProfesional/habilidades", copies("/contextoProfesional/habilidades", 21)),
                "contextoProfesional.habilidades");
    }

    @ParameterizedTest(name = "habilidades de {0}")
    @ValueSource(ints = {1, 20})
    @DisplayName("Las habilidades de 1 y 20 elementos se aceptan")
    void read_shouldAccept_whenSkillsWithinLimits(int count) {
        var list = copies("/contextoProfesional/habilidades", count);

        assertThat(read(with("/contextoProfesional/habilidades", list)).replica()).isNotNull();
    }

    @ParameterizedTest(name = "{0}={1}")
    @MethodSource("badSkillFields")
    @DisplayName("Un campo de habilidad inválido se rechaza")
    void read_shouldReject_whenSkillFieldInvalid(String property, Object value) {
        assertRejectedAt(with("/contextoProfesional/habilidades/0/" + property, value),
                "contextoProfesional.habilidades[0]." + property);
    }

    static Stream<Arguments> badSkillFields() {
        return Stream.of(Arguments.of("nombre", "s".repeat(61)), Arguments.of("nombre", ""),
                Arguments.of("nivel", "EXPERT"), Arguments.of("nivel", null));
    }

    @Test
    @DisplayName("Una habilidad con nombre de 60 puntos de código se acepta")
    void read_shouldAccept_whenSkillNameAtLimit() {
        assertThat(read(with("/contextoProfesional/habilidades/0/nombre", "s".repeat(60))).replica()).isNotNull();
    }

    // ---------------------------------------------------------------- roles aspirados

    @Test
    @DisplayName("Una lista de roles nula, vacía o de 6 elementos se rechaza")
    void read_shouldReject_whenRolesInvalid() {
        assertRejectedAt(with("/rolesAspirados", null), "rolesAspirados");
        assertRejectedAt(with("/rolesAspirados", mapper.createArrayNode()), "rolesAspirados");
        assertRejectedAt(with("/rolesAspirados", copies("/rolesAspirados", 6)), "rolesAspirados");
    }

    @ParameterizedTest(name = "roles de {0}")
    @ValueSource(ints = {1, 5})
    @DisplayName("Los roles de 1 y 5 elementos se aceptan")
    void read_shouldAccept_whenRolesWithinLimits(int count) {
        var list = copies("/rolesAspirados", count);

        assertThat(read(with("/rolesAspirados", list)).replica().aspiredRolesJson()).isNotEmpty();
    }

    @ParameterizedTest(name = "{0}={1}")
    @MethodSource("badRoleFields")
    @DisplayName("Un campo de rol aspirado inválido se rechaza")
    void read_shouldReject_whenRoleFieldInvalid(String property, Object value) {
        assertRejectedAt(with("/rolesAspirados/0/" + property, value), "rolesAspirados[0]." + property);
    }

    static Stream<Arguments> badRoleFields() {
        return Stream.of(Arguments.of("codigo", "backend"), Arguments.of("codigo", null),
                Arguments.of("nombre", ""), Arguments.of("nombre", null), Arguments.of("nombre", "r".repeat(256)));
    }

    @Test
    @DisplayName("Un elemento nulo en la lista de roles se rechaza en su índice")
    void read_shouldReject_whenRoleElementNull() {
        ObjectNode root = tree();
        ArrayNode roles = (ArrayNode) root.get("rolesAspirados");
        roles.removeAll();
        roles.addNull();

        assertRejectedAt(payload(root), "rolesAspirados[0]");
    }

    // ---------------------------------------------------------------- actualizadoEn

    @ParameterizedTest(name = "actualizadoEn={0}")
    @MethodSource("badUpdatedAt")
    @DisplayName("Un actualizadoEn que no cumple el formato o no es una fecha real se rechaza")
    void read_shouldReject_whenUpdatedAtInvalid(Object updatedAt) {
        assertRejectedAt(with("/actualizadoEn", updatedAt), "actualizadoEn");
    }

    static Stream<Object> badUpdatedAt() {
        return Stream.of(null, REMOVE, "2026-10-15T12:00:00Z", "2026-02-30T12:00:00.000Z",
                "2026-10-15T12:00:00.123+00:00", "2026-10-15T25:00:00.000Z");
    }
}
