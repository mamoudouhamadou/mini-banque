package io.github.mamoudouhamadou.minibanque.operation;

import io.github.mamoudouhamadou.minibanque.compte.Compte;
import io.github.mamoudouhamadou.minibanque.compte.CompteIntrouvableException;
import io.github.mamoudouhamadou.minibanque.compte.CompteRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Les mouvements d'argent. Chaque méthode publique est UNE transaction :
 * si quoi que ce soit échoue (solde insuffisant, panne de la base…), TOUT est annulé.
 *
 * <p>Concurrence : chaque compte touché est verrouillé (SELECT … FOR UPDATE) avant d'être lu.
 * Les transactions qui veulent le même compte passent donc une à la fois, avec le solde à jour.
 */
@Service
public class OperationService {

    private final CompteRepository comptes;
    private final OperationRepository operations;
    private final Clock horloge;

    public OperationService(CompteRepository comptes, OperationRepository operations, Clock horloge) {
        this.comptes = comptes;
        this.operations = operations;
        this.horloge = horloge;
    }

    @Transactional
    public OperationReponse deposer(long clientId, long compteId, MouvementRequete demande) {
        Compte compte = verrouillerLeMien(clientId, compteId);
        BigDecimal montant = compte.deposer(demande.montant());
        return enregistrer(compte, TypeOperation.DEPOT, montant, null, demande.description());
    }

    @Transactional
    public OperationReponse retirer(long clientId, long compteId, MouvementRequete demande) {
        Compte compte = verrouillerLeMien(clientId, compteId);
        BigDecimal montant = compte.retirer(demande.montant()); // lève SoldeInsuffisantException si besoin
        return enregistrer(compte, TypeOperation.RETRAIT, montant, null, demande.description());
    }

    /**
     * Virement = débit + crédit dans la MÊME transaction (atomicité, le « A » d'ACID) :
     * impossible que l'argent sorte d'un compte sans entrer dans l'autre.
     *
     * <p>Autorisation : le compte SOURCE doit appartenir au client connecté ; la destination
     * peut appartenir à n'importe quel client (comme un virement Interac).
     */
    @Transactional
    public VirementReponse virer(long clientId, VirementRequete demande) {
        long source = demande.source();
        long destination = demande.destination();
        if (source == destination) {
            throw new VirementInvalideException("Impossible de virer vers le même compte");
        }

        // Anti-interblocage (deadlock) : on verrouille TOUJOURS le plus petit id en premier.
        // Sans cet ordre, A→B et B→A simultanés pourraient s'attendre l'un l'autre pour toujours.
        Compte premier = verrouiller(Math.min(source, destination));
        Compte second = verrouiller(Math.max(source, destination));
        Compte debite = premier.getId() == source ? premier : second;
        Compte credite = debite == premier ? second : premier;

        // On ne peut vider QUE ses propres comptes. Même réponse qu'un compte inexistant (404).
        if (!debite.appartientA(clientId)) {
            throw new CompteIntrouvableException(source);
        }

        BigDecimal montant = debite.retirer(demande.montant()); // échoue AVANT tout crédit si le solde manque
        credite.deposer(montant);

        UUID virementId = UUID.randomUUID();
        Instant maintenant = Instant.now(horloge);
        operations.save(new Operation(debite, TypeOperation.VIREMENT_DEBIT, montant, virementId, demande.description(), maintenant));
        operations.save(new Operation(credite, TypeOperation.VIREMENT_CREDIT, montant, virementId, demande.description(), maintenant));

        return new VirementReponse(
                virementId, source, destination, montant, debite.getSolde(), credite.getSolde(), demande.description(),
                maintenant);
    }

    /** Le relevé : les opérations d'un compte, de la plus récente à la plus ancienne. */
    @Transactional(readOnly = true)
    public Page<OperationReponse> releve(long clientId, long compteId, int page, int taille) {
        if (!comptes.existsByIdAndClientId(compteId, clientId)) {
            throw new CompteIntrouvableException(compteId);
        }
        PageRequest demande = PageRequest.of(page, taille, Sort.by(Sort.Direction.DESC, "id"));
        return operations.findByCompteId(compteId, demande).map(OperationReponse::de);
    }

    private Compte verrouiller(long id) {
        return comptes.verrouiller(id).orElseThrow(() -> new CompteIntrouvableException(id));
    }

    /** Verrouille un compte du client connecté ; le compte d'un autre client répond comme s'il n'existait pas. */
    private Compte verrouillerLeMien(long clientId, long compteId) {
        Compte compte = verrouiller(compteId);
        if (!compte.appartientA(clientId)) {
            throw new CompteIntrouvableException(compteId);
        }
        return compte;
    }

    private OperationReponse enregistrer(
            Compte compte, TypeOperation type, BigDecimal montant, UUID virementId, String description) {
        Operation operation = new Operation(compte, type, montant, virementId, description, Instant.now(horloge));
        return OperationReponse.de(operations.save(operation));
    }
}
