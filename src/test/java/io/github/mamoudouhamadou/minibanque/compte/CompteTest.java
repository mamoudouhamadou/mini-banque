package io.github.mamoudouhamadou.minibanque.compte;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Test UNITAIRE : seulement la classe Compte, sans Spring ni base de données.
 * Très rapide (millisecondes) : on y vérifie les règles métier.
 */
class CompteTest {

    private static final Instant MAINTENANT = Instant.parse("2026-11-11T12:00:00Z");

    @Test
    void ouvreUnCompteAvecDeuxDecimalesEtSansEspacesInutiles() {
        Compte compte = Compte.ouvrir(1L, "  Ana Tremblay  ", new BigDecimal("100"), MAINTENANT);

        assertThat(compte.getTitulaire()).isEqualTo("Ana Tremblay");
        assertThat(compte.getSolde()).isEqualTo(new BigDecimal("100.00")); // equals compare aussi l'échelle
        assertThat(compte.getDevise()).isEqualTo("CAD");
        assertThat(compte.getOuvertLe()).isEqualTo(MAINTENANT);
    }

    @Test
    void refuseUnDepotNegatif() {
        assertThatThrownBy(() -> Compte.ouvrir(1L, "Ana", new BigDecimal("-0.01"), MAINTENANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("négatif");
    }

    @Test
    void refuseUnMontantAvecPlusDeDeuxDecimalesAuLieuDArrondirEnSilence() {
        assertThatThrownBy(() -> Compte.ouvrir(1L, "Ana", new BigDecimal("10.555"), MAINTENANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 décimales");
    }

    @Test
    void refuseUnTitulaireVide() {
        assertThatThrownBy(() -> Compte.ouvrir(1L, "   ", BigDecimal.TEN, MAINTENANT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deposeEtRetireAuCentPres() {
        Compte compte = Compte.ouvrir(1L, "Ana", new BigDecimal("10.00"), MAINTENANT);

        compte.deposer(new BigDecimal("0.10"));
        compte.deposer(new BigDecimal("0.20"));
        compte.retirer(new BigDecimal("5.30"));

        assertThat(compte.getSolde()).isEqualTo(new BigDecimal("5.00"));
    }

    @Test
    void refuseUnRetraitSuperieurAuSoldeSansToucherAuSolde() {
        Compte compte = Compte.ouvrir(1L, "Ana", new BigDecimal("50.00"), MAINTENANT);

        assertThatThrownBy(() -> compte.retirer(new BigDecimal("50.01")))
                .isInstanceOf(SoldeInsuffisantException.class)
                .hasMessageContaining("Solde insuffisant");
        assertThat(compte.getSolde()).isEqualTo(new BigDecimal("50.00")); // inchangé
    }

    @Test
    void permetDeViderLeCompteExactement() {
        Compte compte = Compte.ouvrir(1L, "Ana", new BigDecimal("50.00"), MAINTENANT);
        compte.retirer(new BigDecimal("50"));
        assertThat(compte.getSolde()).isEqualByComparingTo("0");
    }

    @Test
    void refuseUnMontantNulOuNegatif() {
        Compte compte = Compte.ouvrir(1L, "Ana", BigDecimal.TEN, MAINTENANT);
        assertThatThrownBy(() -> compte.deposer(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> compte.retirer(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bigDecimalEviteLesErreursDArrondiDesDouble() {
        // Pourquoi on n'utilise JAMAIS double pour de l'argent :
        assertThat(0.1 + 0.2).isNotEqualTo(0.3);
        assertThat(new BigDecimal("0.1").add(new BigDecimal("0.2"))).isEqualByComparingTo("0.3");
    }
}
