// @vitest-environment node
import { describe, it, expect } from 'vitest';
import { readFileSync } from 'fs';
import { resolve } from 'path';

const battleLogEntrySrc = readFileSync(
    resolve(__dirname, 'battle-log-entry.tsx'),
    'utf-8',
);
const recordZoneSrc = readFileSync(
    resolve(__dirname, 'record-zone.tsx'),
    'utf-8',
);

describe('battle-log-entry.tsx source scan', () => {
    it('contains small_war_log detection string', () => {
        expect(battleLogEntrySrc).toContain('small_war_log');
    });

    it('imports formatLog from formatLog for color-tag fallback', () => {
        expect(battleLogEntrySrc).toContain('formatLog');
    });

    it('imports from formatBattleLog', () => {
        expect(battleLogEntrySrc).toMatch(/from\s+['"]@\/lib\/formatBattleLog['"]/);
    });

    it('exports BattleLogEntry component', () => {
        expect(battleLogEntrySrc).toContain('export function BattleLogEntry');
    });
});

describe('record-zone.tsx wiring', () => {
    it('imports BattleLogEntry from battle-log-entry', () => {
        expect(recordZoneSrc).toContain('BattleLogEntry');
    });

    it('imports isBattleLogHtml from formatBattleLog', () => {
        expect(recordZoneSrc).toContain('isBattleLogHtml');
    });
});
