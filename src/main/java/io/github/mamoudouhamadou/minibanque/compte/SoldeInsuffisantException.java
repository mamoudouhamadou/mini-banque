package io.github.mamoudouhamadou.minibanque.compte;

import java.math.BigDecimal;

/** Retrait ou virement supérieur au solde disponible ; devient une réponse 422. */
public class SoldeInsuffisantException extends RuntimeException {

    private final BigDecimal soldeDisponible;
    private final BigDecimal montantDemande;

    public SoldeInsuffisantException(Long compteId, BigDecimal soldeDisponible, BigDecimal montantDemande) {
        super("Solde insuffisant dans le compte " + compteId + " : " + soldeDisponible + " $ disponibles, "
                + montantDemande + " $ demandés");
        this.soldeDisponible = soldeDisponible;
        this.montantDemande = montantDemande;
    }

    public BigDecimal getSoldeDisponible() {
        return soldeDisponible;
    }

    public BigDecimal getMontantDemande() {
        return montantDemande;
    }
}
