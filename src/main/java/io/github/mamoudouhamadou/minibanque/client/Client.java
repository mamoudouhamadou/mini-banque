package io.github.mamoudouhamadou.minibanque.client;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/** Un client de la banque (la personne qui se connecte). Il possède zéro, un ou plusieurs comptes. */
@Entity
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String courriel;

    @Column(name = "mot_de_passe_hache", nullable = false, length = 100)
    private String motDePasseHache;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    protected Client() {}

    public Client(String courriel, String motDePasseHache, String nom, Instant creeLe) {
        this.courriel = courriel;
        this.motDePasseHache = motDePasseHache;
        this.nom = nom;
        this.creeLe = creeLe;
    }

    public Long getId() {
        return id;
    }

    public String getCourriel() {
        return courriel;
    }

    public String getMotDePasseHache() {
        return motDePasseHache;
    }

    public String getNom() {
        return nom;
    }

    public Instant getCreeLe() {
        return creeLe;
    }
}
