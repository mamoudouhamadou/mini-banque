import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Garde de route : empêche d'ouvrir une page protégée sans être connecté.
 * ⚠️ C'est du CONFORT (éviter une page vide), pas de la sécurité :
 * la vraie protection est côté serveur (Spring Security refuse toute requête sans jeton valide).
 */
export const connecteGuard: CanActivateFn = () => {
  const router = inject(Router);
  return inject(AuthService).jeton() ? true : router.createUrlTree(['/connexion']);
};

/** Inverse : la page de connexion redirige vers les comptes si on est déjà connecté. */
export const visiteurGuard: CanActivateFn = () => {
  const router = inject(Router);
  return inject(AuthService).jeton() ? router.createUrlTree(['/comptes']) : true;
};
