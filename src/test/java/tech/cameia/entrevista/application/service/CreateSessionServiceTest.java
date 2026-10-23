package tech.cameia.entrevista.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import tech.cameia.entrevista.application.command.CreateSessionCommand;
import tech.cameia.entrevista.domain.exception.IdempotencyKeyAlreadyExistsException;
import tech.cameia.entrevista.domain.exception.IdempotencyKeyConflictException;
import tech.cameia.entrevista.domain.exception.InvalidRequestException;
import tech.cameia.entrevista.domain.exception.ProfileNotFoundException;
import tech.cameia.entrevista.domain.exception.ProfileSyncPendingException;
import tech.cameia.entrevista.domain.exception.SessionAlreadyExistsException;
import tech.cameia.entrevista.domain.model.AnswerMode;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.IdempotencyKey;
import tech.cameia.entrevista.domain.model.InterviewLanguage;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.Modality;
import tech.cameia.entrevista.domain.model.ProfileReplica;
import tech.cameia.entrevista.domain.model.ReplicatedProfileStatus;
import tech.cameia.entrevista.domain.model.SessionConfiguration;
import tech.cameia.entrevista.domain.model.SessionConfigurationDraft;
import tech.cameia.entrevista.domain.model.SessionSnapshot;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.model.Tone;
import tech.cameia.entrevista.domain.policy.PersonalizationCatalog;
import tech.cameia.entrevista.domain.policy.SessionConfigurationPolicy;
import tech.cameia.entrevista.domain.port.IdempotencyKeyRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.ProfileReplicaRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class CreateSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final FirebaseUid OWNER = new FirebaseUid("uid-a");
    private static final UUID PROFILE_ID = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final UUID ROLE_ID = UUID.fromString("a0000001-0000-0000-0000-000000000002");
    private static final String CONTEXT = "{\"resumen\":\"Backend con Java\",\"habilidades\":[\"Java\"]}";
    private static final String ROLES = "[\"a0000001-0000-0000-0000-000000000002\"]";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final FakeProfiles profiles = new FakeProfiles();
    private final FakeSessions sessions = new FakeSessions();
    private final FakeKeys keys = new FakeKeys();
    private final FakeTransactions transactions = new FakeTransactions();
    private final CreateSessionService service = new CreateSessionService(
            new SessionConfigurationPolicy(new PersonalizationCatalog(), id -> id.equals(ROLE_ID)), profiles,
            sessions, keys, new TransactionTemplate(transactions), JSON, CLOCK);

    private static ProfileReplica profile(FirebaseUid owner, ReplicatedProfileStatus status, Instant archivedAt) {
        return new ProfileReplica(PROFILE_ID, owner, "Perfil de Ana", status, archivedAt, CONTEXT, ROLES, NOW);
    }

    private static SessionConfigurationDraft draft(String tone, String key) {
        return new SessionConfigurationDraft(PROFILE_ID.toString(), ROLE_ID.toString(), "Buscamos", "SIMULACION",
                tone, "TEXTO", "es-CO", key);
    }

    private CreateSessionResult create(SessionConfigurationDraft draft) {
        return service.create(new CreateSessionCommand(OWNER, draft));
    }

    private void givenUsableProfile() {
        profiles.profile = profile(OWNER, ReplicatedProfileStatus.COMPLETED, null);
    }

    private static InterviewSession existing(SessionStatus status, int retries) {
        SessionConfiguration old = new SessionConfiguration(UUID.randomUUID(), UUID.randomUUID(), "vieja", "{}",
                Modality.SIMULATION, Tone.STRICT, AnswerMode.AUDIO, InterviewLanguage.ENGLISH);
        return InterviewSession.reconstitute(new SessionSnapshot(UUID.randomUUID(), OWNER, old, status, retries,
                Instant.parse("2026-10-19T10:00:00Z"), Instant.parse("2026-10-19T10:00:00Z"), null, null, 2L));
    }

    @Test
    @DisplayName("Sin sesión previa crea una nueva configurada y responde 201")
    void create_shouldCreateNewSession_whenNoneConfigured() {
        givenUsableProfile();

        CreateSessionResult result = create(draft("CALIDO", null));

        assertThat(result.httpStatus()).isEqualTo(201);
        assertThat(result.status()).isEqualTo(SessionStatus.CONFIGURED);
        assertThat(result.ongoing()).isNull();
        assertThat(result.configuration().tone()).isEqualTo(Tone.WARM);
        InterviewSession saved = sessions.byId.get(result.sessionId());
        assertThat(saved.owner()).isEqualTo(OWNER);
        assertThat(saved.createdAt()).isEqualTo(NOW);
        assertThat(saved.planningRetries()).isZero();
        assertThat(saved.configuration().jobOffer()).isEqualTo("Buscamos");
        assertThat(sessions.byId).hasSize(1);
        assertThat(transactions.commits).isEqualTo(1);
    }

    @Test
    @DisplayName("La copia del perfil queda con nombre, contexto, roles y fecha del perfil")
    void create_shouldStoreProfileSnapshot() {
        givenUsableProfile();

        CreateSessionResult result = create(draft("CALIDO", null));

        JsonNode snapshot = JSON.readTree(sessions.byId.get(result.sessionId()).configuration().profileSnapshotJson());
        assertThat(snapshot.get("perfilId").asString()).isEqualTo(PROFILE_ID.toString());
        assertThat(snapshot.get("nombre").asString()).isEqualTo("Perfil de Ana");
        assertThat(snapshot.get("contextoProfesional").get("resumen").asString()).isEqualTo("Backend con Java");
        assertThat(snapshot.get("rolesAspirados").get(0).asString()).isEqualTo(ROLE_ID.toString());
        assertThat(snapshot.get("actualizadoEn").asString()).isEqualTo(NOW.toString());
        assertThat(snapshot.propertyNames()).containsExactlyInAnyOrder("perfilId", "nombre", "contextoProfesional",
                "rolesAspirados", "actualizadoEn");
    }

    @Test
    @DisplayName("Con una sesión configurada la reutiliza: mismo id, configuración nueva y reintentos en 0")
    void create_shouldReuseConfigured_whenOneExists() {
        givenUsableProfile();
        InterviewSession old = existing(SessionStatus.CONFIGURED, 2);
        sessions.byId.put(old.id(), old);

        CreateSessionResult result = create(draft("ESTRICTO", null));

        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.sessionId()).isEqualTo(old.id());
        assertThat(sessions.byId).hasSize(1);
        InterviewSession saved = sessions.byId.get(old.id());
        assertThat(saved.configuration().tone()).isEqualTo(Tone.STRICT);
        assertThat(saved.configuration().jobOffer()).isEqualTo("Buscamos");
        assertThat(saved.planningRetries()).isZero();
        assertThat(saved.updatedAt()).isEqualTo(NOW);
        assertThat(saved.createdAt()).isEqualTo(Instant.parse("2026-10-19T10:00:00Z"));
    }

    @Test
    @DisplayName("Con una entrevista en curso la informa con sus respuestas y no la cambia")
    void create_shouldReportOngoing_whenInProgress() {
        givenUsableProfile();
        InterviewSession running = existing(SessionStatus.IN_PROGRESS, 0);
        sessions.byId.put(running.id(), running);
        sessions.answers.put(running.id(), 2);

        CreateSessionResult result = create(draft("CALIDO", null));

        assertThat(result.httpStatus()).isEqualTo(201);
        assertThat(result.sessionId()).isNotEqualTo(running.id());
        assertThat(result.ongoing()).isEqualTo(new OngoingSession(running.id(), 2));
        assertThat(sessions.byId.get(running.id()).status()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(sessions.byId).hasSize(2);
    }

    @Test
    @DisplayName("Reutilizar la configurada con otra en curso informa la entrevista en curso")
    void create_shouldReuseAndReportOngoing_whenBothExist() {
        givenUsableProfile();
        InterviewSession configured = existing(SessionStatus.CONFIGURED, 1);
        InterviewSession running = existing(SessionStatus.IN_PROGRESS, 0);
        sessions.byId.put(configured.id(), configured);
        sessions.byId.put(running.id(), running);

        CreateSessionResult result = create(draft("CALIDO", null));

        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.sessionId()).isEqualTo(configured.id());
        assertThat(result.ongoing()).isEqualTo(new OngoingSession(running.id(), 0));
    }

    @Test
    @DisplayName("Sin copia local del perfil responde que se está actualizando y no guarda nada")
    void create_shouldThrowSyncPending_whenProfileNotReplicated() {
        assertThatThrownBy(() -> create(draft("CALIDO", "k1"))).isInstanceOf(ProfileSyncPendingException.class)
                .hasMessage("Tu perfil se está actualizando. Inténtalo en unos segundos.");

        assertThat(sessions.byId).isEmpty();
        assertThat(keys.stored).isEmpty();
        assertThat(transactions.rollbacks).isEqualTo(1);
    }

    @Test
    @DisplayName("Un perfil de otra persona responde no encontrado")
    void create_shouldThrowNotFound_whenProfileOfOtherUser() {
        profiles.profile = profile(new FirebaseUid("uid-b"), ReplicatedProfileStatus.COMPLETED, null);

        assertThatThrownBy(() -> create(draft("CALIDO", null))).isInstanceOf(ProfileNotFoundException.class)
                .hasMessage("No encontramos lo que buscabas.");
        assertThat(sessions.byId).isEmpty();
    }

    @Test
    @DisplayName("El dueño se comprueba antes que el uso: un perfil ajeno y archivado da no encontrado")
    void create_shouldCheckOwnerBeforeUsability() {
        profiles.profile = profile(new FirebaseUid("uid-b"), ReplicatedProfileStatus.PENDING, NOW);

        assertThatThrownBy(() -> create(draft("CALIDO", null))).isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    @DisplayName("Un perfil incompleto o archivado responde que se está actualizando")
    void create_shouldThrowSyncPending_whenProfileNotUsable() {
        profiles.profile = profile(OWNER, ReplicatedProfileStatus.IN_PROGRESS, null);
        assertThatThrownBy(() -> create(draft("CALIDO", null))).isInstanceOf(ProfileSyncPendingException.class);

        profiles.profile = profile(OWNER, ReplicatedProfileStatus.COMPLETED, NOW);
        assertThatThrownBy(() -> create(draft("CALIDO", null))).isInstanceOf(ProfileSyncPendingException.class);

        assertThat(sessions.byId).isEmpty();
    }

    @Test
    @DisplayName("Con clave nueva guarda la clave con su estado, su huella y el cuerpo de la respuesta")
    void create_shouldStoreKey_whenKeyGiven() {
        givenUsableProfile();

        CreateSessionResult result = create(draft("CALIDO", "clave-1"));

        IdempotencyKey saved = keys.stored.get("uid-a|clave-1");
        assertThat(saved.operation()).isEqualTo("CREAR_SESION");
        assertThat(saved.owner()).isEqualTo(OWNER);
        assertThat(saved.responseStatus()).isEqualTo(201);
        assertThat(saved.requestFingerprint()).isEqualTo(result.configuration().fingerprint());
        assertThat(saved.createdAt()).isEqualTo(NOW);
        assertThat(JSON.readTree(saved.responseBodyJson()).get("sessionId").asString())
                .isEqualTo(result.sessionId().toString());
    }

    @Test
    @DisplayName("Sin clave no guarda ninguna clave")
    void create_shouldNotStoreKey_whenNoKey() {
        givenUsableProfile();

        create(draft("CALIDO", null));

        assertThat(keys.stored).isEmpty();
    }

    @Test
    @DisplayName("La misma clave con la misma configuración repite la respuesta sin escribir ni leer el perfil")
    void create_shouldReplay_whenSameKeySameBody() {
        givenUsableProfile();
        CreateSessionResult first = create(draft("CALIDO", "clave-1"));
        sessions.saves = 0;
        profiles.reads = 0;

        CreateSessionResult second = create(draft("CALIDO", "clave-1"));

        assertThat(second).isEqualTo(first);
        assertThat(sessions.saves).isZero();
        assertThat(profiles.reads).isZero();
        assertThat(sessions.byId).hasSize(1);
        assertThat(transactions.commits).isEqualTo(1);
    }

    @Test
    @DisplayName("Repetir con la misma clave una respuesta 200 repite el 200")
    void create_shouldReplayStatus200_whenFirstWasReuse() {
        givenUsableProfile();
        InterviewSession old = existing(SessionStatus.CONFIGURED, 0);
        sessions.byId.put(old.id(), old);
        CreateSessionResult first = create(draft("CALIDO", "clave-1"));

        CreateSessionResult second = create(draft("CALIDO", "clave-1"));

        assertThat(first.httpStatus()).isEqualTo(200);
        assertThat(second.httpStatus()).isEqualTo(200);
        assertThat(second.sessionId()).isEqualTo(old.id());
    }

    @Test
    @DisplayName("Repetir con la misma clave y espacios de más en el cuerpo es la misma solicitud")
    void create_shouldReplay_whenOnlyWhitespaceDiffers() {
        givenUsableProfile();
        CreateSessionResult first = create(draft("CALIDO", "clave-1"));

        CreateSessionResult second = create(new SessionConfigurationDraft(PROFILE_ID.toString(), ROLE_ID.toString(),
                "  Buscamos \n", "SIMULACION", " CALIDO", "TEXTO", "es-CO", "clave-1"));

        assertThat(second.sessionId()).isEqualTo(first.sessionId());
        assertThat(sessions.byId).hasSize(1);
    }

    @Test
    @DisplayName("La misma clave con otra configuración responde conflicto y no cambia nada")
    void create_shouldThrowConflict_whenSameKeyDifferentBody() {
        givenUsableProfile();
        create(draft("CALIDO", "clave-1"));
        int savesBefore = sessions.saves;

        assertThatThrownBy(() -> create(draft("ESTRICTO", "clave-1")))
                .isInstanceOf(IdempotencyKeyConflictException.class)
                .hasMessage("Esta solicitud no coincide con la que enviaste antes. Inténtalo de nuevo.");

        assertThat(sessions.saves).isEqualTo(savesBefore);
        assertThat(keys.stored).hasSize(1);
    }

    @Test
    @DisplayName("La misma clave de otra persona no es la misma clave")
    void create_shouldNotShareKeys_betweenUsers() {
        givenUsableProfile();
        create(draft("CALIDO", "clave-1"));
        profiles.profile = new ProfileReplica(PROFILE_ID, new FirebaseUid("uid-b"), "Otro", ReplicatedProfileStatus.COMPLETED,
                null, CONTEXT, ROLES, NOW);

        CreateSessionResult other = service.create(
                new CreateSessionCommand(new FirebaseUid("uid-b"), draft("ESTRICTO", "clave-1")));

        assertThat(other.httpStatus()).isEqualTo(201);
        assertThat(keys.stored).hasSize(2);
    }

    @Test
    @DisplayName("Si otra petición guardó la misma clave a la vez, repite su respuesta fuera de la transacción")
    void create_shouldReplay_whenSameKeyConcurrent() {
        givenUsableProfile();
        CreateSessionResult winner = create(draft("CALIDO", "clave-1"));
        keys.hideFromFirstFind = true;

        CreateSessionResult loser = create(draft("CALIDO", "clave-1"));

        assertThat(loser).isEqualTo(winner);
        assertThat(transactions.rollbacks).isEqualTo(1);
    }

    @Test
    @DisplayName("Si en la carrera la clave ganadora era de otra configuración, responde conflicto")
    void create_shouldThrowConflict_whenRaceWinnerHadOtherBody() {
        givenUsableProfile();
        create(draft("ESTRICTO", "clave-1"));
        keys.hideFromFirstFind = true;

        assertThatThrownBy(() -> create(draft("CALIDO", "clave-1")))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    @Test
    @DisplayName("Si la clave desaparece tras la carrera, se propaga el error original")
    void create_shouldPropagateRace_whenKeyVanishes() {
        givenUsableProfile();
        keys.alwaysFailSave = true;

        assertThatThrownBy(() -> create(draft("CALIDO", "clave-1")))
                .isInstanceOf(IdempotencyKeyAlreadyExistsException.class);
    }

    @Test
    @DisplayName("Dos creaciones simultáneas sin clave: la perdedora recibe SESSION_ALREADY_EXISTS sin atraparlo")
    void create_shouldPropagateSessionAlreadyExists() {
        givenUsableProfile();
        sessions.failNextSaveWith = new SessionAlreadyExistsException();

        assertThatThrownBy(() -> create(draft("CALIDO", null))).isInstanceOf(SessionAlreadyExistsException.class);
        assertThat(transactions.rollbacks).isEqualTo(1);
    }

    @Test
    @DisplayName("Con clave, si la sesión configurada la creó a la vez la ganadora, repite la respuesta guardada")
    void create_shouldReplay_whenSessionRaceHasSavedKey() {
        givenUsableProfile();
        CreateSessionResult winner = create(draft("CALIDO", "clave-1"));
        keys.hideFromFirstFind = true;
        sessions.failNextSaveWith = new SessionAlreadyExistsException();

        CreateSessionResult loser = create(draft("CALIDO", "clave-1"));

        assertThat(loser).isEqualTo(winner);
    }

    @Test
    @DisplayName("Si falla guardar la clave la transacción se revierte y el error se propaga")
    void create_shouldRollback_whenKeySaveFails() {
        givenUsableProfile();
        keys.failSaveWith = new IllegalStateException("fallo imprevisto");

        assertThatThrownBy(() -> create(draft("CALIDO", "clave-1"))).isInstanceOf(IllegalStateException.class);

        assertThat(transactions.rollbacks).isEqualTo(1);
        assertThat(transactions.commits).isZero();
    }

    @Test
    @DisplayName("Una configuración inválida se rechaza antes de leer perfil, sesiones o claves")
    void create_shouldRejectBeforeAnyRead_whenInvalid() {
        SessionConfigurationDraft invalid = new SessionConfigurationDraft(null, null, null, null, null, null, null,
                "clave-1");

        assertThatThrownBy(() -> create(invalid)).isInstanceOf(InvalidRequestException.class);

        assertThat(profiles.reads).isZero();
        assertThat(sessions.reads).isZero();
        assertThat(keys.finds).isZero();
        assertThat(transactions.started).isZero();
    }

    // ---- Dobles en memoria ----

    private static final class FakeProfiles implements ProfileReplicaRepository {
        ProfileReplica profile;
        int reads;

        @Override
        public Optional<ProfileReplica> findById(UUID profileId) {
            reads++;
            return Optional.ofNullable(profile).filter(found -> found.profileId().equals(profileId));
        }

        @Override
        public boolean upsertIfNotOlder(ProfileReplica replica, Instant replicatedAt) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeSessions implements InterviewSessionRepository {
        final Map<UUID, InterviewSession> byId = new LinkedHashMap<>();
        final Map<UUID, Integer> answers = new HashMap<>();
        int saves;
        int reads;
        RuntimeException failNextSaveWith;

        @Override
        public InterviewSession save(InterviewSession session) {
            if (failNextSaveWith != null) {
                RuntimeException error = failNextSaveWith;
                failNextSaveWith = null;
                throw error;
            }
            saves++;
            byId.put(session.id(), session);
            return session;
        }

        @Override
        public Optional<InterviewSession> findByIdAndOwner(UUID id, FirebaseUid owner) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<InterviewSession> findConfiguredByOwner(FirebaseUid owner) {
            return find(owner, SessionStatus.CONFIGURED);
        }

        @Override
        public Optional<InterviewSession> findInProgressByOwner(FirebaseUid owner) {
            return find(owner, SessionStatus.IN_PROGRESS);
        }

        private Optional<InterviewSession> find(FirebaseUid owner, SessionStatus status) {
            reads++;
            return byId.values().stream()
                    .filter(session -> session.owner().equals(owner) && session.status() == status).findFirst();
        }

        @Override
        public int countDefinitiveAnswers(UUID sessionId) {
            return answers.getOrDefault(sessionId, 0);
        }

        @Override
        public void deleteConfigured(UUID id, FirebaseUid owner) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeKeys implements IdempotencyKeyRepository {
        /** Claves guardadas, por «dueño|operación|clave» (la unicidad de la base). */
        final Map<String, IdempotencyKey> byIdentity = new LinkedHashMap<>();
        /** Vista por clave enviada, para las comprobaciones de las pruebas con un solo dueño. */
        final Map<String, IdempotencyKey> stored = new LinkedHashMap<>();
        boolean hideFromFirstFind;
        boolean alwaysFailSave;
        RuntimeException failSaveWith;
        int finds;

        private static String identity(FirebaseUid owner, String operation, String key) {
            return owner.value() + "|" + operation + "|" + key;
        }

        @Override
        public Optional<IdempotencyKey> find(FirebaseUid owner, String operation, String key) {
            finds++;
            if (hideFromFirstFind) {
                hideFromFirstFind = false;
                return Optional.empty();
            }
            return Optional.ofNullable(byIdentity.get(identity(owner, operation, key)));
        }

        @Override
        public IdempotencyKey save(IdempotencyKey key) {
            if (failSaveWith != null) {
                throw failSaveWith;
            }
            String identity = identity(key.owner(), key.operation(), key.key());
            if (alwaysFailSave || byIdentity.containsKey(identity)) {
                throw new IdempotencyKeyAlreadyExistsException();
            }
            byIdentity.put(identity, key);
            stored.put(key.owner().value() + "|" + key.key(), key);
            return key;
        }
    }

    private static final class FakeTransactions implements PlatformTransactionManager {
        int started;
        int commits;
        int rollbacks;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            started++;
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            commits++;
        }

        @Override
        public void rollback(TransactionStatus status) {
            rollbacks++;
        }
    }
}
