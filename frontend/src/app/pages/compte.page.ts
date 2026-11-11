import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BanqueService } from '../banque.service';
import { messageErreur } from '../erreurs';
import { Compte, LIBELLES_OPERATION, Operation, TypeOperation } from '../modeles';

/** Détail d'un compte : solde, dépôt/retrait et relevé paginé. */
@Component({
  selector: 'app-compte',
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, RouterLink],
  template: `
    <a routerLink="/comptes" class="retour">← Mes comptes</a>

    @if (compte(); as c) {
      <div class="titre-page">
        <div>
          <h1>{{ c.titulaire }}</h1>
          <p class="doux">Compte n° {{ c.id }} · ouvert le {{ c.ouvertLe | date: 'longDate' }}</p>
        </div>
        <p class="solde grand">{{ c.solde | currency: c.devise : 'symbol-narrow' }}</p>
      </div>

      <form class="carte formulaire-ligne" [formGroup]="mouvement" (ngSubmit)="executer()">
        <label>
          Opération
          <select formControlName="type">
            <option value="depot">Dépôt</option>
            <option value="retrait">Retrait</option>
          </select>
        </label>
        <label>Montant ($) <input formControlName="montant" type="number" min="0.01" step="0.01" /></label>
        <label class="large">Description <input formControlName="description" maxlength="140" /></label>
        <button class="principal" type="submit" [disabled]="mouvement.invalid || envoi()">Valider</button>
      </form>
    }

    @if (erreur()) { <p class="alerte" role="alert">{{ erreur() }}</p> }

    <section class="carte">
      <h2>Relevé</h2>
      <table>
        <thead>
          <tr><th>Date</th><th>Opération</th><th>Description</th><th class="nombre">Montant</th><th class="nombre">Solde</th></tr>
        </thead>
        <tbody>
          @for (o of operations(); track o.id) {
            <tr>
              <td>{{ o.effectueeLe | date: 'short' }}</td>
              <td>{{ libelle(o.type) }}</td>
              <td class="doux">{{ o.description ?? '—' }}</td>
              <td class="nombre" [class.negatif]="sortie(o.type)" [class.positif]="!sortie(o.type)">
                {{ sortie(o.type) ? '−' : '+' }}{{ o.montant | currency: 'CAD' : 'symbol-narrow' }}
              </td>
              <td class="nombre">{{ o.soldeApres | currency: 'CAD' : 'symbol-narrow' }}</td>
            </tr>
          } @empty {
            <tr><td colspan="5" class="vide">Aucune opération.</td></tr>
          }
        </tbody>
      </table>

      @if (totalPages() > 1) {
        <nav class="pagination" aria-label="Pages du relevé">
          <button type="button" [disabled]="page() === 0" (click)="allerA(page() - 1)">← Plus récentes</button>
          <span>Page {{ page() + 1 }} / {{ totalPages() }}</span>
          <button type="button" [disabled]="page() + 1 >= totalPages()" (click)="allerA(page() + 1)">Plus anciennes →</button>
        </nav>
      }
    </section>
  `,
})
export class ComptePage implements OnInit {
  private readonly banque = inject(BanqueService);

  /** Paramètre de route « :id » (grâce à withComponentInputBinding). */
  readonly id = input.required<string>();

  readonly compte = signal<Compte | null>(null);
  readonly operations = signal<Operation[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly erreur = signal<string | null>(null);
  readonly envoi = signal(false);

  readonly mouvement = inject(FormBuilder).nonNullable.group({
    type: ['depot' as 'depot' | 'retrait'],
    montant: [null as number | null, [Validators.required, Validators.min(0.01)]],
    description: [''],
  });

  ngOnInit(): void {
    this.rafraichir();
  }

  libelle(type: TypeOperation): string {
    return LIBELLES_OPERATION[type];
  }

  sortie(type: TypeOperation): boolean {
    return type === 'RETRAIT' || type === 'VIREMENT_DEBIT';
  }

  allerA(page: number): void {
    this.page.set(page);
    this.chargerReleve();
  }

  executer(): void {
    const { type, montant, description } = this.mouvement.getRawValue();
    if (montant === null) return;
    const id = Number(this.id());
    const appel = type === 'depot'
      ? this.banque.deposer(id, montant, description.trim() || null)
      : this.banque.retirer(id, montant, description.trim() || null);

    this.envoi.set(true);
    appel.subscribe({
      next: () => {
        this.mouvement.reset({ type, montant: null, description: '' });
        this.page.set(0);
        this.rafraichir();
        this.envoi.set(false);
      },
      error: (e: unknown) => {
        this.erreur.set(messageErreur(e)); // ex. « Solde insuffisant… » (422)
        this.envoi.set(false);
      },
    });
  }

  private rafraichir(): void {
    this.erreur.set(null);
    this.banque.compte(Number(this.id())).subscribe({
      next: (c) => this.compte.set(c),
      error: (e: unknown) => this.erreur.set(messageErreur(e)), // 404 si ce n'est pas mon compte
    });
    this.chargerReleve();
  }

  private chargerReleve(): void {
    this.banque.operations(Number(this.id()), this.page()).subscribe({
      next: (p) => {
        this.operations.set(p.content);
        this.totalPages.set(p.page.totalPages);
      },
      error: () => this.operations.set([]),
    });
  }
}
