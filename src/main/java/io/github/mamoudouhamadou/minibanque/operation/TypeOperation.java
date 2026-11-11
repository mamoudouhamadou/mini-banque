package io.github.mamoudouhamadou.minibanque.operation;

/** Les sortes de mouvements d'argent. Stockées en texte (« DEPOT »…), jamais en numéro d'ordre. */
public enum TypeOperation {
    DEPOT,
    RETRAIT,
    VIREMENT_DEBIT,
    VIREMENT_CREDIT
}
