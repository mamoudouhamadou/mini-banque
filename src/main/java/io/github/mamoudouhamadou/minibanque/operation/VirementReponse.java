package io.github.mamoudouhamadou.minibanque.operation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Résultat d'un virement : son identifiant et le solde de chaque compte après coup. */
public record VirementReponse(
        UUID id,
        Long source,
        Long destination,
        BigDecimal montant,
        BigDecimal soldeSourceApres,
        BigDecimal soldeDestinationApres,
        String description,
        Instant effectueLe) {}
