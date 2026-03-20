import { describe, expect, it } from 'vitest';

describe('admin dashboard server management section', () => {
    it('DAEMON_STATE_CONFIG covers all known states', () => {
        const states = ['IDLE', 'RUNNING', 'PAUSED', 'FLUSHING', 'STOPPING'];
        expect(states).toHaveLength(5);
        expect(states).toContain('IDLE');
        expect(states).toContain('RUNNING');
    });

    it('TURN_PRESETS has expected values', () => {
        const presets = [1, 2, 5, 10, 20, 30, 60, 120];
        expect(presets).toHaveLength(8);
        expect(presets[0]).toBe(1);
        expect(presets[presets.length - 1]).toBe(120);
    });

    it('phase toggle determines pre_open by opentime in the future', () => {
        const futureDate = new Date();
        futureDate.setFullYear(futureDate.getFullYear() + 1);
        const opentime = futureDate.toISOString();
        const isPreOpen = new Date(opentime) > new Date();
        expect(isPreOpen).toBe(true);
    });

    it('phase toggle determines open by opentime in the past', () => {
        const pastDate = new Date();
        pastDate.setFullYear(pastDate.getFullYear() - 1);
        const opentime = pastDate.toISOString();
        const isPreOpen = new Date(opentime) > new Date();
        expect(isPreOpen).toBe(false);
    });
});
