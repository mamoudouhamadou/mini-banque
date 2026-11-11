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

    // Chaque méthode reçoit l'id du client connecté : on ne travaille QUE sur ses comptes.

    @Transactional
    public CompteReponse ouvrir(long clientId, OuvertureCompte demande) {
        Compte compte = Compte.ouvrir(clientId, demande.titulaire(), demande.depotInitial(), Instant.now(horloge));
        return CompteReponse.de(comptes.save(compte));
    }

    // readOnly : indique à Hibernate qu'aucune modification n'est prévue (plus léger).
    // Le compte d'un autre client répond 404 (et non 403) : on ne révèle même pas qu'il existe.
    @Transactional(readOnly = true)
    public CompteReponse trouver(long clientId, long id) {
        return comptes.findByIdAndClientId(id, clientId)
                .map(CompteReponse::de)
                .orElseThrow(() -> new CompteIntrouvableException(id));
    }

    @Transactional(readOnly = true)
    public Page<CompteReponse> lister(long clientId, Pageable page) {
        return comptes.findByClientId(clientId, page).map(CompteReponse::de);
    }
}
