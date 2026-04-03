// @vitest-environment node
import { describe, it, expect } from 'vitest';

// Sample battle log HTML from legacy template
const SAMPLE_BATTLE_HTML = '<div class="small_war_log"><span class="me"><span class="name_plate"><span class="crew_type">궁</span><span class="name_plate_cover">【<span class="name">관우</span>】</span></span><span class="crew_plate"><span class="remain_crew">8500</span><span class="killed_plate">(<span class="killed_crew">1500</span>)</span></span></span><span class="war_type war_type_attack">→</span><span class="you"><span class="crew_plate"><span class="remain_crew">7200</span><span class="killed_plate">(<span class="killed_crew">2800</span>)</span></span><span class="name_plate"><span class="name_plate_cover">【<span class="name">하후돈</span>】</span><span class="crew_type">기</span></span></span></div>';

const SAMPLE_DEFENSE_HTML = '<div class="small_war_log"><span class="me"><span class="name_plate"><span class="crew_type">보</span><span class="name_plate_cover">【<span class="name">장비</span>】</span></span><span class="crew_plate"><span class="remain_crew">5000</span><span class="killed_plate">(<span class="killed_crew">500</span>)</span></span></span><span class="war_type war_type_defense">←</span><span class="you"><span class="crew_plate"><span class="remain_crew">4200</span><span class="killed_plate">(<span class="killed_crew">800</span>)</span></span><span class="name_plate"><span class="name_plate_cover">【<span class="name">조조</span>】</span><span class="crew_type">기</span></span></span></div>';

describe('formatBattleLog', () => {
    describe('isBattleLogHtml', () => {
        it('returns true for battle log HTML', async () => {
            const { isBattleLogHtml } = await import('./formatBattleLog');
            expect(isBattleLogHtml(SAMPLE_BATTLE_HTML)).toBe(true);
        });

        it('returns false for color tag string', async () => {
            const { isBattleLogHtml } = await import('./formatBattleLog');
            expect(isBattleLogHtml('<R>normal log</>')).toBe(false);
        });

        it('returns false for plain text', async () => {
            const { isBattleLogHtml } = await import('./formatBattleLog');
            expect(isBattleLogHtml('plain text message')).toBe(false);
        });
    });

    describe('getWarTypeColor', () => {
        it('returns cyan for attack', async () => {
            const { getWarTypeColor } = await import('./formatBattleLog');
            expect(getWarTypeColor('attack')).toBe('cyan');
        });

        it('returns magenta for defense', async () => {
            const { getWarTypeColor } = await import('./formatBattleLog');
            expect(getWarTypeColor('defense')).toBe('magenta');
        });

        it('returns white for siege', async () => {
            const { getWarTypeColor } = await import('./formatBattleLog');
            expect(getWarTypeColor('siege')).toBe('white');
        });
    });

    describe('parseBattleLogHtml', () => {
        it('extracts attacker name from attack HTML', async () => {
            const { parseBattleLogHtml } = await import('./formatBattleLog');
            const result = parseBattleLogHtml(SAMPLE_BATTLE_HTML);
            expect(result).not.toBeNull();
            expect(result!.attacker.name).toBe('관우');
        });

        it('extracts defender name from attack HTML', async () => {
            const { parseBattleLogHtml } = await import('./formatBattleLog');
            const result = parseBattleLogHtml(SAMPLE_BATTLE_HTML);
            expect(result).not.toBeNull();
            expect(result!.defender.name).toBe('하후돈');
        });

        it('extracts remainCrew numbers', async () => {
            const { parseBattleLogHtml } = await import('./formatBattleLog');
            const result = parseBattleLogHtml(SAMPLE_BATTLE_HTML);
            expect(result).not.toBeNull();
            expect(result!.attacker.remainCrew).toBe(8500);
            expect(result!.defender.remainCrew).toBe(7200);
        });

        it('extracts killedCrew numbers', async () => {
            const { parseBattleLogHtml } = await import('./formatBattleLog');
            const result = parseBattleLogHtml(SAMPLE_BATTLE_HTML);
            expect(result).not.toBeNull();
            expect(result!.attacker.killedCrew).toBe(1500);
            expect(result!.defender.killedCrew).toBe(2800);
        });

        it('identifies war type as attack', async () => {
            const { parseBattleLogHtml } = await import('./formatBattleLog');
            const result = parseBattleLogHtml(SAMPLE_BATTLE_HTML);
            expect(result).not.toBeNull();
            expect(result!.warType).toBe('attack');
        });

        it('identifies war type as defense', async () => {
            const { parseBattleLogHtml } = await import('./formatBattleLog');
            const result = parseBattleLogHtml(SAMPLE_DEFENSE_HTML);
            expect(result).not.toBeNull();
            expect(result!.warType).toBe('defense');
        });

        it('extracts crew types', async () => {
            const { parseBattleLogHtml } = await import('./formatBattleLog');
            const result = parseBattleLogHtml(SAMPLE_BATTLE_HTML);
            expect(result).not.toBeNull();
            expect(result!.attacker.crewType).toBe('궁');
            expect(result!.defender.crewType).toBe('기');
        });

        it('returns null for non-battle-log HTML', async () => {
            const { parseBattleLogHtml } = await import('./formatBattleLog');
            expect(parseBattleLogHtml('<div>not a battle log</div>')).toBeNull();
        });
    });
});
