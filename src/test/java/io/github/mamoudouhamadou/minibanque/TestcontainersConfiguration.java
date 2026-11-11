package io.github.mamoudouhamadou.minibanque;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers démarre un VRAI PostgreSQL 17 dans Docker pour les tests.
 * {@code @ServiceConnection} : Spring Boot lit tout seul l'URL, l'utilisateur et le mot de passe
 * du conteneur. Spring réutilise le même contexte (donc le même conteneur) entre les classes de test.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
    }
}
