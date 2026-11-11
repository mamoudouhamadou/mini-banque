package io.github.mamoudouhamadou.minibanque.securite;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Toute la sécurité HTTP au même endroit.
 *
 * <ul>
 *   <li>API « sans état » (stateless) : pas de session ni de cookie ; chaque requête porte son jeton JWT.</li>
 *   <li>Routes publiques : inscription, connexion, santé. Tout le reste exige un jeton valide.</li>
 *   <li>Spring Security vérifie lui-même la signature, l'expiration et l'émetteur de chaque jeton.</li>
 * </ul>
 */
@Configuration
public class ConfigurationSecurite {

    @Bean
    SecurityFilterChain filtres(HttpSecurity http) throws Exception {
        http
                // CSRF protège les sessions par COOKIE. Ici, le jeton voyage dans un en-tête que le navigateur
                // n'envoie jamais tout seul : l'attaque CSRF ne s'applique pas, on peut la désactiver.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(regles -> regles
                        .requestMatchers(HttpMethod.POST, "/api/auth/inscription", "/api/auth/connexion").permitAll()
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated()) // refus par défaut : une route oubliée reste protégée
                .oauth2ResourceServer(rs -> rs
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(nonAuthentifie()))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(nonAuthentifie())
                        .accessDeniedHandler(accesRefuse()));
        return http.build();
    }

    // ---------- Jetons JWT signés par HMAC-SHA256 avec une clé secrète ----------

    @Bean
    SecretKey cleJwt(@Value("${minibanque.jwt.secret:}") String secret) {
        byte[] octets = secret.getBytes(StandardCharsets.UTF_8);
        if (octets.length < 32) {
            // Refuser de démarrer plutôt que de signer avec une clé faible ou absente.
            throw new IllegalStateException(
                    "La variable d'environnement JWT_SECRET doit contenir au moins 32 caractères (voir le README)");
        }
        return new SecretKeySpec(octets, "HmacSHA256");
    }

    @Bean
    JwtEncoder encodeurJwt(SecretKey cle) {
        return NimbusJwtEncoder.withSecretKey(cle).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtDecoder decodeurJwt(SecretKey cle) {
        NimbusJwtDecoder decodeur = NimbusJwtDecoder.withSecretKey(cle).macAlgorithm(MacAlgorithm.HS256).build();
        // Vérifications : signature (automatique), expiration (par défaut) ET émetteur « mini-banque ».
        decodeur.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(Jetons.EMETTEUR),
                new JwtClaimValidator<String>("sub", sub -> sub != null && sub.matches("\\d+"))));
        return decodeur;
    }

    /**
     * Hachage des mots de passe. Le « délégateur » préfixe chaque hachage par son algorithme ({bcrypt}…) :
     * on pourra passer à Argon2 plus tard sans invalider les anciens mots de passe.
     */
    @Bean
    PasswordEncoder encodeurMotsDePasse() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    // ---------- Erreurs 401 et 403 au même format RFC 9457 que le reste de l'API ----------

    private static AuthenticationEntryPoint nonAuthentifie() {
        return (requete, reponse, exception) -> {
            reponse.setStatus(401);
            reponse.setHeader("WWW-Authenticate", "Bearer");
            reponse.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            reponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
            reponse.getWriter().write("""
                    {"title":"Authentification requise","status":401,\
                    "detail":"Jeton absent, invalide ou expiré. Connecte-toi avec POST /api/auth/connexion."}""");
        };
    }

    private static AccessDeniedHandler accesRefuse() {
        return (requete, reponse, exception) -> {
            reponse.setStatus(403);
            reponse.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            reponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
            reponse.getWriter().write("""
                    {"title":"Accès refusé","status":403,"detail":"Tu n'as pas le droit d'accéder à cette ressource."}""");
        };
    }
}
