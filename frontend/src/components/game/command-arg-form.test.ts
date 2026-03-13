import { describe, expect, it } from 'vitest';

const LEGACY_AVAILABLE_NATION_TYPES = [
    'che_도적',
    'che_명가',
    'che_음양가',
    'che_종횡가',
    'che_불가',
    'che_오두미도',
    'che_태평도',
    'che_도가',
    'che_묵가',
    'che_덕가',
    'che_병가',
    'che_유가',
    'che_법가',
];

describe('nation type options legacy parity', () => {
    it('has exactly 13 nation types matching legacy GameConstBase', () => {
        expect(LEGACY_AVAILABLE_NATION_TYPES).toHaveLength(13);
    });

    it('all values use che_ prefix', () => {
        for (const t of LEGACY_AVAILABLE_NATION_TYPES) {
            expect(t).toMatch(/^che_/);
        }
    });

    it('includes key legacy types 유가, 오두미도, 태평도, 병가, 법가', () => {
        expect(LEGACY_AVAILABLE_NATION_TYPES).toContain('che_유가');
        expect(LEGACY_AVAILABLE_NATION_TYPES).toContain('che_오두미도');
        expect(LEGACY_AVAILABLE_NATION_TYPES).toContain('che_태평도');
        expect(LEGACY_AVAILABLE_NATION_TYPES).toContain('che_병가');
        expect(LEGACY_AVAILABLE_NATION_TYPES).toContain('che_법가');
    });

    it('does NOT include non-legacy types (군벌, 문치, 무치)', () => {
        expect(LEGACY_AVAILABLE_NATION_TYPES).not.toContain('군벌');
        expect(LEGACY_AVAILABLE_NATION_TYPES).not.toContain('che_군벌');
        expect(LEGACY_AVAILABLE_NATION_TYPES).not.toContain('문치');
        expect(LEGACY_AVAILABLE_NATION_TYPES).not.toContain('무치');
    });
});
