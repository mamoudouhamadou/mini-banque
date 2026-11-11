package io.github.mamoudouhamadou.minibanque;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Petits outils partagés par les tests d'API : requêtes avec jeton, lecture du JSON, base vide. */
public final class OutilsApi {

    public static final String MOT_DE_PASSE = "mot-de-passe-de-test-123";

    private OutilsApi() {}

    /** Vide toutes les tables (dans l'ordre imposé par les clés étrangères) et remet les compteurs à 1. */
    public static void viderLaBase(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE operation, compte, client RESTART IDENTITY");
    }

    /** Inscrit un client et retourne son jeton JWT. */
    public static String inscrire(MockMvcTester mvc, String courriel) {
        MvcTestResult r = post(mvc, null, "/api/auth/inscription",
                "{\"courriel\":\"" + courriel + "\",\"motDePasse\":\"" + MOT_DE_PASSE + "\",\"nom\":\"Client test\"}");
        return json(r, "$.jeton");
    }

    public static MvcTestResult post(MockMvcTester mvc, String jeton, String url, String corps) {
        var requete = mvc.post().uri(url).contentType(MediaType.APPLICATION_JSON).content(corps);
        if (jeton != null) requete.header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton);
        return requete.exchange();
    }

    public static MvcTestResult get(MockMvcTester mvc, String jeton, String url) {
        var requete = mvc.get().uri(url);
        if (jeton != null) requete.header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton);
        return requete.exchange();
    }

    public static <T> T json(MvcTestResult resultat, String chemin) {
        try {
            return JsonPath.read(resultat.getResponse().getContentAsString(), chemin);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
