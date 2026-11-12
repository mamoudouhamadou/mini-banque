# Déploiement : Neon, Render et GitHub Actions

```
git push (main) → GitHub Actions : API ✓  Interface ✓  Docker ✓ → job « deploy »
                                                                    │ 1. crochet de déploiement → Render construit l'image
                                                                    │ 2. attente de la nouvelle version (/actuator/info)
                                                                    ▼ 3. test de fumée
                                                     https://mini-banque.onrender.com
```

| Service | Rôle |
|---|---|
| **Neon** | Base PostgreSQL (offre gratuite : 1 Go, sans expiration) |
| **Render** | Exécution de l'image Docker ; l'offre gratuite se met en veille après 15 minutes d'inactivité |
| **GitHub Actions** | Tests, image, puis déclenchement du déploiement |

Les migrations Flyway sont appliquées au démarrage de Spring Boot : la CI n'accède pas à la base. Le déploiement automatique de Render est désactivé (`autoDeployTrigger: off`) afin qu'aucune version ne soit publiée sans que tous les tests aient réussi.

## 1. Base de données (Neon)
1. Créer un projet : PostgreSQL 17, région AWS US East 2 (Ohio), *connection pooling* activé.
2. Relever les paramètres de connexion au format JDBC (onglet **Java** de la fenêtre *Connect*) :

| Variable | Exemple |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<hôte>-pooler.us-east-2.aws.neon.tech/neondb?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | `neondb_owner` |
| `SPRING_DATASOURCE_PASSWORD` | mot de passe du rôle |

L'URL doit commencer par `jdbc:postgresql://` et ne contenir ni l'utilisateur ni le mot de passe.

## 2. Application (Render)
1. **New → Blueprint**, puis sélectionner ce dépôt : Render lit `render.yaml`.
2. Renseigner les trois variables `SPRING_DATASOURCE_*`. `JWT_SECRET` est généré par Render.
3. Après le premier déploiement, relever l'adresse publique et le **Deploy Hook** (*Settings → Deploy Hook*).

## 3. Secrets et variables GitHub
*Settings → Secrets and variables → Actions* :

| Type | Nom | Valeur |
|---|---|---|
| Secret | `RENDER_DEPLOY_HOOK_URL` | Deploy Hook de Render |
| Variable | `URL_PRODUCTION` | Adresse publique, sans `/` final |
| Variable | `DEPLOIEMENT_ACTIF` | `true` |

## 4. Déploiement
Chaque push sur `main` déclenche le job **Déploiement** une fois les autres jobs réussis.

## Dépannage
| Symptôme | Cause probable |
|---|---|
| `'url' must start with "jdbc"` | `SPRING_DATASOURCE_URL` n'est pas au format JDBC |
| `password authentication failed` | Utilisateur ou mot de passe incorrect |
| `JWT_SECRET doit contenir au moins 32 caractères` | Variable `JWT_SECRET` absente : la régénérer dans *Environment* |
| Première réponse lente (1 à 3 minutes) | Réveil de l'instance gratuite, puis démarrage de la JVM |
| Le job *deploy* est ignoré | `DEPLOIEMENT_ACTIF` ne vaut pas `true`, ou l'événement n'est pas un push sur `main` |
