import { describe, it, expect } from 'vitest';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from './sheet';

describe('Sheet', () => {
    it('exports Sheet components', () => {
        expect(Sheet).toBeDefined();
        expect(SheetContent).toBeDefined();
        expect(SheetHeader).toBeDefined();
        expect(SheetTitle).toBeDefined();
    });
});
