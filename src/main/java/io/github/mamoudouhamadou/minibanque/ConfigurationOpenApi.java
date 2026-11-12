package io.github.mamoudouhamadou.minibanque;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Description générale de l'API pour OpenAPI 3 (springdoc lit aussi les contrôleurs et les DTO).
 *
 * <ul>
 *   <li>/v3/api-docs : le contrat de l'API en JSON (lisible par des outils : générateurs de clients, Postman…)</li>
 *   <li>/swagger-ui.html : une page web pour lire ET essayer l'API (bouton « Authorize » pour le jeton)</li>
 * </ul>
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Mini-banque API",
                version = "1.0",
                description = """
                        API bancaire : comptes, dépôts, retraits et virements transactionnels.
                        1. Créer un client avec POST /api/auth/inscription (ou se connecter).
                        2. Copier le « jeton » de la réponse, cliquer sur « Authorize » et le coller.
                        3. Appeler les autres routes : elles sont protégées par ce jeton.""",
                contact = @Contact(name = "Mamoudou Hamadou", url = "https://github.com/mamoudouhamadou/mini-banque")),
        // Par défaut, toutes les routes exigent le jeton (les routes publiques l'annulent avec @SecurityRequirements)
        security = @SecurityRequirement(name = "jeton"))
@SecurityScheme(name = "jeton", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class ConfigurationOpenApi {}
