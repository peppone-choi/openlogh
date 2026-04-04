import { describe, expect, it } from 'vitest';

// Mirrors the imperialStatus badge logic in emperor/page.tsx and emperor/detail/page.tsx:
//   emperorNation.meta?.imperialStatus === 'regent' ? '협천자' : '황제국'
function resolveEmperorBadge(imperialStatus: string | undefined): string {
    return imperialStatus === 'regent' ? '협천자' : '황제국';
}

// Mirrors the emperorNation finder logic:
//   nations.find(n => n.level >= 9 || n.meta?.imperialStatus === 'emperor' || n.meta?.imperialStatus === 'regent')
function isEmperorNation(level: number, imperialStatus?: string): boolean {
    return level >= 9 || imperialStatus === 'emperor' || imperialStatus === 'regent';
}

describe('emperor page badge (imperialStatus)', () => {
    it('shows 협천자 for regent status', () => {
        expect(resolveEmperorBadge('regent')).toBe('협천자');
    });

    it('shows 황제국 for emperor status', () => {
        expect(resolveEmperorBadge('emperor')).toBe('황제국');
    });

    it('shows 황제국 for undefined status', () => {
        expect(resolveEmperorBadge(undefined)).toBe('황제국');
    });

    it('shows 황제국 for independent status', () => {
        expect(resolveEmperorBadge('independent')).toBe('황제국');
    });
});

describe('emperor nation finder', () => {
    it('matches nation with level >= 9', () => {
        expect(isEmperorNation(9, 'independent')).toBe(true);
    });

    it('matches nation with emperor status', () => {
        expect(isEmperorNation(5, 'emperor')).toBe(true);
    });

    it('matches nation with regent status', () => {
        expect(isEmperorNation(5, 'regent')).toBe(true);
    });

    it('does not match independent nation below level 9', () => {
        expect(isEmperorNation(8, 'independent')).toBe(false);
    });

    it('does not match nation with no status below level 9', () => {
        expect(isEmperorNation(7, undefined)).toBe(false);
    });
});
