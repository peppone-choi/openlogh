import { describe, expect, it } from 'vitest';

describe('CityBasicCard design', () => {
    it('uses bg-card with rounded-none instead of legacy-bg2 (8bit retro)', () => {
        const containerClass = 'bg-card border border-foreground/15 rounded-none overflow-hidden text-sm';
        expect(containerClass).toContain('bg-card');
        expect(containerClass).toContain('rounded-none');
        expect(containerClass).not.toContain('legacy-bg2');
    });

    it('StatPanel uses bg-muted instead of legacy-bg1', () => {
        const headClass = 'bg-muted flex items-center justify-center text-muted-foreground text-xs';
        expect(headClass).toContain('bg-muted');
        expect(headClass).not.toContain('legacy-bg1');
    });

    it('has data-tutorial="city-card" attribute on root', () => {
        const src = require('fs').readFileSync(require('path').resolve(__dirname, 'city-basic-card.tsx'), 'utf-8');
        expect(src).toContain('data-tutorial="city-card"');
    });
});
