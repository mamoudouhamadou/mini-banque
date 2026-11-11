// Les formes de données échangées avec l'API Spring Boot (voir /v3/api-docs).

export interface ClientInfo {
  id: number;
  courriel: string;
  nom: string;
}

export interface JetonReponse {
  jeton: string;
  type: 'Bearer';
  expireDansSecondes: number;
  client: ClientInfo;
}

export interface Compte {
  id: number;
  titulaire: string;
  solde: number;
  devise: string;
  ouvertLe: string;
}

export type TypeOperation = 'DEPOT' | 'RETRAIT' | 'VIREMENT_DEBIT' | 'VIREMENT_CREDIT';

export interface Operation {
  id: number;
  type: TypeOperation;
  montant: number;
  soldeApres: number;
  virementId: string | null;
  description: string | null;
  effectueeLe: string;
}

export interface Virement {
  id: string;
  source: number;
  destination: number;
  montant: number;
  soldeSourceApres: number;
  soldeDestinationApres: number;
  description: string | null;
  effectueLe: string;
}

/** Format des pages renvoyées par Spring Data (mode VIA_DTO). */
export interface Page<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

export const LIBELLES_OPERATION: Record<TypeOperation, string> = {
  DEPOT: 'Dépôt',
  RETRAIT: 'Retrait',
  VIREMENT_DEBIT: 'Virement envoyé',
  VIREMENT_CREDIT: 'Virement reçu',
};
