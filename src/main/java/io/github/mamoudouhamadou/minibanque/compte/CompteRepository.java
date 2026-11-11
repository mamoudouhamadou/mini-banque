package io.github.mamoudouhamadou.minibanque.compte;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA écrit l'implémentation pour nous : save, findById, findAll(Pageable),
 * deleteAll… sont déjà disponibles, avec le SQL généré par Hibernate.
 */
public interface CompteRepository extends JpaRepository<Compte, Long> {

    /**
     * Lit un compte ET le verrouille jusqu'à la fin de la transaction (SQL : SELECT … FOR UPDATE).
     * Une autre transaction qui veut le même compte ATTEND son tour : deux retraits simultanés
     * ne peuvent plus lire le même solde et dépenser deux fois le même argent.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Compte c where c.id = :id")
    Optional<Compte> verrouiller(@Param("id") long id);
}
