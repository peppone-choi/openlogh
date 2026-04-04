import { describe, expect, it } from 'vitest';
import type { SelectPoolEntry } from '@/types';

describe('SelectPool admin page types', () => {
    it('SelectPoolEntry has required fields', () => {
        const entry: SelectPoolEntry = {
            id: 1,
            worldId: 1,
            uniqueName: 'pool-1',
            ownerId: null,
            generalId: null,
            reservedUntil: null,
            info: { generalName: '조조', leadership: 96, strength: 72, intel: 91, politics: 93, charm: 96 },
            createdAt: '2026-01-01T00:00:00Z',
        };
        expect(entry.uniqueName).toBe('pool-1');
        expect(entry.info.generalName).toBe('조조');
        expect(entry.info.leadership).toBe(96);
    });

    it('available pool entry has null generalId and ownerId', () => {
        const entry: SelectPoolEntry = {
            id: 2,
            worldId: 1,
            uniqueName: 'pool-2',
            ownerId: null,
            generalId: null,
            reservedUntil: null,
            info: {},
            createdAt: '2026-01-01T00:00:00Z',
        };
        expect(entry.generalId).toBeNull();
        expect(entry.ownerId).toBeNull();
    });

    it('picked pool entry has generalId set', () => {
        const entry: SelectPoolEntry = {
            id: 3,
            worldId: 1,
            uniqueName: 'pool-3',
            ownerId: 42,
            generalId: 100,
            reservedUntil: '2026-01-01T00:10:00Z',
            info: {},
            createdAt: '2026-01-01T00:00:00Z',
        };
        expect(entry.generalId).toBe(100);
    });
});
