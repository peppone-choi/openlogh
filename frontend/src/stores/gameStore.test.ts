import { describe, expect, it } from 'vitest';

describe('gameStore loadAll timeout', () => {
    it('withTimeout rejects after specified ms', async () => {
        const withTimeout = <T>(promise: Promise<T>, ms = 10000): Promise<T> =>
            Promise.race([
                promise,
                new Promise<never>((_, reject) => setTimeout(() => reject(new Error('timeout')), ms)),
            ]);

        const slow = new Promise<string>((resolve) => setTimeout(() => resolve('done'), 500));
        await expect(withTimeout(slow, 50)).rejects.toThrow('timeout');
    });

    it('withTimeout resolves if promise finishes first', async () => {
        const withTimeout = <T>(promise: Promise<T>, ms = 10000): Promise<T> =>
            Promise.race([
                promise,
                new Promise<never>((_, reject) => setTimeout(() => reject(new Error('timeout')), ms)),
            ]);

        const fast = Promise.resolve('done');
        await expect(withTimeout(fast, 1000)).resolves.toBe('done');
    });
});
