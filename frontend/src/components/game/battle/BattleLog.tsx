'use client';

import { useEffect, useRef, useState } from 'react';
import { useBattleStore } from '@/stores/battleStore';
import type { BattleEvent, BattleEventType } from '@/types/tactical';
import { cn } from '@/lib/utils';

// ─── Log entry color ──────────────────────────────────────────────────────────
function entryColor(type: BattleEventType, message: string): string {
    if (type === 'info') return 'text-gray-500';
    if (type === 'result' || type === 'victory' || type === 'defeat') return 'text-amber-400';
    if (type === 'morale') return 'text-violet-400';
    // attack / damage / movement / move: detect side from keyword
    if (type === 'attack' || type === 'damage') {
        if (message.includes('제국') || message.includes('empire')) return 'text-amber-300';
        return 'text-sky-300';
    }
    if (type === 'movement' || type === 'move') {
        if (message.includes('제국') || message.includes('empire')) return 'text-amber-400/70';
        return 'text-sky-400/70';
    }
    return 'text-gray-400';
}

function entryPrefix(type: BattleEventType): string {
    switch (type) {
        case 'attack':
        case 'damage':
            return '⚡';
        case 'movement':
        case 'move':
            return '→';
        case 'morale':
            return '◎';
        case 'result':
        case 'victory':
        case 'defeat':
            return '★';
        case 'info':
        default:
            return '·';
    }
}

// ─── Single log row ───────────────────────────────────────────────────────────
function LogRow({ entry, isNew }: { entry: BattleEvent; isNew: boolean }) {
    const color = entryColor(entry.type, entry.message);
    const prefix = entryPrefix(entry.type);

    return (
        <div
            className={cn(
                'flex gap-1.5 text-[9px] font-mono leading-relaxed transition-opacity duration-300',
                color,
                isNew ? 'opacity-100' : 'opacity-70'
            )}
        >
            <span className="flex-shrink-0 text-gray-600 tabular-nums w-9">T{entry.turn}</span>
            <span className="flex-shrink-0">{prefix}</span>
            <span className="flex-1 min-w-0 break-words">{entry.message}</span>
        </div>
    );
}

// ─── BattleLog ────────────────────────────────────────────────────────────────
interface BattleLogProps {
    maxHeight?: number;
    collapsible?: boolean;
}

export function BattleLog({ maxHeight = 140, collapsible = true }: BattleLogProps) {
    const { battleLog, currentTurn } = useBattleStore();
    const scrollRef = useRef<HTMLDivElement>(null);
    const [collapsed, setCollapsed] = useState(false);
    const [prevCount, setPrevCount] = useState(battleLog.length);

    // Auto-scroll to bottom on new entries
    useEffect(() => {
        if (battleLog.length !== prevCount) {
            setPrevCount(battleLog.length);
            if (!collapsed && scrollRef.current) {
                scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
            }
        }
    }, [battleLog.length, prevCount, collapsed]);

    const newestIds = new Set(battleLog.slice(-3).map((e) => e.id));

    return (
        <div className="border border-gray-800/60 rounded bg-[#04040f]/80">
            {/* Header */}
            <div className="flex items-center justify-between px-2 py-1 border-b border-gray-800/40">
                <div className="flex items-center gap-2">
                    <span className="text-[9px] font-mono text-amber-500/60 tracking-widest uppercase">
                        전투 로그 // Battle Log
                    </span>
                    <span className="text-[8px] font-mono text-gray-600 tabular-nums">Turn {currentTurn}</span>
                </div>
                <div className="flex items-center gap-1.5">
                    <span className="text-[8px] font-mono text-gray-600 tabular-nums">{battleLog.length} entries</span>
                    {collapsible && (
                        <button
                            type="button"
                            onClick={() => setCollapsed((c) => !c)}
                            className="text-[9px] font-mono text-gray-600 hover:text-gray-400 transition-colors w-4 h-4 flex items-center justify-center"
                            aria-label={collapsed ? '전투 로그 펼치기' : '전투 로그 접기'}
                        >
                            {collapsed ? '▲' : '▼'}
                        </button>
                    )}
                </div>
            </div>

            {/* Log entries */}
            {!collapsed && (
                <div
                    ref={scrollRef}
                    className="overflow-y-auto px-2 py-1.5 space-y-0.5 scroll-smooth"
                    style={{ maxHeight }}
                >
                    {battleLog.length === 0 ? (
                        <div className="text-[9px] font-mono text-gray-700 text-center py-2">전투 이벤트 없음</div>
                    ) : (
                        battleLog.map((entry) => (
                            <LogRow key={entry.id} entry={entry} isNew={newestIds.has(entry.id)} />
                        ))
                    )}
                </div>
            )}
        </div>
    );
}
