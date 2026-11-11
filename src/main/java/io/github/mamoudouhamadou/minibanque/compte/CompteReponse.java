package io.github.mamoudouhamadou.minibanque.compte;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Ce que l'API renvoie pour un compte. On ne renvoie JAMAIS l'entité JPA directement :
 * - on contrôle exactement les champs exposés (pas de « version » interne, par exemple) ;
 * - on peut changer la table sans casser les clients de l'API.
 */
public record CompteReponse(Long id, String titulaire, BigDecimal solde, String devise, Instant ouvertLe) {

    static CompteReponse de(Compte compte) {
        return new CompteReponse(
                compte.getId(), compte.getTitulaire(), compte.getSolde(), compte.getDevise(), compte.getOuvertLe());
    }
}
