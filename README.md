# Mini-banque

[![CI](https://github.com/mamoudouhamadou/mini-banque/actions/workflows/ci.yml/badge.svg)](https://github.com/mamoudouhamadou/mini-banque/actions/workflows/ci.yml)

**🌐 Démo en ligne : https://mini-banque.onrender.com** · **📖 API interactive : https://mini-banque.onrender.com/swagger-ui.html**
<sub>Hébergement gratuit : la première visite peut prendre 2 à 3 minutes (réveil du serveur, puis démarrage de la JVM).</sub>

API bancaire en **Java 25 / Spring Boot 4** avec interface **Angular** : inscription des clients, ouverture de comptes, dépôts, retraits et virements **transactionnels et sécurisés**, sans perte ni création d'argent, même en cas d'accès simultanés.

Documentation : [architecture](docs/ARCHITECTURE.md) · [déploiement](docs/DEPLOIEMENT.md)

## Stack
- **Java 25** (LTS), **Spring Boot 4.1** : Web MVC, Data JPA (Hibernate), Validation, Actuator
- **Spring Security 7** : jetons **JWT** (serveur de ressources OAuth 2.0), mots de passe **BCrypt**
- **PostgreSQL 17**, **Flyway** (migrations versionnées)
- **Tests :** JUnit 5, AssertJ, MockMvcTester, **Testcontainers** (PostgreSQL réel)
- Erreurs au format standard **RFC 9457** (`application/problem+json`)
- **OpenAPI 3** (springdoc) : contrat sur `/v3/api-docs`, **Swagger UI** sur `/swagger-ui.html`
- **Interface Angular 21** ([`frontend/`](frontend/)) : signals, sans zone.js, Reactive Forms, intercepteur JWT, gardes de route, Vitest
- **Docker** : image de 136 Mo (Angular + Spring Boot, JRE seul, JAR en couches, utilisateur non-root)
- **CI/CD :** GitHub Actions (tests Java et Angular, image, test de bout en bout, déploiement continu sur Render + Neon), Dependabot

## Garanties
- Un virement débite et crédite dans **une seule transaction** (tout ou rien).
- Les comptes sont **verrouillés** (`SELECT … FOR UPDATE`) dans un ordre fixe pour éviter les interblocages ; `@Version` sert de seconde protection.
- Les montants sont exacts (`BigDecimal`, `NUMERIC(19,2)`) ; un solde négatif est refusé par l'application et par la base.
- Des tests de concurrence (opérations simultanées) vérifient qu'aucun montant n'est perdu ni créé.

## Lancer avec Docker

```bash
cp .env.example .env                              # définir JWT_SECRET
docker compose -f compose.prod.yaml up -d --build # http://localhost:8083 (interface, API, Swagger UI)
```

## Développement

Prérequis : JDK 25 et Docker.

```bash
./mvnw test   # 49 tests : unitaires, intégration, concurrence, sécurité (Testcontainers démarre PostgreSQL)
```

L'application exige une clé de signature JWT d'au moins 32 caractères dans la variable d'environnement `JWT_SECRET` :

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
./mvnw spring-boot:run   # http://localhost:8080 ; PostgreSQL démarré automatiquement (compose.yaml)
```

Sous Windows : `mvnw.cmd`. Autre port : `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8082`.

Interface Angular en développement (API attendue sur le port 8082, voir `frontend/proxy.conf.json`) :

```bash
cd frontend
npm install
npm start      # http://localhost:4200
npm test       # tests (Vitest)
```

## API

### Authentification (publique)
| Méthode | URL | Description | Réponses |
|---|---|---|---|
| POST | `/api/auth/inscription` | `{ "courriel", "motDePasse" (12 à 72 car.), "nom" }` → `{ "jeton", "type": "Bearer", "expireDansSecondes", "client" }` | 201, 400, 409 |
| POST | `/api/auth/connexion` | `{ "courriel", "motDePasse" }` → jeton | 200, 400, 401 |

Les autres routes 🔒 exigent l'en-tête `Authorization: Bearer <jeton>` (validité : 1 heure). Un client n'accède qu'à ses propres comptes ; le compte d'un autre client répond `404`.

### Comptes et opérations 🔒
| Méthode | URL | Description | Réponses |
|---|---|---|---|
| POST | `/api/comptes` | Ouverture d'un compte `{ "titulaire", "depotInitial" }` | 201 + `Location`, 400 |
| GET | `/api/comptes/{id}` | Un compte du client | 200, 404 |
| GET | `/api/comptes?page=0&size=20&sort=titulaire` | Comptes du client, paginés | 200 |
| POST | `/api/comptes/{id}/depots` | Dépôt `{ "montant", "description"? }` | 201, 400, 404 |
| POST | `/api/comptes/{id}/retraits` | Retrait | 201, 400, 404, **422** (solde insuffisant) |
| POST | `/api/virements` | Virement `{ "source", "destination", "montant", "description"? }` ; la source doit appartenir au client, la destination peut appartenir à un autre client | 201, 400, 404, 422 |
| GET | `/api/comptes/{id}/operations?page=0&size=20` | Relevé (du plus récent au plus ancien) | 200, 404 |
| GET | `/actuator/health`, `/actuator/info` | État de santé et version déployée (publics) | 200 |

```bash
JETON=$(curl -s -X POST localhost:8080/api/auth/inscription -H "Content-Type: application/json" \
  -d '{"courriel":"ana@exemple.ca","motDePasse":"un-mot-de-passe-solide","nom":"Ana"}' | jq -r .jeton)

curl -X POST localhost:8080/api/comptes -H "Content-Type: application/json" -H "Authorization: Bearer $JETON" \
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
