/**
 * Phase 14 Plan 14-15 — FE-04 / D-13 SuccessionCountdownOverlay.
 *
 * HTML overlay (NOT Konva) that renders above a unit whose succession timer
 * is counting down. Per UI-SPEC Section D Phase 2:
 *
 *   Label: "지휘 승계 중 — {ticksRemaining}틱"
 *   Background: rgba(0,0,0,0.85)
 *   Border: 1px solid #f59e0b (game-gold)
 *   Copy weight: 600, monospaced countdown
 *
 * Rendered by BattleMap as an HTML sibling of the Konva Stage (wrapped in a
 * relative-positioned div) so the pill floats above the canvas and does not
 * interfere with Konva picking. One overlay per unit whose
 * `successionState === 'PENDING_SUCCESSION'`.
 *
 * Screen coords are pre-computed by the caller (BattleMap multiplies by
 * scaleX/scaleY) so this component stays pure + trivially unit-testable.
 */
'use client';

import { memo } from 'react';

export interface SuccessionCountdownOverlayProps {
    /** Screen-space x coord (already scaled to canvas px). */
    screenX: number;
    /** Screen-space y coord (already scaled to canvas px). */
    screenY: number;
    /** Ticks remaining before auto-succession (0..30). */
    ticksRemaining: number;
}

/**
 * Pure helper — clamps the countdown display value into [0, 30] so a
 * late-arriving broadcast can't render "31틱" or "-1틱". Exported for
 * test assertions.
 */
export function clampSuccessionTicks(ticksRemaining: number): number {
    if (!Number.isFinite(ticksRemaining)) return 0;
    if (ticksRemaining < 0) return 0;
    if (ticksRemaining > 30) return 30;
    return Math.floor(ticksRemaining);
}

export const SuccessionCountdownOverlay = memo(function SuccessionCountdownOverlay({
    screenX,
    screenY,
    ticksRemaining,
}: SuccessionCountdownOverlayProps) {
    const display = clampSuccessionTicks(ticksRemaining);
    return (
        <div
            role="status"
            aria-label={`지휘 승계 중, ${display}틱 남음`}
            data-testid="succession-countdown-overlay"
            style={{
                position: 'absolute',
                // Center the pill above the unit (pill is ~120px wide, ~32px tall).
                left: screenX - 60,
                top: screenY - 48,
                width: 120,
                minHeight: 32,
                padding: '4px 10px',
                background: 'rgba(0, 0, 0, 0.85)',
                border: '1px solid #f59e0b',
                borderRadius: 4,
                color: '#f59e0b',
                fontFamily:
                    'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
                fontSize: 12,
                fontWeight: 600,
                lineHeight: '14px',
                textAlign: 'center',
                pointerEvents: 'none',
                userSelect: 'none',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 2,
                zIndex: 10,
                // Subtle glow to draw the eye without full-screen effect (D-14).
                boxShadow: '0 0 6px rgba(245, 158, 11, 0.6)',
            }}
        >
            <span style={{ fontSize: 10, opacity: 0.85 }}>지휘 승계 중</span>
            <span style={{ fontSize: 16, fontWeight: 700 }}>{display}틱</span>
        </div>
    );
});

export default SuccessionCountdownOverlay;
