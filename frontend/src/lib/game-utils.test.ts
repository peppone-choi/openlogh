import { describe, expect, it } from 'vitest';
import {
    REGION_NAMES,
    CITY_LEVEL_NAMES,
    NATION_LEVEL_LABELS,
    getCrewTypeName,
    getNationTypeLabel,
    formatOfficerLevelText,
    getSpecialNationKey,
    getPersonalityName,
    getNationLevelLabel,
    parseCrewTypeCode,
    stripCodePrefix,
} from '@/lib/game-utils';

describe('nation type labels have no parentheses', () => {
    it('getNationTypeLabel returns label without parentheses', () => {
        const types = ['che_도적', 'che_유가', 'che_법가', 'che_덕가', 'che_병가'];
        for (const t of types) {
            const label = getNationTypeLabel(t);
            expect(label).not.toContain('(');
            expect(label).not.toContain(')');
        }
    });
});

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
        expect(getCrewTypeName('cr_1500')).toBe('정란');
        expect(getCrewTypeName('3')).toBe('귀병');
    });
});

describe('stripCodePrefix', () => {
    it('strips che_ prefix from nation type codes', () => {
        expect(stripCodePrefix('che_유가')).toBe('유가');
        expect(stripCodePrefix('che_왕도')).toBe('왕도');
        expect(stripCodePrefix('che_패권')).toBe('패권');
    });

    it('returns code unchanged when no che_ prefix', () => {
        expect(stripCodePrefix('농업')).toBe('농업');
        expect(stripCodePrefix('호전')).toBe('호전');
    });
});

describe('getNationTypeLabel', () => {
    it('returns label without parentheses for known types', () => {
        expect(getNationTypeLabel('che_유가')).toBe('유가');
        expect(getNationTypeLabel('che_법가')).toBe('법가');
        expect(getNationTypeLabel('che_병가')).toBe('병가');
        expect(getNationTypeLabel('che_도적')).toBe('도적');
    });

    it('falls back to stripCodePrefix for unknown types', () => {
        expect(getNationTypeLabel('che_커스텀')).toBe('커스텀');
    });
});

describe('nation level labels (10 levels)', () => {
    it('has 10 nation levels from 0 to 9', () => {
        expect(Object.keys(NATION_LEVEL_LABELS)).toHaveLength(10);
        expect(NATION_LEVEL_LABELS[0]).toBe('방랑군');
        expect(NATION_LEVEL_LABELS[1]).toBe('도위');
        expect(NATION_LEVEL_LABELS[9]).toBe('황제');
    });
});

describe('officer level text with ruler level 20', () => {
    it('returns 군주 for officer level 20 with default map', () => {
        expect(formatOfficerLevelText(20)).toBe('군주');
    });

    it('returns 황제 for officer level 20 at nation level 9', () => {
        expect(formatOfficerLevelText(20, 9)).toBe('황제');
    });

    it('returns 방주 for officer level 20 at nation level 0', () => {
        expect(formatOfficerLevelText(20, 0)).toBe('방주');
    });

    it('returns default name for low officer levels regardless of nation level', () => {
        expect(formatOfficerLevelText(4, 9)).toBe('태수');
        expect(formatOfficerLevelText(1, 9)).toBe('일반');
    });
});

describe('황건(태평도) special officer ranks', () => {
    it('getSpecialNationKey maps che_태평도 to 황건', () => {
        expect(getSpecialNationKey('che_태평도')).toBe('황건');
    });

    it('getSpecialNationKey returns null for non-special types', () => {
        expect(getSpecialNationKey('유가')).toBeNull();
        expect(getSpecialNationKey(undefined)).toBeNull();
        expect(getSpecialNationKey(null)).toBeNull();
    });

    it('returns 천공장군 for officer level 20 with 태평도 type', () => {
        expect(formatOfficerLevelText(20, 2, false, '태평도')).toBe('천공장군');
    });

    it('returns 지공장군 for level 19 and 인공장군 for level 18', () => {
        expect(formatOfficerLevelText(19, 2, false, '태평도')).toBe('지공장군');
        expect(formatOfficerLevelText(18, 2, false, '태평도')).toBe('인공장군');
    });

    it('uses 황건 ranks regardless of nationLevel', () => {
        expect(formatOfficerLevelText(20, 9, false, '태평도')).toBe('천공장군');
        expect(formatOfficerLevelText(17, 0, false, '태평도')).toBe('신상사');
    });

    it('falls back to default for levels below 황건 rank map', () => {
        expect(formatOfficerLevelText(5, 2, false, '태평도')).toBe('졸장');
    });

    it('handles che_ prefixed typeCode via getSpecialNationKey', () => {
        expect(getSpecialNationKey('che_태평도')).toBe('황건');
    });

    it('returns 황건 rank with che_ prefixed typeCode in formatOfficerLevelText', () => {
        expect(formatOfficerLevelText(20, 2, false, 'che_태평도')).toBe('천공장군');
    });

    it('returns 황제 when npcState is 10', () => {
        expect(formatOfficerLevelText(1, 9, true, undefined, 10)).toBe('황제');
    });

    it('does not return 황제 for normal npcState', () => {
        expect(formatOfficerLevelText(1, 9, true)).toBe('일반');
    });
});

describe('getNationLevelLabel', () => {
    it('returns 황제 for level 9 default', () => {
        expect(getNationLevelLabel(9)).toBe('황제');
    });

    it('returns 천공장군 for level 9 황건적', () => {
        expect(getNationLevelLabel(9, '태평도')).toBe('천공장군');
        expect(getNationLevelLabel(9, 'che_태평도')).toBe('천공장군');
    });

    it('returns normal label for non-special nations', () => {
        expect(getNationLevelLabel(8, 'che_유가')).toBe('왕');
        expect(getNationLevelLabel(0)).toBe('방랑군');
    });
});

describe('personality and specialty labels', () => {
    it('personality None shows 미설정', () => {
        expect(getPersonalityName('None')).toBe('미설정');
    });

    it('personality null/undefined shows -', () => {
        expect(getPersonalityName(null)).toBe('-');
        expect(getPersonalityName(undefined)).toBe('-');
    });
});
