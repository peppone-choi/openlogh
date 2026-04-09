// Phase 14 Plan 14-16 — TacticalUnitIcon status-marker tests (D-35, UI-SPEC Section H).
//
// Vitest runs in a `node` environment with no react-konva mount path, so we
// follow the 14-09 CommandRangeCircle test pattern:
//   1. Test the exported pure helper `computeStatusMarker(unit)` — all visual
//      decisions (shape, color, symbol, tooltip copy) are driven by this helper.
//   2. Source-text regression guard — the 3 D-35 hex literals must appear in
//      TacticalUnitIcon.tsx source; the 🤖 glyph must appear exactly once; the
//      icon opacity must not gate on isOnline (per D-35 — opacity reserved for
//      destruction signal).

import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { computeStatusMarker } from './TacticalUnitIcon';
import type { TacticalUnit } from '@/types/tactical';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const iconSource = readFileSync(join(__dirname, 'TacticalUnitIcon.tsx'), 'utf-8');

function baseUnit(overrides: Partial<TacticalUnit> = {}): TacticalUnit {
    return {
        fleetId: 100,
        officerId: 1000,
        officerName: 'Yang',
        factionId: 2,
        side: 'ATTACKER',
        posX: 500,
        posY: 500,
        hp: 100,
        maxHp: 100,
        ships: 300,
        maxShips: 300,
        training: 50,
        morale: 80,
        energy: { beam: 20, gun: 20, shield: 20, engine: 20, warp: 10, sensor: 10 },
        formation: 'MIXED',
        commandRange: 50,
        isAlive: true,
        isRetreating: false,
        retreatProgress: 0,
        unitType: 'battleship',
        ...overrides,
    };
}

describe('TacticalUnitIcon status marker (D-35, UI-SPEC Section H)', () => {
    describe('computeStatusMarker — pure helper', () => {
        it('defaults to ONLINE (filled green disc) when isOnline/isNpc omitted', () => {
            const marker = computeStatusMarker(baseUnit());
            expect(marker.variant).toBe('online');
            expect(marker.fill).toBe('#10b981');
            expect(marker.shape).toBe('filled-disc');
            expect(marker.glyph).toBeUndefined();
        });

        it('returns ONLINE marker for isOnline=true, isNpc=false', () => {
            const marker = computeStatusMarker(baseUnit({ isOnline: true, isNpc: false }));
            expect(marker.variant).toBe('online');
            expect(marker.fill).toBe('#10b981');
            expect(marker.shape).toBe('filled-disc');
            // D-35 tooltip copy from UI-SPEC copywriting
            expect(marker.tooltip).toBe('Yang — 접속 중');
        });

        it('returns OFFLINE marker (hollow ring #7a8599) for isOnline=false, isNpc=false', () => {
            const marker = computeStatusMarker(
                baseUnit({ isOnline: false, isNpc: false, officerName: 'Merkatz' }),
            );
            expect(marker.variant).toBe('offline');
            expect(marker.stroke).toBe('#7a8599');
            expect(marker.fill).toBe('transparent');
            expect(marker.shape).toBe('hollow-ring');
            expect(marker.tooltip).toBe('Merkatz — 오프라인 (AI 위임)');
        });

        it('returns NPC marker (🤖 #a78bfa) when isNpc=true regardless of online state', () => {
            // Online NPC
            const online = computeStatusMarker(
                baseUnit({ isNpc: true, isOnline: true, officerName: 'Poplan' }),
            );
            expect(online.variant).toBe('npc');
            expect(online.fill).toBe('#a78bfa');
            expect(online.glyph).toBe('🤖');
            expect(online.shape).toBe('glyph');
            expect(online.tooltip).toBe('Poplan — NPC');
            // Offline NPC — still renders NPC marker (NPC takes priority per D-35)
            const offline = computeStatusMarker(
                baseUnit({ isNpc: true, isOnline: false, officerName: 'Poplan' }),
            );
            expect(offline.variant).toBe('npc');
            expect(offline.glyph).toBe('🤖');
        });

        it('marker is always fully opaque (D-35 — opacity reserved for destruction)', () => {
            const online = computeStatusMarker(baseUnit({ isOnline: true }));
            const offline = computeStatusMarker(baseUnit({ isOnline: false }));
            const npc = computeStatusMarker(baseUnit({ isNpc: true }));
            expect(online.opacity).toBe(1);
            expect(offline.opacity).toBe(1);
            expect(npc.opacity).toBe(1);
        });
    });

    describe('TacticalUnitIcon source regression guard (D-35 hex literals + 🤖 glyph)', () => {
        it('contains the D-35 online color #10b981 at least once', () => {
            expect(iconSource).toMatch(/#10b981/);
        });

        it('contains the D-35 offline color #7a8599 at least once', () => {
            expect(iconSource).toMatch(/#7a8599/);
        });

        it('contains the D-35 NPC color #a78bfa at least once', () => {
            expect(iconSource).toMatch(/#a78bfa/);
        });

        it('contains the 🤖 glyph at least once (D-35 NPC marker)', () => {
            const matches = iconSource.match(/🤖/g) ?? [];
            expect(matches.length).toBeGreaterThanOrEqual(1);
        });

        it('mentions isOnline and isNpc as reads from the unit prop', () => {
            expect(iconSource).toMatch(/isOnline/);
            expect(iconSource).toMatch(/isNpc/);
        });

        it('does NOT gate the main icon Group opacity on isOnline (opacity reserved for destruction per D-35)', () => {
            // The existing opacity binding is `opacity={unit.isAlive ? 1 : 0.2}` — no isOnline reference allowed inside.
            // Regression guard: the substring `isOnline ? 1 : 0.2` or equivalent must not appear.
            expect(iconSource).not.toMatch(/isOnline\s*\?\s*1\s*:\s*0\.2/);
            expect(iconSource).not.toMatch(/!unit\.isOnline\s*\?\s*0\.2/);
        });
    });
});
