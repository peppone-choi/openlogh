// Phase 14 Plan 14-16 — InfoPanel NPC mission objective tests (D-36, D-37).
//
// Follows the 14-09 CommandRangeCircle test pattern: vitest config uses
// `environment: 'node'` so we DO NOT mount <InfoPanel />. Instead we:
//   1. Test the exported pure helper `resolveMissionObjectiveLabel`.
//   2. Source-text regression guards — the Korean copy + DTO field reads
//      must be present in InfoPanel.tsx source.

import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { resolveMissionObjectiveLabel } from './InfoPanel';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const panelSource = readFileSync(join(__dirname, 'InfoPanel.tsx'), 'utf-8');

describe('InfoPanel NPC mission objective (D-36, D-37)', () => {
    describe('resolveMissionObjectiveLabel — pure helper', () => {
        it('maps CONQUEST to 점령 (UI-SPEC Operation badge labels)', () => {
            expect(resolveMissionObjectiveLabel('CONQUEST')).toBe('점령');
        });
        it('maps DEFENSE to 방어', () => {
            expect(resolveMissionObjectiveLabel('DEFENSE')).toBe('방어');
        });
        it('maps SWEEP to 소탕', () => {
            expect(resolveMissionObjectiveLabel('SWEEP')).toBe('소탕');
        });
        it('returns the raw string for unknown objectives (forward-compat)', () => {
            expect(resolveMissionObjectiveLabel('CUSTOM_FUTURE')).toBe('CUSTOM_FUTURE');
        });
        it('returns null for null / undefined / empty string', () => {
            expect(resolveMissionObjectiveLabel(null)).toBeNull();
            expect(resolveMissionObjectiveLabel(undefined)).toBeNull();
            expect(resolveMissionObjectiveLabel('')).toBeNull();
        });
    });

    describe('InfoPanel source regression guard (Korean copy + DTO reads)', () => {
        it('contains the Korean "현재 목적" label (UI-SPEC NPC objective row)', () => {
            expect(panelSource).toContain('현재 목적');
        });

        it('contains the Korean "목표" label for the target system row', () => {
            expect(panelSource).toMatch(/"목표"|'목표'/);
        });

        it('contains at least one of 점령 / 방어 / 소탕 (mission objective labels)', () => {
            expect(panelSource).toMatch(/점령|방어|소탕/);
        });

        it('reads unit.isNpc to gate the NPC rows (D-35 / D-36)', () => {
            expect(panelSource).toMatch(/isNpc/);
        });

        it('reads unit.missionObjective to gate the mission rows (D-37)', () => {
            expect(panelSource).toMatch(/missionObjective/);
        });

        it('reads unit.targetStarSystemId for the "목표" row (D-37, optional DTO field)', () => {
            expect(panelSource).toMatch(/targetStarSystemId/);
        });

        it('uses useGalaxyStore.getSystem to resolve target system name', () => {
            expect(panelSource).toMatch(/useGalaxyStore/);
            expect(panelSource).toMatch(/getSystem/);
        });

        it('accepts a selectedUnit prop (separate from myOfficerId lookup)', () => {
            expect(panelSource).toMatch(/selectedUnit/);
        });
    });
});
