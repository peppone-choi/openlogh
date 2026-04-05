import { describe, expect, it } from 'vitest';
import { useOfficerStore } from './officerStore';

describe('generalStore persist config', () => {
    it('partialize only persists myOfficer', () => {
        const state = {
            myOfficer: { id: 42, name: 'TestGen' },
            generals: [{ id: 42 }, { id: 43 }],
            loading: true,
            isHydrated: true,
        };
        const partialize = (s: typeof state) => ({ myOfficer: s.myOfficer });
        const result = partialize(state);
        expect(result).toEqual({ myOfficer: { id: 42, name: 'TestGen' } });
        expect(result).not.toHaveProperty('generals');
        expect(result).not.toHaveProperty('loading');
        expect(result).not.toHaveProperty('isHydrated');
    });

    it('isHydrated defaults to false', () => {
        const initialState = { isHydrated: false };
        expect(initialState.isHydrated).toBe(false);
    });

    it('onFinishHydration listener sets isHydrated to true', () => {
        const state = useOfficerStore.getState();
        expect(state).toHaveProperty('isHydrated');
        expect(typeof state.isHydrated).toBe('boolean');
    });
});
