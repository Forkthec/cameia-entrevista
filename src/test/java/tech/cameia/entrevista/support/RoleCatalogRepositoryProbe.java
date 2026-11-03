package tech.cameia.entrevista.support;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import java.util.function.UnaryOperator;

import tech.cameia.entrevista.domain.model.ProfessionalRoleReplica;
import tech.cameia.entrevista.domain.port.RoleCatalogReplicaRepository;

/**
 * Decorador de prueba de la copia del catálogo de roles: cuenta las llamadas y deja inyectar un fallo
 * justo donde ocurriría dentro de la transacción del caso de uso. Envuelve al repositorio ya con su
 * proxy transaccional, así que la transacción se comporta igual que en producción.
 */
public final class RoleCatalogRepositoryProbe implements RoleCatalogReplicaRepository {

    private final RoleCatalogReplicaRepository delegate;
    private final AtomicInteger upsertCalls = new AtomicInteger();
    private volatile UnaryOperator<IntSupplier> aroundUpsert = real -> real;

    /**
     * @param delegate repositorio real, ya con su proxy transaccional
     */
    public RoleCatalogRepositoryProbe(RoleCatalogReplicaRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public int upsertSnapshot(List<ProfessionalRoleReplica> roles, Instant snapshotAt, Instant replicatedAt) {
        upsertCalls.incrementAndGet();
        IntSupplier real = () -> delegate.upsertSnapshot(roles, snapshotAt, replicatedAt);
        return aroundUpsert.apply(real).getAsInt();
    }

    /** @param wrapper recibe la escritura real y devuelve lo que se ejecuta en su lugar */
    public void aroundUpsert(UnaryOperator<IntSupplier> wrapper) {
        this.aroundUpsert = wrapper;
    }

    /** @return escrituras pedidas desde el último {@link #reset()} */
    public int upsertCalls() {
        return upsertCalls.get();
    }

    /** Quita el gancho y pone el contador en cero. */
    public void reset() {
        aroundUpsert = real -> real;
        upsertCalls.set(0);
    }
}
