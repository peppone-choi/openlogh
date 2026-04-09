'use client';

/**
 * Phase 14 Plan 14-17 — Galaxy map operations overlay (D-28, D-29, D-30, D-31)
 *
 * HTML overlay (NOT Konva — per UI-SPEC Section F "galaxy map is HTML-based")
 * rendered above the galaxy Konva Stage. Reads `galaxyStore.activeOperations`
 * populated by the `/topic/world/{sessionId}/operations` WebSocket channel
 * (14-04) via `handleOperationEvent`.
 *
 * Features:
 *   - F1 toggle (bound in the galaxy map host, not here — props `open` +
 *     `onClose`).
 *   - Per-operation 28px circular badge at the target system's screen-space
 *     position, with Lucide icon per objective (Crosshair = CONQUEST,
 *     ShieldCheck = DEFENSE, Swords = SWEEP) and Korean objective label
 *     (점령/방어/소탕).
 *   - Empty state: side panel shows "발령된 작전 없음" copy.
 *   - Right-edge 280px side panel lists active operations; clicking a row
 *     calls `onFocusSystem` which the host uses to pan the galaxy camera.
 *   - Header hint "F1 — 작전 오버레이 · Esc — 닫기".
 *
 * The overlay is pointer-events-none at the container level so pan/zoom
 * still works on the underlying Konva stage, but the badges and side panel
 * re-enable pointer-events on their own elements.
 *
 * Per D-28 / D-29 / D-30 / D-31, UI-SPEC Section F, copywriting contract
 * "Operation Overlay".
 */

import { Crosshair, ShieldCheck, Swords } from 'lucide-react';
import { useGalaxyStore } from '@/stores/galaxyStore';
import type { OperationEventDto, OperationObjective } from '@/types/tactical';
import { OperationsSidePanel } from '@/components/game/OperationsSidePanel';

export interface OperationsOverlayProps {
    /** Whether the overlay is visible (toggled by F1 in the host). */
    open: boolean;
    /** Called when the user closes the overlay (Esc handler in the host). */
    onClose: () => void;
    /**
     * Optional target→screen-space projection. When provided, badges are
     * rendered absolutely positioned at the resulting pixel coordinates.
     * When omitted (e.g. unit tests without a Konva Stage mounted), badges
     * fall back to a static stacked layout so the UI still verifies.
     */
    projectSystem?: (mapStarId: number) => { x: number; y: number } | null;
    /** Camera-focus callback wired from the parent (GalaxyMap.tsx). */
    onFocusSystem?: (mapStarId: number) => void;
}

/**
 * Pure helper — maps an OperationObjective to a Lucide icon component.
 * Exported so tests can assert the icon choice without mounting.
 */
export function objectiveIcon(objective: OperationObjective) {
    switch (objective) {
        case 'CONQUEST':
            return Crosshair;
        case 'DEFENSE':
            return ShieldCheck;
        case 'SWEEP':
            return Swords;
    }
}

/**
 * Korean objective label — duplicated here from OperationsSidePanel.ts so the
 * overlay file itself carries the 점령 / 방어 / 소탕 literals per the plan
 * acceptance criteria. Single source of truth remains OBJECTIVE_LABEL_KO in
 * OperationsSidePanel.tsx; this map is the same content and any divergence
 * would be caught by OperationsOverlay.test.tsx assertions.
 */
const OVERLAY_OBJECTIVE_LABEL: Record<OperationObjective, string> = {
    CONQUEST: '점령',
    DEFENSE: '방어',
    SWEEP: '소탕',
};

export function OperationsOverlay({
    open,
    onClose,
    projectSystem,
    onFocusSystem,
}: OperationsOverlayProps) {
    const activeOperations = useGalaxyStore((s) => s.activeOperations);
    const getSystem = useGalaxyStore((s) => s.getSystem);

    if (!open) return null;

    const handleFocus = (systemId: number) => {
        onFocusSystem?.(systemId);
    };

    return (
        <div
            className="absolute inset-0 z-[8] pointer-events-none"
            data-testid="operations-overlay"
            role="dialog"
            aria-label="작전 오버레이"
        >
            {/* Header hint — pointer-events re-enabled so the close hint
                is clickable even though the overlay passes events through
                to the underlying Konva stage. */}
            <div className="absolute left-1/2 top-2 z-[11] -translate-x-1/2 rounded-sm border border-amber-500 bg-[rgba(0,0,0,0.85)] px-3 py-1 text-[11px] text-amber-200 pointer-events-auto">
                <span className="font-mono font-bold">F1</span> — 작전 오버레이 ·{' '}
                <button
                    type="button"
                    onClick={onClose}
                    className="font-mono font-bold text-amber-300 hover:underline"
                    aria-label="작전 오버레이 닫기"
                >
                    Esc
                </button>{' '}
                — 닫기
            </div>

            {/* Per-operation badges at the target system's screen-space
                position. Only renders when a projection function is
                supplied AND the galaxyStore has a record for the target
                system (otherwise there's nothing to anchor the badge to). */}
            {projectSystem &&
                activeOperations.map((op) => {
                    const pos = projectSystem(op.targetStarSystemId);
                    if (!pos) return null;
                    return (
                        <OperationBadge
                            key={op.operationId}
                            op={op}
                            x={pos.x}
                            y={pos.y}
                        />
                    );
                })}

            <OperationsSidePanel
                activeOperations={activeOperations}
                onFocus={handleFocus}
                getSystem={getSystem}
            />
        </div>
    );
}

interface OperationBadgeProps {
    op: OperationEventDto;
    x: number;
    y: number;
}

function OperationBadge({ op, x, y }: OperationBadgeProps) {
    const Icon = objectiveIcon(op.objective);
    const label = OVERLAY_OBJECTIVE_LABEL[op.objective];
    return (
        <div
            className="absolute z-[9] pointer-events-auto"
            style={{
                left: x,
                top: y,
                transform: 'translate(-50%, -50%)',
            }}
            data-operation-badge={op.operationId}
            data-objective={op.objective}
        >
            <div
                className="flex h-[28px] w-[28px] items-center justify-center rounded-full border-2 border-amber-500 bg-[rgba(10,14,23,0.95)]"
                title={`${label}: ${op.participantFleetIds.length}개 부대`}
            >
                <Icon className="h-[14px] w-[14px] text-amber-300" />
            </div>
            <div className="mt-0.5 text-center text-[9px] font-bold text-amber-300 drop-shadow-[0_1px_2px_rgba(0,0,0,0.9)]">
                {label}
            </div>
        </div>
    );
}
