package io.github.mamoudouhamadou.minibanque.compte;

import io.github.mamoudouhamadou.minibanque.securite.ClientConnecte;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Couche web : traduit HTTP ↔ Java. Aucune règle métier ici, seulement :
 * routes, validation des entrées (@Valid) et codes de statut.
 */
@RestController
@RequestMapping("/api/comptes")
public class CompteController {

    private final CompteService service;

    public CompteController(CompteService service) {
        this.service = service;
    }

    /** POST /api/comptes → 201 Created + en-tête Location vers le nouveau compte. */
    @PostMapping
    public ResponseEntity<CompteReponse> ouvrir(
            @AuthenticationPrincipal Jwt jeton, @Valid @RequestBody OuvertureCompte demande) {
        CompteReponse compte = service.ouvrir(ClientConnecte.id(jeton), demande);
        URI emplacement = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(compte.id())
                .toUri();
        return ResponseEntity.created(emplacement).body(compte);
    }

    /** GET /api/comptes/{id} → 200, ou 404 (voir GestionnaireErreurs). */
    @GetMapping("/{id}")
    public CompteReponse trouver(@AuthenticationPrincipal Jwt jeton, @PathVariable long id) {
        return service.trouver(ClientConnecte.id(jeton), id);
    }

    /** GET /api/comptes?page=0&size=20&sort=titulaire → une page de MES comptes. */
    @GetMapping
    public Page<CompteReponse> lister(
            @AuthenticationPrincipal Jwt jeton,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable page) {
        return service.lister(ClientConnecte.id(jeton), page);
    }
}
