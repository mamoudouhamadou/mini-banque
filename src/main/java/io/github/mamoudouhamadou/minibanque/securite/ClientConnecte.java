package io.github.mamoudouhamadou.minibanque.securite;

import org.springframework.security.oauth2.jwt.Jwt;

/** Lit l'identifiant du client connecté dans le jeton déjà vérifié par Spring Security. */
public final class ClientConnecte {

    private ClientConnecte() {}

    public static long id(Jwt jeton) {
        return Long.parseLong(jeton.getSubject()); // « sub » est validé (que des chiffres) dans ConfigurationSecurite
    }
}
