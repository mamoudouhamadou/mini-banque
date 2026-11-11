import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, map } from 'rxjs';
import { ClientInfo, JetonReponse } from '../modeles';

interface Session {
  jeton: string;
  expireLe: number; // horodatage en millisecondes
  client: ClientInfo;
}

const CLE = 'mini-banque.session';

/**
 * L'état de connexion de toute l'application, sous forme de SIGNALS :
 * quand la session change, chaque composant qui la lit se met à jour tout seul.
 *
 * Stockage : sessionStorage (effacé à la fermeture de l'onglet). C'est un compromis :
 * plus court que localStorage, mais lisible par un script injecté (XSS). En production,
 * un cookie HttpOnly serait plus sûr contre le XSS (mais demande une protection CSRF).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly session = signal<Session | null>(lireSession());

  readonly connecte = computed(() => this.session() !== null);
  readonly client = computed(() => this.session()?.client ?? null);

  /** Le jeton s'il est encore valide ; sinon déconnecte et retourne null. */
  jeton(): string | null {
    const s = this.session();
    if (!s) return null;
    if (Date.now() >= s.expireLe) {
      this.deconnecter(true);
      return null;
    }
    return s.jeton;
  }

  connexion(courriel: string, motDePasse: string): Observable<void> {
    return this.http
      .post<JetonReponse>('/api/auth/connexion', { courriel, motDePasse })
      .pipe(map((r) => this.ouvrirSession(r)));
  }

  inscription(courriel: string, motDePasse: string, nom: string): Observable<void> {
    return this.http
      .post<JetonReponse>('/api/auth/inscription', { courriel, motDePasse, nom })
      .pipe(map((r) => this.ouvrirSession(r)));
  }

  deconnecter(sessionExpiree = false): void {
    ecrireSession(null);
    this.session.set(null);
    void this.router.navigate(['/connexion'], sessionExpiree ? { queryParams: { expiree: 1 } } : {});
  }

  private ouvrirSession(r: JetonReponse): void {
    const s: Session = { jeton: r.jeton, expireLe: Date.now() + r.expireDansSecondes * 1000, client: r.client };
    ecrireSession(s);
    this.session.set(s);
  }
}

// sessionStorage peut être bloqué (navigation privée, réglages) : on ne plante jamais pour autant.
function lireSession(): Session | null {
  try {
    const brut = sessionStorage.getItem(CLE);
    return brut ? (JSON.parse(brut) as Session) : null;
  } catch {
    return null;
  }
}

function ecrireSession(s: Session | null): void {
  try {
    if (s) sessionStorage.setItem(CLE, JSON.stringify(s));
    else sessionStorage.removeItem(CLE);
  } catch {
    /* la session restera en mémoire seulement */
  }
}
