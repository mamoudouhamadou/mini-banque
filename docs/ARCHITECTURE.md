# Architecture

API bancaire simplifiée : comptes, dépôts, retraits et virements entre comptes, avec une garantie d'exactitude des montants, y compris lorsque plusieurs opérations portent simultanément sur les mêmes comptes. Une interface Angular est servie par la même application.

## Vue d'ensemble

```
Navigateur (Angular) ──HTTP/JSON + JWT──> Spring Boot ──JPA/Hibernate──> PostgreSQL
                                             │
                                             └── sert aussi l'interface compilée (index.html, JS, CSS)
```

## Couches

```
Requête HTTP
   │
   ▼
Contrôleurs   ← HTTP, JSON, validation des entrées (Bean Validation), codes de statut
   │
   ▼
Services      ← règles métier et transactions (@Transactional), verrous
   │
   ▼
Repositories  ← accès aux données (Spring Data JPA)
   │
   ▼
PostgreSQL    ← schéma versionné par Flyway
```

## Structure du dépôt

```
src/main/java/io/github/mamoudouhamadou/minibanque/
├── MiniBanqueApplication.java     point d'entrée
├── ConfigurationOpenApi.java      description OpenAPI 3 et schéma de sécurité
├── InterfaceWebController.java    routes de l'interface Angular (renvoi vers index.html)
├── compte/                        entité Compte, repository, service, contrôleur, DTO
├── operation/                     opérations, dépôts, retraits, virements, relevé
├── client/                        clients, inscription et connexion
├── securite/                      Spring Security, émission des JWT, client connecté
└── erreurs/                       réponses d'erreur RFC 9457
src/main/resources/
├── application.properties
└── db/migration/                  V1 comptes, V2 opérations, V3 clients et propriétaires
src/test/                          tests unitaires, d'intégration, de concurrence et de sécurité
frontend/                          interface Angular (voir frontend/README.md)
Dockerfile                         image de production (Angular + Spring Boot, JRE seul)
compose.yaml                       PostgreSQL pour le développement
compose.prod.yaml                  application complète dans Docker
render.yaml                        infrastructure Render (Blueprint)
.github/                           intégration continue et Dependabot
```

## Choix techniques

| Sujet | Choix | Raison |
|---|---|---|
| Montants | `BigDecimal` en Java, `NUMERIC(19,2)` en SQL ; refus des montants à plus de 2 décimales | Calculs exacts, aucun arrondi silencieux |
| Virements | Débit et crédit dans une seule transaction | Atomicité : jamais de débit sans crédit |
| Concurrence | Verrou pessimiste (`SELECT … FOR UPDATE`) pris dans un ordre fixe (plus petit identifiant d'abord) + verrouillage optimiste `@Version` | Pas de mise à jour perdue, pas d'interblocage ; double protection |
| Intégrité | Contraintes SQL (`CHECK solde >= 0`, montants positifs, clés étrangères) | Dernière ligne de défense, indépendante du code |
| Historique | Opérations immuables avec solde après opération | Relevé fiable et rapide à lire |
| Schéma | Flyway, Hibernate en mode `validate` | Migrations versionnées ; écart code/base détecté au démarrage |
| Authentification | Spring Security, serveur de ressources OAuth 2.0 (JWT HS256, 1 h), BCrypt | API sans état ; clé de signature fournie par l'environnement |
| Autorisation | Filtre par client dans les services et les requêtes SQL | Un client n'accède qu'à ses comptes (404 sinon) ; la destination d'un virement peut appartenir à un autre client |
| Erreurs | RFC 9457 (`application/problem+json`) | Format standard : 400 validation, 401, 404, 409 conflit, 422 règle métier |
| Documentation | springdoc-openapi (OpenAPI 3, Swagger UI) | Contrat généré depuis le code, toujours à jour |
| Interface | Angular 21 : composants autonomes, signals, mode sans zone.js, intercepteur JWT, gardes de route | Interface légère ; la sécurité reste assurée par le serveur |
| En-têtes HTTP | CSP, HSTS, `X-Frame-Options`, `nosniff` | Réduction de la surface d'attaque (XSS, clickjacking) |

## Tests

| Niveau | Outils | Portée |
|---|---|---|
| Unitaires | JUnit 5, AssertJ | Règles de l'entité `Compte` (montants, soldes) |
| Intégration | Spring Boot Test, MockMvcTester, Testcontainers (PostgreSQL 17) | API complète : comptes, opérations, virements, erreurs, OpenAPI, interface |
| Concurrence | Tâches parallèles synchronisées (`CountDownLatch`) | 20 retraits simultanés, 100 virements croisés : aucun montant perdu ni créé, aucun interblocage |
| Sécurité | Jetons réels et jetons volontairement invalides | Signature, expiration, émetteur, isolation entre clients, anti-énumération |
| Interface | Vitest, `HttpTestingController` | Session, intercepteur, gestion des erreurs |
| Bout en bout | Docker Compose, curl | Image de production : interface, Swagger, inscription, virement, 401, 422 |

## Intégration et déploiement continus

À chaque push et pull request, GitHub Actions exécute :
1. **API Java** : `./mvnw verify` (tests avec Testcontainers) ;
2. **Interface Angular** : tests et compilation de production ;
3. **Docker** : construction de l'image, démarrage avec PostgreSQL et test de bout en bout ;
4. **Déploiement** (branche `main`) : déclenchement de Render, attente de la nouvelle version (`/actuator/info`) et test de fumée.

Les migrations Flyway s'appliquent au démarrage de l'application. Voir [DEPLOIEMENT.md](DEPLOIEMENT.md).
