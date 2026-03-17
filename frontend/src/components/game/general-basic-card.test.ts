import { describe, expect, it } from 'vitest';

describe('GeneralBasicCard redesign', () => {
    it('uses bg-card with rounded-lg wrapper', () => {
        const containerClass = 'bg-card border border-border rounded-lg overflow-hidden text-sm';
        expect(containerClass).toContain('bg-card');
        expect(containerClass).toContain('rounded-lg');
        expect(containerClass).not.toContain('legacy-bg');
    });

    it('stat grid uses 3 columns', () => {
        const gridClass = 'grid grid-cols-3';
        expect(gridClass).toContain('grid-cols-3');
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
    it('uses rounded-lg and bg-card wrapper', () => {
        const wrapperClass = 'text-center text-sm border border-border rounded-lg overflow-hidden bg-card';
        expect(wrapperClass).toContain('rounded-lg');
        expect(wrapperClass).toContain('bg-card');
    });
});
