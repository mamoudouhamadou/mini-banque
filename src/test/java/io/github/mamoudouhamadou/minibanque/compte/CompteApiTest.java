package io.github.mamoudouhamadou.minibanque.compte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import io.github.mamoudouhamadou.minibanque.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Tests d'INTÉGRATION : toute l'application (contrôleur → service → JPA → Flyway → PostgreSQL réel
 * dans Docker). MockMvcTester simule les requêtes HTTP sans ouvrir de port réseau.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CompteApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    CompteRepository comptes;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void viderLaBase() {
        // Chaque test part de tables vides : les tests restent indépendants.
        // (On vide « operation » aussi : sa clé étrangère interdit de supprimer un compte qui a un historique.)
        jdbc.execute("TRUNCATE operation, compte RESTART IDENTITY");
    }

    private MvcTestResult ouvrir(String json) {
        return mvc.post().uri("/api/comptes").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    private static <T> T json(MvcTestResult resultat, String chemin) {
        try {
            return JsonPath.read(resultat.getResponse().getContentAsString(), chemin);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void ouvreUnCompteEtRenvoie201AvecLEmplacement() throws Exception {
        MvcTestResult resultat = ouvrir("""
                { "titulaire": "Ana Tremblay", "depotInitial": 100.5 }
                """);

        assertThat(resultat).hasStatus(HttpStatus.CREATED);
        Integer id = json(resultat, "$.id");
        assertThat(resultat.getResponse().getHeader("Location")).endsWith("/api/comptes/" + id);
        assertThat((String) json(resultat, "$.titulaire")).isEqualTo("Ana Tremblay");
        assertThat((String) json(resultat, "$.devise")).isEqualTo("CAD");
        assertThat(resultat.getResponse().getContentAsString()).doesNotContain("version"); // champ interne caché

        // Vérification directe dans PostgreSQL : montant exact, 2 décimales
        assertThat(comptes.findById(id.longValue()).orElseThrow().getSolde()).isEqualTo(new BigDecimal("100.50"));
    }

    @Test
    void retrouveUnCompteParSonId() {
        Integer id = json(ouvrir("{ \"titulaire\": \"Bob\", \"depotInitial\": 20 }"), "$.id");

        MvcTestResult resultat = mvc.get().uri("/api/comptes/{id}", id).exchange();

        assertThat(resultat).hasStatusOk();
        assertThat((String) json(resultat, "$.titulaire")).isEqualTo("Bob");
    }

    @Test
    void repond404AuFormatProblemDetailsPourUnCompteInexistant() {
        MvcTestResult resultat = mvc.get().uri("/api/comptes/999999").exchange();

        assertThat(resultat).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat((String) json(resultat, "$.title")).isEqualTo("Compte introuvable");
        assertThat((String) json(resultat, "$.detail")).isEqualTo("Le compte 999999 n'existe pas");
        assertThat((Integer) json(resultat, "$.status")).isEqualTo(404);
    }

    @Test
    void listeChaqueChampInvalide() {
        MvcTestResult resultat = ouvrir("{ \"titulaire\": \"\", \"depotInitial\": -5 }");

        assertThat(resultat).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        List<String> champs = json(resultat, "$.erreurs[*].champ");
        assertThat(champs).containsExactlyInAnyOrder("titulaire", "depotInitial");
        assertThat(comptes.count()).isZero(); // rien n'a été enregistré
    }

    @Test
    void refuseUnMontantAvecTroisDecimales() {
        MvcTestResult resultat = ouvrir("{ \"titulaire\": \"Ana\", \"depotInitial\": 10.555 }");

        assertThat(resultat).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat((String) json(resultat, "$.erreurs[0].message")).contains("2 décimales");
    }

    @Test
    void refuseUnJsonMalForme() {
        assertThat(ouvrir("{ pas du json")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void paginePlusieursComptesTriesParTitulaire() {
        ouvrir("{ \"titulaire\": \"Chloé\", \"depotInitial\": 1 }");
        ouvrir("{ \"titulaire\": \"Ana\", \"depotInitial\": 2 }");
        ouvrir("{ \"titulaire\": \"Bob\", \"depotInitial\": 3 }");

        MvcTestResult resultat = mvc.get().uri("/api/comptes?size=2&sort=titulaire").exchange();

        assertThat(resultat).hasStatusOk();
        List<String> titulaires = json(resultat, "$.content[*].titulaire");
        assertThat(titulaires).containsExactly("Ana", "Bob");
        assertThat((Integer) json(resultat, "$.page.totalElements")).isEqualTo(3);
        assertThat((Integer) json(resultat, "$.page.totalPages")).isEqualTo(2);
    }

    @Test
    void laBaseDeDonneesRefuseElleAussiUnSoldeNegatif() {
        // Même en contournant le code Java, la contrainte CHECK de la migration Flyway protège les données.
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO compte (titulaire, solde, ouvert_le) VALUES ('Pirate', -1, now())"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("solde_non_negatif");
    }
}
