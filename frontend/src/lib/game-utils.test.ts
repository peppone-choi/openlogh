import { describe, expect, it } from 'vitest';
import { getCrewTypeName, parseCrewTypeCode } from '@/lib/game-utils';

describe('crew type parsing', () => {
    it('parses legacy prefixed crew type', () => {
        expect(parseCrewTypeCode('che_0')).toBe(0);
    });

    it('parses map-prefixed crew type codes', () => {
        expect(parseCrewTypeCode('cr_1300')).toBe(1300);
        expect(parseCrewTypeCode('miniche_1400')).toBe(1400);
    });

    it('parses numeric string and number', () => {
        expect(parseCrewTypeCode('1200')).toBe(1200);
        expect(parseCrewTypeCode(1100)).toBe(1100);
    });

    it('falls back to 0 on invalid values', () => {
        expect(parseCrewTypeCode('unknown')).toBe(0);
        expect(parseCrewTypeCode(null)).toBe(0);
        expect(parseCrewTypeCode(undefined)).toBe(0);
    });

    it('resolves crew type display name with map-prefixed code', () => {
        expect(getCrewTypeName('cr_1300')).toBe('기병');
    });
});
