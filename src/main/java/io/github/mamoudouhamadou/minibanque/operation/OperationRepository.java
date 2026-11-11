package io.github.mamoudouhamadou.minibanque.operation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationRepository extends JpaRepository<Operation, Long> {

    // « Méthodes dérivées » : Spring Data lit le NOM de la méthode et génère la requête.
    // findByCompteId → WHERE compte_id = ? ; le tri vient du Pageable.
    Page<Operation> findByCompteId(Long compteId, Pageable page);

    List<Operation> findByVirementId(UUID virementId);
}
