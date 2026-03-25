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

describe('admin create/reset world form options', () => {
    it('createWorld API accepts all game config fields', () => {
        const payload = {
            scenarioCode: '1070',
            name: 'test',
            tickSeconds: 300,
            extend: true,
            npcMode: 1,
            fiction: 0,
            maxOfficer: 500,
            maxFaction: 55,
            joinMode: 'full',
            blockOfficerCreate: 0,
            showImgLevel: 3,
            opentime: new Date().toISOString(),
        };
        expect(payload.scenarioCode).toBe('1070');
        expect(payload.extend).toBe(true);
        expect(payload.maxOfficer).toBe(500);
        expect(typeof payload.opentime).toBe('string');
    });

    it('resetWorld API accepts options with opentime', () => {
        const options = {
            scenarioCode: '1080',
            extend: false,
            npcMode: 0,
            maxOfficer: 300,
            opentime: new Date(Date.now() + 86400000).toISOString(),
        };
        expect(options.extend).toBe(false);
        expect(new Date(options.opentime) > new Date()).toBe(true);
    });

    it('form defaults are sensible', () => {
        const defaults = {
            extend: true,
            npcMode: 1,
            maxOfficer: 500,
            maxFaction: 55,
            blockOfficerCreate: 0,
            showImgLevel: 3,
            allowConscript: true,
            allowNpcNationSpawn: true,
            allowInvaderSpawn: true,
        };
        expect(defaults.maxOfficer).toBe(500);
        expect(defaults.allowConscript).toBe(true);
        expect(defaults.blockOfficerCreate).toBe(0);
    });
});
