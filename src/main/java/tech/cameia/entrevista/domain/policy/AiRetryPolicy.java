package tech.cameia.entrevista.domain.policy;

import java.util.List;
import java.util.Optional;

import tech.cameia.entrevista.domain.model.AiProvider;

/**
 * Decide el proveedor de cada intento de un ciclo de hasta tres.
 *
 * <p>Los dos primeros intentos van al proveedor principal y el tercero al de respaldo. Un proveedor que devolvió
 * límite de uso (429) no se vuelve a usar dentro del ciclo: el intento pasa al otro y, si el otro tampoco está
 * disponible, el ciclo termina antes.</p>
 */
public final class AiRetryPolicy {

    /** Intentos máximos de un ciclo. */
    public static final int MAX_ATTEMPTS = 3;

    private static final int FIRST_FALLBACK_ATTEMPT = 3;

    /**
     * Un intento ya hecho.
     *
     * @param provider proveedor con el que se hizo
     * @param limitReached si terminó por límite de uso
     */
    public record Attempt(AiProvider provider, boolean limitReached) {
    }

    /**
     * Proveedor del intento siguiente, o vacío si el ciclo termina.
     *
     * @param done intentos ya hechos, en orden
     * @param fallbackAvailable si el respaldo tiene llave
     * @return el proveedor del siguiente intento, o vacío si ya no se intenta más
     */
    public Optional<AiProvider> next(List<Attempt> done, boolean fallbackAvailable) {
        if (done.size() >= MAX_ATTEMPTS) {
            return Optional.empty();
        }
        AiProvider preferred = done.size() + 1 >= FIRST_FALLBACK_ATTEMPT ? AiProvider.FALLBACK : AiProvider.PRIMARY;
        if (preferred == AiProvider.PRIMARY && limited(done, AiProvider.PRIMARY)) {
            preferred = AiProvider.FALLBACK;
        }
        if (preferred == AiProvider.FALLBACK && (!fallbackAvailable || limited(done, AiProvider.FALLBACK))) {
            preferred = AiProvider.PRIMARY;
        }
        return limited(done, preferred) ? Optional.empty() : Optional.of(preferred);
    }

    private static boolean limited(List<Attempt> done, AiProvider provider) {
        return done.stream().anyMatch(attempt -> attempt.provider() == provider && attempt.limitReached());
    }
}
