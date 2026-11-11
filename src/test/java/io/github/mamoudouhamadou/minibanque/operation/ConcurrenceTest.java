package io.github.mamoudouhamadou.minibanque.operation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mamoudouhamadou.minibanque.TestcontainersConfiguration;
import io.github.mamoudouhamadou.minibanque.compte.CompteRepository;
import io.github.mamoudouhamadou.minibanque.compte.CompteService;
import io.github.mamoudouhamadou.minibanque.compte.OuvertureCompte;
import io.github.mamoudouhamadou.minibanque.compte.SoldeInsuffisantException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Tests de CONCURRENCE : des dizaines de transactions lancées EXACTEMENT en même temps
 * (une « barrière » CountDownLatch les libère toutes d'un coup) contre un vrai PostgreSQL.
 *
 * <p>Sans verrou, ces tests échouent : deux retraits lisent le même solde et dépensent deux fois
 * le même argent (« lost update »), ou deux virements croisés s'interbloquent (deadlock).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConcurrenceTest {

    @Autowired
    CompteService comptes;

    @Autowired
    OperationService service;

    @Autowired
    CompteRepository depot;

    @Autowired
    OperationRepository operations;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void viderLaBase() {
        jdbc.execute("TRUNCATE operation, compte RESTART IDENTITY");
    }

    /** Lance toutes les tâches en même temps et attend leurs résultats. */
    private static <T> List<Future<T>> lancerEnMemeTemps(List<Callable<T>> taches) throws InterruptedException {
        ExecutorService executeur = Executors.newFixedThreadPool(taches.size());
        CountDownLatch depart = new CountDownLatch(1);
        List<Future<T>> resultats = new ArrayList<>();
        for (Callable<T> tache : taches) {
            resultats.add(executeur.submit(() -> {
                depart.await(); // tout le monde attend le signal…
                return tache.call();
            }));
        }
        depart.countDown(); // …partez !
        executeur.shutdown();
        assertThat(executeur.awaitTermination(60, TimeUnit.SECONDS)).as("aucun interblocage").isTrue();
        return resultats;
    }

    @Test
    void vingtRetraitsSimultanesNeDepensentJamaisPlusQueLeSolde() throws Exception {
        long id = comptes.ouvrir(new OuvertureCompte("Ana", new BigDecimal("1000.00"))).id();

        List<Callable<Boolean>> retraits = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            retraits.add(() -> {
                try {
                    service.retirer(id, new MouvementRequete(new BigDecimal("100.00"), null));
                    return true;
                } catch (SoldeInsuffisantException e) {
                    return false;
                }
            });
        }

        int reussis = 0;
        for (Future<Boolean> r : lancerEnMemeTemps(retraits)) {
            if (r.get()) reussis++; // r.get() relance toute exception inattendue → le test échoue
        }

        // 1000 $ / 100 $ = exactement 10 retraits possibles, ni plus ni moins.
        assertThat(reussis).isEqualTo(10);
        assertThat(depot.findById(id).orElseThrow().getSolde()).isEqualByComparingTo("0");
        assertThat(operations.count()).isEqualTo(10);
    }

    @Test
    void centVirementsCroisesConserventLArgentSansInterblocage() throws Exception {
        long ana = comptes.ouvrir(new OuvertureCompte("Ana", new BigDecimal("500.00"))).id();
        long bob = comptes.ouvrir(new OuvertureCompte("Bob", new BigDecimal("500.00"))).id();

        // 50 virements Ana→Bob et 50 Bob→Ana, tous en même temps : le scénario classique d'interblocage.
        List<Callable<VirementReponse>> virements = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            virements.add(() -> service.virer(new VirementRequete(ana, bob, new BigDecimal("7.00"), null)));
            virements.add(() -> service.virer(new VirementRequete(bob, ana, new BigDecimal("3.00"), null)));
        }
        for (Future<VirementReponse> v : lancerEnMemeTemps(virements)) {
            v.get(); // aucun virement ne doit échouer
        }

        BigDecimal soldeAna = depot.findById(ana).orElseThrow().getSolde();
        BigDecimal soldeBob = depot.findById(bob).orElseThrow().getSolde();

        // L'argent ne se crée ni ne se perd : le total reste 1000 $.
        assertThat(soldeAna.add(soldeBob)).isEqualByComparingTo("1000.00");
        // Et chaque compte a exactement le solde attendu : 500 − 50×7 + 50×3 = 300 $.
        assertThat(soldeAna).isEqualByComparingTo("300.00");
        assertThat(soldeBob).isEqualByComparingTo("700.00");
        assertThat(operations.count()).isEqualTo(200); // 100 virements × (débit + crédit)
    }
}
