import { create } from 'zustand';
import type { Officer } from '@/types';
import { officerApi as generalApi } from '@/lib/gameApi';

interface GeneralStore {
    myGeneral: Officer | null;
    generals: Officer[];
    loading: boolean;
    fetchMyGeneral: (worldId: number) => Promise<void>;
    fetchGenerals: (worldId: number) => Promise<void>;
    clearMyGeneral: () => void;
}

export const useGeneralStore = create<GeneralStore>((set) => ({
    myGeneral: null,
    generals: [],
    loading: false,

    fetchMyGeneral: async (worldId) => {
        set({ loading: true });
        try {
            const { data } = await generalApi.getMine(worldId);
            set({ myGeneral: data });
        } catch {
            set({ myGeneral: null });
        } finally {
            set({ loading: false });
        }
    },

    clearMyGeneral: () => set({ myGeneral: null }),

    fetchGenerals: async (worldId) => {
        set({ loading: true });
        try {
            const { data } = await generalApi.listByWorld(worldId);
            set({ generals: data });
        } finally {
            set({ loading: false });
        }
    },
}));

// Alias for LOGH migration - used by 27+ files
export { useGeneralStore as useOfficerStore };
