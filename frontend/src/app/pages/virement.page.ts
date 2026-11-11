import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { BanqueService } from '../banque.service';
import { messageErreur } from '../erreurs';
import { Compte, Virement } from '../modeles';

/** Virement depuis un de MES comptes vers n'importe quel compte (le mien ou celui d'un autre client). */
@Component({
  selector: 'app-virement',
  imports: [CurrencyPipe, ReactiveFormsModule],
  template: `
    <h1>Faire un virement</h1>

    <form class="carte etroite" [formGroup]="formulaire" (ngSubmit)="virer()">
      <label>
        Depuis
        <select formControlName="source">
          <option [ngValue]="null" disabled>Choisis un de tes comptes</option>
          @for (c of comptes(); track c.id) {
            <option [ngValue]="c.id">n° {{ c.id }} · {{ c.titulaire }} ({{ c.solde | currency: c.devise : 'symbol-narrow' }})</option>
          }
        </select>
      </label>
      <label>
        Vers le compte n°
        <input formControlName="destination" type="number" min="1" placeholder="ex. 12" />
        <small>Un de tes comptes ou celui d'un autre client.</small>
      </label>
      <label>Montant ($) <input formControlName="montant" type="number" min="0.01" step="0.01" /></label>
      <label>Description <input formControlName="description" maxlength="140" placeholder="ex. Loyer" /></label>

      @if (erreur()) { <p class="alerte" role="alert">{{ erreur() }}</p> }

      <button class="principal" type="submit" [disabled]="formulaire.invalid || envoi()">
        {{ envoi() ? 'Virement en cours…' : 'Virer' }}
      </button>
    </form>

    @if (resultat(); as v) {
      <section class="carte succes" role="status">
        <h2>✅ Virement effectué</h2>
        <p>{{ v.montant | currency: 'CAD' : 'symbol-narrow' }} du compte n° {{ v.source }} vers le compte n° {{ v.destination }}.</p>
        <p class="doux">Nouveau solde du compte n° {{ v.source }} : {{ v.soldeSourceApres | currency: 'CAD' : 'symbol-narrow' }}</p>
        <p class="doux">Référence : <code>{{ v.id }}</code></p>
      </section>
    }
  `,
})
export class VirementPage implements OnInit {
  private readonly banque = inject(BanqueService);

  readonly comptes = signal<Compte[]>([]);
  readonly erreur = signal<string | null>(null);
  readonly envoi = signal(false);
  readonly resultat = signal<Virement | null>(null);

  readonly formulaire = inject(FormBuilder).group({
    source: [null as number | null, Validators.required],
    destination: [null as number | null, [Validators.required, Validators.min(1)]],
    montant: [null as number | null, [Validators.required, Validators.min(0.01)]],
    description: [''],
  });

  ngOnInit(): void {
    this.chargerComptes();
  }

  virer(): void {
    const { source, destination, montant, description } = this.formulaire.getRawValue();
    if (source === null || destination === null || montant === null) return;

    this.envoi.set(true);
    this.erreur.set(null);
    this.resultat.set(null);
    this.banque.virer(source, destination, montant, description?.trim() || null).subscribe({
      next: (v) => {
        this.resultat.set(v);
        this.formulaire.patchValue({ montant: null, description: '' });
        this.chargerComptes(); // soldes à jour dans la liste
        this.envoi.set(false);
      },
      error: (e: unknown) => {
        this.erreur.set(messageErreur(e));
        this.envoi.set(false);
      },
    });
  }

  private chargerComptes(): void {
    this.banque.mesComptes().subscribe({
      next: (p) => this.comptes.set(p.content),
      error: (e: unknown) => this.erreur.set(messageErreur(e)),
    });
  }
}
