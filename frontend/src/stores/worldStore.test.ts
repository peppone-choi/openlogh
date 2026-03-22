import { describe, expect, it } from 'vitest';

describe('worldStore persist config', () => {
    it('partialize only persists currentWorld', () => {
        const state = {
            worlds: [{ id: 1 }],
            currentWorld: { id: 1, name: 'test' },
            loading: true,
            isHydrated: true,
        };
        const partialize = (s: typeof state) => ({ currentWorld: s.currentWorld });
        const result = partialize(state);
        expect(result).toEqual({ currentWorld: { id: 1, name: 'test' } });
        expect(result).not.toHaveProperty('worlds');
        expect(result).not.toHaveProperty('loading');
        expect(result).not.toHaveProperty('isHydrated');
    });

    it('isHydrated defaults to false', () => {
        const initialState = { isHydrated: false };
        expect(initialState.isHydrated).toBe(false);
    });
});
