import { describe, expect, it } from 'vitest';

describe('nations page killTurn display logic', () => {
    function shouldShowDeathMarker(killTurn: number | null): boolean {
        return killTurn != null && killTurn <= 0;
    }

    it('should NOT show death marker when killTurn is positive (alive)', () => {
        expect(shouldShowDeathMarker(240)).toBe(false);
        expect(shouldShowDeathMarker(70)).toBe(false);
        expect(shouldShowDeathMarker(1)).toBe(false);
    });

    it('should show death marker when killTurn is 0 (about to die)', () => {
        expect(shouldShowDeathMarker(0)).toBe(true);
    });

    it('should show death marker when killTurn is negative', () => {
        expect(shouldShowDeathMarker(-1)).toBe(true);
        expect(shouldShowDeathMarker(-10)).toBe(true);
    });

    it('should NOT show death marker when killTurn is null', () => {
        expect(shouldShowDeathMarker(null)).toBe(false);
    });
});
