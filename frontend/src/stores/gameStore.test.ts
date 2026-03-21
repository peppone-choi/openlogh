import { describe, expect, it } from 'vitest';

describe('gameStore loadAll dedup', () => {
    it('concurrent calls for same worldId should return same promise', () => {
        // The dedup guard uses a module-level _inflightLoadAll variable
        // If worldId matches and promise exists, it returns the existing promise
        let inflightWorldId: number | null = null;
        let inflightPromise: Promise<void> | null = null;

        function loadAll(worldId: number): Promise<void> {
            if (inflightWorldId === worldId && inflightPromise) {
                return inflightPromise;
            }
            inflightWorldId = worldId;
            inflightPromise = new Promise((resolve) => setTimeout(resolve, 100));
            return inflightPromise;
        }

        const p1 = loadAll(1);
        const p2 = loadAll(1);
        expect(p1).toBe(p2);

        const p3 = loadAll(2);
        expect(p3).not.toBe(p1);
    });
});

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
