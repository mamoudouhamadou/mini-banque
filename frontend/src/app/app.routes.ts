import { Routes } from '@angular/router';
import { connecteGuard, visiteurGuard } from './auth/auth.guard';

// loadComponent : chaque page est chargée seulement quand on la visite (« lazy loading ») →
// le premier chargement de l'application est plus léger.
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'comptes' },
  {
    path: 'connexion',
    canActivate: [visiteurGuard],
    title: 'Connexion · Mini-banque',
    loadComponent: () => import('./pages/connexion.page').then((m) => m.ConnexionPage),
  },
  {
    path: 'comptes',
    canActivate: [connecteGuard],
    title: 'Mes comptes · Mini-banque',
    loadComponent: () => import('./pages/comptes.page').then((m) => m.ComptesPage),
  },
  {
    path: 'comptes/:id',
    canActivate: [connecteGuard],
    title: 'Compte · Mini-banque',
    loadComponent: () => import('./pages/compte.page').then((m) => m.ComptePage),
  },
  {
    path: 'virement',
    canActivate: [connecteGuard],
    title: 'Virement · Mini-banque',
    loadComponent: () => import('./pages/virement.page').then((m) => m.VirementPage),
  },
  { path: '**', redirectTo: 'comptes' },
];
