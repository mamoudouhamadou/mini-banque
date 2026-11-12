# Journal d'apprentissage : Mini-banque

Pour chaque étape : ce qui a été fait, les notions à comprendre, et les questions qu'un recruteur pourrait poser. Lis le code dans l'ordre indiqué : chaque fichier est commenté en français.

---

## Étape 1 : comptes, couches Spring, PostgreSQL et tests

### Ordre de lecture
1. `pom.xml` : les dépendances (*starters* Spring Boot) et la version de Java.
2. `src/main/resources/db/migration/V1__creer_table_compte.sql` : la table.
3. `compte/Compte.java` : l'entité et ses règles.
4. `compte/CompteRepository.java`, puis `CompteService.java`, puis `CompteController.java`.
5. `compte/OuvertureCompte.java` et `CompteReponse.java` : les DTO.
6. `erreurs/GestionnaireErreurs.java`.
7. `application.properties` et `compose.yaml`.
8. Les tests : `CompteTest.java` (unitaire), puis `CompteApiTest.java` (intégration).

### Notions clés
- **Spring Boot :** un *framework* qui configure tout automatiquement selon les dépendances présentes. Avec `spring-boot-starter-data-jpa` et le pilote PostgreSQL, il crée lui-même la connexion à la base.
- **Inversion de contrôle et injection de dépendances :** on ne fait jamais `new CompteService(...)`. Spring crée les objets (*beans*) et les passe aux constructeurs. Résultat : des classes faiblement couplées, faciles à tester.
- **Architecture en couches :**
  - **Contrôleur** : HTTP et JSON, aucune logique métier ;
  - **Service** : règles et **transactions** ;
  - **Repository** : accès aux données.
  Chaque couche a une seule responsabilité (le « S » de SOLID).
- **JPA et Hibernate :** une classe `@Entity` correspond à une table, et un objet à une ligne. **Spring Data** génère l'implémentation du *repository* (`save`, `findById`, `findAll(Pageable)`…) à partir d'une simple interface.
- **Flyway :** les migrations SQL versionnées (`V1__…`). Hibernate est en mode `validate` : il **vérifie** que les entités correspondent aux tables, sans jamais les modifier. C'est cette vérification qui a détecté le problème `CHAR(3)`, plus bas.
- **`BigDecimal` pour l'argent :** en `double`, `0.1 + 0.2 = 0.30000000000000004` (le test `bigDecimalEviteLesErreursDArrondiDesDouble` le démontre). On utilise `NUMERIC(19,2)` en SQL et `BigDecimal` en Java, et on **refuse** les montants à 3 décimales plutôt que d'arrondir en silence.
- **Entité riche :** pas de *setters* publics. La seule façon de créer un compte est `Compte.ouvrir(...)`, qui applique les règles. Le constructeur vide `protected` n'existe que pour JPA.
- **Défense en profondeur :** le solde négatif est refusé à trois niveaux : validation de l'API (`@PositiveOrZero`), entité (`Compte.ouvrir`) et base de données (`CHECK solde >= 0`). Un test vérifie que la base le refuse même en contournant Java.
- **DTO (`record`) :** on n'expose jamais l'entité. `OuvertureCompte` décrit ce qui entre, et `CompteReponse` ce qui sort (sans le champ interne `version`).
- **Bean Validation :** les annotations (`@NotBlank`, `@Digits`…) combinées à `@Valid` donnent un `400` automatique. Les messages sont écrits en clair, pour être identiques quelle que soit la langue du serveur (la CI tourne en anglais).
- **RFC 9457 (*Problem Details*) :** un format d'erreur standard, `application/problem+json`, avec `type`, `title`, `status`, `detail` et `instance`. `@RestControllerAdvice` centralise la gestion des erreurs.
- **REST soigné :** `201 Created` avec un en-tête `Location` vers la nouvelle ressource, une **pagination** (`?page=0&size=20&sort=titulaire`) et un format de page stable (`VIA_DTO`).
- **`@Transactional` :** tout réussit ou tout est annulé. `readOnly = true` pour les lectures. Ce sera central à l'étape 2 (virements).
- **`open-in-view=false` :** aucune requête SQL cachée pendant la génération du JSON. Les données sont chargées dans le service, et c'est tout.
- **`@Version` (verrouillage optimiste) :** préparé pour l'étape 2. Si deux transactions modifient le même compte en même temps, la seconde échoue au lieu d'écraser la première.
- **`Clock` injectée :** l'heure vient d'un *bean*, donc un test peut la fixer. Il n'y a aucun `Instant.now()` caché dans le code métier.

### Tests
| Type | Fichier | Ce qu'il vérifie | Durée |
|---|---|---|---|
| Unitaire | `CompteTest` (5 tests) | Les règles de `Compte`, sans Spring ni base | ~75 ms |
| Intégration | `CompteApiTest` (8 tests) | Toute la chaîne HTTP → service → JPA → Flyway → **vrai PostgreSQL 17** (Testcontainers) | ~50 s, dont le démarrage de PostgreSQL |

- **Testcontainers** démarre PostgreSQL dans Docker pour les tests, et `@ServiceConnection` relie Spring au conteneur automatiquement. On teste avec la **même base qu'en production** (pas de base H2 « qui ressemble »).
- **MockMvcTester** (nouveau, basé sur AssertJ) simule des requêtes HTTP sans ouvrir de port.

### Problème rencontré (une bonne histoire C.A.R.)
- **Contexte :** au premier lancement des tests, l'application refusait de démarrer : *« Schema validation: wrong column type encountered in column [devise] »*.
- **Action :** j'ai lu le rapport de test. La migration Flyway crée `devise` en `CHAR(3)` (longueur fixe, adaptée à un code ISO comme « CAD »), mais Hibernate s'attend à un `VARCHAR` pour un `String`. Plutôt que de désactiver la validation, j'ai déclaré le type exact avec `@JdbcTypeCode(SqlTypes.CHAR)`.
- **Résultat :** les 13 tests passent, et la validation du schéma reste active. Elle garantit que le code et la base ne divergent jamais.

### Vérifié en vrai
L'application tourne sur le port 8082, et Spring Boot démarre PostgreSQL tout seul grâce à `compose.yaml` :
- `/actuator/health` → `UP` ;
- création → `201` + `Location` ;
- lecture, pagination triée ;
- `404` et `400` au format RFC 9457.

### Questions possibles en entrevue
- *Pourquoi `BigDecimal` et pas `double` ?* → `double` est binaire et ne représente pas exactement 0,1. Une banque ne peut pas perdre de cents.
- *Différence entre `@Controller`, `@Service` et `@Repository` ?* → Ce sont trois couches, avec trois responsabilités. Spring les gère toutes comme des *beans*.
- *Pourquoi Flyway plutôt que `ddl-auto=update` ?* → Les migrations sont versionnées, relues, identiques dans tous les environnements et réversibles par une nouvelle migration. `update` modifie la production sans contrôle.
- *Qu'est-ce que l'injection de dépendances ?* → Les dépendances d'une classe sont fournies de l'extérieur (par le constructeur), au lieu d'être créées par elle. On peut donc les remplacer dans les tests.
- *Pourquoi Testcontainers plutôt que H2 ?* → H2 n'est pas PostgreSQL : types, contraintes et SQL diffèrent. Avec Testcontainers, on teste exactement ce qui tournera en production.
- *Qu'est-ce qu'un DTO ?* → Un objet dédié aux échanges de l'API, distinct de l'entité. Ça évite d'exposer des champs internes et de lier l'API au schéma de la base.

### Exercices pour toi
1. Lance les tests (`mvnw test`, voir le README) et l'application, puis crée des comptes avec `curl` ou Postman.
2. Ajoute un champ `courriel` au titulaire : nouvelle migration (`V3__…`, puisque `V2` existe maintenant), entité, DTO, validation `@Email` et test. Remarque que tu ne modifies **jamais** une migration existante.
3. Ajoute un filtre `GET /api/comptes?titulaire=ana` (méthode dérivée de Spring Data : `findByTitulaireContainingIgnoreCase`).

---

## Étape 2 : dépôts, retraits, virements et concurrence

### Ordre de lecture
1. `db/migration/V2__creer_table_operation.sql` : la table du relevé et ses contraintes.
2. `Compte.java` : `deposer()` et `retirer()`.
3. `CompteRepository.verrouiller()` : `@Lock(PESSIMISTIC_WRITE)`.
4. `operation/OperationService.java` : **le cœur du projet**, surtout `virer()`.
5. `operation/Operation.java` (`@ManyToOne`, `@Enumerated`), puis `OperationController.java`.
6. `erreurs/GestionnaireErreurs.java` : les nouveaux codes `422` et `409`.
7. Les tests : `CompteTest`, `OperationApiTest`, puis **`ConcurrenceTest`**.

### Notions clés
- **ACID :**
  - **A**tomicité : tout ou rien ;
  - **C**ohérence : les contraintes sont toujours respectées ;
  - **I**solation : les transactions simultanées ne se marchent pas dessus ;
  - **D**urabilité : une fois validé, c'est écrit.
  Un virement, c'est **un débit et un crédit dans une seule transaction** (`@Transactional`). Si le crédit échoue, le débit est annulé.
- **Le problème du « lost update » :** deux retraits simultanés de 100 $ sur un compte de 100 $. Les deux lisent « solde = 100 », les deux retirent, et la banque a donné 200 $. Le test `vingtRetraitsSimultanes…` reproduit exactement ce scénario, 20 fois en parallèle.
- **Verrou pessimiste (`SELECT … FOR UPDATE`) :** on verrouille la ligne **avant** de la lire. Les autres transactions qui veulent ce compte **attendent** leur tour, puis lisent le solde à jour. On l'a choisi pour l'argent, parce que les conflits sont fréquents et qu'un échec coûte cher.
- **Verrou optimiste (`@Version`) :** on ne bloque personne. Au moment d'écrire, on vérifie `WHERE version = ?`, et si quelqu'un a modifié la ligne entre-temps, l'écriture échoue (`409`). Ici, c'est le **filet de sécurité** derrière le verrou pessimiste.
- **Interblocage (*deadlock*) :** Ana→Bob verrouille Ana puis attend Bob, pendant que Bob→Ana verrouille Bob puis attend Ana. Les deux attendent pour toujours. **Solution :** verrouiller **toujours dans le même ordre** (le plus petit id d'abord). Le test des 100 virements croisés le prouve.
- **Codes HTTP métier :**
  - `400` : la requête est mal formée (montant à 3 décimales) ;
  - `404` : le compte n'existe pas ;
  - `422` : la requête est correcte, mais une règle l'interdit (solde insuffisant, virement vers soi-même) ;
  - `409` : il y a eu un conflit de modification simultanée.
- **Historique immuable :** une opération ne se modifie jamais. On corrige une erreur avec une **nouvelle** opération inverse, comme en comptabilité. `soldeApres` permet de lire le relevé sans tout recalculer.
- **Contraintes SQL comme garde-fous :** `montant > 0`, un type parmi 4 valeurs, un `virement_id` présent **si et seulement si** c'est un virement, une clé étrangère `ON DELETE RESTRICT` (on ne peut pas supprimer un compte qui a un historique) et un index `(compte_id, id DESC)` pour le relevé.
- **JPA :** `@ManyToOne(fetch = LAZY)` ne charge le compte que si on y accède. `@Enumerated(STRING)` stocke « DEPOT » et non `0`, donc réordonner l'enum ne corrompt rien.

### La contre-épreuve (excellente histoire d'entrevue)
- **Contexte :** les 27 tests passaient du premier coup. Mais un test de concurrence qui passe ne prouve rien s'il passerait aussi avec du code bogué.
- **Action :** j'ai **retiré temporairement le verrou pessimiste** (`findById` au lieu de `verrouiller`) et relancé `ConcurrenceTest`.
- **Résultat :**
  - les deux tests ont **échoué**, ce qui prouve qu'ils détectent vraiment le problème ;
  - et l'erreur était `ObjectOptimisticLockingFailureException` : même sans verrou, **`@Version` avait empêché la perte d'argent** en annulant les transactions en conflit ;
  - conclusion : deux protections complémentaires. Le verrou pessimiste fait **attendre** les clients, et `@Version` **refuse** une écriture basée sur une donnée périmée.
  - J'ai remis le verrou et ajouté une réponse `409` propre pour ce cas.

### Vérifié en vrai
Au démarrage, Flyway a appliqué `V2` à la base de développement **sans toucher aux comptes existants**. Dépôt, virement (avec son UUID et les deux soldes), `422` avec le solde disponible, et relevé du plus récent au plus ancien : tout fonctionne.

### Questions possibles en entrevue
- *Comment garantissez-vous qu'un virement ne perd pas d'argent ?* → Une seule transaction, et un débit qui échoue **avant** le crédit si le solde manque. Des tests le vérifient, en parallèle.
- *Verrou pessimiste ou optimiste ?* → Le pessimiste quand les conflits sont fréquents et coûteux (l'argent). L'optimiste quand ils sont rares (modifier un profil). J'utilise les deux ici.
- *Comment évitez-vous les interblocages ?* → En verrouillant les ressources toujours dans le même ordre.
- *Comment testez-vous la concurrence ?* → Des tâches lancées en même temps grâce à une barrière (`CountDownLatch`), contre un vrai PostgreSQL, en vérifiant des **invariants** (total conservé, solde jamais négatif). Puis une contre-épreuve : je casse le code exprès pour vérifier que le test échoue.
- *Pourquoi `422` et pas `400` pour un solde insuffisant ?* → La requête est valide, c'est l'état du compte qui l'empêche.
- *Que se passe-t-il si le client renvoie deux fois le même virement (panne réseau) ?* → Aujourd'hui, il serait exécuté deux fois. La solution pro est une **clé d'idempotence** (voir l'exercice 3).

### Exercices pour toi
1. Lance `ConcurrenceTest` seul (`mvnw test -Dtest=ConcurrenceTest`), puis refais la contre-épreuve toi-même.
2. Ajoute une limite de retrait de 2 000 $ par jour : il faut additionner les retraits du jour (une requête `@Query` avec `SUM`). Pense au cas de deux retraits simultanés, le verrou est déjà là.
3. **Idempotence :** un en-tête `Idempotency-Key` sur `POST /api/virements`, une table `virement` avec une contrainte `UNIQUE` sur la clé, et le même virement renvoyé si la clé existe déjà.

---

## Étape 3 : Spring Security, JWT et isolation des clients

### Ordre de lecture
1. `db/migration/V3__clients_et_proprietaires.sql` : une migration sur une table **déjà remplie**.
2. `securite/ConfigurationSecurite.java` : **le fichier central**.
3. `client/AuthService.java`, `client/AuthController.java`, puis `securite/Jetons.java`.
4. `client/Inscription.java` : le constructeur compact.
5. `CompteService`, puis `OperationService` (`verrouillerLeMien`, et la vérification de la source dans `virer`).
6. `securite/SecuriteApiTest.java`.

### Notions clés
- **Authentification ou autorisation :** l'*authentification* répond à « qui es-tu ? » (jeton absent ou invalide → `401`). L'*autorisation* répond à « as-tu le droit ? » (ce compte est-il le tien ?).
- **Chaîne de filtres de Spring Security :** chaque requête traverse des filtres **avant** d'atteindre le contrôleur. Le filtre *Bearer token* lit `Authorization: Bearer …`, et Spring vérifie la **signature**, l'**expiration** et l'**émetteur** (`iss`). On n'écrit aucun code de vérification : c'est le rôle de `oauth2ResourceServer().jwt()`.
- **Refus par défaut :** `anyRequest().authenticated()`. Une nouvelle route oubliée reste **protégée**. On n'ouvre explicitement que l'inscription, la connexion et la santé.
- **JWT (HS256) :** `en-tête.contenu.signature`. Le contenu (`iss`, `sub` = id du client, `iat`, `exp`) est **lisible** par tous, mais il ne peut pas être **modifié** sans la clé secrète. Le serveur ne garde aucune session : il est *stateless*, donc on peut lancer plusieurs instances sans partager d'état.
- **Secret hors du code :** `JWT_SECRET` vient d'une variable d'environnement. L'application **refuse de démarrer** si la clé est absente ou fait moins de 32 caractères (256 bits pour HS256).
- **BCrypt :** un hachage lent, avec un sel aléatoire. `DelegatingPasswordEncoder` préfixe le hachage par son algorithme (`{bcrypt}$2a$10$…`), ce qui permettra de migrer vers Argon2 sans invalider les anciens mots de passe.
- **CSRF désactivé, et pourquoi c'est correct ici :** l'attaque CSRF exploite les **cookies**, que le navigateur envoie tout seul. Notre jeton voyage dans un **en-tête** que le navigateur n'envoie jamais de lui-même.
- **`404` plutôt que `403`** pour le compte d'un autre client : on ne confirme même pas son existence. On le vérifie au niveau du **service**, pas seulement du contrôleur, et en SQL (`WHERE client_id = ?`).
- **Virement entre clients :** la **source** doit m'appartenir, mais la **destination** peut être à quelqu'un d'autre, comme un virement Interac.
- **Anti-énumération :**
  - le même message d'erreur pour un courriel inconnu et pour un mauvais mot de passe ;
  - un **hachage factice** comparé quand le courriel n'existe pas, pour que la réponse prenne le même temps : on ne peut pas deviner les courriels inscrits au chronomètre.
- **Unicité robuste :** un index `UNIQUE (lower(courriel))` en base. Si deux inscriptions arrivent en même temps, c'est la base qui tranche, et l'exception devient un `409`.

### La migration V3 sur des données existantes (*expand / backfill / contract*)
Une colonne `NOT NULL` ne peut pas être ajoutée à une table qui contient déjà des lignes. Méthode en 3 temps :
1. ajouter la colonne en acceptant `NULL` ;
2. **remplir** les lignes existantes (rattachées à un client technique **qui ne peut pas se connecter**) ;
3. **resserrer** : `NOT NULL`, clé étrangère et index.

✅ Vérifié en vrai : ta base de développement avait 4 comptes des étapes 1-2. Ils ont tous été rattachés sans perte, et la connexion au client technique est impossible (`401`).

### Problèmes rencontrés (histoires C.A.R.)
1. **Un fichier de configuration de test qui écrase celui de l'application.** `src/test/resources/application.properties` **remplace** celui de `main` au lieu de le compléter : on aurait perdu `ddl-auto=validate`. Solution : le placer dans `src/test/resources/config/`. Spring Boot charge alors **les deux**.
2. **Un vrai bogue trouvé par un test :** « ␣Ana@Exemple.CA␣ » (avec des espaces) était refusé par `@Email`, parce que la validation passe **avant** le nettoyage. Solution : un **constructeur compact** du `record`, qui normalise le courriel dès sa création, donc avant la validation. C'est exactement le même bogue que dans le Projet 1 (Zod) : une erreur classique, à connaître.

### Tests
**40 tests**, dont **13 de sécurité** (`SecuriteApiTest`, organisé en classes `@Nested`) :
- **inscription :** hachage BCrypt en base, courriel normalisé, doublon refusé (`409`), mot de passe faible refusé ;
- **connexion :** la réponse est **identique** pour un mauvais mot de passe et un courriel inconnu ;
- **jetons :** absent → `401` au format RFC 9457 avec `WWW-Authenticate: Bearer` ; refusés aussi s'ils sont **signés avec une autre clé**, **expirés**, d'un **autre émetteur** ou **modifiés** ;
- **isolation :** Bob ne voit pas les comptes d'Ana (`404`), ne peut ni retirer ni virer depuis ceux-ci, et le solde d'Ana reste intact. Ana, elle, peut virer **vers** Bob.

### Questions possibles en entrevue
- *Session ou JWT ?* → La session garde l'état côté serveur (facile à révoquer, mais il faut la partager entre instances). Le JWT est sans état côté serveur (facile à multiplier, mais difficile à révoquer avant son expiration, d'où la durée courte d'une heure).
- *Comment révoquer un JWT ?* → Avec une durée courte et un jeton de rafraîchissement, ou une liste de jetons révoqués. Pour des systèmes plus gros : un serveur d'autorisation (Keycloak, Azure AD) et des clés RS256.
- *HS256 ou RS256 ?* → HS256 utilise une seule clé secrète, qui signe et vérifie. RS256 utilise une clé privée pour signer et une clé publique pour vérifier : d'autres services peuvent alors vérifier sans pouvoir fabriquer de jetons.
- *Pourquoi désactiver CSRF ?* → Parce que l'authentification ne passe pas par un cookie (voir plus haut).
- *Où vérifiez-vous les droits ?* → Dans le service et dans la requête SQL. Jamais seulement côté interface, et jamais seulement dans le contrôleur.
- *Comment testez-vous la sécurité ?* → Avec de vrais jetons émis par l'application, et des jetons **fabriqués exprès** pour être invalides : mauvaise clé, expiré, mauvais émetteur, signature modifiée.

### Exercices pour toi
1. Inscris-toi avec `curl`, colle ton jeton sur **jwt.io** et lis son contenu. Change un caractère et rappelle l'API : tu obtiens `401`.
2. Ajoute `GET /api/auth/moi`, qui renvoie le client connecté (`@AuthenticationPrincipal Jwt`).
3. Ajoute une **limite de tentatives** de connexion : par exemple 5 par 15 minutes et par courriel, avec un compteur en mémoire ou dans une table.

---

## Étape 4 : documentation OpenAPI et interface Angular

### Partie A : OpenAPI et Swagger UI

**Ordre de lecture :** `ConfigurationOpenApi.java`, les annotations `@Tag` et `@Operation` des contrôleurs, la règle ajoutée dans `ConfigurationSecurite`, puis `OpenApiTest.java`.

- **OpenAPI 3 :** un format standard (JSON ou YAML) qui décrit l'API : routes, corps, réponses et sécurité. **springdoc** le **génère depuis le code** : la documentation ne peut donc jamais être en retard sur le code.
- **Swagger UI** (`/swagger-ui.html`) : une page pour **lire et essayer** l'API. Le bouton **Authorize** reçoit le jeton JWT grâce au `@SecurityScheme` de type *bearer*.
- **« API-first » :** à partir du contrat `/v3/api-docs`, on peut **générer** un client TypeScript, Java ou C#, l'importer dans Postman, ou écrire des tests de contrat.
- **Routes publiques documentées :** `@SecurityRequirements` vide sur `AuthController` indique dans la doc que l'inscription et la connexion n'exigent pas de jeton.
- **La documentation est publique, les données non :** seules les pages de documentation (`GET`) sont ouvertes dans Spring Security. Elles décrivent l'API sans donner accès à rien.
- **Testé :** le contrat liste les 8 routes, déclare le schéma *bearer*, marque les routes publiques, et **n'expose pas** le paramètre interne `@AuthenticationPrincipal Jwt`. Swagger UI répond sans jeton.

### Partie B : interface Angular

**Ordre de lecture :**
1. `frontend/src/app/app.config.ts` : routeur, HttpClient avec intercepteur, locale `fr-CA`.
2. `app.routes.ts` : routes chargées à la demande et gardes.
3. `auth/auth.service.ts`, puis `auth.interceptor.ts` et `auth.guard.ts`.
4. `banque.service.ts` et `erreurs.ts`.
5. `pages/` : connexion, comptes, compte, virement.
6. `app.spec.ts` et `erreurs.spec.ts`.

**Notions clés :**
- **Composants *standalone* :** chaque composant déclare ce qu'il utilise (`imports: [...]`), sans `NgModule`. C'est la façon moderne d'écrire Angular.
- **Signals :** `signal()` pour l'état, `computed()` pour les valeurs dérivées (le solde total), et la lecture dans le gabarit (`total()`). Quand un signal change, Angular met à jour **seulement** ce qui en dépend.
- ***Zoneless* :** il n'y a plus de `zone.js` qui surveille tout. L'affichage se met à jour grâce aux signals : c'est plus léger et plus prévisible. Tout l'état des pages est donc dans des signals.
- **Nouveau contrôle de flux :** `@if`, `@for (…; track c.id)` et `@empty` dans les gabarits. `track` indique à Angular comment reconnaître chaque élément d'une liste.
- **Injection de dépendances :** `inject(AuthService)`, le même principe que Spring côté serveur.
- **Reactive Forms :** le formulaire est un objet TypeScript (`FormBuilder.nonNullable.group`) avec des validateurs. La validation **finale** reste celle de l'API, dont les messages précis sont affichés.
- **Intercepteur HTTP :** il ajoute `Authorization: Bearer …` à chaque appel (sauf `/api/auth/*`), et **déconnecte** automatiquement sur un `401`.
- **Gardes de route :** elles évitent d'ouvrir une page protégée sans être connecté. ⚠️ C'est du **confort**, pas de la sécurité : la vraie protection est sur le serveur. Un pirate peut modifier le JavaScript, mais pas Spring Security.
- ***Lazy loading* :** `loadComponent` charge chaque page seulement quand on la visite. La compilation montre un fichier séparé par page, de 3 à 5 ko.
- **Proxy de développement** (`proxy.conf.json`) : `localhost:4200/api` est relayé vers l'API. Pour le navigateur, c'est la même origine, donc pas de CORS.
- **Formats québécois :** `registerLocaleData(fr-CA)` et `LOCALE_ID` affichent `2 320,75 $` et `11 novembre 2026`.
- **Où garder le jeton ?** Dans `sessionStorage`, effacé à la fermeture de l'onglet. C'est plus court que `localStorage`, mais lisible par un script injecté (XSS). Un cookie `HttpOnly` serait plus sûr contre le XSS, mais demanderait une protection CSRF : c'est un compromis à savoir expliquer.

**Tests (Vitest, 9 tests) :**
- `HttpTestingController` remplace le réseau : on vérifie les requêtes envoyées (corps, en-tête `Authorization`) et on choisit les réponses ;
- cas couverts : la connexion ouvre la session ; l'intercepteur ajoute le jeton, mais **pas** sur les routes publiques ; un `401` déconnecte ; un jeton expiré est ignoré ; l'en-tête n'apparaît qu'une fois connecté ; les messages d'erreur RFC 9457 sont traduits correctement.

**Vérifié en vrai** (API + Angular, dans le navigateur) :
- accès sans être connecté → redirection vers `/connexion` ;
- inscription → « Mes comptes », avec le total au format québécois ;
- virement par le formulaire → confirmation avec la référence UUID et soldes mis à jour ;
- retrait trop gros → le message `422` du serveur s'affiche ;
- relevé avec « Virement reçu +300,00 $ » ;
- compte d'un autre client → « Le compte 1 n'existe pas » ;
- téléphone : aucun débordement ;
- aucune erreur dans la console.

### Problèmes rencontrés
1. **Angular 22 exigeait Node 24.15**, alors que le PC a Node 24.13. Plutôt que de modifier Node sur tout le système (ce qui pourrait toucher d'autres projets), j'ai pris **Angular 21.2**, la version majeure précédente, toujours maintenue. Le code est le même. Mettre Node à jour permettra de passer à Angular 22 avec `ng update`.
2. **Un test vérifiait un paramètre qui n'existait pas :** la route `/api/virements` n'a *aucun* paramètre dans la doc, ce qui était exactement le comportement voulu. J'ai rendu le test plus précis (le relevé expose seulement `id`, `page` et `size`) au lieu de l'affaiblir.
3. **Erreurs non gérées dans les tests Angular :** la déconnexion naviguait vers `/connexion` dans un routeur de test vide. Solution : une route « attrape-tout » dans la configuration de test.

### Questions possibles en entrevue
- *Pourquoi documenter avec OpenAPI ?* → Un contrat standard, toujours à jour, et des clients générés automatiquement. Les équipes front-end et back-end travaillent à partir du même contrat.
- *Signals ou RxJS ?* → Les signals servent à l'**état** synchrone (ce qu'on affiche). RxJS sert aux **flux** asynchrones (requêtes HTTP, événements). Ici, `HttpClient` renvoie des `Observable`, et le résultat est rangé dans des signals.
- *Une garde de route, c'est de la sécurité ?* → Non. C'est de l'ergonomie. La sécurité est sur le serveur.
- *Comment gérez-vous l'authentification dans Angular ?* → Un service avec la session dans des signals, un intercepteur qui ajoute le jeton et réagit au `401`, et des gardes pour la navigation.
- ***Zoneless*, qu'est-ce que ça change ?* → Angular ne détecte plus les changements « par magie » après chaque événement : ce sont les signals qui le préviennent. C'est plus rapide et plus explicite.

### Exercices pour toi
1. Lance l'API et l'interface (README), crée ton accès et fais un virement vers un autre client.
2. Génère un client TypeScript depuis `/v3/api-docs` (avec `openapi-typescript`), puis compare-le à `modeles.ts`.
3. Ajoute la pagination à la page « Mes comptes », comme celle du relevé.

---

## Étape 5 : Docker, CI/CD et mise en ligne

### Ordre de lecture
1. `InterfaceWebController.java` et les nouvelles règles de `ConfigurationSecurite` : Spring sert l'interface Angular.
2. `application.properties` : `server.port=${PORT}`, `forward-headers-strategy`, `/actuator/info`.
3. `Dockerfile` (3 étapes), puis `.dockerignore` et `compose.prod.yaml`.
4. `.github/workflows/ci.yml`, puis `.github/dependabot.yml`.
5. `render.yaml` et `docs/DEPLOIEMENT.md`.

### Notions clés
- **Un seul service à héberger :** Angular est compilé, puis copié dans `src/main/resources/static`, et Spring Boot sert ces fichiers. Pour une route Angular rechargée dans le navigateur (`/comptes/7`), Spring renvoie `index.html` (*forward*), puis Angular affiche la bonne page. Les routes sont **listées explicitement** : une URL inconnue n'est pas servie comme l'interface.
- **Sécurité :** l'interface (fichiers statiques et routes) est publique, puisqu'elle ne contient aucune donnée. `/api/**` exige toujours un jeton (testé). Une **CSP** (`default-src 'self'`) n'autorise que les scripts de notre domaine. J'ai désactivé l'*inlining* du CSS critique d'Angular, qui ajoute un `onload="…"` en ligne : la CSP l'aurait bloqué.
- **Dockerfile en 3 étapes :**
  1. Node : compile Angular ;
  2. Maven et JDK 25 : compile Spring Boot avec l'interface intégrée, puis **découpe le JAR en couches** ;
  3. **JRE seulement**, utilisateur non-root, *healthcheck* : **136 Mo**.
  Les couches font qu'une nouvelle version ne renvoie que la petite couche « application » : les dépendances restent en cache.
- **Cache Maven (`--mount=type=cache`) :** les dépendances ne sont pas retéléchargées à chaque construction.
- **Configuration par variables d'environnement :** `SPRING_DATASOURCE_URL` correspond automatiquement à `spring.datasource.url` (*relaxed binding* de Spring Boot). Aucun mot de passe de production dans le code.
- **`server.forward-headers-strategy=framework` :** derrière le proxy HTTPS de Render, Spring construit des URL en `https://` (l'en-tête `Location` par exemple).
- **Migrations en production :** Flyway les applique **au démarrage**. Contrairement au Projet 1, la CI n'a pas besoin d'accéder à la base.
- **CI, 4 jobs :**
  - `api` : `./mvnw verify`, avec les 49 tests et Testcontainers sur les machines GitHub ;
  - `interface` : tests Vitest et build de production ;
  - `docker` : l'image et un **test de bout en bout** (interface, Swagger, inscription, comptes, dépôt, virement, `401`, `422`) ;
  - `deploy` : crochet Render, puis attente que `/actuator/info` annonce **ce** commit, puis test de fumée.

### Problèmes rencontrés (histoires C.A.R.)
1. **`mvnw` sans droit d'exécution :** Windows ne garde pas ce droit, donc `./mvnw` aurait échoué sous Linux dans la CI. Je l'ai corrigé dans Git (`git update-index --chmod=+x mvnw`), avant même le premier essai.
2. **Le test de bout en bout recevait un `401`** alors que toutes les pages répondaient `200`. En traçant le script (`bash -x`), j'ai trouvé la cause : `curl -I` envoie une requête **HEAD**, alors que la sécurité n'autorise que **GET** sur l'interface. L'application était correcte, puisque les navigateurs font des GET. J'ai corrigé le test pour lire les en-têtes d'un vrai GET.
3. **Avast** (comme au Projet 1) : le certificat est passé à Node **et** au magasin de certificats Java de l'étape de construction, comme *secret de build*. Il n'est jamais copié dans l'image finale.

### Vérifié en vrai
- ✅ Image construite (136 Mo) ; application et PostgreSQL `healthy`.
- ✅ Le test de bout en bout du workflow, exécuté localement : `✅ Mini-banque fonctionnelle de bout en bout`.
- ✅ Dans le navigateur : Angular se charge depuis Spring Boot, avec les styles et la redirection vers la connexion, et **aucune erreur CSP** dans la console.
- ✅ 49 tests Java, 9 tests Angular, et actionlint sans erreur.
- ✅ **En ligne** sur https://mini-banque.onrender.com (Neon + Render). **Problème rencontré au premier déploiement :** `'url' must start with "jdbc"`. L'URL Neon avait été collée dans son format habituel (`postgresql://…`) au lieu du format Java (`jdbc:postgresql://…`). Je l'ai corrigée dans les variables de Render, sans changer le code.

### Questions possibles en entrevue
- *Comment déployez-vous une application Spring Boot avec Angular ?* → Une seule image Docker. Angular est compilé dans les ressources statiques, et Spring sert l'interface et l'API. C'est une seule origine, donc pas de CORS.
- *Pourquoi un JAR en couches ?* → Pour accélérer les constructions et les déploiements : seule la couche de l'application change d'une version à l'autre.
- *JDK ou JRE dans l'image finale ?* → Le JRE suffit pour exécuter : l'image est plus petite et la surface d'attaque réduite.
- *Comment savez-vous que la bonne version est en ligne ?* → `/actuator/info` expose le commit déployé, et la CI attend qu'il corresponde avant le test de fumée.
