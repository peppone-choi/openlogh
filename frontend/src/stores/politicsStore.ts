import { create } from 'zustand';
import type {
  FactionPoliticsOverview,
  CoupStatus,
  CouncilStatus,
  Election,
  Loan,
  IntelligenceOffer,
  NobilityEntry,
} from '@/types/politics';
import { politicsApi } from '@/lib/politicsApi';
import api from '@/lib/api';

interface PoliticsState {
  overview: FactionPoliticsOverview | null;
  coupStatus: CoupStatus | null;
  councilStatus: CouncilStatus | null;
  activeElection: Election | null;
  loans: Loan[];
  intelOffers: IntelligenceOffer[];
  nobilityList: NobilityEntry[];
  /** Faction type resolved from officer's factionId ('empire' | 'alliance' | 'fezzan' | 'rebel') */
  factionType: string | null;
  loading: boolean;
  error: string | null;
}

interface PoliticsActions {
  fetchFactionType: (sessionId: number, factionId: number) => Promise<void>;
  fetchOverview: (sessionId: number, factionId: number) => Promise<void>;
  fetchCoupStatus: (sessionId: number, factionId: number) => Promise<void>;
  fetchCouncil: (sessionId: number, factionId: number) => Promise<void>;
  fetchElection: (sessionId: number, factionId: number) => Promise<void>;
  fetchLoans: (sessionId: number, factionId: number) => Promise<void>;
  fetchIntelOffers: (sessionId: number) => Promise<void>;
  fetchNobility: (sessionId: number, factionId: number) => Promise<void>;
  initiateCoup: (sessionId: number, factionId: number, officerId: number) => Promise<void>;
  joinCoup: (sessionId: number, coupId: number, officerId: number) => Promise<void>;
  abortCoup: (sessionId: number, coupId: number, officerId: number) => Promise<void>;
  startElection: (sessionId: number, factionId: number, seatCode?: string) => Promise<void>;
  castVote: (sessionId: number, officerId: number, electionId: number, candidateId: number) => Promise<void>;
  takeLoan: (sessionId: number, factionId: number, amount: number) => Promise<void>;
  repayLoan: (sessionId: number, loanId: number, amount: number) => Promise<void>;
  buyIntel: (sessionId: number, factionId: number, targetFactionId: number, type: string) => Promise<void>;
  clearError: () => void;
}

export const usePoliticsStore = create<PoliticsState & PoliticsActions>((set, get) => ({
  // State
  overview: null,
  coupStatus: null,
  councilStatus: null,
  activeElection: null,
  loans: [],
  intelOffers: [],
  nobilityList: [],
  factionType: null,
  loading: false,
  error: null,

  // Fetch faction type from /api/{sessionId}/factions/{factionId}
  fetchFactionType: async (sessionId, factionId) => {
    try {
      const { data } = await api.get<{ factionType?: string; faction_type?: string }>(
        `/${sessionId}/factions/${factionId}`
      );
      set({ factionType: data.factionType ?? data.faction_type ?? null });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  // Fetch actions
  fetchOverview: async (sessionId, factionId) => {
    set({ loading: true, error: null });
    try {
      const { data } = await politicsApi.getOverview(sessionId, factionId);
      set({
        overview: data,
        coupStatus: data.activeCoup ?? null,
        councilStatus: data.councilStatus ?? null,
        activeElection: data.activeElection ?? null,
        loans: data.loans,
        loading: false,
      });
    } catch (e) {
      set({ loading: false, error: (e as Error).message });
    }
  },

  fetchCoupStatus: async (sessionId, factionId) => {
    try {
      const { data } = await politicsApi.getCoupStatus(sessionId, factionId);
      set({ coupStatus: data });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  fetchCouncil: async (sessionId, factionId) => {
    try {
      const { data } = await politicsApi.getCouncilStatus(sessionId, factionId);
      set({ councilStatus: data });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  fetchElection: async (sessionId, factionId) => {
    try {
      const { data } = await politicsApi.getActiveElection(sessionId, factionId);
      set({ activeElection: data });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  fetchLoans: async (sessionId, factionId) => {
    try {
      const { data } = await politicsApi.getLoans(sessionId, factionId);
      set({ loans: data });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  fetchIntelOffers: async (sessionId) => {
    try {
      const { data } = await politicsApi.getIntelOffers(sessionId);
      set({ intelOffers: data });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  fetchNobility: async (sessionId, factionId) => {
    try {
      const { data } = await politicsApi.getNobility(sessionId, factionId);
      set({ nobilityList: data });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  // Action methods
  initiateCoup: async (sessionId, factionId, officerId) => {
    try {
      const { data } = await politicsApi.initiateCoup(sessionId, factionId, officerId);
      set({ coupStatus: data });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  joinCoup: async (sessionId, coupId, officerId) => {
    try {
      await politicsApi.joinCoup(sessionId, coupId, officerId);
      const { overview } = get();
      if (overview) {
        await get().fetchCoupStatus(sessionId, overview.factionId);
      }
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  abortCoup: async (sessionId, coupId, officerId) => {
    try {
      await politicsApi.abortCoup(sessionId, coupId, officerId);
      set({ coupStatus: null });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  startElection: async (sessionId, factionId, seatCode) => {
    try {
      const { data } = await politicsApi.startElection(sessionId, factionId, seatCode);
      set({ activeElection: data });
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  castVote: async (sessionId, officerId, electionId, candidateId) => {
    try {
      await politicsApi.castVote(sessionId, officerId, electionId, candidateId);
      const { overview } = get();
      if (overview) {
        await get().fetchElection(sessionId, overview.factionId);
      }
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  takeLoan: async (sessionId, factionId, amount) => {
    try {
      await politicsApi.takeLoan(sessionId, factionId, amount);
      await get().fetchLoans(sessionId, factionId);
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  repayLoan: async (sessionId, loanId, amount) => {
    try {
      await politicsApi.repayLoan(sessionId, loanId, amount);
      const { overview } = get();
      if (overview) {
        await get().fetchLoans(sessionId, overview.factionId);
      }
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  buyIntel: async (sessionId, factionId, targetFactionId, type) => {
    try {
      await politicsApi.buyIntel(sessionId, factionId, targetFactionId, type);
    } catch (e) {
      set({ error: (e as Error).message });
    }
  },

  clearError: () => set({ error: null }),
}));
