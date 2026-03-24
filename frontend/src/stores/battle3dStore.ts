import { create } from 'zustand';
import type { AnimationSequence, Battle3DState, BattleViewMode } from '@/types/battle3d';

interface Battle3DActions {
    loadSequence: (seq: AnimationSequence) => void;
    play: () => void;
    pause: () => void;
    reset: () => void;
    nextPhase: () => void;
    setSpeed: (speed: number) => void;
    setViewMode: (mode: BattleViewMode) => void;
    toggleEffects: () => void;
    toggleHUD: () => void;
}

export const useBattle3DStore = create<Battle3DState & Battle3DActions>((set, get) => ({
    viewMode: '3d',
    playState: 'idle',
    sequence: null,
    currentPhase: 0,
    playbackSpeed: 1,
    showEffects: true,
    showHUD: true,

    loadSequence: (seq) => set({ sequence: seq, currentPhase: 0, playState: 'loading' }),

    play: () => set({ playState: 'playing' }),

    pause: () => set({ playState: 'paused' }),

    reset: () => set({ currentPhase: 0, playState: 'idle' }),

    nextPhase: () => {
        const { sequence, currentPhase } = get();
        if (!sequence) return;
        const lastIndex = sequence.phases.length - 1;
        if (currentPhase >= lastIndex) {
            set({ playState: 'finished' });
        } else {
            set({ currentPhase: currentPhase + 1 });
        }
    },

    setSpeed: (speed) => set({ playbackSpeed: speed }),

    setViewMode: (mode) => set({ viewMode: mode }),

    toggleEffects: () => set((s) => ({ showEffects: !s.showEffects })),

    toggleHUD: () => set((s) => ({ showHUD: !s.showHUD })),
}));
