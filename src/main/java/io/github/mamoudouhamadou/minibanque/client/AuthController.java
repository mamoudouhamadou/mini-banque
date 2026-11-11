package io.github.mamoudouhamadou.minibanque.client;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Routes PUBLIQUES (sans jeton) : inscription et connexion. Voir ConfigurationSecurite. */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "1. Authentification", description = "Routes publiques : obtenir un jeton JWT")
@SecurityRequirements // liste vide : ces routes n'exigent PAS de jeton (annule la règle par défaut)
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/inscription")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un client", description = "Retourne directement un jeton valide 1 heure. Mot de passe : 12 à 72 caractères.")
    public JetonReponse inscrire(@Valid @RequestBody Inscription demande) {
        return service.inscrire(demande);
    }

    @PostMapping("/connexion")
    @Operation(summary = "Se connecter", description = "Même message d'erreur que le courriel existe ou non (anti-énumération).")
    public JetonReponse connecter(@Valid @RequestBody Connexion demande) {
        return service.connecter(demande);
    }
}
