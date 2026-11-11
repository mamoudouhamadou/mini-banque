package io.github.mamoudouhamadou.minibanque.client;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, Long> {

    // Les courriels sont stockés en minuscules (voir AuthService) : la recherche exacte suffit.
    Optional<Client> findByCourriel(String courriel);

    boolean existsByCourriel(String courriel);
}
