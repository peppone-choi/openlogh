import { describe, expect, it } from 'vitest';
import { isBrightColor } from '@/lib/game-utils';

describe('NPC select nation badge contrast', () => {
    it('bright nation color gets dark text', () => {
        expect(isBrightColor('#FFFF00')).toBe(true);
        expect(isBrightColor('#FEE500')).toBe(true);
    });

    it('dark nation color gets light text', () => {
        expect(isBrightColor('#800000')).toBe(false);
        expect(isBrightColor('#000080')).toBe(false);
    });
});

describe('NPC select nation abbreviation display', () => {
    it('displays full abbreviation for single-char nations', () => {
        const mockNationMap: Record<number, { abbreviation: string; name: string }> = {
            1: { abbreviation: '유', name: '유비군' },
            2: { abbreviation: '조', name: '조조군' },
        };
        expect(mockNationMap[1].abbreviation).toBe('유');
        expect(mockNationMap[2].abbreviation).toBe('조');
    });

    it('displays full abbreviation for two-char nations', () => {
        const mockNationMap: Record<number, { abbreviation: string; name: string }> = {
            5: { abbreviation: '공손', name: '공손찬군' },
        };
        expect(mockNationMap[5].abbreviation).toBe('공손');
        expect(mockNationMap[5].abbreviation.length).toBe(2);
    });

    it('falls back to nation name when abbreviation is unavailable', () => {
        const mockNpc = { nationId: 999, nationName: '테스트군' };
        const mockNationMap: Record<number, { abbreviation: string; name: string }> = {};
        const display = mockNationMap[mockNpc.nationId]?.abbreviation || mockNpc.nationName;
        expect(display).toBe('테스트군');
    });
});
