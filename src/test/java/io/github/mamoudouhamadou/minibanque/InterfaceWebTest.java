package io.github.mamoudouhamadou.minibanque;

import static io.github.mamoudouhamadou.minibanque.OutilsApi.get;
import static io.github.mamoudouhamadou.minibanque.OutilsApi.json;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Déploiement en un seul service : Spring sert l'interface Angular et l'API.
 * (src/test/resources/static/index.html remplace la vraie interface pendant les tests.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InterfaceWebTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void lesRoutesAngularRenvoientIndexHtmlSansJeton() {
        for (String route : new String[] {"/connexion", "/comptes", "/comptes/7", "/virement"}) {
            MvcTestResult r = get(mvc, null, route);
            assertThat(r).as(route).hasStatusOk();
            assertThat(r.getResponse().getForwardedUrl()).as(route).isEqualTo("/index.html");
        }
    }

    @Test
    void lApiResteProtegeeMemeSiLInterfaceEstPublique() {
        assertThat(get(mvc, null, "/api/comptes")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(get(mvc, null, "/api/comptes/7")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void uneRouteInconnueNEstPasServieCommeLInterface() {
        assertThat(get(mvc, null, "/n-importe-quoi/secret")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void lesEnTetesDeSecuriteSontPresents() {
        MvcTestResult r = get(mvc, null, "/connexion");
        assertThat(r.getResponse().getHeader("Content-Security-Policy"))
                .contains("default-src 'self'")
                .contains("frame-ancestors 'none'");
        assertThat(r.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(r.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY");
    }

    @Test
    void laVersionDeployeeEstPubliqueDansActuatorInfo() {
        MvcTestResult r = get(mvc, null, "/actuator/info");
        assertThat(r).hasStatusOk();
        assertThat((String) json(r, "$.app.version")).isEqualTo("dev");
    }
}
