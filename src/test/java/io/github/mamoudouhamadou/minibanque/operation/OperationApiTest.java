package io.github.mamoudouhamadou.minibanque.operation;

import static io.github.mamoudouhamadou.minibanque.OutilsApi.get;
import static io.github.mamoudouhamadou.minibanque.OutilsApi.inscrire;
import static io.github.mamoudouhamadou.minibanque.OutilsApi.json;
import static io.github.mamoudouhamadou.minibanque.OutilsApi.viderLaBase;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.mamoudouhamadou.minibanque.OutilsApi;
import io.github.mamoudouhamadou.minibanque.TestcontainersConfiguration;
import io.github.mamoudouhamadou.minibanque.compte.CompteRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Dépôts, retraits, virements et relevé, de bout en bout avec un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OperationApiTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    CompteRepository comptes;

    @Autowired
    OperationRepository operations;

    @Autowired
    JdbcTemplate jdbc;

    String jeton;

    @BeforeEach
    void preparer() {
        viderLaBase(jdbc);
        jeton = inscrire(mvc, "ana@exemple.ca");
    }

    // ---------- petits outils ----------

    private MvcTestResult post(String url, String json) {
        return OutilsApi.post(mvc, jeton, url, json);
    }

    private long ouvrir(String titulaire, String depot) {
        Integer id = json(post("/api/comptes", "{\"titulaire\":\"" + titulaire + "\",\"depotInitial\":" + depot + "}"), "$.id");
        return id.longValue();
    }

    private BigDecimal solde(long id) {
        return comptes.findById(id).orElseThrow().getSolde();
    }

    // ---------- dépôts et retraits ----------

    @Test
    void depotEtRetraitMettentLeSoldeAJourEtLaissentUneTrace() {
        long ana = ouvrir("Ana", "100");

        MvcTestResult depot = post("/api/comptes/" + ana + "/depots", "{\"montant\": 50.25, \"description\": \"Paie\"}");
        MvcTestResult retrait = post("/api/comptes/" + ana + "/retraits", "{\"montant\": 30}");

        assertThat(depot).hasStatus(HttpStatus.CREATED);
        assertThat((String) json(depot, "$.type")).isEqualTo("DEPOT");
        assertThat((Double) json(depot, "$.soldeApres")).isEqualTo(150.25);
        assertThat(retrait).hasStatus(HttpStatus.CREATED);
        assertThat((Double) json(retrait, "$.soldeApres")).isEqualTo(120.25);
        assertThat(solde(ana)).isEqualTo(new BigDecimal("120.25"));
    }

    @Test
    void retraitSuperieurAuSoldeRepond422EtNeChangeRien() {
        long ana = ouvrir("Ana", "20");

        MvcTestResult resultat = post("/api/comptes/" + ana + "/retraits", "{\"montant\": 20.01}");

        assertThat(resultat).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat((String) json(resultat, "$.title")).isEqualTo("Solde insuffisant");
        assertThat((Double) json(resultat, "$.soldeDisponible")).isEqualTo(20.0);
        assertThat(solde(ana)).isEqualTo(new BigDecimal("20.00"));
        assertThat(operations.count()).isZero();
    }

    @Test
    void refuseUnMontantInvalideOuUnCompteInexistant() {
        long ana = ouvrir("Ana", "20");
        assertThat(post("/api/comptes/" + ana + "/depots", "{\"montant\": 0}")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(post("/api/comptes/" + ana + "/depots", "{\"montant\": 1.001}")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(post("/api/comptes/" + ana + "/depots", "{\"montant\": 2000000}")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(post("/api/comptes/999/depots", "{\"montant\": 5}")).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---------- virements ----------

    @Test
    void virementDebiteCrediteEtCreeDeuxOperationsLiees() {
        long ana = ouvrir("Ana", "100");
        long bob = ouvrir("Bob", "5");

        MvcTestResult resultat = post("/api/virements",
                "{\"source\": " + ana + ", \"destination\": " + bob + ", \"montant\": 40, \"description\": \"Loyer\"}");

        assertThat(resultat).hasStatus(HttpStatus.CREATED);
        assertThat((Double) json(resultat, "$.soldeSourceApres")).isEqualTo(60.0);
        assertThat((Double) json(resultat, "$.soldeDestinationApres")).isEqualTo(45.0);
        assertThat(solde(ana)).isEqualTo(new BigDecimal("60.00"));
        assertThat(solde(bob)).isEqualTo(new BigDecimal("45.00"));

        String virementId = json(resultat, "$.id");
        List<Operation> lignes = operations.findByVirementId(UUID.fromString(virementId));
        assertThat(lignes).extracting(Operation::getType)
                .containsExactlyInAnyOrder(TypeOperation.VIREMENT_DEBIT, TypeOperation.VIREMENT_CREDIT);
    }

    @Test
    void virementSansProvisionNeChangeAucunCompte() {
        long ana = ouvrir("Ana", "10");
        long bob = ouvrir("Bob", "0");

        MvcTestResult resultat = post("/api/virements",
                "{\"source\": " + ana + ", \"destination\": " + bob + ", \"montant\": 10.01}");

        assertThat(resultat).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(solde(ana)).isEqualTo(new BigDecimal("10.00"));
        assertThat(solde(bob)).isEqualTo(new BigDecimal("0.00"));
        assertThat(operations.count()).isZero(); // transaction annulée : aucune trace partielle
    }

    @Test
    void virementVersLeMemeCompteOuUnCompteInexistantEstRefuse() {
        long ana = ouvrir("Ana", "10");

        MvcTestResult memeCompte = post("/api/virements",
                "{\"source\": " + ana + ", \"destination\": " + ana + ", \"montant\": 1}");
        MvcTestResult inconnu = post("/api/virements",
                "{\"source\": " + ana + ", \"destination\": 999, \"montant\": 1}");

        assertThat(memeCompte).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat((String) json(memeCompte, "$.title")).isEqualTo("Virement invalide");
        assertThat(inconnu).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(solde(ana)).isEqualTo(new BigDecimal("10.00"));
    }

    // ---------- relevé ----------

    @Test
    void releveDuPlusRecentAuPlusAncienAvecPagination() {
        long ana = ouvrir("Ana", "0");
        post("/api/comptes/" + ana + "/depots", "{\"montant\": 10}");
        post("/api/comptes/" + ana + "/depots", "{\"montant\": 20}");
        post("/api/comptes/" + ana + "/retraits", "{\"montant\": 5}");

        MvcTestResult resultat = get(mvc, jeton, "/api/comptes/" + ana + "/operations?size=2");

        assertThat(resultat).hasStatusOk();
        List<String> types = json(resultat, "$.content[*].type");
        List<Double> soldes = json(resultat, "$.content[*].soldeApres");
        assertThat(types).containsExactly("RETRAIT", "DEPOT");
        assertThat(soldes).containsExactly(25.0, 30.0);
        assertThat((Integer) json(resultat, "$.page.totalElements")).isEqualTo(3);
    }

    @Test
    void releveRefuseUneTailleDePageExcessiveEtUnCompteInexistant() {
        long ana = ouvrir("Ana", "0");
        assertThat(get(mvc, jeton, "/api/comptes/" + ana + "/operations?size=500")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(get(mvc, jeton, "/api/comptes/999/operations")).hasStatus(HttpStatus.NOT_FOUND);
    }
}
