import { describe, it, expect } from 'vitest';
import { parseLogEvents, buildPhasesFromLogs } from './BattleDataParser';

describe('parseLogEvents', () => {
    it('퇴각 → includes retreat', () => {
        const events = parseLogEvents('퇴각하였다');
        expect(events).toContain('retreat');
    });

    it('점령 → includes city_occupied', () => {
        const events = parseLogEvents('성을 점령하였다');
        expect(events).toContain('city_occupied');
    });

    it('empty log → empty events', () => {
        const events = parseLogEvents('');
        expect(events).toHaveLength(0);
    });

    it('unrelated log → empty events', () => {
        const events = parseLogEvents('일반 전투');
        expect(events).toHaveLength(0);
    });
});

describe('buildPhasesFromLogs', () => {
    it('returns correct number of phases', () => {
        const phases = buildPhasesFromLogs(['로그1', '로그2', '로그3'], 1000, 800, 3);
        expect(phases).toHaveLength(3);
    });

    it('phase numbers are sequential starting from 1', () => {
        const phases = buildPhasesFromLogs([], 1000, 800, 3);
        expect(phases[0].phaseNumber).toBe(1);
        expect(phases[1].phaseNumber).toBe(2);
        expect(phases[2].phaseNumber).toBe(3);
    });

    it('HP decreases over phases', () => {
        const phases = buildPhasesFromLogs([], 1000, 800, 4);
        expect(phases[0].attackerHpBefore).toBeGreaterThanOrEqual(phases[phases.length - 1].attackerHpAfter);
        expect(phases[0].defenderHpBefore).toBeGreaterThanOrEqual(phases[phases.length - 1].defenderHpAfter);
    });

    it('parses events from logs in each phase', () => {
        const phases = buildPhasesFromLogs(['퇴각하였다', '정상전투'], 1000, 800, 2);
        expect(phases[0].events).toContain('retreat');
        expect(phases[1].events).not.toContain('retreat');
    });
});
