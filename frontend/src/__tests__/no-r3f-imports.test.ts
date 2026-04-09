// Regression guard for D-25: Phase 14 removes R3F (@react-three/*, three)
// and keeps Konva 2D as the single tactical renderer. Plan 14-08 flipped
// this guard from Wave 0 tolerance mode to strict assertion after deleting
// the 4 R3F source files and removing the 4 @react-three/* + three deps.
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
        // Exclude this file itself — the string literals below would otherwise
        // cause the guard to flag its own test.
        if (rel.includes('__tests__/no-r3f-imports')) continue;
        const content = readFileSync(f, 'utf8');
        if (/@react-three\//.test(content) || /from ['"]three['"]/.test(content)) {
            offenders.push(rel);
        }
    }
    return offenders;
}

describe('R3F regression guard (D-25)', () => {
    it('no file under frontend/src imports @react-three/* or three', () => {
        const srcRoot = join(__dirname, '..');
        const offenders = findR3fOffenders(srcRoot);
        expect(offenders).toEqual([]);
    });

    it('package.json does not list @react-three or three', () => {
        // __dirname = frontend/src/__tests__ → ../../package.json = frontend/package.json
        const pkg = JSON.parse(
            readFileSync(join(__dirname, '..', '..', 'package.json'), 'utf8')
        ) as { dependencies?: Record<string, string>; devDependencies?: Record<string, string> };
        const allDeps = { ...pkg.dependencies, ...pkg.devDependencies };
        expect(allDeps['@react-three/fiber']).toBeUndefined();
        expect(allDeps['@react-three/drei']).toBeUndefined();
        expect(allDeps['three']).toBeUndefined();
        expect(allDeps['@types/three']).toBeUndefined();
    });
});
