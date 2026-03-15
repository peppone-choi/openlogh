import { describe, it, expect } from 'vitest';
import { useIsMobile } from './use-mobile';

describe('useIsMobile', () => {
    it('exports useIsMobile hook', () => {
        expect(useIsMobile).toBeDefined();
        expect(typeof useIsMobile).toBe('function');
    });
});
