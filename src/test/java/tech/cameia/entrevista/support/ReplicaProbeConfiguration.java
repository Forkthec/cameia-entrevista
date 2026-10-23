package tech.cameia.entrevista.support;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import tech.cameia.entrevista.domain.port.ProfileReplicaRepository;

/**
 * Sustituye la copia del perfil por {@link ReplicaRepositoryProbe} después de que Spring le ponga su
 * proxy transaccional. Se importa en las pruebas que inyectan fallos.
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
            return bean;
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }
}
