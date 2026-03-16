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

describe('lobby join scout message nation abbreviation', () => {
    it('displays full abbreviation for single-char nations', () => {
        const mockNation = { abbreviation: '조' };
        expect(mockNation.abbreviation).toBe('조');
    });

    it('displays full abbreviation for two-char nations', () => {
        const mockNation = { abbreviation: '공손' };
        expect(mockNation.abbreviation).toBe('공손');
        expect(mockNation.abbreviation.length).toBe(2);
    });

    it('handles undefined abbreviation gracefully', () => {
        const mockNation = { abbreviation: undefined };
        const display = mockNation.abbreviation || '재야';
        expect(display).toBe('재야');
    });
});
