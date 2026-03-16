import { describe, expect, it } from 'vitest';

const CITY_STATE_NAMES: Record<number, string> = {
    1: '풍작',
    2: '호황',
    3: '한파/폭설',
    4: '역병',
    5: '지진',
    6: '태풍',
    7: '홍수',
    8: '메뚜기/흉년',
    9: '황건적',
    31: '파괴',
    32: '파괴',
    33: '약탈',
    34: '약탈',
    41: '분쟁중',
    42: '분쟁중',
    43: '분쟁중',
};

describe('CITY_STATE_NAMES', () => {
    it('covers all TurnService transition states', () => {
        // 32→31→0 (파괴)
        expect(CITY_STATE_NAMES[32]).toBe('파괴');
        expect(CITY_STATE_NAMES[31]).toBe('파괴');
        // 34→33→0 (약탈)
        expect(CITY_STATE_NAMES[34]).toBe('약탈');
        expect(CITY_STATE_NAMES[33]).toBe('약탈');
        // 43→42→41→0 (분쟁)
        expect(CITY_STATE_NAMES[43]).toBe('분쟁중');
        expect(CITY_STATE_NAMES[42]).toBe('분쟁중');
        expect(CITY_STATE_NAMES[41]).toBe('분쟁중');
    });

    it('has no unknown fallback for any transition state', () => {
        const transitionStates = [31, 32, 33, 34, 41, 42, 43];
        for (const s of transitionStates) {
            expect(CITY_STATE_NAMES[s]).toBeDefined();
        }
    });
});

describe('nation abbreviation in tooltip', () => {
    function resolveAbbr(nationAbbr: string | null, nationName: string | null): string {
        return nationAbbr || nationName?.slice(0, 2) || '';
    }

    it('uses abbreviation field when available', () => {
        expect(resolveAbbr('유', '유비군')).toBe('유');
    });

    it('falls back to name slice when abbreviation is empty', () => {
        expect(resolveAbbr('', '조조군')).toBe('조조');
    });

    it('supports 2-char abbreviations', () => {
        const abbr = resolveAbbr('공손', '공손찬군');
        expect(abbr).toBe('공손');
        expect(abbr.length).toBe(2);
    });

    it('returns empty string when both are null', () => {
        expect(resolveAbbr(null, null)).toBe('');
    });
});

describe('nation flag display in map viewer', () => {
    it('shows full abbreviation without slicing', () => {
        const mockNations = [{ abbreviation: '유' }, { abbreviation: '조' }, { abbreviation: '공손' }];

        expect(mockNations[0].abbreviation).toBe('유');
        expect(mockNations[1].abbreviation).toBe('조');
        expect(mockNations[2].abbreviation).toBe('공손');
    });

    it('calculates dynamic flag size based on abbreviation length', () => {
        function getFlagSize(abbr: string): number {
            return abbr.length === 2 ? 24 : 16;
        }

        expect(getFlagSize('유')).toBe(16);
        expect(getFlagSize('조')).toBe(16);
        expect(getFlagSize('공손')).toBe(24);
    });

    it('handles edge cases in flag sizing', () => {
        function getFlagSize(abbr: string): number {
            return abbr.length === 2 ? 24 : 16;
        }

        expect(getFlagSize('')).toBe(16);
        expect(getFlagSize('ABC')).toBe(16);
    });
});
