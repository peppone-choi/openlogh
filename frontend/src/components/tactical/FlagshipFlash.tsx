/**
 * Phase 14 Plan 14-15 — FE-04 / D-14 FlagshipFlash component.
 *
 * A 0.5s local ring flash that fires when a tacticalStore
 * `activeFlagshipDestroyedFleetIds` entry is rendered by BattleMap's
 * `succession-fx` layer. Per UI-SPEC Section D Phase 1a:
 *
 *   - Local ring flash at the destroyed flagship's screen coords
 *   - NO full-screen viewport effect
 *   - 0.5s duration (wall-clock), radius 24→96, opacity 0.9→0
 *   - Konva-native animation (no motion libraries) — uses requestAnimationFrame
 *
 * The visual math is split into a pure `computeFlashFrame(progress)` helper
 * so vitest env=node can assert the easing curve without mounting
 * react-konva. Same pure-helper pattern as CommandRangeCircle (14-09) /
 * FogLayer (14-11).
 */
'use client';

import { Group, Circle } from 'react-konva';
import { useEffect, useState } from 'react';

export interface FlagshipFlashProps {
    /** Screen-space x coord of the destroyed flagship. */
    cx: number;
    /** Screen-space y coord of the destroyed flagship. */
    cy: number;
    /** Total animation duration in ms (D-14 default: 500). */
    durationMs?: number;
    /** Optional callback when the animation completes (used to clean up layer). */
    onComplete?: () => void;
}

/**
 * Pure helper — computes the visual state for a given animation progress.
 *
 * `progress` is in [0, 1]:
 *   - radius grows from 24 to 96 (linear expansion, "shockwave" feel)
 *   - opacity fades from 0.9 to 0 (ease-out)
 *
 * Exported so vitest env=node can assert the ring geometry without
 * mounting react-konva.
 */
export interface FlashFrame {
    radius: number;
    opacity: number;
}

export function computeFlashFrame(progress: number): FlashFrame {
    const p = Math.max(0, Math.min(1, progress));
    const radius = 24 + 72 * p;
    const opacity = 0.9 * (1 - p);
    return { radius, opacity };
}

export function FlagshipFlash({
    cx,
    cy,
    durationMs = 500,
    onComplete,
}: FlagshipFlashProps) {
    // progress in [0, 1] — driven by requestAnimationFrame
    const [progress, setProgress] = useState(0);

    useEffect(() => {
        const start = performance.now();
        let rafId = 0;
        let cancelled = false;
        const tick = (now: number) => {
            if (cancelled) return;
            const p = Math.min(1, (now - start) / durationMs);
            setProgress(p);
            if (p < 1) {
                rafId = requestAnimationFrame(tick);
            } else {
                onComplete?.();
            }
        };
        rafId = requestAnimationFrame(tick);
        return () => {
            cancelled = true;
            cancelAnimationFrame(rafId);
        };
    }, [durationMs, onComplete]);

    const { radius, opacity } = computeFlashFrame(progress);

    return (
        <Group x={cx} y={cy} listening={false}>
            {/* Outer shockwave ring (white fill) */}
            <Circle radius={radius} fill="white" opacity={opacity} />
            {/* Inner echo ring — softer stroke */}
            <Circle
                radius={radius * 0.7}
                stroke="white"
                strokeWidth={1}
                opacity={opacity * 0.7}
            />
        </Group>
    );
}

export default FlagshipFlash;
