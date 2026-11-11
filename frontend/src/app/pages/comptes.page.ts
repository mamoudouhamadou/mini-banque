import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BanqueService } from '../banque.service';
import { messageErreur } from '../erreurs';
import { Compte } from '../modeles';

/** Liste de mes comptes, solde total, ouverture d'un nouveau compte. */
@Component({
  selector: 'app-comptes',
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, RouterLink],
  template: `
    <div class="titre-page">
      <div>
        <h1>Mes comptes</h1>
        <p class="doux">Solde total : <strong class="montant">{{ total() | currency: 'CAD' : 'symbol-narrow' }}</strong></p>
      </div>
      <button class="principal" type="button" (click)="formulaireOuvert.set(!formulaireOuvert())">
        {{ formulaireOuvert() ? 'Annuler' : '+ Ouvrir un compte' }}
      </button>
    </div>

    @if (formulaireOuvert()) {
      <form class="carte formulaire-ligne" [formGroup]="ouverture" (ngSubmit)="ouvrir()">
        <label>Titulaire <input formControlName="titulaire" placeholder="ex. Compte chèques" /></label>
        <label>Dépôt initial ($) <input formControlName="depotInitial" type="number" min="0" step="0.01" /></label>
        <button class="principal" type="submit" [disabled]="ouverture.invalid || envoi()">Ouvrir</button>
      </form>
    }

    @if (erreur()) { <p class="alerte" role="alert">{{ erreur() }}</p> }

    @if (chargement()) {
      <p class="doux">Chargement…</p>
    } @else {
      <ul class="grille-comptes">
        @for (c of comptes(); track c.id) {
          <li>
            <a class="carte compte" [routerLink]="['/comptes', c.id]">
              <span class="doux">Compte n° {{ c.id }}</span>
              <strong>{{ c.titulaire }}</strong>
              <span class="solde">{{ c.solde | currency: c.devise : 'symbol-narrow' }}</span>
              <small class="doux">Ouvert le {{ c.ouvertLe | date: 'longDate' }}</small>
            </a>
          </li>
        } @empty {
          <li class="vide">Aucun compte pour l'instant. Ouvre ton premier compte !</li>
        }
      </ul>
    }
  `,
})
export class ComptesPage implements OnInit {
  private readonly banque = inject(BanqueService);

  readonly comptes = signal<Compte[]>([]);
  readonly chargement = signal(true);
  readonly erreur = signal<string | null>(null);
  readonly envoi = signal(false);
  readonly formulaireOuvert = signal(false);

  // computed : recalculé automatiquement quand « comptes » change
  readonly total = computed(() => this.comptes().reduce((somme, c) => somme + c.solde, 0));

  readonly ouverture = inject(FormBuilder).nonNullable.group({
    titulaire: ['', [Validators.required, Validators.maxLength(100)]],
    depotInitial: [0, [Validators.required, Validators.min(0)]],
  });

  ngOnInit(): void {
    this.charger();
  }

  charger(): void {
    this.banque.mesComptes().subscribe({
      next: (page) => {
        this.comptes.set(page.content);
        this.chargement.set(false);
      },
      error: (e: unknown) => {
        this.erreur.set(messageErreur(e));
        this.chargement.set(false);
      },
    });
  }

  ouvrir(): void {
    const { titulaire, depotInitial } = this.ouverture.getRawValue();
    this.envoi.set(true);
    this.banque.ouvrir(titulaire, depotInitial).subscribe({
      next: (compte) => {
        this.comptes.update((liste) => [...liste, compte]);
        this.ouverture.reset();
        this.formulaireOuvert.set(false);
        this.erreur.set(null);
        this.envoi.set(false);
      },
      error: (e: unknown) => {
        this.erreur.set(messageErreur(e));
        this.envoi.set(false);
      },
    });
  }
}
