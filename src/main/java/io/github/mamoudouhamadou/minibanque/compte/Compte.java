package io.github.mamoudouhamadou.minibanque.compte;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entité JPA : un objet Compte correspond à une ligne de la table « compte ».
 *
 * <p>Les règles métier vivent DANS l'entité (pas de setters publics) : impossible de créer
 * un compte avec un solde négatif, quel que soit le code qui l'utilise.
 */
@Entity
public class Compte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // PostgreSQL génère l'id
    private Long id;

    @Column(nullable = false, length = 100)
    private String titulaire;

    // BigDecimal et non double : 0.1 + 0.2 = 0.30000000000000004 en double !
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal solde;

    // Code ISO 4217 de 3 lettres (CAD, USD…) : colonne CHAR(3) de longueur fixe.
    // Sans @JdbcTypeCode, Hibernate attend un VARCHAR et la validation du schéma échoue.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String devise;

    @Column(name = "ouvert_le", nullable = false)
    private Instant ouvertLe;

    // Verrouillage optimiste : si deux transactions modifient le même compte en même temps,
    // la deuxième échoue au lieu d'écraser silencieusement la première (utile à l'étape 2).
    @Version
    private long version;

    /** Constructeur vide exigé par JPA (protected : le code applicatif ne doit pas l'utiliser). */
    protected Compte() {}

    private Compte(String titulaire, BigDecimal soldeInitial, Instant ouvertLe) {
        this.titulaire = titulaire;
        this.solde = soldeInitial;
        this.devise = "CAD";
        this.ouvertLe = ouvertLe;
    }

    /** « Méthode fabrique » : la seule façon de créer un compte, avec ses règles. */
    public static Compte ouvrir(String titulaire, BigDecimal depotInitial, Instant maintenant) {
        if (titulaire == null || titulaire.isBlank()) {
            throw new IllegalArgumentException("Le titulaire est obligatoire");
        }
        Objects.requireNonNull(depotInitial, "Le dépôt initial est obligatoire");
        if (depotInitial.signum() < 0) {
            throw new IllegalArgumentException("Le dépôt initial ne peut pas être négatif");
        }
        return new Compte(titulaire.strip(), enCents(depotInitial), maintenant);
    }

    /** Ajoute de l'argent. Retourne le montant normalisé (2 décimales). */
    public BigDecimal deposer(BigDecimal montant) {
        BigDecimal m = montantPositif(montant);
        solde = solde.add(m);
        return m;
    }

    /**
     * Retire de l'argent. Lève {@link SoldeInsuffisantException} si le solde ne suffit pas :
     * le solde n'est alors PAS modifié (on vérifie avant de changer quoi que ce soit).
     */
    public BigDecimal retirer(BigDecimal montant) {
        BigDecimal m = montantPositif(montant);
        if (solde.compareTo(m) < 0) {
            throw new SoldeInsuffisantException(id, solde, m);
        }
        solde = solde.subtract(m);
        return m;
    }

    private static BigDecimal montantPositif(BigDecimal montant) {
        Objects.requireNonNull(montant, "Le montant est obligatoire");
        if (montant.signum() <= 0) {
            throw new IllegalArgumentException("Le montant doit être positif");
        }
        return enCents(montant);
    }

    /** Toujours 2 décimales (des cents) ; on refuse plutôt que d'arrondir en silence (10,555 $ → erreur). */

    static BigDecimal enCents(BigDecimal montant) {
        try {
            return montant.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Un montant a au plus 2 décimales", e);
        }
    }

    public Long getId() {
        return id;
    }

    public String getTitulaire() {
        return titulaire;
    }

    public BigDecimal getSolde() {
        return solde;
    }

    public String getDevise() {
        return devise;
    }

    public Instant getOuvertLe() {
        return ouvertLe;
    }
}
