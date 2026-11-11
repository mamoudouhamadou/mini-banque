import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';
import { authInterceptor } from './auth/auth.interceptor';
import { AuthService } from './auth/auth.service';
import { BanqueService } from './banque.service';

// HttpTestingController remplace le vrai réseau : on vérifie les requêtes envoyées et on choisit les réponses.

function configurer() {
  TestBed.configureTestingModule({
    imports: [App],
    providers: [
      // Route « attrape-tout » vide : la déconnexion navigue vers /connexion sans charger de vraie page
      provideRouter([{ path: '**', children: [] }]),
      provideHttpClient(withInterceptors([authInterceptor])),
      provideHttpClientTesting(),
    ],
  });
  return {
    http: TestBed.inject(HttpTestingController),
    auth: TestBed.inject(AuthService),
    banque: TestBed.inject(BanqueService),
  };
}

const reponseConnexion = {
  jeton: 'jeton-abc',
  type: 'Bearer',
  expireDansSecondes: 3600,
  client: { id: 1, courriel: 'ana@exemple.ca', nom: 'Ana' },
};

describe('Authentification', () => {
  beforeEach(() => sessionStorage.clear());

  it('ouvre une session après la connexion et la garde dans sessionStorage', () => {
    const { http, auth } = configurer();

    auth.connexion('ana@exemple.ca', 'mot-de-passe').subscribe();
    const requete = http.expectOne('/api/auth/connexion');
    expect(requete.request.body).toEqual({ courriel: 'ana@exemple.ca', motDePasse: 'mot-de-passe' });
    expect(requete.request.headers.has('Authorization')).toBe(false); // route publique : pas de jeton
    requete.flush(reponseConnexion);

    expect(auth.connecte()).toBe(true);
    expect(auth.client()?.nom).toBe('Ana');
    expect(sessionStorage.getItem('mini-banque.session')).toContain('jeton-abc');
  });

  it("l'intercepteur ajoute le jeton aux appels de l'API", () => {
    const { http, auth, banque } = configurer();
    auth.connexion('ana@exemple.ca', 'x').subscribe();
    http.expectOne('/api/auth/connexion').flush(reponseConnexion);

    banque.mesComptes().subscribe();

    const requete = http.expectOne((r) => r.url === '/api/comptes');
    expect(requete.request.headers.get('Authorization')).toBe('Bearer jeton-abc');
    requete.flush({ content: [], page: { size: 100, number: 0, totalElements: 0, totalPages: 0 } });
  });

  it('une réponse 401 déconnecte automatiquement', () => {
    const { http, auth, banque } = configurer();
    auth.connexion('ana@exemple.ca', 'x').subscribe();
    http.expectOne('/api/auth/connexion').flush(reponseConnexion);

    banque.mesComptes().subscribe({ error: () => undefined });
    http.expectOne((r) => r.url === '/api/comptes').flush(
      { title: 'Authentification requise' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(auth.connecte()).toBe(false);
    expect(sessionStorage.getItem('mini-banque.session')).toBeNull();
  });

  it('un jeton expiré est ignoré et la session fermée', () => {
    sessionStorage.setItem('mini-banque.session', JSON.stringify({ ...reponseConnexion, expireLe: Date.now() - 1000 }));
    const { auth } = configurer();

    expect(auth.jeton()).toBeNull();
    expect(auth.connecte()).toBe(false);
  });
});

describe('Coquille de l’application', () => {
  beforeEach(() => sessionStorage.clear());

  it("n'affiche pas la navigation tant qu'on n'est pas connecté", async () => {
    configurer();
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).querySelector('header')).toBeNull();
  });

  it('affiche le nom du client et la navigation une fois connecté', async () => {
    const { http, auth } = configurer();
    auth.connexion('ana@exemple.ca', 'x').subscribe();
    http.expectOne('/api/auth/connexion').flush(reponseConnexion);

    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const html = fixture.nativeElement as HTMLElement;

    expect(html.querySelector('header')?.textContent).toContain('Ana');
    expect(html.querySelector('nav')?.textContent).toContain('Virement');
  });
});
