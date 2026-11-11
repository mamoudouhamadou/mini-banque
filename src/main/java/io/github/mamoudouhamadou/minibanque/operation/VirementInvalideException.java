package io.github.mamoudouhamadou.minibanque.operation;

/** Virement impossible par nature (ex. vers le même compte) ; devient une réponse 422. */
public class VirementInvalideException extends RuntimeException {

    public VirementInvalideException(String message) {
        super(message);
    }
}
