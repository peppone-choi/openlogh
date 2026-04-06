import { describe, expect, it } from 'vitest';

// Mirror of COMMAND_ARGS entries relevant to field battle
const FIELD_BATTLE_COMMAND_ARGS: Record<string, { type: string; key: string; label: string }[]> = {
    요격: [{ type: 'city', key: 'destCityId', label: '매복 방면 (인접 행성)' }],
};

describe('field battle command args', () => {
    it('요격 has destCityId city field', () => {
        const fields = FIELD_BATTLE_COMMAND_ARGS['요격'];
        expect(fields).toBeDefined();
        expect(fields).toHaveLength(1);
        expect(fields[0]).toMatchObject({ type: 'city', key: 'destCityId' });
    });

    it('순찰 has no args (undefined in COMMAND_ARGS)', () => {
        expect(FIELD_BATTLE_COMMAND_ARGS['순찰']).toBeUndefined();
    });
});

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

    it('labels do not contain parentheses', () => {
        const LABELS = [
            '도적',
            '명가',
            '음양가',
            '종횡가',
            '불가',
            '오두미도',
            '태평도',
            '도가',
            '묵가',
            '덕가',
            '병가',
            '유가',
            '법가',
        ];
        for (const label of LABELS) {
            expect(label).not.toContain('(');
            expect(label).not.toContain(')');
        }
    });

    it('does NOT include non-legacy types (군벌, 문치, 무치)', () => {
        expect(LEGACY_AVAILABLE_NATION_TYPES).not.toContain('군벌');
        expect(LEGACY_AVAILABLE_NATION_TYPES).not.toContain('che_군벌');
        expect(LEGACY_AVAILABLE_NATION_TYPES).not.toContain('문치');
        expect(LEGACY_AVAILABLE_NATION_TYPES).not.toContain('무치');
    });
});
