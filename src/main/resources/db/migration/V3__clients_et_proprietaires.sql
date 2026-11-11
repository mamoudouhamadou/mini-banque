-- Migration Flyway n° 3 : les clients, et le propriétaire de chaque compte.
--
-- Difficulté réelle : la table « compte » contient peut-être DÉJÀ des lignes (base de dev, production…).
-- On ne peut donc pas simplement ajouter une colonne NOT NULL sans valeur. Méthode classique en 3 temps :
--   1. AJOUTER la colonne en acceptant NULL ;
--   2. REMPLIR les lignes existantes (« backfill ») ;
--   3. RESSERRER : NOT NULL + clé étrangère.

CREATE TABLE client (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    courriel          VARCHAR(254) NOT NULL,
    -- Jamais le mot de passe : seulement son hachage (BCrypt), préfixé par l'algorithme : {bcrypt}$2a$10$…
    mot_de_passe_hache VARCHAR(100) NOT NULL,
    nom               VARCHAR(100) NOT NULL,
    cree_le           TIMESTAMPTZ  NOT NULL
);

-- Un seul compte client par courriel, sans tenir compte des majuscules (Ana@X.ca = ana@x.ca)
CREATE UNIQUE INDEX client_courriel_unique ON client (lower(courriel));

-- 1. Ajouter la colonne (NULL accepté pour l'instant)
ALTER TABLE compte ADD COLUMN client_id BIGINT;

-- 2. Les comptes créés avant l'arrivée des clients sont rattachés à un client technique
--    dont le mot de passe ne peut correspondre à rien (« {bcrypt}! » n'est pas un hachage BCrypt valide :
--    toute tentative de connexion échoue).
INSERT INTO client (courriel, mot_de_passe_hache, nom, cree_le)
SELECT 'comptes-historiques@mini-banque.invalid', '{bcrypt}!', 'Comptes antérieurs aux clients', now()
WHERE EXISTS (SELECT 1 FROM compte);

UPDATE compte
SET client_id = (SELECT id FROM client WHERE courriel = 'comptes-historiques@mini-banque.invalid')
WHERE client_id IS NULL;

-- 3. Resserrer : chaque compte a désormais obligatoirement un propriétaire existant
ALTER TABLE compte ALTER COLUMN client_id SET NOT NULL;
ALTER TABLE compte ADD CONSTRAINT compte_client_fk FOREIGN KEY (client_id) REFERENCES client (id) ON DELETE RESTRICT;

-- « Mes comptes » est la requête la plus fréquente : on l'indexe.
CREATE INDEX compte_client ON compte (client_id);
