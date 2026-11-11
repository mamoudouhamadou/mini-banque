package io.github.mamoudouhamadou.minibanque.operation;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Corps de POST /api/comptes/{id}/depots et /retraits. */
public record MouvementRequete(
        @NotNull(message = "Le montant est obligatoire")
        @Positive(message = "Le montant doit être positif")
        @Digits(integer = 15, fraction = 2, message = "Montant invalide : au plus 2 décimales")
        @DecimalMax(value = "1000000.00", message = "Le montant maximal par opération est de 1 000 000 $")
        BigDecimal montant,

        @Size(max = 140, message = "La description doit contenir au plus 140 caractères")
        String description) {}
