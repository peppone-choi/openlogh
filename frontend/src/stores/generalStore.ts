import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';
import type { General } from '@/types';
import { generalApi } from '@/lib/gameApi';

interface GeneralStore {
    myGeneral: General | null;
    generals: General[];
    loading: boolean;
    isHydrated: boolean;
    fetchMyGeneral: (worldId: number) => Promise<void>;
    fetchGenerals: (worldId: number) => Promise<void>;
    clearMyGeneral: () => void;
}

export const useGeneralStore = create<GeneralStore>()(
    persist(
        (set) => ({
            myGeneral: null,
            generals: [],
            loading: false,
            isHydrated: false,

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
        }),
        {
            name: 'general-store',
            storage: createJSONStorage(() => sessionStorage),
            partialize: (state) => ({ myGeneral: state.myGeneral }),
        }
    )
);

// Reliably detect hydration completion (onRehydrateStorage can misfire in Next.js App Router)
if (typeof window !== 'undefined') {
    useGeneralStore.persist.onFinishHydration(() => {
        useGeneralStore.setState({ isHydrated: true });
    });
    if (useGeneralStore.persist.hasHydrated()) {
        useGeneralStore.setState({ isHydrated: true });
    }
}
