package io.github.mamoudouhamadou.minibanque.client;

import io.github.mamoudouhamadou.minibanque.securite.Jetons;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inscription et connexion. */
@Service
public class AuthService {

    private final ClientRepository clients;
    private final PasswordEncoder motsDePasse;
    private final Jetons jetons;
    private final Clock horloge;

    // Hachage « factice » calculé une seule fois : sert à comparer quand le courriel n'existe pas,
    // pour que la réponse prenne le même temps (sinon on devinerait les courriels inscrits au chronomètre).
    private final String hachageFactice;

    public AuthService(ClientRepository clients, PasswordEncoder motsDePasse, Jetons jetons, Clock horloge) {
        this.clients = clients;
        this.motsDePasse = motsDePasse;
        this.jetons = jetons;
        this.horloge = horloge;
        this.hachageFactice = motsDePasse.encode("mot-de-passe-factice-jamais-utilise");
    }

    @Transactional
    public JetonReponse inscrire(Inscription demande) {
        String courriel = normaliser(demande.courriel());
        if (clients.existsByCourriel(courriel)) {
            throw new CourrielDejaUtiliseException();
        }
        Client client = new Client(courriel, motsDePasse.encode(demande.motDePasse()), demande.nom().strip(), Instant.now(horloge));
        try {
            client = clients.saveAndFlush(client);
        } catch (DataIntegrityViolationException e) {
            // Deux inscriptions simultanées avec le même courriel : l'index UNIQUE tranche.
            throw new CourrielDejaUtiliseException();
        }
        return reponse(client);
    }

    @Transactional(readOnly = true)
    public JetonReponse connecter(Connexion demande) {
        Client client = clients.findByCourriel(normaliser(demande.courriel())).orElse(null);
        String hachage = client != null ? client.getMotDePasseHache() : hachageFactice;
        boolean valide = motsDePasse.matches(demande.motDePasse(), hachage);
        if (client == null || !valide) {
            throw new IdentifiantsInvalidesException();
        }
        return reponse(client);
    }

    private JetonReponse reponse(Client client) {
        return new JetonReponse(
                jetons.emettre(client.getId()),
                "Bearer",
                Jetons.DUREE.toSeconds(),
                new JetonReponse.ClientReponse(client.getId(), client.getCourriel(), client.getNom()));
    }

    private static String normaliser(String courriel) {
        return courriel.strip().toLowerCase(Locale.ROOT);
    }
}
