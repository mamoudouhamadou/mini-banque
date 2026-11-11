package io.github.mamoudouhamadou.minibanque.client;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/** Corps de POST /api/auth/inscription. */
public record Inscription(
        @NotBlank(message = "Le courriel est obligatoire")
        @Email(message = "Courriel invalide")
        @Size(max = 254, message = "Courriel trop long")
        String courriel,

        // Longueur minimale 12 (recommandation actuelle) ; maximale 72 (limite de BCrypt en octets)
        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = 12, max = 72, message = "Le mot de passe doit contenir entre 12 et 72 caractères")
        String motDePasse,

        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom doit contenir au plus 100 caractères")
        String nom) {

    /**
     * Constructeur « compact » : s'exécute à la création, donc AVANT la validation (@Valid).
     * Sans lui, « ␣Ana@Exemple.CA␣ » serait refusé par @Email à cause des espaces.
     */
    public Inscription {
        courriel = normaliser(courriel);
    }

    static String normaliser(String courriel) {
        return courriel == null ? null : courriel.strip().toLowerCase(Locale.ROOT);
    }
}
