package tech.cameia.entrevista.support;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.UnaryOperator;

import tech.cameia.entrevista.domain.model.ProfileReplica;
import tech.cameia.entrevista.domain.port.ProfileReplicaRepository;

/**
 * Decorador de prueba de la copia del perfil: cuenta las llamadas y deja inyectar un fallo o una
 * espera justo donde ocurriría dentro de la transacción del caso de uso. Envuelve al repositorio
 * ya con su proxy transaccional, así que la transacción se comporta igual que en producción.
 */
public final class ReplicaRepositoryProbe implements ProfileReplicaRepository {

    private final ProfileReplicaRepository delegate;
    private final AtomicInteger findCalls = new AtomicInteger();
    private final AtomicInteger upsertCalls = new AtomicInteger();
    private volatile Runnable beforeFind = () -> { };
    private volatile UnaryOperator<BooleanSupplier> aroundUpsert = real -> real;

    /**
     * @param delegate repositorio real, ya con su proxy transaccional
     */
    public ReplicaRepositoryProbe(ProfileReplicaRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<ProfileReplica> findById(UUID profileId) {
        findCalls.incrementAndGet();
        beforeFind.run();
        return delegate.findById(profileId);
    }

    @Override
    public boolean upsertIfNotOlder(ProfileReplica replica, Instant replicatedAt) {
        upsertCalls.incrementAndGet();
        BooleanSupplier real = () -> delegate.upsertIfNotOlder(replica, replicatedAt);
        return aroundUpsert.apply(real).getAsBoolean();
    }

    /** @param action se ejecuta antes de cada lectura; puede lanzar */
    public void beforeFind(Runnable action) {
        this.beforeFind = action;
    }

    /** @param wrapper recibe el reemplazo real y devuelve lo que se ejecuta en su lugar */
    public void aroundUpsert(UnaryOperator<BooleanSupplier> wrapper) {
        this.aroundUpsert = wrapper;
    }

    /** @return lecturas por identificador desde el último {@link #reset()} */
    public int findCalls() {
        return findCalls.get();
    }

    /** @return reemplazos pedidos desde el último {@link #reset()} */
    public int upsertCalls() {
        return upsertCalls.get();
    }

    /** Quita los ganchos y pone los contadores en cero. */
    public void reset() {
        beforeFind = () -> { };
        aroundUpsert = real -> real;
        findCalls.set(0);
        upsertCalls.set(0);
    }
}
