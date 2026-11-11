# Mise en ligne : Neon + Render + GitHub Actions

Même principe que pour le Projet 1 (suivi-candidatures), avec une différence : **Flyway applique les migrations tout seul au démarrage** de Spring Boot. La CI n'a donc qu'à déclencher Render et vérifier.

```
git push (main) → GitHub Actions : API ✓  Interface ✓  Docker ✓ → job « deploy »
                                                                    │ 1. crochet de déploiement → Render construit l'image
                                                                    │ 2. attend que /actuator/info annonce le nouveau commit
                                                                    ▼ 3. test de fumée
                                           https://mini-banque-xxxx.onrender.com
```

## 1. Base de données : Neon
1. Va sur **console.neon.tech** (ton organisation « Personnel » existe déjà) et clique sur **New Project**.
2. Nom `mini-banque`, PostgreSQL **17**, région **AWS US East 2 (Ohio)**, seulement « Base de données Postgres ».
3. **Connect** → choisis la branche `main`, la base `neondb`, et garde **Connection pooling** activé.
4. Neon affiche une URL du type :
   `postgresql://neondb_owner:MOTDEPASSE@ep-xxx-pooler.us-east-2.aws.neon.tech/neondb?sslmode=require`
5. ⚠️ Java n'utilise **pas** ce format. Découpe-la en 3 valeurs :

| Variable | Valeur |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://ep-xxx-pooler.us-east-2.aws.neon.tech/neondb?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | `neondb_owner` |
| `SPRING_DATASOURCE_PASSWORD` | le mot de passe (entre `:` et `@` dans l'URL) |

(Dans Neon, l'onglet **Java** de la fenêtre *Connect* donne directement l'URL JDBC.)

## 2. Application : Render
1. Sur **dashboard.render.com** : **New → Blueprint**, puis choisis `mini-banque`. Si le dépôt n'apparaît pas, autorise-le dans les réglages GitHub de Render.
2. **Blueprint Name :** `mini-banque`.
3. Remplis les 3 variables `SPRING_DATASOURCE_*` (le tableau ci-dessus). `JWT_SECRET` est généré automatiquement.
4. **Deploy Blueprint.** La première construction prend 5 à 10 minutes (Angular, puis Maven).
5. Quand le service est **Live**, copie son adresse (`https://mini-banque-xxxx.onrender.com`) et son **Deploy Hook** (*Settings → Deploy Hook*, une URL secrète).

## 3. GitHub : secrets et variables
Dépôt `mini-banque` → **Settings → Secrets and variables → Actions** :

| Onglet | Nom | Valeur |
|---|---|---|
| *Secrets* | `RENDER_DEPLOY_HOOK_URL` | Le Deploy Hook |
| *Variables* | `URL_PRODUCTION` | L'adresse Render, **sans** `/` à la fin |
| *Variables* | `DEPLOIEMENT_ACTIF` | `true` |

Ici, il n'y a pas de `DATABASE_URL` dans GitHub : contrairement au Projet 1, la CI ne touche pas à la base. C'est Render qui la connaît.

## 4. Premier déploiement automatique
Fais un push sur `main`. Le 4e job, **Déploiement (Render + Neon)**, déclenche Render, attend la nouvelle version et vérifie la production.

## Dépannage
| Symptôme | Cause probable |
|---|---|
| Render : `Connection to … refused` / `password authentication failed` | Les variables `SPRING_DATASOURCE_*` sont mal recopiées (l'URL doit commencer par `jdbc:postgresql://` et ne contenir ni l'utilisateur ni le mot de passe) |
| Render : `JWT_SECRET doit contenir au moins 32 caractères` | La variable `JWT_SECRET` a été effacée : régénère-la dans *Environment* |
| La page met 1 à 2 minutes à répondre | Normal : le service gratuit se réveille, puis la JVM démarre |
| Le job *deploy* n'apparaît pas | `DEPLOIEMENT_ACTIF` ne vaut pas `true`, ou ce n'est pas un push sur `main` |
