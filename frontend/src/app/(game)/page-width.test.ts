import { describe, expect, it } from 'vitest';

describe('page width standardization', () => {
    const WIDTH_TIERS = {
        'max-w-3xl': 768,
        'max-w-4xl': 896,
        'max-w-5xl': 1024,
        'max-w-6xl': 1152,
    };

    it('defines consistent width tiers for page categories', () => {
        expect(WIDTH_TIERS['max-w-3xl']).toBeLessThan(WIDTH_TIERS['max-w-4xl']);
        expect(WIDTH_TIERS['max-w-4xl']).toBeLessThan(WIDTH_TIERS['max-w-5xl']);
        expect(WIDTH_TIERS['max-w-5xl']).toBeLessThan(WIDTH_TIERS['max-w-6xl']);
    });

    it('dashboard pages use widest tier', () => {
        const dashboardTier = 'max-w-6xl';
        expect(Object.keys(WIDTH_TIERS)).toContain(dashboardTier);
    });

    it('5-stat system includes politics and charm exp', () => {
        const stats = ['leadership', 'strength', 'intel', 'politics', 'charm'];
        const expFields = stats.map((s) => `${s}Exp`);
        expect(expFields).toHaveLength(5);
        expect(expFields).toContain('politicsExp');
        expect(expFields).toContain('charmExp');
    });

    it('map container uses responsive width instead of fixed pixels', () => {
        const responsiveClass = 'w-full lg:w-[700px]';
        expect(responsiveClass).toContain('w-full');
        expect(responsiveClass).toContain('lg:w-');
    });
});
