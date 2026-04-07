'use client';

import { useState, useEffect, useCallback } from 'react';
import { toast } from 'sonner';
import { Loader2 } from 'lucide-react';
import { commandApi } from '@/lib/gameApi';
import { publishWebSocket } from '@/lib/websocket';
import type { CommandTableEntry, CommandResult } from '@/types';

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
    onResult?: (result: CommandResult) => void;
}

export function CommandExecutionPanel({
    commands,
    officerId,
    sessionId,
    pcpCurrent,
    mcpCurrent,
    selectedGroup,
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

    if (filteredCommands.length === 0) {
        return (
            <div className="flex items-center justify-center h-24 text-slate-500 text-sm">
                사용 가능한 명령이 없습니다.
            </div>
        );
    }

    return (
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

                const isDisabled = !cmd.enabled || !hasEnoughCp || onCooldown || executing !== null;

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

                        {/* Execute button */}
                        <button
                            type="button"
                            disabled={isDisabled}
                            onClick={() => void handleExecute(cmd)}
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
                    </div>
                );
            })}
        </div>
    );
}
