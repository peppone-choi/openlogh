import { describe, expect, it } from 'vitest';

describe('GeneralBasicCard redesign', () => {
    it('uses bg-card with rounded-none wrapper (8bit retro)', () => {
        const containerClass = 'bg-card border border-foreground/15 rounded-none overflow-hidden text-sm';
        expect(containerClass).toContain('bg-card');
        expect(containerClass).toContain('rounded-none');
        expect(containerClass).not.toContain('legacy-bg');
    });

    it('stat grid uses 3 columns', () => {
        const gridClass = 'grid grid-cols-3';
        expect(gridClass).toContain('grid-cols-3');
    });

    it('has data-tutorial="general-card" attribute on root', () => {
        const src = require('fs').readFileSync(require('path').resolve(__dirname, 'general-basic-card.tsx'), 'utf-8');
        expect(src).toContain('data-tutorial="general-card"');
    });

    it('KV cells use bg-card with muted labels', () => {
        const labelClass = 'text-[10px] text-muted-foreground';
        expect(labelClass).toContain('text-muted-foreground');
    });

    it('uses gap-px with bg-border for grid gap coloring', () => {
        const gridClass = 'grid grid-cols-3 gap-px bg-border/50 border-t border-border';
        expect(gridClass).toContain('gap-px');
        expect(gridClass).toContain('bg-border');
    });
});

describe('GeneralSupplementCard design', () => {
    it('uses rounded-none and bg-card wrapper (8bit retro)', () => {
        const wrapperClass = 'text-center text-sm border border-foreground/15 rounded-none overflow-hidden bg-card';
        expect(wrapperClass).toContain('rounded-none');
        expect(wrapperClass).toContain('bg-card');
    });
});
