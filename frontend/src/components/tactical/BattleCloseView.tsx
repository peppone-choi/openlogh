'use client';
// BattleCloseView — gin7-style battle close-range view root layout
// Top: 3-column (ally panel | combat scene | enemy panel) at ~45% height
// Bottom: 3D tactical grid map at ~55% height
import { useTacticalStore } from '@/stores/tacticalStore';
import { BattleCloseViewScene } from './BattleCloseViewScene';
import { BattleCloseViewPanel, buildPanelProps } from './BattleCloseViewPanel';
import { TacticalMapR3F } from './TacticalMapR3F';

// Faction display metadata
const FACTION_META: Record<number, { name: string; color: string }> = {};

function getFactionMeta(factionId: number, attackerFactionId: number, defenderFactionId: number) {
    if (factionId === attackerFactionId) {
        return { name: '은하제국', color: '#4466ff' };
    }
    if (factionId === defenderFactionId) {
        return { name: '자유행성동맹', color: '#ff4444' };
    }
    return { name: '알 수 없음', color: '#888888' };
}

export function BattleCloseView() {
    const { units, currentBattle, clearBattle } = useTacticalStore();

    if (!currentBattle) return null;

    const attackerFactionId = currentBattle.attackerFactionId;
    const defenderFactionId = currentBattle.defenderFactionId;

    // Pick representative units for the panels: first alive attacker and first alive defender
    const allyUnit = units.find((u) => u.factionId === attackerFactionId && u.isAlive);
    const enemyUnit = units.find((u) => u.factionId === defenderFactionId && u.isAlive);

    const allyMeta = getFactionMeta(attackerFactionId, attackerFactionId, defenderFactionId);
    const enemyMeta = getFactionMeta(defenderFactionId, attackerFactionId, defenderFactionId);

    const allyPanelProps = buildPanelProps(allyUnit, allyMeta.color, allyMeta.name);
    const enemyPanelProps = buildPanelProps(enemyUnit, enemyMeta.color, enemyMeta.name);

    return (
        <div
            style={{
                position: 'fixed',
                inset: 0,
                zIndex: 50,
                backgroundColor: '#000',
                display: 'flex',
                flexDirection: 'column',
            }}
        >
            {/* 전술전 종료 button */}
            <button
                onClick={clearBattle}
                style={{
                    position: 'absolute',
                    top: '12px',
                    right: '12px',
                    zIndex: 60,
                    backgroundColor: '#1a0000',
                    border: '1px solid #ff4444',
                    color: '#ff4444',
                    padding: '6px 14px',
                    fontSize: '12px',
                    cursor: 'pointer',
                    fontFamily: 'inherit',
                }}
            >
                전술전 종료
            </button>

            {/* TOP SECTION: 3-column layout at ~45% height */}
            <div
                style={{
                    height: '45%',
                    display: 'flex',
                    flexDirection: 'row',
                    overflow: 'hidden',
                    flexShrink: 0,
                }}
            >
                {/* Left: Ally panel */}
                {allyPanelProps ? (
                    <BattleCloseViewPanel
                        side="ally"
                        officer={allyPanelProps.officer}
                        unit={allyPanelProps.unit}
                    />
                ) : (
                    <div style={{ width: '180px', flexShrink: 0, backgroundColor: '#3a1010' }} />
                )}

                {/* Center: Combat scene */}
                <div style={{ flex: 1, overflow: 'hidden' }}>
                    <BattleCloseViewScene />
                </div>

                {/* Right: Enemy panel */}
                {enemyPanelProps ? (
                    <BattleCloseViewPanel
                        side="enemy"
                        officer={enemyPanelProps.officer}
                        unit={enemyPanelProps.unit}
                    />
                ) : (
                    <div style={{ width: '180px', flexShrink: 0, backgroundColor: '#0d1a3a' }} />
                )}
            </div>

            {/* BOTTOM SECTION: Tactical map at ~55% height */}
            <div style={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>
                <TacticalMapR3F />
            </div>
        </div>
    );
}
