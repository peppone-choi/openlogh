import { describe, expect, it } from 'vitest';

// Mirrors the officerText logic in generals/[id]/page.tsx:
//   general.npcState === 10
//     ? '황제'
//     : formatOfficerLevelText(general.officerLevel, nation?.level, general.nationId > 0, nation?.typeCode)
function resolveOfficerText(npcState: number, fallback: string): string {
    return npcState === 10 ? '황제' : fallback;
}

describe('officer text (generals detail page)', () => {
    it('returns "황제" when npcState is 10', () => {
        expect(resolveOfficerText(10, '승상')).toBe('황제');
    });

    it('uses formatOfficerLevelText result when npcState is not 10', () => {
        expect(resolveOfficerText(0, '승상')).toBe('승상');
    });

    it('uses formatOfficerLevelText result for npcState 1 (normal NPC)', () => {
        expect(resolveOfficerText(1, '태위')).toBe('태위');
    });

    it('does not return "황제" for npcState 9', () => {
        expect(resolveOfficerText(9, '일반')).not.toBe('황제');
    });

    it('does not return "황제" for npcState 11', () => {
        expect(resolveOfficerText(11, '일반')).not.toBe('황제');
    });
});
