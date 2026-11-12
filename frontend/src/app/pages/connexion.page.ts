import { Component, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { messageErreur } from '../erreurs';

/** Connexion et inscription dans un seul formulaire réactif (Reactive Forms). */
@Component({
  selector: 'app-connexion',
  imports: [ReactiveFormsModule],
  template: `
    <main class="centre">
      <section class="carte etroite">
        <h1>🏦 Mini-banque</h1>
        <p class="doux">{{ inscription() ? 'Créez votre accès client' : 'Connectez-vous à votre espace client' }}</p>

        @if (expiree()) {
          <p class="info" role="status">Votre session a expiré. Veuillez vous reconnecter.</p>
        }

        <form [formGroup]="formulaire" (ngSubmit)="soumettre()">
          @if (inscription()) {
            <label>Nom <input formControlName="nom" autocomplete="name" /></label>
          }
          <label>Courriel <input formControlName="courriel" type="email" autocomplete="email" /></label>
          <label>
            Mot de passe
            <input formControlName="motDePasse" type="password"
                   [attr.autocomplete]="inscription() ? 'new-password' : 'current-password'" />
            @if (inscription()) { <small>12 caractères minimum</small> }
          </label>

          @if (erreur()) { <p class="alerte" role="alert">{{ erreur() }}</p> }

          <button class="principal" type="submit" [disabled]="envoi()">
            {{ envoi() ? 'Un instant…' : inscription() ? 'Créer mon accès' : 'Se connecter' }}
          </button>
        </form>

        <p class="bascule">
          {{ inscription() ? 'Déjà client ?' : 'Pas encore client ?' }}
          <button type="button" class="lien" (click)="basculer()">
            {{ inscription() ? 'Se connecter' : 'Créer un accès' }}
          </button>
        </p>
      </section>
    </main>
  `,
})
export class ConnexionPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** ?expiree=1 dans l'URL (ajouté par l'intercepteur quand le jeton a expiré). */
  readonly expiree = input<string>();

  readonly mode = signal<'connexion' | 'inscription'>('connexion');
  readonly inscription = computed(() => this.mode() === 'inscription');
  readonly erreur = signal<string | null>(null);
  readonly envoi = signal(false);

  readonly formulaire = inject(FormBuilder).nonNullable.group({
    nom: [''],
    courriel: ['', [Validators.required, Validators.email]],
    motDePasse: ['', Validators.required],
  });

  basculer(): void {
    this.mode.set(this.inscription() ? 'connexion' : 'inscription');
    this.erreur.set(null);
  }

  soumettre(): void {
    const { nom, courriel, motDePasse } = this.formulaire.getRawValue();
    // La validation finale est faite par l'API (messages précis) ; ici, on évite seulement les envois vides.
    if (!courriel || !motDePasse || (this.inscription() && !nom.trim())) {
      this.erreur.set('Veuillez remplir tous les champs.');
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    const appel = this.inscription()
      ? this.auth.inscription(courriel, motDePasse, nom)
      : this.auth.connexion(courriel, motDePasse);

    appel.subscribe({
      next: () => void this.router.navigate(['/comptes']),
      error: (e: unknown) => {
        this.erreur.set(messageErreur(e));
        this.envoi.set(false);
      },
    });
  }
}
