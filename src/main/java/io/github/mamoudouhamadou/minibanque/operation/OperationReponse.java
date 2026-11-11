package io.github.mamoudouhamadou.minibanque.operation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Une ligne du relevé, telle que l'API la renvoie. */
public record OperationReponse(
        Long id,
        TypeOperation type,
        BigDecimal montant,
        BigDecimal soldeApres,
        UUID virementId,
        String description,
        Instant effectueeLe) {

    static OperationReponse de(Operation o) {
        return new OperationReponse(
                o.getId(), o.getType(), o.getMontant(), o.getSoldeApres(), o.getVirementId(), o.getDescription(),
                o.getEffectueeLe());
    }
}
