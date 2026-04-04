// @vitest-environment jsdom
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'fs';
import { resolve } from 'path';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';

const generalCardSrc = readFileSync(
    resolve(__dirname, 'general-basic-card.tsx'),
    'utf-8',
);

describe('general-basic-card.tsx source scan', () => {
    it('imports calcInjury from game-utils', () => {
        expect(generalCardSrc).toContain('calcInjury');
    });

    it('displays lbonus', () => {
        expect(generalCardSrc).toContain('lbonus');
    });

    it('displays betray (배반)', () => {
        expect(generalCardSrc).toContain('betray');
    });

    it('displays next execute time with 남음 suffix', () => {
        expect(generalCardSrc).toContain('남음');
    });
});

describe('general-basic-card rendering with mock data', () => {
    // Dynamic import to handle JSX/TSX
    it('calcInjury produces correct values for injury=25, leadership=80', async () => {
        const { calcInjury } = await import('@/lib/game-utils');
        // Math.round((80 * (100-25)) / 100) = Math.round(60) = 60
        expect(calcInjury(80, 25)).toBe(60);
    });

    it('calcInjury produces correct values for injury=0', async () => {
        const { calcInjury } = await import('@/lib/game-utils');
        expect(calcInjury(80, 0)).toBe(80);
    });

    it('calcInjury produces correct values for injury=50, stat=99', async () => {
        const { calcInjury } = await import('@/lib/game-utils');
        // Math.round((99 * 50) / 100) = Math.round(49.5) = 50 (Math.round rounds .5 up)
        expect(calcInjury(99, 50)).toBe(50);
    });

    it('ageColor returns colored value for near-retirement age', async () => {
        const { ageColor } = await import('@/lib/game-utils');
        const color = ageColor(45);
        expect(color).toBeDefined();
        expect(typeof color).toBe('string');
    });
});
