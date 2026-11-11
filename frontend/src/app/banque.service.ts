import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Compte, Operation, Page, Virement } from './modeles';

/** Tous les appels à l'API bancaire. Le jeton est ajouté automatiquement par l'intercepteur. */
@Injectable({ providedIn: 'root' })
export class BanqueService {
  private readonly http = inject(HttpClient);

  mesComptes(): Observable<Page<Compte>> {
    return this.http.get<Page<Compte>>('/api/comptes', { params: { size: 100, sort: 'id' } });
  }

  compte(id: number): Observable<Compte> {
    return this.http.get<Compte>(`/api/comptes/${id}`);
  }

  ouvrir(titulaire: string, depotInitial: number): Observable<Compte> {
    return this.http.post<Compte>('/api/comptes', { titulaire, depotInitial });
  }

  operations(id: number, page: number, taille = 10): Observable<Page<Operation>> {
    const params = new HttpParams().set('page', page).set('size', taille);
    return this.http.get<Page<Operation>>(`/api/comptes/${id}/operations`, { params });
  }

  deposer(id: number, montant: number, description: string | null): Observable<Operation> {
    return this.http.post<Operation>(`/api/comptes/${id}/depots`, { montant, description });
  }

  retirer(id: number, montant: number, description: string | null): Observable<Operation> {
    return this.http.post<Operation>(`/api/comptes/${id}/retraits`, { montant, description });
  }

  virer(source: number, destination: number, montant: number, description: string | null): Observable<Virement> {
    return this.http.post<Virement>('/api/virements', { source, destination, montant, description });
  }
}
