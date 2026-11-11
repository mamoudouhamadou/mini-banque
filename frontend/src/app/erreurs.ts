import { HttpErrorResponse } from '@angular/common/http';

/**
 * Transforme une erreur HTTP en message lisible.
 * L'API répond au format RFC 9457 : { title, detail, status, erreurs?: [{ champ, message }] }.
 */
export function messageErreur(erreur: unknown): string {
  if (!(erreur instanceof HttpErrorResponse)) return 'Erreur inattendue';
  if (erreur.status === 0) return "Serveur injoignable. L'API est-elle démarrée ?";

  const probleme = erreur.error as { title?: string; detail?: string; erreurs?: { message: string }[] } | null;
  if (probleme?.erreurs?.length) return probleme.erreurs.map((e) => e.message).join(' · ');
  return probleme?.detail ?? probleme?.title ?? `Erreur ${erreur.status}`;
}
