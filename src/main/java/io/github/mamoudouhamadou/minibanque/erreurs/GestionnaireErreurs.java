package io.github.mamoudouhamadou.minibanque.erreurs;

import io.github.mamoudouhamadou.minibanque.client.CourrielDejaUtiliseException;
import io.github.mamoudouhamadou.minibanque.client.IdentifiantsInvalidesException;
import io.github.mamoudouhamadou.minibanque.compte.CompteIntrouvableException;
import io.github.mamoudouhamadou.minibanque.compte.SoldeInsuffisantException;
import io.github.mamoudouhamadou.minibanque.operation.VirementInvalideException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Transforme les exceptions en réponses d'erreur au format standard RFC 9457 (« Problem Details ») :
 * { "type", "title", "status", "detail", "instance", ... }, avec le type de contenu
 * application/problem+json. Un format standard = des clients plus simples à écrire.
 *
 * <p>Hériter de ResponseEntityExceptionHandler fournit déjà ce format pour les erreurs de Spring
 * (JSON mal formé, méthode non permise, etc.) ; on personnalise les nôtres.
 */
@RestControllerAdvice
public class GestionnaireErreurs extends ResponseEntityExceptionHandler {

    /** 404 : compte inexistant. */
    @ExceptionHandler(CompteIntrouvableException.class)
    ProblemDetail compteIntrouvable(CompteIntrouvableException e) {
        ProblemDetail probleme = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        probleme.setTitle("Compte introuvable");
        probleme.setType(URI.create("https://mini-banque.example/problemes/compte-introuvable"));
        return probleme;
    }

    /**
     * 422 Unprocessable Content : la requête est bien formée, mais la règle métier l'interdit.
     * (400 signifierait une requête mal formée ; ici elle est valide, c'est le solde qui manque.)
     */
    @ExceptionHandler(SoldeInsuffisantException.class)
    ProblemDetail soldeInsuffisant(SoldeInsuffisantException e) {
        ProblemDetail probleme = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
        probleme.setTitle("Solde insuffisant");
        probleme.setType(URI.create("https://mini-banque.example/problemes/solde-insuffisant"));
        probleme.setProperty("soldeDisponible", e.getSoldeDisponible());
        probleme.setProperty("montantDemande", e.getMontantDemande());
        return probleme;
    }

    @ExceptionHandler(VirementInvalideException.class)
    ProblemDetail virementInvalide(VirementInvalideException e) {
        ProblemDetail probleme = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
        probleme.setTitle("Virement invalide");
        probleme.setType(URI.create("https://mini-banque.example/problemes/virement-invalide"));
        return probleme;
    }

    /** 401 : mauvais courriel ou mot de passe (même message dans les deux cas). */
    @ExceptionHandler(IdentifiantsInvalidesException.class)
    ProblemDetail identifiantsInvalides(IdentifiantsInvalidesException e) {
        ProblemDetail probleme = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        probleme.setTitle("Connexion refusée");
        return probleme;
    }

    /** 409 : courriel déjà inscrit. */
    @ExceptionHandler(CourrielDejaUtiliseException.class)
    ProblemDetail courrielDejaUtilise(CourrielDejaUtiliseException e) {
        ProblemDetail probleme = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        probleme.setTitle("Courriel déjà utilisé");
        return probleme;
    }

    /**
     * 409 : deux transactions ont modifié le même compte en même temps et @Version a protégé les données.
     * Avec le verrou pessimiste du service, ça ne devrait pas arriver ; le cas échéant, le client peut réessayer.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail conflit(ObjectOptimisticLockingFailureException e) {
        ProblemDetail probleme = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Le compte a été modifié en même temps par une autre opération. Veuillez réessayer.");
        probleme.setTitle("Modification concurrente");
        return probleme;
    }

    /** 400 : paramètre hors limites (ex. ?size=500 alors que le maximum est 100). */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail parametreInvalide(ConstraintViolationException e) {
        ProblemDetail probleme = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Paramètre invalide");
        probleme.setTitle("Données invalides");
        return probleme;
    }

    /** 400 : validation des paramètres de méthode (Spring 6.1+ lève HandlerMethodValidationException). */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException e, HttpHeaders entetes, HttpStatusCode statut, WebRequest requete) {
        ProblemDetail probleme = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Paramètre invalide");
        probleme.setTitle("Données invalides");
        return ResponseEntity.badRequest().body(probleme);
    }

    /** 400 : la validation (@Valid) a échoué → on liste chaque champ fautif. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders entetes, HttpStatusCode statut, WebRequest requete) {
        ProblemDetail probleme = ProblemDetail.forStatusAndDetail(statut, "Une ou plusieurs valeurs sont invalides");
        probleme.setTitle("Données invalides");
        List<Map<String, String>> erreurs = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("champ", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
                .toList();
        probleme.setProperty("erreurs", erreurs);
        return ResponseEntity.status(statut).body(probleme);
    }
}
