package io.github.mamoudouhamadou.minibanque;

import static io.github.mamoudouhamadou.minibanque.OutilsApi.get;
import static io.github.mamoudouhamadou.minibanque.OutilsApi.json;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Le contrat OpenAPI généré : public, complet, et sans fuite de détails internes. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void leContratEstPublicEtDecritToutesLesRoutes() {
        MvcTestResult r = get(mvc, null, "/v3/api-docs");

        assertThat(r).hasStatusOk();
        Map<String, Object> chemins = json(r, "$.paths");
        assertThat(chemins).containsKeys(
                "/api/auth/inscription", "/api/auth/connexion", "/api/comptes", "/api/comptes/{id}",
                "/api/comptes/{id}/depots", "/api/comptes/{id}/retraits", "/api/virements",
                "/api/comptes/{id}/operations");
        assertThat((String) json(r, "$.info.title")).isEqualTo("Mini-banque API");
    }

    @Test
    void leSchemaDeSecuriteBearerEstDeclareEtLesRoutesPubliquesNExigentPasDeJeton() {
        MvcTestResult r = get(mvc, null, "/v3/api-docs");

        assertThat((String) json(r, "$.components.securitySchemes.jeton.scheme")).isEqualTo("bearer");
        List<?> securiteInscription = json(r, "$.paths['/api/auth/inscription'].post.security");
        assertThat(securiteInscription).isEmpty(); // publique
        List<?> securiteGlobale = json(r, "$.security");
        assertThat(securiteGlobale).isNotEmpty(); // le reste exige le jeton
    }

    @Test
    void leJetonInterneNApparaitPasCommeParametre() {
        MvcTestResult r = get(mvc, null, "/v3/api-docs");

        // @AuthenticationPrincipal Jwt ne doit pas devenir un faux paramètre « jeton » dans la doc :
        // le relevé n'expose QUE ses vrais paramètres.
        List<String> parametres = json(r, "$.paths['/api/comptes/{id}/operations'].get.parameters[*].name");
        assertThat(parametres).containsExactlyInAnyOrder("id", "page", "size");
        Map<String, Object> virement = json(r, "$.paths['/api/virements'].post");
        assertThat(virement).doesNotContainKey("parameters"); // seulement un corps JSON
    }

    @Test
    void swaggerUiEstAccessibleSansJeton() {
        MvcTestResult r = get(mvc, null, "/swagger-ui/index.html");
        assertThat(r).hasStatusOk();
    }
}
