import { describe, it, expect } from 'vitest';
import { MobileMenuSheet } from './mobile-menu-sheet';

describe('MobileMenuSheet', () => {
    it('exports MobileMenuSheet component', () => {
        expect(MobileMenuSheet).toBeDefined();
        expect(typeof MobileMenuSheet).toBe('function');
    });
});
