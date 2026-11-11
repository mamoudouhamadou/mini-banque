package io.github.mamoudouhamadou.minibanque.client;

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
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/inscription")
    @ResponseStatus(HttpStatus.CREATED)
    public JetonReponse inscrire(@Valid @RequestBody Inscription demande) {
        return service.inscrire(demande);
    }

    @PostMapping("/connexion")
    public JetonReponse connecter(@Valid @RequestBody Connexion demande) {
        return service.connecter(demande);
    }
}
