import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './auth/auth.service';

/** La « coquille » de l'application : en-tête et navigation, puis la page courante (router-outlet). */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    @if (auth.connecte()) {
      <header class="entete">
        <a routerLink="/comptes" class="marque">🏦 Mini-banque</a>
        <nav>
          <a routerLink="/comptes" routerLinkActive="actif">Mes comptes</a>
          <a routerLink="/virement" routerLinkActive="actif">Virement</a>
        </nav>
        <div class="client">
          <span>{{ auth.client()?.nom }}</span>
          <button type="button" (click)="auth.deconnecter()">Se déconnecter</button>
        </div>
      </header>
    }
    <main class="contenu">
      <router-outlet />
    </main>
  `,
})
export class App {
  protected readonly auth = inject(AuthService);
}
