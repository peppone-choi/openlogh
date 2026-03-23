import { describe, it, expect } from 'vitest';
import { ResponsiveSheet } from './responsive-sheet';

describe('ResponsiveSheet', () => {
    it('exports ResponsiveSheet component', () => {
        expect(ResponsiveSheet).toBeDefined();
        expect(typeof ResponsiveSheet).toBe('function');
    });
});
