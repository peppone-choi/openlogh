'use client';

import type { AnimationSequence, BattlePlayState } from '@/types/battle3d';
import { Button } from '@/components/ui/button';
import { HealthBar } from './HealthBar';
import { PhaseCounter } from './PhaseCounter';
import { VictoryOverlay } from './VictoryOverlay';

interface BattleHUDProps {
    sequence: AnimationSequence;
    currentPhase: number;
    playState: BattlePlayState;
    onPlayPause: () => void;
    onSpeedChange: (speed: number) => void;
    onModeToggle: () => void;
    onClose: () => void;
}

const SPEED_OPTIONS = [0.5, 1, 2];

export function BattleHUD({
    sequence,
    currentPhase,
    playState,
    onPlayPause,
    onSpeedChange,
    onModeToggle,
    onClose,
}: BattleHUDProps) {
    const phase = sequence.phases[currentPhase];
    const totalPhases = sequence.phases.length;

    const attackerInitial = sequence.attacker.initialCrew;
    const defenderInitial = sequence.defenders[0]?.initialCrew ?? 1;

    const attackerCurrent = phase?.attackerHpAfter ?? sequence.result.attackerRemaining;
    const defenderCurrent = phase?.defenderHpAfter ?? sequence.result.defenderRemaining;

    const isPlaying = playState === 'playing';
    const isFinished = playState === 'finished';

    return (
        <div className="absolute inset-0 pointer-events-none flex flex-col">
            {/* Top bar: health bars + phase counter */}
            <div className="flex items-start justify-between gap-2 p-3 pointer-events-none">
                <HealthBar
                    label={sequence.attacker.name}
                    current={attackerCurrent}
                    max={attackerInitial}
                    color="#3b82f6"
                    side="left"
                />
                <PhaseCounter current={currentPhase} total={totalPhases} />
                <HealthBar
                    label={sequence.defenders[0]?.name ?? '방어측'}
                    current={defenderCurrent}
                    max={defenderInitial}
                    color="#ef4444"
                    side="right"
                />
            </div>

            {/* Spacer */}
            <div className="flex-1" />

            {/* Bottom control bar */}
            <div className="flex items-center gap-2 p-3 bg-black/60 pointer-events-auto">
                {/* Play/Pause */}
                <Button size="sm" variant="outline" onClick={onPlayPause} className="h-8 px-3">
                    {isPlaying ? '❚❚' : '▶'}
                </Button>

                {/* Speed buttons */}
                <div className="flex gap-1">
                    {SPEED_OPTIONS.map((s) => (
                        <Button
                            key={s}
                            size="sm"
                            variant="outline"
                            onClick={() => onSpeedChange(s)}
                            className="h-8 px-2 text-xs"
                        >
                            {s}x
                        </Button>
                    ))}
                </div>

                {/* 2D/3D toggle */}
                <Button size="sm" variant="outline" onClick={onModeToggle} className="h-8 px-3 text-xs">
                    2D/3D
                </Button>

                <div className="flex-1" />

                {/* Close */}
                <Button size="sm" variant="ghost" onClick={onClose} className="h-8 px-3 text-xs text-gray-300">
                    ✕ 닫기
                </Button>
            </div>

            {/* Victory overlay */}
            {isFinished && (
                <VictoryOverlay
                    outcome={sequence.result}
                    attackerName={sequence.attacker.name}
                    defenderName={sequence.defenders[0]?.name ?? '방어측'}
                    onClose={onClose}
                    onReplay={() => {
                        /* replay is handled by parent resetting the store */
                    }}
                />
            )}
        </div>
    );
}
