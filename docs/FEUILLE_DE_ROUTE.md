# Feuille de route : Mini-banque

## Objectif
Une API bancaire simplifiée : ouvrir des comptes, déposer, retirer et **virer de l'argent entre comptes sans jamais perdre ni créer un sou**, même quand plusieurs virements arrivent en même temps.

C'est le genre de problème que les banques, les assurances et Loto-Québec posent en entrevue Java : transactions, concurrence, précision des montants, tests.

## Stack
- **Java 25** (LTS) et **Spring Boot 4.1**
- **Spring Web MVC** (API REST), **Bean Validation**, erreurs au format **RFC 9457** (`ProblemDetail`)
- **Spring Data JPA** (Hibernate) et **PostgreSQL 17**
- **Flyway** (migrations SQL versionnées)
- **JUnit 5**, **AssertJ**, **MockMvcTester** et **Testcontainers** (un vrai PostgreSQL dans les tests)
- Maven (avec le *wrapper* `mvnw`)

## Architecture (en couches)

```
Requête HTTP
   │
   ▼
CompteController   ← couche web : HTTP, JSON, validation des entrées, codes de statut
   │
   ▼
CompteService      ← couche métier : règles et transactions (@Transactional)
   │
   ▼
CompteRepository   ← accès aux données : Spring Data JPA génère le SQL
   │
   ▼
PostgreSQL         ← schéma créé et versionné par Flyway
```

```
src/main/java/io/github/mamoudouhamadou/minibanque/
├── MiniBanqueApplication.java     point d'entrée
├── compte/
│   ├── Compte.java                entité JPA (une ligne de la table « compte »)
│   ├── CompteRepository.java      interface : Spring Data fournit l'implémentation
│   ├── CompteService.java         logique métier et transactions
│   ├── CompteController.java      routes REST /api/comptes
│   ├── OuvertureCompte.java       ce que le client envoie (record + validation)
│   ├── CompteReponse.java         ce que l'API renvoie (record)
│   └── CompteIntrouvableException.java
├── client/
│   ├── Client.java                le client (courriel, hachage du mot de passe)
│   ├── AuthService.java           inscription et connexion (BCrypt, temps constant)
│   └── AuthController.java        /api/auth/inscription, /api/auth/connexion (publiques)
├── securite/
│   ├── ConfigurationSecurite.java règles d'accès, JWT (HS256), mots de passe, erreurs 401/403
│   ├── Jetons.java                émission des jetons
│   └── ClientConnecte.java        id du client à partir du jeton
├── operation/
│   ├── Operation.java             une ligne du relevé (immuable), relation @ManyToOne vers Compte
│   ├── OperationService.java      dépôts, retraits, VIREMENTS (transaction + verrous ordonnés)
│   ├── OperationController.java   /api/comptes/{id}/depots, /retraits, /operations ; /api/virements
│   └── …Requete / …Reponse        DTO
└── erreurs/
    └── GestionnaireErreurs.java   transforme les exceptions en réponses RFC 9457
src/main/resources/
├── application.properties
└── db/migration/
    ├── V1__creer_table_compte.sql
    ├── V2__creer_table_operation.sql
    └── V3__clients_et_proprietaires.sql
```

## Étapes

| # | Étape | Ce que tu apprends | État |
|---|---|---|---|
| 1 | Comptes : ouvrir, consulter, lister (pagination) ; validation ; erreurs RFC 9457 ; tests avec un vrai PostgreSQL | Spring Boot, couches, JPA, Flyway, Bean Validation, Testcontainers | ✅ |
| 2 | Dépôts, retraits et **virements transactionnels** ; historique des opérations ; **tests de concurrence** | `@Transactional`, verrous, `BigDecimal`, ACID | ✅ |
| 3 | Sécurité : Spring Security + JWT, chaque client ne voit que ses comptes | Authentification, autorisation | ✅ |
| 4 | Documentation OpenAPI (Swagger UI) et interface Angular | API-first, Angular | ✅ |
| 5 | Docker, CI GitHub Actions et mise en ligne | DevOps | ✅ En ligne : https://mini-banque.onrender.com |

Après chaque étape : lire `docs/JOURNAL.md`, relancer les tests soi-même, faire un commit.
