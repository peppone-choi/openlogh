'use client';

import type { TacticalBattle, TacticalUnit } from '@/types/tactical';
import { useGalaxyStore } from '@/stores/galaxyStore';

interface InfoPanelProps {
    battle: TacticalBattle;
    units: TacticalUnit[];
    myOfficerId?: number;
    /** UC year for date display */
    ucYear?: number;
    /** UC month for date display */
    ucMonth?: number;
    /** Star system name */
    starSystemName?: string;
    /** Operation name */
    operationName?: string;
    /** Commanding officer name */
    commanderName?: string;
    /** Commanding officer rank */
    commanderRank?: string;
    /** Merit points */
    meritPoints?: number;
    /**
     * Phase 14 Plan 14-16 (D-36 / D-37) — currently selected tactical unit.
     * When non-null and the unit is NPC-controlled with a mission objective,
     * the panel renders "현재 목적" / "목표" / "추적 대상" rows beneath the
     * standard battle metadata.
     *
     * Passed in by the parent tactical page (not derived from myOfficerId
     * so non-player selection surfaces the NPC context as well).
     */
    selectedUnit?: TacticalUnit | null;
    /**
     * Phase 14 Plan 14-14 (FE-03, D-11) — command relation of the currently
     * selected tactical unit to the logged-in officer. When set, the panel
     * renders a single badge at the TOP of the body before the standard info
     * rows per UI-SPEC Section C "Gating Visual States":
     *
     *   - 'self' | 'subordinate'  → "내 지휘권 하 유닛"   (gold 0.2 alpha)
     *   - 'friendly-other'        → "{officerName} 지휘권 ({rank})" (faction 0.2 alpha)
     *   - 'enemy'                 → "적 부대"            (destructive 0.2 alpha)
     *   - null / undefined        → no badge
     *
     * Computed by the caller — BattleMap / tactical page runs
     * `canCommandUnit(myOfficerId, myHierarchy, selectedUnit)` and translates
     * the result into one of the four strings so this component stays
     * free of gating logic.
     */
    selectedUnitCommandRelation?: 'self' | 'subordinate' | 'friendly-other' | 'enemy' | null;
    /** Phase 14 Plan 14-14 — friendly-other badge officer display name. */
    friendlyOtherOfficerName?: string;
    /** Phase 14 Plan 14-14 — friendly-other badge officer rank label. */
    friendlyOtherOfficerRank?: string;
}

const LABEL_STYLE: React.CSSProperties = {
    color: '#666',
    fontSize: 9,
    fontFamily: 'monospace',
    marginBottom: 1,
};

const VALUE_STYLE: React.CSSProperties = {
    color: '#cccccc',
    fontSize: 11,
    fontFamily: 'monospace',
    marginBottom: 6,
};

function InfoRow({ label, value }: { label: string; value: string }) {
    return (
        <div>
            <div style={LABEL_STYLE}>{label}</div>
            <div style={VALUE_STYLE}>{value}</div>
        </div>
    );
}

/**
 * Phase 14 Plan 14-16 (D-37) — Korean label for the OperationObjective enum.
 * UI-SPEC Copywriting Contract — Operation badge labels.
 */
const MISSION_OBJECTIVE_LABEL_KO: Record<string, string> = {
    CONQUEST: '점령',
    DEFENSE: '방어',
    SWEEP: '소탕',
};

/**
 * Pure helper — resolves the Korean label for a missionObjective string.
 * Exported for isolated testing; falls back to the raw string if the enum
 * value is unknown (forward-compatible if backend adds new objectives).
 */
export function resolveMissionObjectiveLabel(objective: string | null | undefined): string | null {
    if (!objective) return null;
    return MISSION_OBJECTIVE_LABEL_KO[objective] ?? objective;
}

/**
 * Phase 14 Plan 14-14 (D-11) — Compute the badge style + copy for the
 * top-of-panel command-relation marker. Pure helper so the component body
 * stays focused on layout. Returns null for `null`/`undefined` relation
 * (no badge rendered).
 *
 * Colours follow UI-SPEC Section C at 0.2 alpha:
 *   - 'self'/'subordinate' → gold `#f59e0b` (matches TacticalUnitIcon border)
 *   - 'friendly-other'     → faction blue `#4466ff` (neutral ally default)
 *   - 'enemy'              → destructive `#ef4444`
 */
function resolveCommandRelationBadge(
    relation: 'self' | 'subordinate' | 'friendly-other' | 'enemy' | null | undefined,
    friendlyName?: string,
    friendlyRank?: string,
): { label: string; background: string; borderColor: string } | null {
    if (!relation) return null;
    if (relation === 'self' || relation === 'subordinate') {
        return {
            label: '내 지휘권 하 유닛',
            background: 'rgba(245, 158, 11, 0.2)',
            borderColor: '#f59e0b',
        };
    }
    if (relation === 'friendly-other') {
        const nameLabel = friendlyName ?? '—';
        const rankLabel = friendlyRank ? ` (${friendlyRank})` : '';
        return {
            label: `${nameLabel} 지휘권${rankLabel}`,
            background: 'rgba(68, 102, 255, 0.2)',
            borderColor: '#4466ff',
        };
    }
    // enemy
    return {
        label: '적 부대',
        background: 'rgba(239, 68, 68, 0.2)',
        borderColor: '#ef4444',
    };
}

export function InfoPanel({
    battle,
    units,
    myOfficerId,
    ucYear = 800,
    ucMonth = 1,
    starSystemName = '미지정',
    operationName,
    commanderName = '—',
    commanderRank = '',
    meritPoints = 0,
    selectedUnit,
    selectedUnitCommandRelation,
    friendlyOtherOfficerName,
    friendlyOtherOfficerRank,
}: InfoPanelProps) {
    // Phase 14 Plan 14-14 (D-11) — top-of-panel command relation badge
    const relationBadge = resolveCommandRelationBadge(
        selectedUnitCommandRelation,
        friendlyOtherOfficerName,
        friendlyOtherOfficerRank,
    );
    const totalSupplies = units.reduce((sum, u) => sum + (u.ships ?? 0), 0);
    const myUnit = myOfficerId ? units.find((u) => u.officerId === myOfficerId) : undefined;
    const mySide = myUnit?.side;
    const factionName = mySide === 'ATTACKER' ? '자유행성동맹' : '은하제국';

    // ── Phase 14 D-36 / D-37 ──
    // NPC mission objective rows only render when the selected unit is
    // NPC-controlled AND has a missionObjective populated. We look up the
    // target system name via galaxyStore.getSystem so the row reads naturally
    // ("목표: 이제르론" instead of "목표: 42").
    const getSystem = useGalaxyStore((s) => s.getSystem);
    const showNpcMission = Boolean(selectedUnit?.isNpc && selectedUnit?.missionObjective);
    const missionLabel = showNpcMission
        ? resolveMissionObjectiveLabel(selectedUnit?.missionObjective)
        : null;
    const targetSystem =
        showNpcMission && selectedUnit?.targetStarSystemId != null
            ? getSystem?.(selectedUnit.targetStarSystemId)
            : undefined;
    // StarSystem has { nameKo, nameEn }, not a generic `name` — 14-14 Rule 3
    // auto-fix: the original 14-16 code referenced a non-existent `.name`
    // field. Prefer Korean display name to stay consistent with the rest of
    // the InfoPanel's copy (factionName / "미지정" / 턴 labels).
    const targetSystemName = targetSystem?.nameKo ?? targetSystem?.nameEn ?? null;

    return (
        <div
            style={{
                position: 'absolute',
                bottom: 8,
                right: 8,
                width: 200,
                background: '#0d0d1a',
                border: '1px solid #333',
                padding: 8,
                zIndex: 10,
                fontFamily: 'monospace',
            }}
        >
            {/*
             * ── Phase 14 Plan 14-14 (D-11) — command-relation badge ──
             * Rendered at the TOP of the panel per UI-SPEC Section C so
             * glance order matches icon → badge → detail rows. Background
             * is 0.2 alpha, border is the solid colour, Korean copy is
             * sourced from the Copywriting Contract FE-03 rows.
             */}
            {relationBadge && (
                <div
                    data-testid="command-relation-badge"
                    style={{
                        background: relationBadge.background,
                        border: `1px solid ${relationBadge.borderColor}`,
                        color: '#eeeeee',
                        fontSize: 11,
                        fontFamily: 'monospace',
                        padding: '3px 6px',
                        marginBottom: 6,
                        textAlign: 'center',
                    }}
                >
                    {relationBadge.label}
                </div>
            )}

            <InfoRow label="진영" value={factionName} />
            <InfoRow label="입력 턴" value={`${battle.tickCount}턴`} />
            <InfoRow label="UC/RC 날짜" value={`UC ${ucYear}년 ${ucMonth}월`} />
            <InfoRow label="성계명" value={starSystemName} />
            <InfoRow label="작전명" value={operationName ?? '미지정'} />
            <InfoRow
                label="작전총사령관"
                value={commanderRank ? `${commanderRank} ${commanderName}` : commanderName}
            />
            <InfoRow label="작전공적" value={`${meritPoints.toLocaleString()} pt`} />
            <InfoRow label="총물자량" value={`${totalSupplies.toLocaleString()} 척`} />

            {/*
             * ── Phase 14 D-36 / D-37 — NPC mission objective rows ──
             * Only render when the selected unit is NPC-controlled with a
             * mission objective. Non-NPC selection (human players, allies,
             * enemies with no objective data) renders nothing so the panel
             * stays compact.
             *
             * Korean copy from UI-SPEC Copywriting Contract §NPC/Offline
             * Markers:
             *   - "현재 목적: {missionLabel}"
             *   - "목표: {targetSystem}"   (omitted if targetStarSystemId
             *     missing from DTO — see tactical.ts comment)
             *   - "추적 대상: {enemyFleetName}" (future — requires
             *     targetFleetId on DTO)
             */}
            {showNpcMission && missionLabel && (
                <>
                    <div
                        style={{
                            borderTop: '1px solid #333',
                            marginTop: 4,
                            paddingTop: 6,
                        }}
                    >
                        <InfoRow label="현재 목적" value={missionLabel} />
                    </div>
                    {targetSystemName && (
                        <InfoRow label="목표" value={targetSystemName} />
                    )}
                </>
            )}
        </div>
    );
}
