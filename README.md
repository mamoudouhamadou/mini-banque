# Mini-banque

[![CI](https://github.com/mamoudouhamadou/mini-banque/actions/workflows/ci.yml/badge.svg)](https://github.com/mamoudouhamadou/mini-banque/actions/workflows/ci.yml)

**🌐 Démo en ligne : https://mini-banque.onrender.com** · **📖 API interactive : https://mini-banque.onrender.com/swagger-ui.html**
<sub>Hébergement gratuit : la première visite peut prendre 2 à 3 minutes (le serveur se réveille, puis Java démarre).</sub>

API bancaire en **Java 25 / Spring Boot 4** : des clients qui s'inscrivent, ouvrent des comptes, déposent, retirent et virent de l'argent **de façon transactionnelle et sécurisée**, sans jamais perdre un cent, même en cas d'accès simultanés.

Voir la [feuille de route](docs/FEUILLE_DE_ROUTE.md), le [journal d'apprentissage](docs/JOURNAL.md) et le [guide de mise en ligne](docs/DEPLOIEMENT.md).

## Stack
- **Java 25** (LTS), **Spring Boot 4.1** : Web MVC, Data JPA (Hibernate), Validation, Actuator
- **Spring Security 7** : jetons **JWT** (OAuth 2.0 *resource server*), mots de passe **BCrypt**
- **PostgreSQL 17**, **Flyway** (migrations versionnées)
- **Tests :** JUnit 5, AssertJ, MockMvcTester, **Testcontainers** (vrai PostgreSQL dans Docker)
- Erreurs au format standard **RFC 9457** (`application/problem+json`)
- **Documentation OpenAPI 3** (springdoc) : contrat sur `/v3/api-docs`, **Swagger UI** sur `/swagger-ui.html`
- **Interface Angular 21** (dossier [`frontend/`](frontend/)) : signals, *zoneless*, Reactive Forms, intercepteur JWT, gardes de route, Vitest
- **Docker** : une image de 136 Mo (Angular + Spring Boot, JRE seulement, JAR en couches, utilisateur non-root) ; **GitHub Actions** (tests Java et Angular, image, test de bout en bout, déploiement continu sur Render + Neon) ; **Dependabot**

## Tout lancer avec Docker

```bash
cp .env.example .env                              # puis remplace JWT_SECRET
docker compose -f compose.prod.yaml up -d --build # http://localhost:8083 (interface + API + Swagger UI)
```

## Développer

Prérequis : **JDK 25** et **Docker Desktop** démarré.

```bash
./mvnw test   # 40 tests : unitaires, intégration, concurrence, sécurité (Testcontainers démarre PostgreSQL)
```

L'application exige une clé de signature JWT d'au moins 32 caractères, dans la variable d'environnement `JWT_SECRET` :

```bash
export JWT_SECRET="$(openssl rand -base64 48)"   # PowerShell : $env:JWT_SECRET = "…une longue valeur aléatoire…"
./mvnw spring-boot:run                            # API sur http://localhost:8080 ; PostgreSQL démarre tout seul (compose.yaml)
```

Sous Windows, utiliser `mvnw.cmd`. Pour un autre port : `./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8082`.

**Essayer l'API dans le navigateur :** ouvre `/swagger-ui.html`, crée un client avec `POST /api/auth/inscription`, clique sur **Authorize** et colle le jeton.

**Interface web :** l'API doit tourner sur le port 8082 (voir `frontend/proxy.conf.json`).

```bash
cd frontend
npm install
npm start      # http://localhost:4200
npm test       # tests Angular (Vitest)
```

## API

### Authentification (publique)
| Méthode | URL | Description | Réponses |
|---|---|---|---|
| POST | `/api/auth/inscription` | `{ "courriel", "motDePasse" (12 à 72 car.), "nom" }` → `{ "jeton", "type": "Bearer", "expireDansSecondes", "client" }` | 201, 400, 409 |
| POST | `/api/auth/connexion` | `{ "courriel", "motDePasse" }` → jeton | 200, 400, 401 |

Toutes les autres routes 🔒 exigent l'en-tête `Authorization: Bearer <jeton>` (valide 1 heure). Chaque client ne voit et ne manipule **que ses propres comptes**. Le compte d'un autre client répond `404`, comme s'il n'existait pas.

### Comptes et opérations 🔒
| Méthode | URL | Description | Réponses |
|---|---|---|---|
| POST | `/api/comptes` | Ouvre un compte `{ "titulaire", "depotInitial" }` | 201 + `Location`, 400 |
| GET | `/api/comptes/{id}` | Un de mes comptes | 200, 404 |
| GET | `/api/comptes?page=0&size=20&sort=titulaire` | Mes comptes, paginés | 200 |
| POST | `/api/comptes/{id}/depots` | Dépôt `{ "montant", "description"? }` | 201, 400, 404 |
| POST | `/api/comptes/{id}/retraits` | Retrait | 201, 400, 404, **422** (solde insuffisant) |
| POST | `/api/virements` | Virement `{ "source", "destination", "montant", "description"? }`. La source doit m'appartenir ; la destination peut appartenir à un autre client. | 201, 400, 404, 422 |
| GET | `/api/comptes/{id}/operations?page=0&size=20` | Relevé (du plus récent au plus ancien) | 200, 404 |
| GET | `/actuator/health` | État de santé (public) | 200 |

**Garanties :** un virement débite et crédite dans **une seule transaction** (tout ou rien). Les comptes sont **verrouillés** (`SELECT … FOR UPDATE`) dans un ordre fixe pour éviter les interblocages, et `@Version` sert de deuxième protection. Des tests lancent des dizaines d'opérations **simultanées** et vérifient qu'aucun cent ne se crée ni ne se perd.

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
