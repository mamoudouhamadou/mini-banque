-- Migration Flyway n° 2 : l'historique des opérations (le « relevé de compte »).
-- Chaque mouvement d'argent laisse une trace IMMUABLE : on n'efface ni ne modifie jamais une opération.
-- Un virement crée DEUX opérations (débit + crédit) qui partagent le même virement_id.

CREATE TABLE operation (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Clé étrangère : l'opération appartient à un compte existant.
    -- ON DELETE RESTRICT : impossible de supprimer un compte qui a un historique.
    compte_id    BIGINT         NOT NULL REFERENCES compte (id) ON DELETE RESTRICT,
    type         VARCHAR(20)    NOT NULL,
    montant      NUMERIC(19, 2) NOT NULL,
    solde_apres  NUMERIC(19, 2) NOT NULL,
    virement_id  UUID,
    description  VARCHAR(140),
    effectuee_le TIMESTAMPTZ    NOT NULL,

    CONSTRAINT type_valide CHECK (type IN ('DEPOT', 'RETRAIT', 'VIREMENT_DEBIT', 'VIREMENT_CREDIT')),
    CONSTRAINT montant_positif CHECK (montant > 0),
    -- Un virement a toujours un identifiant ; un dépôt ou un retrait n'en a jamais.
    CONSTRAINT virement_coherent CHECK ((type LIKE 'VIREMENT_%') = (virement_id IS NOT NULL))
);

-- Le relevé d'un compte se lit du plus récent au plus ancien : cet index rend la requête rapide.
CREATE INDEX operation_compte_recentes ON operation (compte_id, id DESC);
CREATE INDEX operation_virement ON operation (virement_id) WHERE virement_id IS NOT NULL;
