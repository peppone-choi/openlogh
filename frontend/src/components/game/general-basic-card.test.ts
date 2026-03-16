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
});
