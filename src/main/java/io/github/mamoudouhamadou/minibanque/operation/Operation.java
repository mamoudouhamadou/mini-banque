package io.github.mamoudouhamadou.minibanque.operation;

import io.github.mamoudouhamadou.minibanque.compte.Compte;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Une ligne du relevé de compte. Immuable : aucun setter, on ne corrige jamais une opération
 * (en comptabilité, une erreur se corrige par une NOUVELLE opération inverse).
 */
@Entity
public class Operation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relation « plusieurs opérations → un compte ». LAZY : le compte n'est chargé que si on y accède.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "compte_id")
    private Compte compte;

    // STRING et non ORDINAL : réordonner l'enum ne corrompt pas les données existantes.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TypeOperation type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal montant;

    // Le solde juste après l'opération : le relevé se lit sans recalculer tout l'historique.
    @Column(name = "solde_apres", nullable = false, precision = 19, scale = 2)
    private BigDecimal soldeApres;

    @Column(name = "virement_id")
    private UUID virementId;

    @Column(length = 140)
    private String description;

    @Column(name = "effectuee_le", nullable = false)
    private Instant effectueeLe;

    protected Operation() {}

    public Operation(
            Compte compte, TypeOperation type, BigDecimal montant, UUID virementId, String description, Instant quand) {
        this.compte = compte;
        this.type = type;
        this.montant = montant;
        this.soldeApres = compte.getSolde();
        this.virementId = virementId;
        this.description = description;
        this.effectueeLe = quand;
    }

    public Long getId() {
        return id;
    }

    public TypeOperation getType() {
        return type;
    }

    public BigDecimal getMontant() {
        return montant;
    }

    public BigDecimal getSoldeApres() {
        return soldeApres;
    }

    public UUID getVirementId() {
        return virementId;
    }

    public String getDescription() {
        return description;
    }

    public Instant getEffectueeLe() {
        return effectueeLe;
    }
}
