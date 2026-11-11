import { HttpErrorResponse } from '@angular/common/http';
import { messageErreur } from './erreurs';

describe('messageErreur (réponses RFC 9457 de l’API)', () => {
  it('liste les erreurs de validation champ par champ', () => {
    const e = new HttpErrorResponse({
      status: 400,
      error: { title: 'Données invalides', erreurs: [{ champ: 'montant', message: 'Le montant doit être positif' }] },
    });
    expect(messageErreur(e)).toBe('Le montant doit être positif');
  });

  it('affiche le détail métier (ex. solde insuffisant)', () => {
    const e = new HttpErrorResponse({ status: 422, error: { title: 'Solde insuffisant', detail: 'Solde insuffisant dans le compte 3' } });
    expect(messageErreur(e)).toBe('Solde insuffisant dans le compte 3');
  });

  it('explique quand le serveur est injoignable', () => {
    expect(messageErreur(new HttpErrorResponse({ status: 0 }))).toContain('injoignable');
  });
});
