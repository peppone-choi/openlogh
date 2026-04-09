'use client';

import { useState, useEffect, useCallback } from 'react';
import { toast } from 'sonner';
import { Loader2 } from 'lucide-react';
import { commandApi } from '@/lib/gameApi';
import { publishWebSocket } from '@/lib/websocket';
import {
    Tooltip,
    TooltipContent,
    TooltipProvider,
    TooltipTrigger,
} from '@/components/ui/tooltip';
import { canCommandUnit } from '@/lib/canCommandUnit';
import { useCommandStore } from '@/stores/commandStore';
import type { CommandTableEntry, CommandResult } from '@/types';
import type { CommandHierarchyDto, TacticalUnit } from '@/types/tactical';

// CommandGroup type from the legacy index: commandGroup field is a string
type CommandGroup =
    | 'OPERATION'
    | 'PERSONAL'
    | 'COMMAND'
    | 'LOGISTICS'
    | 'PERSONNEL'
    | 'POLITICS'
    | 'INTELLIGENCE';

interface CommandExecutionPanelProps {
    commands: CommandTableEntry[];
    officerId: number;
    sessionId: number;
    pcpCurrent: number;
    mcpCurrent: number;
    selectedGroup: CommandGroup | null;
    /**
     * Phase 14 — Plan 14-14 (FE-03, D-09..D-12) — currently selected tactical
     * unit for FE-03 permission gating. When provided together with
     * `myHierarchy`, each command button is wrapped in a Radix tooltip and
     * disabled when `canCommandUnit(officerId, myHierarchy, selectedUnit)` is
     * not allowed. Shift+click on a disabled button dispatches the proposal
     * flow via `commandStore.createProposal`.
     */
    selectedUnit?: TacticalUnit | null;
    /** Phase 14 — FE-03 — logged-in officer's side command hierarchy. */
    myHierarchy?: CommandHierarchyDto | null;
    /** Phase 14 — FE-03 — optional display name for the approver in the toast body. */
    superiorName?: string;
    onResult?: (result: CommandResult) => void;
}

export function CommandExecutionPanel({
    commands,
    officerId,
    sessionId,
    pcpCurrent,
    mcpCurrent,
    selectedGroup,
    selectedUnit,
    myHierarchy,
    superiorName,
    onResult,
}: CommandExecutionPanelProps) {
    const [executing, setExecuting] = useState<string | null>(null);
    // Map<actionCode, cooldownEndEpochMs>
    const [cooldowns, setCooldowns] = useState<Map<string, number>>(new Map());
    const [now, setNow] = useState(() => Date.now());

    // Tick every second to refresh cooldown timers
    useEffect(() => {
        const id = setInterval(() => setNow(Date.now()), 1000);
        return () => clearInterval(id);
    }, []);

    const setCooldown = useCallback((code: string, seconds: number) => {
        if (seconds <= 0) return;
        setCooldowns((prev) => {
            const next = new Map(prev);
            next.set(code, Date.now() + seconds * 1000);
            return next;
        });
    }, []);

    const filteredCommands = selectedGroup
        ? commands.filter((cmd) => cmd.commandGroup === selectedGroup)
        : commands;

    const handleExecute = async (cmd: CommandTableEntry) => {
        if (executing) return;
        setExecuting(cmd.actionCode);
        try {
            // Try WebSocket publish to strategic command channel (fire-and-forget)
            publishWebSocket(`/app/command/${sessionId}/execute`, {
                officerId,
                commandCode: cmd.actionCode,
                args: {},
            });

            // REST call for authoritative result
            const { data: result } = await commandApi.execute(officerId, cmd.actionCode, {});

            if (result.success) {
                const msg = Array.isArray(result.logs) ? result.logs[0] : result.message;
                toast.success(msg ?? '명령 성공');
                setCooldown(cmd.actionCode, cmd.durationSeconds ?? 0);
            } else {
                const msg = Array.isArray(result.logs) ? result.logs[0] : result.message;
                toast.error(msg ?? '명령 실패');
            }
            onResult?.(result);
        } catch (err) {
            const message = err instanceof Error ? err.message : '명령 실행 오류';
            toast.error(message);
            console.error('Command execute error:', err);
        } finally {
            setExecuting(null);
        }
    };

    /**
     * Phase 14 — Plan 14-14 (FE-03, D-10) — Shift+click on a gated button
     * dispatches a proposal to the superior officer instead of trying to
     * execute the command directly. Plain clicks on disabled buttons are a
     * no-op (the DOM won't even fire the handler; the guard below is a
     * belt-and-braces check for tests that bypass the `disabled` attr).
     */
    const handleGatedClick = useCallback(
        async (
            cmd: CommandTableEntry,
            event: React.MouseEvent<HTMLButtonElement>,
        ) => {
            if (!event.shiftKey) return;
            if (!selectedUnit || !myHierarchy) return;

            // The approver is normally the fleet commander (delegated or not)
            // per D-10. Sub-fleet commanders fall back to the top-level
            // fleetCommander since a gated button means the current officer
            // isn't already in the target's chain.
            const superiorOfficerId =
                myHierarchy.activeCommander ?? myHierarchy.fleetCommander;

            try {
                await useCommandStore.getState().createProposal(
                    officerId,
                    cmd.actionCode,
                    {
                        targetOfficerId: selectedUnit.officerId,
                        superiorOfficerId,
                        targetFleetId: selectedUnit.fleetId,
                    },
                );
                const approverLabel = superiorName ?? '상위자';
                toast.success(`${cmd.name} 제안을 ${approverLabel}에게 발송했습니다.`);
            } catch (err) {
                console.error('createProposal failed:', err);
                toast.error('제안 발송 실패');
            }
        },
        [officerId, selectedUnit, myHierarchy, superiorName],
    );

    if (filteredCommands.length === 0) {
        return (
            <div className="flex items-center justify-center h-24 text-slate-500 text-sm">
                사용 가능한 명령이 없습니다.
            </div>
        );
    }

    return (
        <TooltipProvider delayDuration={400}>
            <div className="overflow-y-auto divide-y divide-slate-800">
                {filteredCommands.map((cmd) => {
                    const cooldownEnd = cooldowns.get(cmd.actionCode) ?? 0;
                    const remainSec = Math.max(0, Math.ceil((cooldownEnd - now) / 1000));
                    const onCooldown = remainSec > 0;
                    const isExecuting = executing === cmd.actionCode;
                    const cpCost = cmd.commandPointCost ?? 0;
                    const poolType = cmd.poolType ?? 'PCP';

                    const hasEnoughCp =
                        poolType === 'PCP' ? pcpCurrent >= cpCost : mcpCurrent >= cpCost;

                    // FE-03 gating — compute only when both the selected unit
                    // and hierarchy are present. When either is missing we
                    // fall back to "allowed" so non-tactical command panels
                    // (e.g. strategic phase) keep working.
                    const gate =
                        selectedUnit && myHierarchy !== undefined
                            ? canCommandUnit(officerId, myHierarchy, selectedUnit)
                            : { allowed: true, reason: null };

                    // Non-gating disabled states: cooldown, insufficient CP,
                    // explicitly disabled from the command table, or an
                    // in-flight execution. These use the native `disabled`
                    // attribute so browsers skip the click entirely.
                    const isHardDisabled =
                        !cmd.enabled ||
                        !hasEnoughCp ||
                        onCooldown ||
                        executing !== null;

                    // FE-03 gating uses aria-disabled instead of native
                    // `disabled` so Shift+click still dispatches the proposal
                    // handler (per D-10). The visual state + accessibility
                    // semantics remain the same as a disabled button.
                    const isGated = !gate.allowed;
                    const isDisabled = isHardDisabled || isGated;

                    const gateTooltipBody =
                        gate.reason === 'JAMMED'
                            ? (gate.message ?? '통신 방해 중 — 명령 발령 불가')
                            : '지휘권 없음 — Shift+클릭으로 상위자에게 제안하기';

                    const button = (
                        <button
                            type="button"
                            // Hard-disabled (cooldown/CP/…) uses native attr
                            // so the browser suppresses the click. Gated-only
                            // state relies on aria-disabled so onClick still
                            // fires and we can inspect event.shiftKey.
                            disabled={isHardDisabled}
                            aria-disabled={isDisabled || undefined}
                            onClick={(event) => {
                                if (isHardDisabled) return;
                                if (isGated) {
                                    void handleGatedClick(cmd, event);
                                    return;
                                }
                                void handleExecute(cmd);
                            }}
                            aria-label={isGated ? gateTooltipBody : cmd.name}
                            className={`shrink-0 px-2.5 py-1 text-xs rounded transition-colors ${
                                isDisabled
                                    ? 'bg-slate-700 text-slate-500 cursor-not-allowed'
                                    : 'bg-blue-700 text-white hover:bg-blue-600 active:bg-blue-800'
                            }`}
                        >
                            {isExecuting ? (
                                <Loader2 className="size-3 animate-spin" />
                            ) : (
                                '실행'
                            )}
                        </button>
                    );

                    return (
                        <div
                            key={cmd.actionCode}
                            className="flex items-center gap-3 px-3 py-2.5 bg-slate-900 hover:bg-slate-800 transition-colors"
                        >
                            {/* Command name */}
                            <div className="flex-1 min-w-0">
                                <div className="text-sm text-white font-medium truncate">{cmd.name}</div>
                                {cmd.reason && (
                                    <div className="text-[11px] text-slate-500 truncate">{cmd.reason}</div>
                                )}
                            </div>

                            {/* CP cost badge */}
                            <span
                                className={`shrink-0 text-[11px] px-1.5 py-0.5 rounded tabular-nums ${
                                    poolType === 'PCP'
                                        ? 'bg-blue-700 text-white'
                                        : 'bg-orange-600 text-white'
                                }`}
                            >
                                {poolType} {cpCost}
                            </span>

                            {/* Cooldown text */}
                            {(cmd.durationSeconds ?? 0) > 0 && (
                                <span className="shrink-0 text-[11px] text-slate-500 w-16 text-right tabular-nums">
                                    {onCooldown ? `${remainSec}초 대기` : `${cmd.durationSeconds}초`}
                                </span>
                            )}

                            {/* Execute button — wrapped in Radix Tooltip when gated
                                per D-09. We also include a sr-only span so the
                                gating copy ("지휘권 없음") is reachable by RTL
                                textContent assertions without opening the portal. */}
                            {!gate.allowed ? (
                                <Tooltip>
                                    <TooltipTrigger asChild>
                                        <span className="inline-flex">
                                            {button}
                                            <span className="sr-only">
                                                {gateTooltipBody}
                                            </span>
                                        </span>
                                    </TooltipTrigger>
                                    <TooltipContent>
                                        <span className="inline-flex items-center gap-1">
                                            지휘권 없음 —{' '}
                                            <kbd className="px-1 rounded border border-foreground/40 text-[10px]">
                                                Shift
                                            </kbd>
                                            +클릭으로 상위자에게 제안하기
                                        </span>
                                    </TooltipContent>
                                </Tooltip>
                            ) : (
                                button
                            )}
                        </div>
                    );
                })}
            </div>
        </TooltipProvider>
    );
}
