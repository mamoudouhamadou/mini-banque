package io.github.mamoudouhamadou.minibanque.client;

/**
 * Réponse d'inscription et de connexion (format inspiré d'OAuth 2.0) :
 * le client renvoie ensuite l'en-tête « Authorization: Bearer &lt;jeton&gt; ».
 */
public record JetonReponse(String jeton, String type, long expireDansSecondes, ClientReponse client) {

    public record ClientReponse(Long id, String courriel, String nom) {}
}
