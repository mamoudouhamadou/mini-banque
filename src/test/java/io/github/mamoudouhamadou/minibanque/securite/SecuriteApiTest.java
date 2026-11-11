package io.github.mamoudouhamadou.minibanque.securite;

import static io.github.mamoudouhamadou.minibanque.OutilsApi.MOT_DE_PASSE;
import static io.github.mamoudouhamadou.minibanque.OutilsApi.get;
import static io.github.mamoudouhamadou.minibanque.OutilsApi.inscrire;
import static io.github.mamoudouhamadou.minibanque.OutilsApi.json;
import static io.github.mamoudouhamadou.minibanque.OutilsApi.post;
import static io.github.mamoudouhamadou.minibanque.OutilsApi.viderLaBase;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.mamoudouhamadou.minibanque.TestcontainersConfiguration;
import io.github.mamoudouhamadou.minibanque.client.ClientRepository;
import io.github.mamoudouhamadou.minibanque.compte.CompteRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Inscription, connexion, jetons et isolation des données entre clients. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecuriteApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ClientRepository clients;

    @Autowired
    CompteRepository comptes;

    @Autowired
    JwtEncoder vraiEncodeur; // le même que l'application : pour fabriquer des jetons « presque valides »

    @BeforeEach
    void preparer() {
        viderLaBase(jdbc);
    }

    private static String inscription(String courriel, String motDePasse) {
        return "{\"courriel\":\"" + courriel + "\",\"motDePasse\":\"" + motDePasse + "\",\"nom\":\"Ana\"}";
    }

    private static String connexion(String courriel, String motDePasse) {
        return "{\"courriel\":\"" + courriel + "\",\"motDePasse\":\"" + motDePasse + "\"}";
    }

    @Nested
    class Inscription {

        @Test
        void creeLeClientEtRenvoieUnJetonSansJamaisExposerLeMotDePasse() throws Exception {
            MvcTestResult r = post(mvc, null, "/api/auth/inscription", inscription("  Ana@Exemple.CA ", MOT_DE_PASSE));

            assertThat(r).hasStatus(HttpStatus.CREATED);
            assertThat((String) json(r, "$.type")).isEqualTo("Bearer");
            assertThat((String) json(r, "$.jeton")).matches("[\\w-]+\\.[\\w-]+\\.[\\w-]+"); // en-tête.contenu.signature
            assertThat((String) json(r, "$.client.courriel")).isEqualTo("ana@exemple.ca"); // normalisé
            assertThat(r.getResponse().getContentAsString()).doesNotContain(MOT_DE_PASSE);

            // En base : un hachage BCrypt, jamais le mot de passe en clair
            String hachage = clients.findByCourriel("ana@exemple.ca").orElseThrow().getMotDePasseHache();
            assertThat(hachage).startsWith("{bcrypt}$2").doesNotContain(MOT_DE_PASSE);
        }

        @Test
        void refuseUnCourrielDejaUtiliseMemeAvecDesMajuscules() {
            post(mvc, null, "/api/auth/inscription", inscription("ana@exemple.ca", MOT_DE_PASSE));

            MvcTestResult r = post(mvc, null, "/api/auth/inscription", inscription("ANA@exemple.ca", MOT_DE_PASSE));

            assertThat(r).hasStatus(HttpStatus.CONFLICT);
            assertThat(clients.count()).isEqualTo(1);
        }

        @Test
        void refuseUnMotDePasseTropCourtOuUnCourrielInvalide() {
            MvcTestResult court = post(mvc, null, "/api/auth/inscription", inscription("ana@exemple.ca", "court"));
            MvcTestResult courriel = post(mvc, null, "/api/auth/inscription", inscription("pas-un-courriel", MOT_DE_PASSE));

            assertThat(court).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat((String) json(court, "$.erreurs[0].champ")).isEqualTo("motDePasse");
            assertThat(courriel).hasStatus(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    class Connexion {

        @BeforeEach
        void inscrireAna() {
            inscrire(mvc, "ana@exemple.ca");
        }

        @Test
        void connecteAvecLeBonMotDePasse() {
            MvcTestResult r = post(mvc, null, "/api/auth/connexion", connexion("Ana@Exemple.ca", MOT_DE_PASSE));

            assertThat(r).hasStatusOk();
            String jeton = json(r, "$.jeton");
            assertThat(get(mvc, jeton, "/api/comptes")).hasStatusOk(); // le jeton ouvre bien l'API
        }

        @Test
        void memeReponsePourUnMauvaisMotDePasseEtUnCourrielInconnu() throws Exception {
            MvcTestResult mauvais = post(mvc, null, "/api/auth/connexion", connexion("ana@exemple.ca", "mauvais-mot-de-passe"));
            MvcTestResult inconnu = post(mvc, null, "/api/auth/connexion", connexion("personne@exemple.ca", MOT_DE_PASSE));

            assertThat(mauvais).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(inconnu).hasStatus(HttpStatus.UNAUTHORIZED);
            // On ne révèle pas quels courriels sont inscrits (protection contre l'énumération)
            assertThat(mauvais.getResponse().getContentAsString()).isEqualTo(inconnu.getResponse().getContentAsString());
        }
    }

    @Nested
    class Jetons {

        private String jetonFabrique(JwtEncoder encodeur, String emetteur, Instant expiration) {
            JwtClaimsSet revendications = JwtClaimsSet.builder()
                    .issuer(emetteur)
                    .subject("1")
                    .issuedAt(expiration.minusSeconds(3600))
                    .expiresAt(expiration)
                    .build();
            return encodeur.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), revendications))
                    .getTokenValue();
        }

        @Test
        void sansJetonLaRequeteEstRefuseeEn401AuFormatProblemDetails() {
            MvcTestResult r = get(mvc, null, "/api/comptes");

            // « compatible » : accepte le paramètre ;charset=UTF-8 (nécessaire pour les accents du message)
            assertThat(r).hasStatus(HttpStatus.UNAUTHORIZED).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(r.getResponse().getHeader("WWW-Authenticate")).startsWith("Bearer");
            assertThat((String) json(r, "$.title")).isEqualTo("Authentification requise");
        }

        @Test
        void refuseUnJetonSigneAvecUneAutreCle() {
            JwtEncoder pirate = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(
                            "une-autre-cle-secrete-de-32-caracteres!!".getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                    .algorithm(MacAlgorithm.HS256)
                    .build();
            String faux = jetonFabrique(pirate, "mini-banque", Instant.now().plusSeconds(3600));

            assertThat(get(mvc, faux, "/api/comptes")).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void refuseUnJetonExpireOuDUnAutreEmetteur() {
            String expire = jetonFabrique(vraiEncodeur, "mini-banque", Instant.now().minusSeconds(120));
            String autreEmetteur = jetonFabrique(vraiEncodeur, "autre-application", Instant.now().plusSeconds(3600));

            assertThat(get(mvc, expire, "/api/comptes")).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(get(mvc, autreEmetteur, "/api/comptes")).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void refuseUnJetonModifie() {
            String jeton = inscrire(mvc, "ana@exemple.ca");
            String[] parties = jeton.split("\\.");
            String modifie = parties[0] + "." + parties[1] + "." + new StringBuilder(parties[2]).reverse();

            assertThat(get(mvc, modifie, "/api/comptes")).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void laSanteEtLaConnexionRestentPubliques() {
            assertThat(get(mvc, null, "/actuator/health")).hasStatusOk();
        }
    }

    @Nested
    class IsolationEntreClients {

        String ana;
        String bob;
        long compteAna;

        @BeforeEach
        void deuxClients() {
            ana = inscrire(mvc, "ana@exemple.ca");
            bob = inscrire(mvc, "bob@exemple.ca");
            Integer id = json(post(mvc, ana, "/api/comptes", "{\"titulaire\":\"Ana\",\"depotInitial\":100}"), "$.id");
            compteAna = id;
        }

        @Test
        void bobNeVoitPasLesComptesDAna() {
            List<?> comptesDeBob = json(get(mvc, bob, "/api/comptes"), "$.content");

            assertThat(comptesDeBob).isEmpty();
            // 404 et non 403 : Bob n'apprend même pas que ce compte existe
            assertThat(get(mvc, bob, "/api/comptes/" + compteAna)).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(get(mvc, bob, "/api/comptes/" + compteAna + "/operations")).hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void bobNePeutNiRetirerNiVirerDepuisLeCompteDAna() {
            Integer id = json(post(mvc, bob, "/api/comptes", "{\"titulaire\":\"Bob\",\"depotInitial\":0}"), "$.id");
            long compteBob = id;

            MvcTestResult retrait = post(mvc, bob, "/api/comptes/" + compteAna + "/retraits", "{\"montant\": 50}");
            MvcTestResult vol = post(mvc, bob, "/api/virements",
                    "{\"source\": " + compteAna + ", \"destination\": " + compteBob + ", \"montant\": 100}");

            assertThat(retrait).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(vol).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(comptes.findById(compteAna).orElseThrow().getSolde()).isEqualByComparingTo("100.00"); // intact
        }

        @Test
        void anaPeutVirerVersLeCompteDeBob() {
            Integer id = json(post(mvc, bob, "/api/comptes", "{\"titulaire\":\"Bob\",\"depotInitial\":0}"), "$.id");
            long compteBob = id;

            MvcTestResult virement = post(mvc, ana, "/api/virements",
                    "{\"source\": " + compteAna + ", \"destination\": " + compteBob + ", \"montant\": 25}");

            assertThat(virement).hasStatus(HttpStatus.CREATED);
            assertThat(comptes.findById(compteBob).orElseThrow().getSolde()).isEqualTo(new BigDecimal("25.00"));
        }
    }
}
