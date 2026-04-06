import { describe, expect, it } from 'vitest';
import { metadata } from './layout';

describe('root layout metadata', () => {
    it('has title', () => {
        expect(metadata.title).toBe('오픈은하영웅전설');
    });

    it('has favicon and apple-touch-icon', () => {
        expect(metadata.icons).toEqual({
            icon: '/favicon.ico',
            apple: '/icons/favicon-192.png',
        });
    });
});
