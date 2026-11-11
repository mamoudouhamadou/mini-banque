package io.github.mamoudouhamadou.minibanque.operation;

import io.github.mamoudouhamadou.minibanque.securite.ClientConnecte;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Routes des mouvements d'argent. Toute la logique est dans OperationService.
 * {@code @AuthenticationPrincipal Jwt} : le jeton déjà vérifié par Spring Security, d'où l'on tire l'id du client.
 */
@RestController
@Tag(name = "3. Opérations", description = "Dépôts, retraits, virements et relevé")
@Validated // active la validation des paramètres simples (@Min, @Max sur page et taille)
public class OperationController {

    private final OperationService service;

    public OperationController(OperationService service) {
        this.service = service;
    }

    @PostMapping("/api/comptes/{id}/depots")
    @Operation(summary = "Déposer dans un de mes comptes")
    @ResponseStatus(HttpStatus.CREATED)
    public OperationReponse deposer(
            @AuthenticationPrincipal Jwt jeton, @PathVariable long id, @Valid @RequestBody MouvementRequete demande) {
        return service.deposer(ClientConnecte.id(jeton), id, demande);
    }

    @PostMapping("/api/comptes/{id}/retraits")
    @Operation(summary = "Retirer d'un de mes comptes", description = "422 si le solde est insuffisant.")
    @ResponseStatus(HttpStatus.CREATED)
    public OperationReponse retirer(
            @AuthenticationPrincipal Jwt jeton, @PathVariable long id, @Valid @RequestBody MouvementRequete demande) {
        return service.retirer(ClientConnecte.id(jeton), id, demande);
    }

    @PostMapping("/api/virements")
    @Operation(summary = "Virer de l'argent", description = "Débit et crédit dans une seule transaction. La source doit m'appartenir ; la destination peut être le compte d'un autre client.")
    @ResponseStatus(HttpStatus.CREATED)
    public VirementReponse virer(@AuthenticationPrincipal Jwt jeton, @Valid @RequestBody VirementRequete demande) {
        return service.virer(ClientConnecte.id(jeton), demande);
    }

    /** GET /api/comptes/{id}/operations?page=0&size=20 → le relevé, du plus récent au plus ancien. */
    @GetMapping("/api/comptes/{id}/operations")
    @Operation(summary = "Relevé d'un de mes comptes", description = "Du plus récent au plus ancien.")
    public Page<OperationReponse> releve(
            @AuthenticationPrincipal Jwt jeton,
            @PathVariable long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.releve(ClientConnecte.id(jeton), id, page, size);
    }
}
