package io.github.mamoudouhamadou.minibanque.client;

/** Inscription avec un courriel déjà pris ; devient une réponse 409 Conflict. */
public class CourrielDejaUtiliseException extends RuntimeException {

    public CourrielDejaUtiliseException() {
        super("Ce courriel est déjà utilisé");
    }
}
