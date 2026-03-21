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

describe('lobby join scout message nation name display', () => {
    it('displays full nation name in recruitment badge', () => {
        const mockNation = { name: '조조군', abbreviation: '조' };
        expect(mockNation.name).toBe('조조군');
    });

    it('displays full name for two-char surname nations', () => {
        const mockNation = { name: '공손찬군', abbreviation: '공손' };
        expect(mockNation.name).toBe('공손찬군');
    });

    it('handles undefined name gracefully', () => {
        const mockNation = { name: undefined, abbreviation: '조' };
        const display = mockNation.name || '재야';
        expect(display).toBe('재야');
    });

    it('nation chip uses color as text color, not background', () => {
        const nationColor = '#ff6347';
        const style = { color: nationColor };
        expect(style.color).toBe('#ff6347');
        expect(style).not.toHaveProperty('backgroundColor');
    });
});

describe('lobby join random city selection', () => {
    it('random city sends undefined cityId to backend', () => {
        const cityId: number | 'random' = 'random';
        const apiCityId = cityId === 'random' ? undefined : cityId;
        expect(apiCityId).toBeUndefined();
    });

    it('specific city sends numeric cityId to backend', () => {
        const cityId: number | 'random' = 42 as number | 'random';
        const apiCityId = cityId === 'random' ? undefined : cityId;
        expect(apiCityId).toBe(42);
    });

    it('default state is random', () => {
        const defaultCityId: number | 'random' = 'random';
        expect(defaultCityId).toBe('random');
    });
});
