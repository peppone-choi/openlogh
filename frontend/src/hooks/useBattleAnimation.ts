'use client';

import { useRef, useCallback, useEffect } from 'react';
import { useBattle3DStore } from '@/stores/battle3dStore';
import { PHASE_DURATION_MS } from '@/lib/battle3d-constants';

interface UseBattleAnimationResult {
    phaseProgress: React.RefObject<number>;
}

export function useBattleAnimation(): UseBattleAnimationResult {
    const phaseProgress = useRef<number>(0);
    const elapsedRef = useRef<number>(0);
    const lastTimeRef = useRef<number | null>(null);
    const rafRef = useRef<number | null>(null);

    const playState = useBattle3DStore((s) => s.playState);
    const playbackSpeed = useBattle3DStore((s) => s.playbackSpeed);
    const nextPhase = useBattle3DStore((s) => s.nextPhase);

    const tick = useCallback(
        (now: number) => {
            if (lastTimeRef.current === null) {
                lastTimeRef.current = now;
            }
            const delta = now - lastTimeRef.current;
            lastTimeRef.current = now;

            const effectiveSpeed = playbackSpeed > 0 ? playbackSpeed : 1;
            elapsedRef.current += delta * effectiveSpeed;

            const phaseDuration = PHASE_DURATION_MS;
            const progress = Math.min(elapsedRef.current / phaseDuration, 1);
            phaseProgress.current = progress;

            if (progress >= 1) {
                elapsedRef.current = 0;
                lastTimeRef.current = null;
                nextPhase();
            }

            rafRef.current = requestAnimationFrame(tick);
        },
        [playbackSpeed, nextPhase]
    );

    useEffect(() => {
        if (playState !== 'playing') {
            if (rafRef.current !== null) {
                cancelAnimationFrame(rafRef.current);
                rafRef.current = null;
            }
            lastTimeRef.current = null;
            if (playState === 'idle' || playState === 'finished') {
                elapsedRef.current = 0;
                phaseProgress.current = 0;
            }
            return;
        }

        rafRef.current = requestAnimationFrame(tick);

        return () => {
            if (rafRef.current !== null) {
                cancelAnimationFrame(rafRef.current);
                rafRef.current = null;
            }
        };
    }, [playState, tick]);

    return { phaseProgress };
}
