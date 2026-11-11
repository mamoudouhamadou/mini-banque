package io.github.mamoudouhamadou.minibanque.client;

import jakarta.validation.constraints.NotBlank;

/** Corps de POST /api/auth/connexion. */
public record Connexion(
        @NotBlank(message = "Le courriel est obligatoire") String courriel,
        @NotBlank(message = "Le mot de passe est obligatoire") String motDePasse) {

    /** Même normalisation qu'à l'inscription : « Ana@Exemple.ca » se connecte au compte « ana@exemple.ca ». */
    public Connexion {
        courriel = Inscription.normaliser(courriel);
    }
}
