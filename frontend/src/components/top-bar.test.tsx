import { describe, it, expect } from 'vitest';
import { TopBar } from './top-bar';

describe('TopBar', () => {
    it('exports TopBar component', () => {
        expect(TopBar).toBeDefined();
        expect(typeof TopBar).toBe('function');
    });
});
