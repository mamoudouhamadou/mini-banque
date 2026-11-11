package io.github.mamoudouhamadou.minibanque.operation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Routes des mouvements d'argent. Toute la logique est dans OperationService. */
@RestController
@Validated // active la validation des paramètres simples (@Min, @Max sur page et taille)
public class OperationController {

    private final OperationService service;

    public OperationController(OperationService service) {
        this.service = service;
    }

    @PostMapping("/api/comptes/{id}/depots")
    @ResponseStatus(HttpStatus.CREATED)
    public OperationReponse deposer(@PathVariable long id, @Valid @RequestBody MouvementRequete demande) {
        return service.deposer(id, demande);
    }

    @PostMapping("/api/comptes/{id}/retraits")
    @ResponseStatus(HttpStatus.CREATED)
    public OperationReponse retirer(@PathVariable long id, @Valid @RequestBody MouvementRequete demande) {
        return service.retirer(id, demande);
    }

    @PostMapping("/api/virements")
    @ResponseStatus(HttpStatus.CREATED)
    public VirementReponse virer(@Valid @RequestBody VirementRequete demande) {
        return service.virer(demande);
    }

    /** GET /api/comptes/{id}/operations?page=0&size=20 → le relevé, du plus récent au plus ancien. */
    @GetMapping("/api/comptes/{id}/operations")
    public Page<OperationReponse> releve(
            @PathVariable long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.releve(id, page, size);
    }
}
