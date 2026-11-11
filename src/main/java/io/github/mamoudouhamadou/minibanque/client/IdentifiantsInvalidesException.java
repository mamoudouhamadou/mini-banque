package io.github.mamoudouhamadou.minibanque.client;

/**
 * Connexion refusée. Le message est volontairement le MÊME que le courriel existe ou non :
 * on ne révèle pas quels courriels ont un compte (protection contre l'énumération).
 */
public class IdentifiantsInvalidesException extends RuntimeException {

    public IdentifiantsInvalidesException() {
        super("Courriel ou mot de passe incorrect");
    }
}
