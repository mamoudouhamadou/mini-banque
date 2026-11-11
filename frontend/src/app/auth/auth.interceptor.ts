import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Intercepteur HTTP : s'exécute pour CHAQUE requête de HttpClient.
 * 1. Ajoute « Authorization: Bearer <jeton> » aux appels de l'API (sauf inscription/connexion).
 * 2. Si l'API répond 401 (jeton expiré ou révoqué), déconnecte et renvoie vers la page de connexion.
 */
export const authInterceptor: HttpInterceptorFn = (requete, suivant) => {
  const auth = inject(AuthService);
  const publique = requete.url.startsWith('/api/auth/');
  const jeton = publique ? null : auth.jeton();

  const avecJeton = jeton
    ? requete.clone({ setHeaders: { Authorization: `Bearer ${jeton}` } })
    : requete;

  return suivant(avecJeton).pipe(
    catchError((erreur: unknown) => {
      if (erreur instanceof HttpErrorResponse && erreur.status === 401 && jeton) {
        auth.deconnecter(true);
      }
      return throwError(() => erreur);
    }),
  );
};
