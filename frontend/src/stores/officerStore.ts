import { create } from 'zustand';
import type { Officer } from '@/types';
import { officerApi as generalApi } from '@/lib/gameApi';

interface OfficerStore {
    myOfficer: Officer | null;
    officers: Officer[];
    loading: boolean;
    fetchMyOfficer: (worldId: number) => Promise<void>;
    fetchOfficers: (worldId: number) => Promise<void>;
    clearMyOfficer: () => void;
    // Deprecated aliases
    /** @deprecated use myOfficer */ myGeneral: Officer | null;
    /** @deprecated use fetchMyOfficer */ fetchMyGeneral: (worldId: number) => Promise<void>;
    /** @deprecated use fetchOfficers */ fetchGenerals: (worldId: number) => Promise<void>;
    /** @deprecated use clearMyOfficer */ clearMyGeneral: () => void;
}

export const useOfficerStore = create<OfficerStore>((set, get) => ({
    myOfficer: null,
    officers: [],
    loading: false,

    fetchMyOfficer: async (worldId) => {
        set({ loading: true });
        try {
            const { data } = await generalApi.getMine(worldId);
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
            const { data } = await generalApi.listByWorld(worldId);
            set({ officers: data });
        } finally {
            set({ loading: false });
        }
    },

    // Deprecated aliases
    get myGeneral() {
        return get().myOfficer;
    },
    fetchMyGeneral: async (worldId) => get().fetchMyOfficer(worldId),
    fetchGenerals: async (worldId) => get().fetchOfficers(worldId),
    clearMyGeneral: () => get().clearMyOfficer(),
}));

/** @deprecated Use useOfficerStore */
export { useOfficerStore as useGeneralStore };
