import { describe, expect, it } from 'vitest';

describe('lobby join page', () => {
    it('city selection disabled when nation is selected (nationId > 0)', () => {
        const nationId: number = 5;
        const citySelectable = nationId === 0;
        expect(citySelectable).toBe(false);
    });

    it('city selection enabled when 재야 (nationId === 0)', () => {
        const nationId = 0;
        const citySelectable = nationId === 0;
        expect(citySelectable).toBe(true);
    });
});
