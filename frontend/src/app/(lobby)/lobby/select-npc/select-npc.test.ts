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
