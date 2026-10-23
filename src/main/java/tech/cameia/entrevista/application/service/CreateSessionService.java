package tech.cameia.entrevista.application.service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import tech.cameia.entrevista.application.command.CreateSessionCommand;
import tech.cameia.entrevista.domain.exception.IdempotencyKeyAlreadyExistsException;
import tech.cameia.entrevista.domain.exception.IdempotencyKeyConflictException;
import tech.cameia.entrevista.domain.exception.ProfileNotFoundException;
import tech.cameia.entrevista.domain.exception.ProfileSyncPendingException;
import tech.cameia.entrevista.domain.exception.SessionAlreadyExistsException;
import tech.cameia.entrevista.domain.model.FirebaseUid;
import tech.cameia.entrevista.domain.model.IdempotencyKey;
import tech.cameia.entrevista.domain.model.InterviewSession;
import tech.cameia.entrevista.domain.model.ProfileReplica;
import tech.cameia.entrevista.domain.model.SessionConfiguration;
import tech.cameia.entrevista.domain.model.SessionStatus;
import tech.cameia.entrevista.domain.model.ValidatedSessionRequest;
import tech.cameia.entrevista.domain.policy.SessionConfigurationPolicy;
import tech.cameia.entrevista.domain.port.IdempotencyKeyRepository;
import tech.cameia.entrevista.domain.port.InterviewSessionRepository;
import tech.cameia.entrevista.domain.port.ProfileReplicaRepository;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Crea la sesión configurada de la persona o reutiliza la que ya tenía, una sola vez por clave anti-duplicados.
 *
 * <p>Orden: (1) valida todo; (2) con clave, si ya existe, repite su respuesta o rechaza otra configuración;
 * (3) en una transacción: comprueba el perfil en la copia local, crea o reutiliza la sesión, informa la entrevista
 * en curso y, con clave, la guarda junto con la respuesta; (4) si otra petición guardó la misma clave a la vez, se
 * relee fuera de la transacción revertida.</p>
 *
 * <p>No llama a Perfil: lee la copia local que mantienen los eventos. No crea preguntas, turnos ni ejecuciones de IA.</p>
 */
@Service
public class CreateSessionService {

    /** Operación con la que se guarda la clave anti-duplicados. */
    static final String OPERATION = "CREAR_SESION";

    private static final int CREATED = 201;
    private static final int REUSED = 200;

    private static final Logger log = LoggerFactory.getLogger(CreateSessionService.class);

    private final SessionConfigurationPolicy policy;
    private final ProfileReplicaRepository profiles;
    private final InterviewSessionRepository sessions;
    private final IdempotencyKeyRepository keys;
    private final TransactionTemplate transaction;
    private final JsonMapper json;
    private final Clock clock;

    CreateSessionService(SessionConfigurationPolicy policy, ProfileReplicaRepository profiles,
            InterviewSessionRepository sessions, IdempotencyKeyRepository keys, TransactionTemplate transaction,
            JsonMapper json, Clock clock) {
        this.policy = policy;
        this.profiles = profiles;
        this.sessions = sessions;
        this.keys = keys;
        this.transaction = transaction;
        this.json = json;
        this.clock = clock;
    }

    /**
     * Crea o reutiliza la sesión configurada.
     *
     * @param command dueño y configuración enviada
     * @return la sesión con su estado HTTP (201 nueva, 200 reutilizada) y la entrevista en curso, si hay
     * @throws tech.cameia.entrevista.domain.exception.InvalidRequestException si algún campo es inválido
     * @throws ProfileSyncPendingException si la copia del perfil falta o todavía no sirve
     * @throws ProfileNotFoundException si el perfil es de otra persona
     * @throws IdempotencyKeyConflictException si la clave ya se usó con otra configuración
     */
    public CreateSessionResult create(CreateSessionCommand command) {
        ValidatedSessionRequest request = policy.validate(command.draft());
        String fingerprint = request.fingerprint();
        Optional<CreateSessionResult> replay = replayIfKnown(command.owner(), request, fingerprint);
        if (replay.isPresent()) {
            return replay.get();
        }
        CreateSessionResult result;
        try {
            result = transaction.execute(status -> createOrReuse(command.owner(), request, fingerprint));
        } catch (IdempotencyKeyAlreadyExistsException | SessionAlreadyExistsException race) {
            // Otra petición terminó primero (con la misma clave, o creando la sesión configurada que esta también
            // iba a crear): si su clave ya quedó guardada se repite su respuesta o se rechaza si era otra
            // configuración; si no, el error original llega al cliente. Va fuera de la transacción revertida.
            return replayIfKnown(command.owner(), request, fingerprint).orElseThrow(() -> race);
        }
        // Solo configuración confirmada: la configuración y el perfil nunca se registran.
        log.info("Sesión de entrevista configurada [sessionId={}, firebaseUid={}, estado={}]", result.sessionId(),
                command.owner().value(), result.status());
        return result;
    }

    private Optional<CreateSessionResult> replayIfKnown(FirebaseUid owner, ValidatedSessionRequest request,
            String fingerprint) {
        if (request.idempotencyKey() == null) {
            return Optional.empty();
        }
        return keys.find(owner, OPERATION, request.idempotencyKey()).map(saved -> {
            if (!saved.requestFingerprint().equals(fingerprint)) {
                throw new IdempotencyKeyConflictException();
            }
            return fromJson(saved);
        });
    }

    private CreateSessionResult createOrReuse(FirebaseUid owner, ValidatedSessionRequest request,
            String fingerprint) {
        SessionConfiguration configuration = configurationOf(request, requireUsableProfile(owner, request));
        Optional<InterviewSession> configured = sessions.findConfiguredByOwner(owner);
        InterviewSession saved;
        int httpStatus;
        if (configured.isPresent()) {
            InterviewSession session = configured.get();
            session.reconfigure(configuration, clock);
            saved = sessions.save(session);
            httpStatus = REUSED;
        } else {
            saved = sessions.save(InterviewSession.configure(UUID.randomUUID(), owner, configuration, clock));
            httpStatus = CREATED;
        }
        CreateSessionResult result = new CreateSessionResult(httpStatus, saved.id(), saved.status(), request,
                ongoingOf(owner));
        if (request.idempotencyKey() != null) {
            keys.save(new IdempotencyKey(UUID.randomUUID(), owner, OPERATION, request.idempotencyKey(), fingerprint,
                    httpStatus, toJson(result), clock.instant()));
        }
        return result;
    }

    // El dueño se comprueba antes que el uso: un perfil ajeno nunca revela que existe.
    private ProfileReplica requireUsableProfile(FirebaseUid owner, ValidatedSessionRequest request) {
        ProfileReplica profile = profiles.findById(request.profileId()).orElseThrow(ProfileSyncPendingException::new);
        if (!profile.owner().equals(owner)) {
            throw new ProfileNotFoundException();
        }
        if (!profile.isUsableForInterview()) {
            throw new ProfileSyncPendingException();
        }
        return profile;
    }

    private SessionConfiguration configurationOf(ValidatedSessionRequest request, ProfileReplica profile) {
        return new SessionConfiguration(request.profileId(), request.targetRoleId(), request.jobOffer(),
                snapshotOf(profile), request.mode(), request.tone(), request.answerFormat(),
                request.sessionLanguage());
    }

    // Copia del contexto del perfil en este instante: no cambia aunque llegue después otro evento del perfil.
    private String snapshotOf(ProfileReplica profile) {
        ObjectNode snapshot = json.createObjectNode();
        snapshot.put("perfilId", profile.profileId().toString());
        snapshot.put("nombre", profile.name());
        snapshot.set("contextoProfesional", json.readTree(profile.professionalContextJson()));
        snapshot.set("rolesAspirados", json.readTree(profile.aspiredRolesJson()));
        snapshot.put("actualizadoEn", profile.updatedAt().toString());
        return json.writeValueAsString(snapshot);
    }

    private OngoingSession ongoingOf(FirebaseUid owner) {
        return sessions.findInProgressByOwner(owner)
                .map(session -> new OngoingSession(session.id(), sessions.countDefinitiveAnswers(session.id())))
                .orElse(null);
    }

    // La respuesta guardada es el resultado sin su estado HTTP, que va en su propia columna.
    private record StoredBody(UUID sessionId, SessionStatus status, ValidatedSessionRequest configuration,
            OngoingSession ongoing) {
    }

    private String toJson(CreateSessionResult result) {
        return json.writeValueAsString(
                new StoredBody(result.sessionId(), result.status(), result.configuration(), result.ongoing()));
    }

    private CreateSessionResult fromJson(IdempotencyKey saved) {
        StoredBody body = json.readValue(saved.responseBodyJson(), StoredBody.class);
        return new CreateSessionResult(saved.responseStatus(), body.sessionId(), body.status(), body.configuration(),
                body.ongoing());
    }
}
