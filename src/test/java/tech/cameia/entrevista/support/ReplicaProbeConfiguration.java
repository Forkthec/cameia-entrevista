package tech.cameia.entrevista.support;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import tech.cameia.entrevista.domain.port.ProfileReplicaRepository;
import tech.cameia.entrevista.domain.port.RoleCatalogReplicaRepository;

/**
 * Sustituye la copia del perfil por {@link ReplicaRepositoryProbe} y la del catálogo de roles por
 * {@link RoleCatalogRepositoryProbe} después de que Spring les ponga su proxy transaccional. Se
 * importa en las pruebas que inyectan fallos.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ReplicaProbeConfiguration {

    /** @return el postprocesador que envuelve al repositorio; corre al final, después de crear los proxies */
    @Bean
    static BeanPostProcessor replicaProbePostProcessor() {
        return new ProbeInstaller();
    }

    private static final class ProbeInstaller implements BeanPostProcessor, Ordered {

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (bean instanceof ProfileReplicaRepository repository && !(bean instanceof ReplicaRepositoryProbe)) {
                return new ReplicaRepositoryProbe(repository);
            }
            if (bean instanceof RoleCatalogReplicaRepository repository
                    && !(bean instanceof RoleCatalogRepositoryProbe)) {
                return new RoleCatalogRepositoryProbe(repository);
            }
            return bean;
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }
}
