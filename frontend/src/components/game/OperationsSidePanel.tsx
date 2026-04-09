'use client';

/**
 * Phase 14 Plan 14-17 — Operations side panel (D-30)
 *
 * Right-edge 280px panel rendered as a child of OperationsOverlay. Lists each
 * `activeOperations` entry with an objective badge (점령/방어/소탕), target
 * system name, and participant count. Clicking a row calls `onFocus` with
 * the target system mapStarId — the parent (GalaxyMap host) uses this to
 * pan the camera.
 *
 * Empty state is handled here: when the list is empty, renders the D-30
 * copywriting contract ("발령된 작전 없음 / 지휘 권한 패널에서 작전계획을
 * 발령하면 이 곳에 표시됩니다.").
 *
 * Per UI-SPEC Section F + copywriting contract Operation Overlay.
 */

import type { OperationEventDto, OperationObjective } from '@/types/tactical';
import type { StarSystem } from '@/types/galaxy';

export interface OperationsSidePanelProps {
    activeOperations: OperationEventDto[];
    /** Called with the mapStarId when a row is clicked. */
    onFocus: (targetStarSystemId: number) => void;
    /** Resolver used to turn `targetStarSystemId` into a Korean system name. */
    getSystem: (mapStarId: number) => StarSystem | undefined;
}

/**
 * Korean label for each operation objective — matches D-30 copywriting
 * contract. Exported pure so unit tests can assert the full set without
 * mounting the component.
 */
export const OBJECTIVE_LABEL_KO: Record<OperationObjective, string> = {
    CONQUEST: '점령',
    DEFENSE: '방어',
    SWEEP: '소탕',
};

export function OperationsSidePanel({
    activeOperations,
    onFocus,
    getSystem,
}: OperationsSidePanelProps) {
    // Empty-state copy pulled from UI-SPEC Section F / copywriting contract.
    if (activeOperations.length === 0) {
        return (
            <aside
                className="absolute right-0 top-0 bottom-0 z-[10] w-[280px] border-l border-gray-700 bg-[rgba(10,14,23,0.92)] p-4 text-gray-200 pointer-events-auto"
                aria-label="진행 중인 작전"
            >
                <h2 className="mb-3 text-sm font-bold text-gray-100">진행 중인 작전</h2>
                <div className="mt-6 flex flex-col items-center justify-center gap-2 text-center">
                    <p className="text-xs font-bold text-gray-300">발령된 작전 없음</p>
                    <p className="text-[11px] leading-snug text-gray-500">
                        지휘 권한 패널에서 작전계획을 발령하면 이 곳에 표시됩니다.
                    </p>
                </div>
            </aside>
        );
    }

    return (
        <aside
            className="absolute right-0 top-0 bottom-0 z-[10] w-[280px] overflow-y-auto border-l border-gray-700 bg-[rgba(10,14,23,0.92)] p-4 text-gray-200 pointer-events-auto"
            aria-label="진행 중인 작전"
        >
            <h2 className="mb-3 text-sm font-bold text-gray-100">진행 중인 작전</h2>
            <ul className="flex flex-col gap-2">
                {activeOperations.map((op) => {
                    const target = getSystem(op.targetStarSystemId);
                    const targetLabel = target?.nameKo ?? `#${op.targetStarSystemId}`;
                    const objectiveLabel = OBJECTIVE_LABEL_KO[op.objective];
                    return (
                        <li key={op.operationId}>
                            <button
                                type="button"
                                onClick={() => onFocus(op.targetStarSystemId)}
                                className="w-full rounded-none border border-gray-700 bg-[rgba(20,25,40,0.8)] px-3 py-2 text-left text-xs hover:border-amber-500 hover:bg-[rgba(30,35,55,0.9)] focus:outline-none focus:ring-1 focus:ring-amber-400"
                                data-operation-id={op.operationId}
                                data-objective={op.objective}
                            >
                                <div className="flex items-center justify-between">
                                    <span className="rounded-sm border border-amber-500 px-1.5 py-0.5 text-[10px] font-bold text-amber-300">
                                        {objectiveLabel}
                                    </span>
                                    <span className="text-[10px] text-gray-500">
                                        {op.status}
                                    </span>
                                </div>
                                <div className="mt-1.5 text-[12px] font-bold text-gray-100">
                                    {targetLabel}
                                </div>
                                <div className="mt-0.5 text-[10px] text-gray-400">
                                    참가 부대 {op.participantFleetIds.length}개
                                </div>
                            </button>
                        </li>
                    );
                })}
            </ul>
        </aside>
    );
}
