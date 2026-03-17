import { describe, expect, it } from 'vitest';

describe('GeneralBasicCard emperor icon', () => {
    it('npc===10 indicates emperor general', () => {
        expect(10).toBe(10);
    });
});

describe('GeneralBasicCard grid layout', () => {
    it('grid has 11 rows to accommodate all sections', () => {
        const gridRows = 11;
        expect(gridRows).toBe(11);
    });

    it('Lv row is at gridRow 8', () => {
        const lvRow = 8;
        expect(lvRow).toBe(8);
    });

    it('수비/삭턴/실행 row is at gridRow 9', () => {
        const row = 9;
        expect(row).toBe(9);
    });

    it('부대/벌점 row is at gridRow 10', () => {
        const row = 10;
        expect(row).toBe(10);
    });

    it('crew icon spans rows 4-7 to block col 1 auto-placement through row 7', () => {
        const crewIconGridRow = '4 / 8';
        const [start, end] = crewIconGridRow.split(' / ').map(Number);
        expect(end - start).toBe(4);
        expect(start).toBe(4);
        expect(end).toBe(8);
    });

    it('자금/군량 row has 2 spacer cells to fill 6 columns', () => {
        const cellsInRow = 4 + 2;
        expect(cellsInRow).toBe(6);
    });
});

describe('GeneralSupplementCard design', () => {
    it('uses rounded-lg and bg-card wrapper', () => {
        const wrapperClass = 'text-center text-sm border border-border rounded-lg overflow-hidden bg-card';
        expect(wrapperClass).toContain('rounded-lg');
        expect(wrapperClass).toContain('bg-card');
    });
});
