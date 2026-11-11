-- Migration Flyway n° 1 : création de la table des comptes.
-- Flyway exécute les fichiers V1__, V2__, ... dans l'ordre, une seule fois chacun,
-- et note dans la table « flyway_schema_history » ceux qui sont déjà appliqués.
-- Règle d'or : on ne modifie JAMAIS une migration déjà appliquée ; on en ajoute une nouvelle.

CREATE TABLE compte (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    titulaire VARCHAR(100)   NOT NULL,
    -- NUMERIC(19, 2) : montant exact (jamais de FLOAT ou DOUBLE pour de l'argent !)
    solde     NUMERIC(19, 2) NOT NULL,
    devise    CHAR(3)        NOT NULL DEFAULT 'CAD',
    ouvert_le TIMESTAMPTZ    NOT NULL,
    -- Verrouillage optimiste : incrémenté à chaque modification (voir @Version dans Compte.java)
    version   BIGINT         NOT NULL DEFAULT 0,

    -- Dernière ligne de défense : même si le code Java a un bogue, la base refuse un solde négatif.
    CONSTRAINT solde_non_negatif CHECK (solde >= 0)
);
