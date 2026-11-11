package io.github.mamoudouhamadou.minibanque.compte;

/** Levée quand on demande un compte qui n'existe pas ; devient une réponse 404. */
public class CompteIntrouvableException extends RuntimeException {

    private final long id;

    public CompteIntrouvableException(long id) {
        super("Le compte " + id + " n'existe pas");
        this.id = id;
    }

    public long getId() {
        return id;
    }
}
