import { create } from 'zustand';
import { TUTORIAL_STEPS } from '@/data/tutorial/steps';
import type { TutorialPhase, TutorialStep } from '@/data/tutorial/steps';

const STORAGE_KEY = 'tutorial-progress';

interface TutorialState {
    isTutorialMode: boolean;
    currentStep: number;
    steps: TutorialStep[];
    phase: TutorialPhase;

    // Actions
    start: () => void;
    nextStep: () => void;
    prevStep: () => void;
    goToStep: (stepId: number) => void;
    skipToPhase: (phase: TutorialPhase) => void;
    exit: () => void;

    // Persistence
    saveProgress: () => void;
    loadProgress: () => number | null;
}

export const useTutorialStore = create<TutorialState>((set, get) => ({
    isTutorialMode: false,
    currentStep: 0,
    steps: TUTORIAL_STEPS,
    phase: 'intro',

    start: () => {
        const saved = get().loadProgress();
        const step = saved !== null ? saved : 0;
        const phase = TUTORIAL_STEPS[step]?.phase ?? 'intro';
        set({ isTutorialMode: true, currentStep: step, phase });
    },

    nextStep: () => {
        const { currentStep, steps } = get();
        if (currentStep >= steps.length - 1) return;
        const next = currentStep + 1;
        const phase = steps[next].phase;
        set({ currentStep: next, phase });
        get().saveProgress();
    },

    prevStep: () => {
        const { currentStep, steps } = get();
        if (currentStep <= 0) return;
        const prev = currentStep - 1;
        const phase = steps[prev].phase;
        set({ currentStep: prev, phase });
        get().saveProgress();
    },

    goToStep: (stepId: number) => {
        const { steps } = get();
        const idx = steps.findIndex((s) => s.id === stepId);
        if (idx === -1) return;
        set({ currentStep: idx, phase: steps[idx].phase });
        get().saveProgress();
    },

    skipToPhase: (phase: TutorialPhase) => {
        const { steps } = get();
        const idx = steps.findIndex((s) => s.phase === phase);
        if (idx === -1) return;
        set({ currentStep: idx, phase });
        get().saveProgress();
    },

    exit: () => {
        if (typeof window !== 'undefined') {
            localStorage.removeItem(STORAGE_KEY);
        }
        set({ isTutorialMode: false, currentStep: 0, phase: 'intro' });
    },

    saveProgress: () => {
        if (typeof window !== 'undefined') {
            localStorage.setItem(STORAGE_KEY, String(get().currentStep));
        }
    },

    loadProgress: () => {
        if (typeof window === 'undefined') return null;
        const saved = localStorage.getItem(STORAGE_KEY);
        return saved !== null ? Number(saved) : null;
    },
}));
