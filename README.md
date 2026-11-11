# Mini-banque

API bancaire en **Java 25 / Spring Boot 4** : ouvrir des comptes, déposer, retirer et virer de l'argent **de façon transactionnelle**, sans jamais perdre un cent, même en cas d'accès simultanés.

> 🚧 En construction. Voir la [feuille de route](docs/FEUILLE_DE_ROUTE.md) et le [journal](docs/JOURNAL.md).

## Stack
- **Java 25** (LTS), **Spring Boot 4.1** : Web MVC, Data JPA (Hibernate), Validation, Actuator
- **PostgreSQL 17**, **Flyway** (migrations versionnées)
- **Tests :** JUnit 5, AssertJ, MockMvcTester, **Testcontainers** (vrai PostgreSQL dans Docker)
- Erreurs au format standard **RFC 9457** (`application/problem+json`)

## Lancer

Prérequis : **JDK 25** et **Docker Desktop** démarré.

```bash
./mvnw test            # tests unitaires + intégration (Testcontainers démarre PostgreSQL)
./mvnw spring-boot:run # API sur http://localhost:8080 ; PostgreSQL démarre tout seul (compose.yaml)
```

Sous Windows, utiliser `mvnw.cmd`. Pour un autre port : `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8082`.

## API

| Méthode | URL | Description | Réponses |
|---|---|---|---|
| POST | `/api/comptes` | Ouvre un compte `{ "titulaire", "depotInitial" }` | 201 + `Location`, 400 |
| GET | `/api/comptes/{id}` | Un compte | 200, 404 |
| GET | `/api/comptes?page=0&size=20&sort=titulaire` | Liste paginée | 200 |
| POST | `/api/comptes/{id}/depots` | Dépôt `{ "montant", "description"? }` | 201, 400, 404 |
| POST | `/api/comptes/{id}/retraits` | Retrait | 201, 400, 404, **422** (solde insuffisant) |
| POST | `/api/virements` | Virement `{ "source", "destination", "montant", "description"? }` | 201, 400, 404, 422 |
| GET | `/api/comptes/{id}/operations?page=0&size=20` | Relevé (du plus récent au plus ancien) | 200, 404 |
| GET | `/actuator/health` | État de santé | 200 |

**Garanties :** un virement débite et crédite dans **une seule transaction** (tout ou rien). Les comptes sont **verrouillés** (`SELECT … FOR UPDATE`) dans un ordre fixe pour éviter les interblocages, et `@Version` sert de deuxième protection. Des tests lancent des dizaines d'opérations **simultanées** et vérifient qu'aucun cent ne se crée ni ne se perd.

```bash
curl -X POST localhost:8080/api/comptes -H "Content-Type: application/json" \
     -d '{"titulaire":"Ana Tremblay","depotInitial":1500.25}'
```

Exemple d'erreur (RFC 9457) :

```json
{
  "type": "https://mini-banque.example/problemes/compte-introuvable",
  "title": "Compte introuvable",
  "status": 404,
  "detail": "Le compte 42 n'existe pas",
  "instance": "/api/comptes/42"
}
```
