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
