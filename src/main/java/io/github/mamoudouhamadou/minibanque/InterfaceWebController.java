package io.github.mamoudouhamadou.minibanque;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Application d'une seule page (SPA) : l'interface Angular est copiée dans src/main/resources/static
 * au moment de construire l'image Docker. Spring Boot sert ses fichiers (index.html, *.js, *.css).
 *
 * <p>Problème : si on recharge la page /comptes/7, le NAVIGATEUR demande /comptes/7 au serveur, qui
 * ne connaît pas cette « page » (c'est une route Angular). On renvoie donc index.html, puis Angular
 * affiche la bonne page. Les routes sont listées explicitement : une URL inconnue reste une vraie 404.
 */
@Controller
public class InterfaceWebController {

    @GetMapping({"/connexion", "/comptes", "/comptes/{id:\\d+}", "/virement"})
    public String routeAngular() {
        return "forward:/index.html";
    }
}
