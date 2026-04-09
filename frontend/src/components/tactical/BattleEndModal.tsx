'use client';

// Phase 14 Plan 14-18 — End-of-Battle Modal (FE-01 summary, D-32..D-34).
//
// Full-screen Radix Dialog triggered on `currentBattle.phase` transitioning
// from ACTIVE → ENDED. Fetches the merit breakdown from the 14-02 endpoint
// (`GET /api/v1/battle/{sessionId}/{battleId}/summary`) and renders the
// D-33 "기본 X + 작전 +Y = 총 Z" rows, highlighting operation participants
// per UI-SPEC Section G.
//
// Test strategy (mirrors 14-09 CommandRangeCircle and 14-11 FogLayer):
// vitest runs under `environment: 'node'` so we DO NOT mount this
// component. Instead we export pure helpers (`resolveHeader`,
// `formatMeritBreakdown`, `computeMySide`) covering every D-32..D-34
// decision, and the BattleEndModal.test.tsx asserts against them plus a
// source-text regex guard for the Korean copy contract.

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';

import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogDescription,
} from '@/components/ui/dialog';
import {
    Table,
    TableHeader,
    TableBody,
    TableRow,
    TableCell,
    TableHead,
} from '@/components/ui/table';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';

import { useTacticalStore } from '@/stores/tacticalStore';
import { useWorldStore } from '@/stores/worldStore';
import { fetchBattleSummary } from '@/lib/tacticalApi';
import type { BattleSummaryDto, BattleSide } from '@/types/tactical';

// ───────────────────────────────────────────────────────────────────────
// Pure helpers exported for unit tests (14-18 Task 1 behaviors 4-8).
// ───────────────────────────────────────────────────────────────────────

/**
 * A thin projection of `BattleSummaryRow` used by {@link formatMeritBreakdown}.
 * Tests construct these directly without pulling the full DTO.
 */
export interface BattleSummaryRowView {
    fleetId: number;
    officerName: string;
    survivingShips: number;
    maxShips: number;
    baseMerit: number;
    operationMultiplier: number;
    totalMerit: number;
    isOperationParticipant: boolean;
}

/**
 * Resolve the header copy for the end-of-battle modal per D-32.
 *
 * Mapping (UI-SPEC Section G + Copywriting Contract):
 *   - winner side === my side → "승리" (win variant, green)
 *   - winner side !== my side → "패배" (loss variant, red)
 *   - draw / null winner / null mySide → "교전 종료" (draw variant, neutral)
 *
 * The `winner` param matches `BattleSummaryDto.winner` which the backend
 * serialises lowercase per 14-02 key-decision: "attacker_win" /
 * "defender_win" / "draw" / null.
 */
export function resolveHeader(
    winner: string | null,
    mySide: BattleSide | null,
): { text: string; variant: 'win' | 'loss' | 'draw' } {
    if (winner == null || mySide == null) {
        return { text: '교전 종료', variant: 'draw' };
    }
    if (winner === 'draw') {
        return { text: '교전 종료', variant: 'draw' };
    }
    const winningSide: BattleSide | null =
        winner === 'attacker_win' ? 'ATTACKER' : winner === 'defender_win' ? 'DEFENDER' : null;
    if (winningSide == null) {
        return { text: '교전 종료', variant: 'draw' };
    }
    return winningSide === mySide
        ? { text: '승리', variant: 'win' }
        : { text: '패배', variant: 'loss' };
}

/**
 * Format a single row's merit cell per D-33 / UI-SPEC Section G.
 *
 * Format when bonus > 0: "기본 {base} + 작전 +{bonus} = 총 {total}"
 * Format when bonus = 0: "기본 {base} = 총 {total}"   (no 작전 segment)
 *
 * Bonus = totalMerit - baseMerit, not recomputed from operationMultiplier
 * — this keeps the UI aligned with whatever integer rounding the backend
 * `buildBattleSummary` decided, so what the UI shows equals what was
 * credited to Officer.meritPoints.
 */
export function formatMeritBreakdown(row: BattleSummaryRowView): string {
    const bonus = row.totalMerit - row.baseMerit;
    if (bonus > 0) {
        return `기본 ${row.baseMerit} + 작전 +${bonus} = 총 ${row.totalMerit}`;
    }
    return `기본 ${row.baseMerit} = 총 ${row.totalMerit}`;
}

/**
 * Determine which side the logged-in officer fought on by scanning the
 * summary rows. Returns null if the officer has no row in the summary
 * (spectator, admin, or officerId not yet hydrated).
 */
export function computeMySide(
    rows: ReadonlyArray<{ officerId: number; side: BattleSide }>,
    myOfficerId: number | null,
): BattleSide | null {
    if (myOfficerId == null) return null;
    const myRow = rows.find((r) => r.officerId === myOfficerId);
    return myRow ? myRow.side : null;
}

// ───────────────────────────────────────────────────────────────────────
// Component
// ───────────────────────────────────────────────────────────────────────

export interface BattleEndModalProps {
    /**
     * Optional faction name override for the subtitle. Normally resolved
     * from worldStore / officerStore, but the prop lets parents inject it
     * directly when the store isn't hydrated (e.g. in Playwright fixtures).
     */
    factionName?: string;
}

/**
 * End-of-battle modal. Self-mounting: the component subscribes to
 * `tacticalStore.currentBattle` and opens automatically on the ACTIVE →
 * ENDED transition. Consumers just render `<BattleEndModal />` once in
 * the tactical layout.
 */
export function BattleEndModal({ factionName }: BattleEndModalProps = {}) {
    const router = useRouter();
    const battle = useTacticalStore((s) => s.currentBattle);
    const myOfficerId = useTacticalStore((s) => s.myOfficerId);
    const currentWorld = useWorldStore((s) => s.currentWorld);

    const [open, setOpen] = useState(false);
    const [summary, setSummary] = useState<BattleSummaryDto | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    // Track the id of the battle we've already opened for so we don't
    // re-open when the user dismisses and the store still holds ENDED.
    const [openedForBattleId, setOpenedForBattleId] = useState<number | null>(null);

    // ── ACTIVE → ENDED phase watcher (D-32) ─────────────────────────────
    useEffect(() => {
        if (!battle) return;
        if (battle.phase === 'ENDED' && openedForBattleId !== battle.id) {
            setOpen(true);
            setOpenedForBattleId(battle.id);
        }
    }, [battle, openedForBattleId]);

    // ── Fetch on open (D-33 merit breakdown) ────────────────────────────
    useEffect(() => {
        if (!open || summary || !battle) return;
        let cancelled = false;
        setLoading(true);
        setError(null);
        fetchBattleSummary(battle.sessionId, battle.id)
            .then((dto) => {
                if (!cancelled) setSummary(dto);
            })
            .catch((e: unknown) => {
                if (!cancelled) {
                    const message = e instanceof Error ? e.message : String(e);
                    setError(message);
                }
            })
            .finally(() => {
                if (!cancelled) setLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, [open, summary, battle]);

    // ── Derived header state ────────────────────────────────────────────
    const mySide: BattleSide | null = summary
        ? computeMySide(summary.rows, myOfficerId)
        : null;
    const header = summary
        ? resolveHeader(summary.winner, mySide)
        : { text: '교전 종료', variant: 'draw' as const };

    const headerColorClass =
        header.variant === 'win'
            ? 'text-emerald-500'
            : header.variant === 'loss'
            ? 'text-destructive'
            : 'text-foreground';

    // factionName is an optional prop; when omitted, render the subtitle
    // as just "{ticks}틱 교전" (the faction context is still visible in
    // the page chrome). The General/Officer entity does not currently
    // surface factionName directly, so we don't attempt a store fallback.
    const subtitle = summary
        ? `${factionName ? factionName + ' · ' : ''}${summary.durationTicks}틱 교전`
        : '';

    // ── Primary CTA navigation ──────────────────────────────────────────
    const handleReturnToGalaxy = () => {
        const sessionId = battle?.sessionId ?? currentWorld?.id;
        if (sessionId != null) {
            router.push(`/world/${sessionId}/galaxy`);
        }
        setOpen(false);
    };

    const handleViewHistory = () => {
        const sessionId = battle?.sessionId ?? currentWorld?.id;
        if (sessionId != null && battle?.id != null) {
            router.push(`/world/${sessionId}/battle/${battle.id}/history`);
        }
    };

    // Do not render the Dialog primitive at all when there's no battle.
    // Radix Dialog handles the open/closed state from the `open` prop but
    // omitting the tree entirely keeps Playwright snapshots clean.
    if (!battle) return null;

    return (
        <Dialog open={open} onOpenChange={setOpen}>
            <DialogContent
                className="max-w-[960px] w-full max-h-[80vh] flex flex-col p-0 gap-0"
                showCloseButton
            >
                {/* Header — 80px, bottom border, faction-tinted title. */}
                <DialogHeader className="h-20 px-8 py-4 border-b flex flex-row items-center gap-4">
                    <div className="flex flex-col gap-1 text-left">
                        <DialogTitle
                            className={`text-xl font-semibold leading-none ${headerColorClass}`}
                        >
                            {header.text}
                        </DialogTitle>
                        <DialogDescription className="text-sm text-muted-foreground">
                            {subtitle}
                        </DialogDescription>
                    </div>
                </DialogHeader>

                {/* Body — ScrollArea wrapping the merit breakdown table. */}
                <ScrollArea className="flex-1 px-8 py-6">
                    {loading || !summary ? (
                        <div className="flex flex-col items-center justify-center gap-4 py-12">
                            <p className="text-sm text-muted-foreground">
                                전투 결과를 집계하는 중입니다…
                            </p>
                            <div className="w-full flex flex-col gap-2">
                                <Skeleton className="h-8 w-full" />
                                <Skeleton className="h-8 w-full" />
                                <Skeleton className="h-8 w-full" />
                            </div>
                        </div>
                    ) : error ? (
                        <p className="text-sm text-destructive py-12 text-center">
                            전투 결과를 불러오지 못했습니다: {error}
                        </p>
                    ) : (
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>부대</TableHead>
                                    <TableHead className="text-right">
                                        함선 (잔존/초기)
                                    </TableHead>
                                    <TableHead className="text-right">격침</TableHead>
                                    <TableHead className="text-right">공적</TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {summary.rows.map((row) => {
                                    const destroyed = row.maxShips - row.survivingShips;
                                    const meritText = formatMeritBreakdown(row);
                                    return (
                                        <TableRow
                                            key={row.fleetId}
                                            data-operation={
                                                row.isOperationParticipant
                                                    ? 'true'
                                                    : undefined
                                            }
                                            className={
                                                row.isOperationParticipant
                                                    ? 'bg-[rgba(245,158,11,0.08)]'
                                                    : undefined
                                            }
                                        >
                                            <TableCell className="font-medium">
                                                <div className="flex items-center gap-2">
                                                    <span>{row.officerName}</span>
                                                    {row.isOperationParticipant && (
                                                        <Badge
                                                            variant="outline"
                                                            className="border-[#f59e0b] text-[#f59e0b]"
                                                        >
                                                            작전 참가
                                                        </Badge>
                                                    )}
                                                </div>
                                            </TableCell>
                                            <TableCell className="text-right font-mono">
                                                {row.survivingShips}/{row.maxShips}
                                            </TableCell>
                                            <TableCell className="text-right font-mono">
                                                {destroyed}
                                            </TableCell>
                                            <TableCell className="text-right font-mono">
                                                {meritText}
                                            </TableCell>
                                        </TableRow>
                                    );
                                })}
                            </TableBody>
                        </Table>
                    )}
                </ScrollArea>

                {/* Footer — 64px, top border, left/right CTAs per UI-SPEC Section G. */}
                <div
                    data-slot="dialog-footer"
                    className="h-16 px-6 border-t flex flex-row items-center justify-between"
                >
                    <Button variant="outline" onClick={handleViewHistory}>
                        전투 기록 보기
                    </Button>
                    <Button variant="default" onClick={handleReturnToGalaxy}>
                        전략맵으로 돌아가기
                    </Button>
                </div>
            </DialogContent>
        </Dialog>
    );
}

export default BattleEndModal;
