import { MAX_VISIBLE_UNITS, UNIT_SPACING, FORMATION_COLS } from '@/lib/battle3d-constants';

export function easeInOutQuad(t: number): number {
    return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
}

export function easeOutBack(t: number): number {
    const c1 = 1.70158;
    const c3 = c1 + 1;
    return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
}

export function lerp(a: number, b: number, t: number): number {
    return a + (b - a) * t;
}

export function lerpVec3(
    a: [number, number, number],
    b: [number, number, number],
    t: number
): [number, number, number] {
    return [lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)];
}

export function getVisibleUnitCount(crew: number): number {
    if (crew <= 100) return 3;
    if (crew <= 500) return Math.min(Math.ceil(crew / 50), 10);
    return Math.min(Math.ceil(crew / 100), MAX_VISIBLE_UNITS);
}

export function calculateFormationPositions(
    count: number,
    startX: number,
    facing: 'right' | 'left'
): [number, number, number][] {
    const result: [number, number, number][] = [];
    for (let i = 0; i < count; i++) {
        const col = i % FORMATION_COLS;
        const row = Math.floor(i / FORMATION_COLS);
        const colCount = Math.min(FORMATION_COLS, count - row * FORMATION_COLS);
        const rowWidth = (colCount - 1) * UNIT_SPACING;
        const xStep = facing === 'right' ? row * UNIT_SPACING : -row * UNIT_SPACING;
        const x = startX + xStep;
        const z = col * UNIT_SPACING - rowWidth / 2;
        result.push([x, 0, z]);
    }
    return result;
}

export function isWebGLSupported(): boolean {
    if (typeof window === 'undefined') return false;
    try {
        const canvas = document.createElement('canvas');
        return !!(canvas.getContext('webgl2') || canvas.getContext('webgl'));
    } catch {
        return false;
    }
}
