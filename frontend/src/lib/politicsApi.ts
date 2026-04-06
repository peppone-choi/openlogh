import axios from 'axios';
import type {
  FactionPoliticsOverview,
  CoupStatus,
  NobilityEntry,
  CouncilStatus,
  Election,
  Loan,
  IntelligenceOffer,
} from '@/types/politics';

const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080',
});

api.interceptors.request.use((config) => {
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

export const politicsApi = {
  // Overview
  getOverview: (sessionId: number, factionId: number) =>
    api.get<FactionPoliticsOverview>(
      `/api/world/${sessionId}/politics/overview`,
      { params: { factionId } },
    ),

  // Empire
  getCoupStatus: (sessionId: number, factionId: number) =>
    api.get<CoupStatus | null>(
      `/api/world/${sessionId}/politics/empire/coup`,
      { params: { factionId } },
    ),

  initiateCoup: (sessionId: number, factionId: number, officerId: number) =>
    api.post<CoupStatus>(
      `/api/world/${sessionId}/politics/empire/coup/initiate`,
      { factionId },
      { params: { officerId } },
    ),

  joinCoup: (sessionId: number, coupId: number, officerId: number) =>
    api.post(
      `/api/world/${sessionId}/politics/empire/coup/${coupId}/join`,
      null,
      { params: { officerId } },
    ),

  abortCoup: (sessionId: number, coupId: number, officerId: number) =>
    api.post(
      `/api/world/${sessionId}/politics/empire/coup/${coupId}/abort`,
      null,
      { params: { officerId } },
    ),

  getNobility: (sessionId: number, factionId: number) =>
    api.get<NobilityEntry[]>(
      `/api/world/${sessionId}/politics/empire/nobility`,
      { params: { factionId } },
    ),

  // Alliance
  getCouncilStatus: (sessionId: number, factionId: number) =>
    api.get<CouncilStatus>(
      `/api/world/${sessionId}/politics/alliance/council`,
      { params: { factionId } },
    ),

  getActiveElection: (sessionId: number, factionId: number) =>
    api.get<Election | null>(
      `/api/world/${sessionId}/politics/alliance/election`,
      { params: { factionId } },
    ),

  startElection: (sessionId: number, factionId: number, seatCode?: string) =>
    api.post<Election>(
      `/api/world/${sessionId}/politics/alliance/election/start`,
      { seatCode },
      { params: { factionId } },
    ),

  castVote: (sessionId: number, officerId: number, electionId: number, candidateId: number) =>
    api.post(
      `/api/world/${sessionId}/politics/alliance/election/vote`,
      { electionId, candidateId },
      { params: { officerId } },
    ),

  // Fezzan
  getLoans: (sessionId: number, factionId: number) =>
    api.get<Loan[]>(
      `/api/world/${sessionId}/politics/fezzan/loans`,
      { params: { factionId } },
    ),

  takeLoan: (sessionId: number, factionId: number, amount: number) =>
    api.post<Loan>(
      `/api/world/${sessionId}/politics/fezzan/loan/take`,
      { amount },
      { params: { factionId } },
    ),

  repayLoan: (sessionId: number, loanId: number, amount: number) =>
    api.post(
      `/api/world/${sessionId}/politics/fezzan/loan/repay`,
      { loanId, amount },
    ),

  getIntelOffers: (sessionId: number) =>
    api.get<IntelligenceOffer[]>(
      `/api/world/${sessionId}/politics/fezzan/intel`,
    ),

  buyIntel: (sessionId: number, factionId: number, targetFactionId: number, intelligenceType: string) =>
    api.post<Record<string, unknown>>(
      `/api/world/${sessionId}/politics/fezzan/intel/buy`,
      { targetFactionId, intelligenceType },
      { params: { factionId } },
    ),
};
