package io.github.mamoudouhamadou.minibanque;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

/**
 * Point d'entrée. {@code @SpringBootApplication} active la configuration automatique :
 * Spring détecte les dépendances (Web, JPA, PostgreSQL, Flyway…) et configure tout seul.
 */
@SpringBootApplication
// Pages JSON stables : { "content": [...], "page": { "size", "number", "totalElements", "totalPages" } }
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class MiniBanqueApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniBanqueApplication.class, args);
    }

    /** L'horloge du système, injectable : les tests peuvent la remplacer par une heure fixe. */
    @Bean
    Clock horloge() {
        return Clock.systemUTC();
    }
}
