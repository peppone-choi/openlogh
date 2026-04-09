// Regression guard for D-25: Phase 14 removes R3F (@react-three/*) and
// keeps Konva 2D as the single tactical renderer. Until plan 14-08 removes
// all legacy files, this scaffold tolerates existing offenders and simply
// asserts the scanner runs. Plan 14-08 will flip the assertion to
// `expect(offenders).toHaveLength(0)`.
//
// Uses Node's built-in fs (no external `glob` dependency) so the test works
// under vitest env=node without added installs.

import { describe, it, expect } from 'vitest';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

function walk(dir: string, acc: string[] = []): string[] {
    for (const entry of readdirSync(dir)) {
        const full = join(dir, entry);
        const st = statSync(full);
        if (st.isDirectory()) {
            // Skip node_modules, build artifacts, and this very test dir so we
            // never match ourselves by accident.
            if (entry === 'node_modules' || entry === '.next' || entry === 'dist') continue;
            walk(full, acc);
        } else if (/\.(ts|tsx)$/.test(entry)) {
            acc.push(full);
        }
    }
    return acc;
}

function findR3fOffenders(srcRoot: string): string[] {
    const files = walk(srcRoot);
    const offenders: string[] = [];
    for (const f of files) {
        const rel = relative(srcRoot, f);
        // Exclude this file itself — the string literal below would otherwise
        // cause the guard to flag its own test.
        if (rel.includes('__tests__/no-r3f-imports')) continue;
        const content = readFileSync(f, 'utf8');
        if (/@react-three\//.test(content)) {
            offenders.push(rel);
        }
    }
    return offenders;
}

describe('R3F regression guard (D-25)', () => {
    it('scanner runs and produces a defined offender list (Wave 0 tolerance)', () => {
        const srcRoot = join(__dirname, '..');
        const offenders = findR3fOffenders(srcRoot);
        // Wave 0: scaffold only — existing files may still import @react-three/*.
        // Plan 14-08 will remove those files and flip the assertion below.
        expect(Array.isArray(offenders)).toBe(true);
    });

    it.skip('WILL assert zero offenders after 14-08 removes R3F', () => {
        // Implemented in 14-08 — will replace the above test body with
        // `expect(offenders).toHaveLength(0)` after TacticalMapR3F and
        // BattleCloseViewScene are deleted.
        expect(true).toBe(true);
    });
});
