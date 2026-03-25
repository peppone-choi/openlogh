import { create } from 'zustand';
import type { Officer } from '@/types';
import { officerApi } from '@/lib/gameApi';

interface OfficerStore {
    myOfficer: Officer | null;
    officers: Officer[];
    loading: boolean;
    fetchMyOfficer: (worldId: number) => Promise<void>;
    fetchOfficers: (worldId: number) => Promise<void>;
    clearMyOfficer: () => void;
    /** @deprecated use myOfficer */ myGeneral: Officer | null;
    /** @deprecated use fetchMyOfficer */ fetchMyGeneral: (worldId: number) => Promise<void>;
    /** @deprecated use clearMyOfficer */ clearMyGeneral: () => void;
    /** @deprecated use fetchOfficers */ fetchGenerals: (worldId: number) => Promise<void>;
}

export const useOfficerStore = create<OfficerStore>((set, get) => ({
    myOfficer: null,
    officers: [],
    loading: false,

    get myGeneral() {
        return get().myOfficer;
    },
    get fetchMyGeneral() {
        return get().fetchMyOfficer;
    },
    get clearMyGeneral() {
        return get().clearMyOfficer;
    },
    get fetchGenerals() {
        return get().fetchOfficers;
    },

    fetchMyOfficer: async (worldId) => {
        set({ loading: true });
        try {
            const { data } = await officerApi.getMine(worldId);
            set({ myOfficer: data });
        } catch {
            set({ myOfficer: null });
        } finally {
            set({ loading: false });
        }
    },

    clearMyOfficer: () => set({ myOfficer: null }),

    fetchOfficers: async (worldId) => {
        set({ loading: true });
        try {
            const { data } = await officerApi.listByWorld(worldId);
            set({ officers: data });
        } finally {
            set({ loading: false });
        }
    },
}));

// Deprecated alias for backward compat
export { useOfficerStore as useGeneralStore };
