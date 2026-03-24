'use client';

import { useRef } from 'react';
import { useFrame } from '@react-three/fiber';
import { getAttackerFormation, getDefenderFormation, getPhasePositions } from './UnitFormation';
import { UnitModel } from './UnitModel';
import { getVisibleUnitCount } from '@/lib/battle3d-utils';
import type { UnitConfig, BattlePhase, BattlePlayState } from '@/types/battle3d';

interface UnitManagerProps {
    attackerConfig: UnitConfig;
    defenderConfigs: UnitConfig[];
    currentPhase: BattlePhase | null;
    phaseProgress: React.RefObject<number>;
    playState: BattlePlayState;
}

interface UnitState {
    positions: [number, number, number][];
    isAttacking: boolean;
    isHit: boolean;
}

export function UnitManager({
    attackerConfig,
    defenderConfigs,
    currentPhase,
    phaseProgress,
    playState,
}: UnitManagerProps) {
    const attackerStateRef = useRef<UnitState>({
        positions: getAttackerFormation(attackerConfig),
        isAttacking: false,
        isHit: false,
    });
    const defenderStateRef = useRef<UnitState>({
        positions: getDefenderFormation(defenderConfigs[0] ?? defenderConfigs[0]),
        isAttacking: false,
        isHit: false,
    });
    // Trigger re-render on each frame
    const frameCountRef = useRef(0);

    useFrame(() => {
        if (playState !== 'playing' || !currentPhase) return;

        const progress = phaseProgress.current ?? 0;

        const attackerBase = getAttackerFormation(attackerConfig);
        const activeDefender = defenderConfigs[currentPhase.activeDefenderIndex] ?? defenderConfigs[0];
        const defenderBase = getDefenderFormation(activeDefender);

        const ADVANCE_END = 0.3;
        const CLASH_END = 0.7;
        const inClash = progress >= ADVANCE_END && progress <= CLASH_END;

        attackerStateRef.current = {
            positions: getPhasePositions(attackerBase, 'attacker', progress),
            isAttacking: inClash,
            isHit: inClash && currentPhase.attackerDamage > 0,
        };
        defenderStateRef.current = {
            positions: getPhasePositions(defenderBase, 'defender', progress),
            isAttacking: inClash,
            isHit: inClash && currentPhase.defenderDamage > 0,
        };

        frameCountRef.current += 1;
    });

    if (!currentPhase) return null;

    const activeDefenderIndex = currentPhase.activeDefenderIndex;
    const activeDefender = defenderConfigs[activeDefenderIndex] ?? defenderConfigs[0];

    // Attacker units: fade/shrink units proportionally to HP loss
    const attackerVisibleTotal = getVisibleUnitCount(attackerConfig.initialCrew);
    const attackerHpRatio = Math.max(currentPhase.attackerHpAfter / Math.max(currentPhase.attackerHpBefore, 1), 0);
    const attackerAliveCount = Math.ceil(attackerVisibleTotal * attackerHpRatio);

    // Defender units
    const defenderVisibleTotal = getVisibleUnitCount(activeDefender.initialCrew);
    const defenderHpRatio = Math.max(currentPhase.defenderHpAfter / Math.max(currentPhase.defenderHpBefore, 1), 0);
    const defenderAliveCount = Math.ceil(defenderVisibleTotal * defenderHpRatio);

    const attackerPositions = attackerStateRef.current.positions;
    const defenderPositions = defenderStateRef.current.positions;

    return (
        <>
            {/* Attacker units */}
            {attackerPositions.map((pos, i) => {
                const alive = i < attackerAliveCount;
                const opacity = alive ? 1 : 0;
                if (!alive) return null;
                return (
                    <UnitModel
                        key={`attacker-${i}`}
                        position={pos}
                        color={attackerConfig.nationColor}
                        crewType={attackerConfig.crewType}
                        isAttacking={attackerStateRef.current.isAttacking}
                        isHit={attackerStateRef.current.isHit}
                        opacity={opacity}
                        scale={1}
                    />
                );
            })}

            {/* Defender units */}
            {defenderPositions.map((pos, i) => {
                const alive = i < defenderAliveCount;
                const opacity = alive ? 1 : 0;
                if (!alive) return null;
                return (
                    <UnitModel
                        key={`defender-${activeDefenderIndex}-${i}`}
                        position={pos}
                        color={activeDefender.nationColor}
                        crewType={activeDefender.crewType}
                        isAttacking={defenderStateRef.current.isAttacking}
                        isHit={defenderStateRef.current.isHit}
                        opacity={opacity}
                        scale={1}
                    />
                );
            })}
        </>
    );
}
