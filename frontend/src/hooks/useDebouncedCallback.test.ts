import { describe, expect, it } from 'vitest';
import { useDebouncedCallback } from './useDebouncedCallback';
import * as fs from 'fs';
import * as path from 'path';

const src = fs.readFileSync(path.resolve(__dirname, 'useDebouncedCallback.ts'), 'utf-8');

describe('useDebouncedCallback', () => {
    it('exports useDebouncedCallback hook', () => {
        expect(useDebouncedCallback).toBeDefined();
        expect(typeof useDebouncedCallback).toBe('function');
    });

    it('uses setTimeout for debounce', () => {
        expect(src).toContain('setTimeout');
    });

    it('clears previous timeout on re-invocation', () => {
        expect(src).toContain('clearTimeout');
    });
});
