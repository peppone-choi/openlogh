'use client';

import { useEffect, useState, useCallback } from 'react';
import { cn } from '@/lib/utils';

interface CpDisplayProps {
    /** Political Command Points (정략 CP) */
    pcp: number;
    /** Military Command Points (군사 CP) */
    mcp: number;
    /** Maximum CP per type (default 10) */
    maxCp?: number;
    /** Turn term in minutes (for recovery timer calculation) */
    turnTerm?: number;
    /** Last executed timestamp (ISO string) — used to compute next recovery */
    lastExecuted?: string | null;
}

/** Recovery interval: every 5 real-time minutes = 2 game hours */
const RECOVERY_INTERVAL_MS = 5 * 60 * 1000;

function formatCountdown(ms: number): string {
    if (ms <= 0) return '00:00';
    const totalSec = Math.ceil(ms / 1000);
    const min = Math.floor(totalSec / 60);
    const sec = totalSec % 60;
    return `${String(min).padStart(2, '0')}:${String(sec).padStart(2, '0')}`;
}

/**
 * Dual CP bar showing PCP (정략) and MCP (군사) separately
 * with a recovery countdown timer.
 *
 * gin7 rules:
 * - CP recovers every 5 real-time minutes
 * - Cross-use: PCP/MCP can substitute at 2x cost
 */
export function CpDisplay({ pcp, mcp, maxCp = 10, lastExecuted }: CpDisplayProps) {
    const [countdown, setCountdown] = useState<number>(0);

    const computeCountdown = useCallback(() => {
        if (!lastExecuted) return RECOVERY_INTERVAL_MS;
        const lastMs = new Date(lastExecuted).getTime();
        if (Number.isNaN(lastMs)) return RECOVERY_INTERVAL_MS;
        const now = Date.now();
        const elapsed = now - lastMs;
        const remaining = RECOVERY_INTERVAL_MS - (elapsed % RECOVERY_INTERVAL_MS);
        return remaining;
    }, [lastExecuted]);

    useEffect(() => {
        setCountdown(computeCountdown());
        const interval = setInterval(() => {
            setCountdown(computeCountdown());
        }, 1000);
        return () => clearInterval(interval);
    }, [computeCountdown]);

    const pcpPercent = maxCp > 0 ? Math.min(100, (pcp / maxCp) * 100) : 0;
    const mcpPercent = maxCp > 0 ? Math.min(100, (mcp / maxCp) * 100) : 0;

    return (
        <div className="space-y-1.5">
            <div className="flex items-center justify-between">
                <span className="text-[10px] font-mono text-amber-500/70 tracking-widest uppercase">
                    커맨드 포인트 // CP
                </span>
                <span className="text-[9px] font-mono text-gray-500">
                    회복{' '}
                    <span className={cn('font-bold', countdown <= 30000 ? 'text-amber-400' : 'text-gray-400')}>
                        {formatCountdown(countdown)}
                    </span>
                </span>
            </div>

            {/* PCP Bar */}
            <div className="space-y-0.5">
                <div className="flex items-center justify-between gap-1">
                    <div className="flex items-center gap-1.5 min-w-0 flex-1">
                        <span className="text-[10px] font-mono font-bold w-8 shrink-0 text-blue-400">PCP</span>
                        <span className="text-[8px] text-gray-600 truncate">정략 커맨드</span>
                    </div>
                    <span className="text-[11px] font-mono font-bold text-blue-300 w-12 text-right tabular-nums shrink-0">
                        {pcp}/{maxCp}
                    </span>
                </div>
                <div className="relative h-3 flex items-center">
                    <div className="absolute inset-0 bg-gray-800/80 rounded-full" />
                    <div
                        className="absolute inset-y-0 left-0 rounded-full bg-blue-500 transition-all duration-300"
                        style={{ width: `${pcpPercent}%`, opacity: 0.85 }}
                    />
                    {/* Segment markers */}
                    {Array.from({ length: maxCp - 1 }, (_, i) => (
                        <div
                            key={i}
                            className="absolute top-0 bottom-0 w-px bg-gray-700/50"
                            style={{ left: `${((i + 1) / maxCp) * 100}%` }}
                        />
                    ))}
                </div>
            </div>

            {/* MCP Bar */}
            <div className="space-y-0.5">
                <div className="flex items-center justify-between gap-1">
                    <div className="flex items-center gap-1.5 min-w-0 flex-1">
                        <span className="text-[10px] font-mono font-bold w-8 shrink-0 text-red-400">MCP</span>
                        <span className="text-[8px] text-gray-600 truncate">군사 커맨드</span>
                    </div>
                    <span className="text-[11px] font-mono font-bold text-red-300 w-12 text-right tabular-nums shrink-0">
                        {mcp}/{maxCp}
                    </span>
                </div>
                <div className="relative h-3 flex items-center">
                    <div className="absolute inset-0 bg-gray-800/80 rounded-full" />
                    <div
                        className="absolute inset-y-0 left-0 rounded-full bg-red-500 transition-all duration-300"
                        style={{ width: `${mcpPercent}%`, opacity: 0.85 }}
                    />
                    {Array.from({ length: maxCp - 1 }, (_, i) => (
                        <div
                            key={i}
                            className="absolute top-0 bottom-0 w-px bg-gray-700/50"
                            style={{ left: `${((i + 1) / maxCp) * 100}%` }}
                        />
                    ))}
                </div>
            </div>

            {/* Cross-use hint */}
            <div className="text-[7px] font-mono text-gray-700 leading-relaxed">PCP/MCP 교차 사용 시 2배 소모</div>
        </div>
    );
}
