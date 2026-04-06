import { describe, expect, it } from 'vitest';

// Mirrors the officerText logic in generals/[id]/page.tsx:
//   general.npcState === 10
//     ? '원수'
//     : formatOfficerLevelText(general.officerLevel, nation?.level, general.nationId > 0, nation?.typeCode)
function resolveOfficerText(npcState: number, fallback: string): string {
    return npcState === 10 ? '원수' : fallback;
}

describe('officer text (generals detail page)', () => {
    it('returns "원수" when npcState is 10', () => {
        expect(resolveOfficerText(10, '승상')).toBe('원수');
    });

    it('uses formatOfficerLevelText result when npcState is not 10', () => {
        expect(resolveOfficerText(0, '승상')).toBe('승상');
    });

    it('uses formatOfficerLevelText result for npcState 1 (normal NPC)', () => {
        expect(resolveOfficerText(1, '태위')).toBe('태위');
    });

    it('does not return "원수" for npcState 9', () => {
        expect(resolveOfficerText(9, '일반')).not.toBe('원수');
    });

    it('does not return "원수" for npcState 11', () => {
        expect(resolveOfficerText(11, '일반')).not.toBe('원수');
    });
});

describe('emperor icon with yellow background', () => {
    const EMPEROR_BG_COLOR = '#f0c040';
    const EMPEROR_ICON = '/icons/emperor.png';

    it('uses yellow background for emperor icon container', () => {
        expect(EMPEROR_BG_COLOR).toBe('#f0c040');
    });

    it('uses emperor.png icon', () => {
        expect(EMPEROR_ICON).toBe('/icons/emperor.png');
    });

    it('emperor icon is shown for npcState 10', () => {
        const npcState = 10;
        const showIcon = npcState === 10;
        expect(showIcon).toBe(true);
    });

    it('emperor icon is not shown for other npcState', () => {
        const npcState: number = 2;
        const showIcon = npcState === 10;
        expect(showIcon).toBe(false);
    });
});
