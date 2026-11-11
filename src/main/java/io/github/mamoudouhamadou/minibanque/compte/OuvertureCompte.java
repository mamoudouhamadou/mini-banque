package io.github.mamoudouhamadou.minibanque.compte;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Corps de la requête POST /api/comptes (un « DTO » : objet de transfert de données).
 *
 * <p>Un {@code record} Java est une classe immuable en une ligne : constructeur, accesseurs,
 * equals, hashCode et toString sont générés. Les annotations Bean Validation décrivent ce qui
 * est accepté ; Spring les vérifie automatiquement grâce à {@code @Valid} dans le contrôleur.
 *
 * <p>Les messages sont écrits en clair pour être identiques quelle que soit la langue du serveur.
 */
public record OuvertureCompte(
        @NotBlank(message = "Le titulaire est obligatoire")
        @Size(max = 100, message = "Le titulaire doit contenir au plus 100 caractères")
        String titulaire,

        @NotNull(message = "Le dépôt initial est obligatoire")
        @PositiveOrZero(message = "Le dépôt initial ne peut pas être négatif")
        @Digits(integer = 15, fraction = 2, message = "Montant invalide : au plus 2 décimales")
        BigDecimal depotInitial) {}
