import { describe, it, expect } from 'vitest';

// Pure logic tests for MapTransition transition state calculations

function getOpacity(
    activeMode: '2d' | '3d',
    viewMode: '2d' | '3d',
    transitioning: boolean,
    target: '2d' | '3d'
): number {
    if (target === '2d') {
        return transitioning ? (viewMode === '2d' ? 1 : 0) : activeMode === '2d' ? 1 : 0;
    }
    return transitioning ? (viewMode === '3d' ? 1 : 0) : activeMode === '3d' ? 1 : 0;
}

function getScale(activeMode: '2d' | '3d', viewMode: '2d' | '3d', transitioning: boolean, target: '2d' | '3d'): number {
    if (target === '3d') {
        return (transitioning && viewMode === '3d') || activeMode === '3d' ? 1 : 0.95;
    }
    return (transitioning && viewMode === '2d') || activeMode === '2d' ? 1 : 0.95;
}

function shouldShow(activeMode: '2d' | '3d', transitioning: boolean, target: '2d' | '3d'): boolean {
    return activeMode === target || transitioning;
}

describe('MapTransition opacity logic', () => {
    it('shows 2D at full opacity when stable in 2D mode', () => {
        expect(getOpacity('2d', '2d', false, '2d')).toBe(1);
        expect(getOpacity('2d', '2d', false, '3d')).toBe(0);
    });

    it('shows 3D at full opacity when stable in 3D mode', () => {
        expect(getOpacity('3d', '3d', false, '3d')).toBe(1);
        expect(getOpacity('3d', '3d', false, '2d')).toBe(0);
    });

    it('fades 3D in and 2D out during 2D→3D transition', () => {
        // activeMode still '2d', viewMode already changed to '3d', transitioning=true
        expect(getOpacity('2d', '3d', true, '2d')).toBe(0);
        expect(getOpacity('2d', '3d', true, '3d')).toBe(1);
    });

    it('fades 2D in and 3D out during 3D→2D transition', () => {
        expect(getOpacity('3d', '2d', true, '2d')).toBe(1);
        expect(getOpacity('3d', '2d', true, '3d')).toBe(0);
    });
});

describe('MapTransition scale logic', () => {
    it('3D view scales to 1 when active', () => {
        expect(getScale('3d', '3d', false, '3d')).toBe(1);
    });

    it('3D view scales to 0.95 when inactive and not transitioning', () => {
        expect(getScale('2d', '2d', false, '3d')).toBe(0.95);
    });

    it('3D view scales to 1 while transitioning into 3D', () => {
        expect(getScale('2d', '3d', true, '3d')).toBe(1);
    });

    it('2D view scales to 1 when active', () => {
        expect(getScale('2d', '2d', false, '2d')).toBe(1);
    });
});

describe('MapTransition shouldShow logic', () => {
    it('shows both views during transition', () => {
        expect(shouldShow('2d', true, '2d')).toBe(true);
        expect(shouldShow('2d', true, '3d')).toBe(true);
    });

    it('shows only active view when stable', () => {
        expect(shouldShow('2d', false, '2d')).toBe(true);
        expect(shouldShow('2d', false, '3d')).toBe(false);
        expect(shouldShow('3d', false, '3d')).toBe(true);
        expect(shouldShow('3d', false, '2d')).toBe(false);
    });
});
