package io.github.mamoudouhamadou.minibanque.compte;

import java.time.Clock;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Couche métier. {@code @Transactional} : tout ce que fait une méthode réussit ensemble
 * ou est annulé ensemble (atomicité). C'est essentiel pour les virements de l'étape 2.
 *
 * <p>Les dépendances arrivent par le constructeur (« injection de dépendances ») :
 * Spring les fournit, et les tests peuvent en passer d'autres.
 */
@Service
public class CompteService {

    private final CompteRepository comptes;
    private final Clock horloge; // injectée : les tests peuvent fixer l'heure

    public CompteService(CompteRepository comptes, Clock horloge) {
        this.comptes = comptes;
        this.horloge = horloge;
    }

    @Transactional
    public CompteReponse ouvrir(OuvertureCompte demande) {
        Compte compte = Compte.ouvrir(demande.titulaire(), demande.depotInitial(), Instant.now(horloge));
        return CompteReponse.de(comptes.save(compte));
    }

    // readOnly : indique à Hibernate qu'aucune modification n'est prévue (plus léger).
    @Transactional(readOnly = true)
    public CompteReponse trouver(long id) {
        return comptes.findById(id).map(CompteReponse::de).orElseThrow(() -> new CompteIntrouvableException(id));
    }

    @Transactional(readOnly = true)
    public Page<CompteReponse> lister(Pageable page) {
        return comptes.findAll(page).map(CompteReponse::de);
    }
}
