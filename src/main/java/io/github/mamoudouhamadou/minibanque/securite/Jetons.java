package io.github.mamoudouhamadou.minibanque.securite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Fabrique les jetons JWT signés (HMAC-SHA256). La VÉRIFICATION des jetons reçus est faite
 * automatiquement par Spring Security (voir ConfigurationSecurite) : on n'écrit aucun code de vérification.
 */
@Component
public class Jetons {

    public static final String EMETTEUR = "mini-banque";
    public static final Duration DUREE = Duration.ofHours(1);

    private final JwtEncoder encodeur;
    private final Clock horloge;

    public Jetons(JwtEncoder encodeur, Clock horloge) {
        this.encodeur = encodeur;
        this.horloge = horloge;
    }

    /** Un jeton qui dit « je suis le client {id} », valide 1 heure. */
    public String emettre(long clientId) {
        Instant maintenant = Instant.now(horloge);
        JwtClaimsSet revendications = JwtClaimsSet.builder()
                .issuer(EMETTEUR)
                .subject(Long.toString(clientId)) // « sub » : à qui appartient le jeton
                .issuedAt(maintenant)
                .expiresAt(maintenant.plus(DUREE))
                .build();
        JwsHeader entete = JwsHeader.with(MacAlgorithm.HS256).build();
        return encodeur.encode(JwtEncoderParameters.from(entete, revendications)).getTokenValue();
    }
}
