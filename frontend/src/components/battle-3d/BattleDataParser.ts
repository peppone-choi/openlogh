import type {
    AnimationSequence,
    BattlePhase,
    PhaseEvent,
    UnitConfig,
    TerrainType,
    WeatherType,
} from '@/types/battle3d';
import type { BattleSimResponse, BattleSimUnit, BattleSimCity, BattlePhaseDetail, Nation } from '@/types';

function getNationColor(nationId: number, nations: Nation[]): string {
    const nation = nations.find((n) => n.id === nationId);
    return nation?.color ?? '#888888';
}

function unitToConfig(unit: BattleSimUnit, name: string, nationId: number, nationColor: string): UnitConfig {
    return {
        name: unit.name ?? name,
        nationId,
        nationColor,
        crewType: unit.crewType ?? 0,
        initialCrew: unit.crew ?? 0,
        leadership: unit.leadership ?? 0,
        strength: unit.strength ?? 0,
        intel: unit.intel ?? 0,
    };
}

export function parseLogEvents(log: string): PhaseEvent[] {
    const events: PhaseEvent[] = [];

    if (/퇴각/.test(log)) events.push('retreat');
    if (/점령/.test(log)) events.push('city_occupied');
    if (/치명타|크리티컬/.test(log)) events.push('critical');
    if (/회피|빗나/.test(log)) events.push('dodge');
    if (/화공|불/.test(log)) events.push('trigger_fire');
    if (/빙공|얼음/.test(log)) events.push('trigger_ice');
    if (/뇌공|번개/.test(log)) events.push('trigger_lightning');
    if (/부상|상처/.test(log)) events.push('injury');
    if (/군량|식량 부족/.test(log)) events.push('rice_shortage');
    if (/수비대 교체|방어대 교체/.test(log)) events.push('defender_switch');

    return events;
}

export function buildPhasesFromDetails(details: BattlePhaseDetail[], logs: string[]): BattlePhase[] {
    return details.map((detail, idx) => {
        const log = logs[idx] ?? '';
        const detailEvents = detail.events.flatMap((e) => parseLogEvents(e));
        const logEvents = parseLogEvents(log);

        const allEvents = Array.from(new Set([...detailEvents, ...logEvents])) as PhaseEvent[];

        const attackerHpBefore = idx === 0 ? detail.attackerHp + detail.defenderDamage : details[idx - 1].attackerHp;
        const defenderHpBefore = idx === 0 ? detail.defenderHp + detail.attackerDamage : details[idx - 1].defenderHp;

        return {
            phaseNumber: detail.phase,
            attackerHpBefore,
            attackerHpAfter: detail.attackerHp,
            defenderHpBefore,
            defenderHpAfter: detail.defenderHp,
            attackerDamage: detail.attackerDamage,
            defenderDamage: detail.defenderDamage,
            activeDefenderIndex: detail.defenderIndex,
            log,
            events: allEvents,
        };
    });
}

export function buildPhasesFromLogs(
    logs: string[],
    attackerCrew: number,
    defenderCrew: number,
    rounds: number
): BattlePhase[] {
    const count = Math.max(rounds, logs.length, 1);
    const phases: BattlePhase[] = [];

    for (let i = 0; i < count; i++) {
        const t = count === 1 ? 1 : i / (count - 1);
        const tNext = count === 1 ? 1 : (i + 1) / (count - 1);

        const atkBefore = Math.round(attackerCrew * (1 - t));
        const atkAfter = Math.round(attackerCrew * (1 - tNext));
        const defBefore = Math.round(defenderCrew * (1 - t));
        const defAfter = Math.round(defenderCrew * (1 - tNext));

        const log = logs[i] ?? '';
        const events = parseLogEvents(log);

        phases.push({
            phaseNumber: i + 1,
            attackerHpBefore: atkBefore,
            attackerHpAfter: atkAfter,
            defenderHpBefore: defBefore,
            defenderHpAfter: defAfter,
            attackerDamage: Math.max(0, defBefore - defAfter),
            defenderDamage: Math.max(0, atkBefore - atkAfter),
            activeDefenderIndex: 0,
            log,
            events,
        });
    }

    return phases;
}

export function parseSimulateResult(
    result: BattleSimResponse,
    attackerUnit: BattleSimUnit,
    defenderUnit: BattleSimUnit,
    defenderCity: BattleSimCity,
    nations: Nation[],
    options?: { terrain?: string; weather?: string; attackerNationId?: number; defenderNationId?: number }
): AnimationSequence {
    const attackerNationId = options?.attackerNationId ?? 0;
    const defenderNationId = options?.defenderNationId ?? 0;

    const attackerColor = getNationColor(attackerNationId, nations);
    const defenderColor = getNationColor(defenderNationId, nations);

    const attacker = unitToConfig(attackerUnit, '공격군', attackerNationId, attackerColor);
    const defender = unitToConfig(defenderUnit, '방어군', defenderNationId, defenderColor);

    const phases =
        result.phaseDetails && result.phaseDetails.length > 0
            ? buildPhasesFromDetails(result.phaseDetails, result.logs)
            : buildPhasesFromLogs(result.logs, attackerUnit.crew ?? 0, defenderUnit.crew ?? 0, result.rounds);

    const terrain = (options?.terrain ?? result.terrain ?? 'plain') as TerrainType;
    const weather = (options?.weather ?? result.weather ?? 'clear') as WeatherType;

    const city =
        defenderCity.level !== undefined || defenderCity.def !== undefined
            ? {
                  name: '성',
                  level: defenderCity.level ?? 1,
                  def: defenderCity.def ?? 0,
                  wall: defenderCity.wall ?? 0,
                  nationId: defenderNationId,
              }
            : undefined;

    return {
        terrain,
        weather,
        attacker,
        defenders: [defender],
        city,
        phases,
        result: {
            winner: result.winner === 'attacker' ? 'attacker' : 'defender',
            cityOccupied: result.logs.some((l) => /점령/.test(l)),
            attackerRemaining: result.attackerRemaining,
            defenderRemaining: result.defenderRemaining,
        },
    };
}
