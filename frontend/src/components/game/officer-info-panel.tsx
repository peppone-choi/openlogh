'use client';

import type { Officer } from '@/types/officer';

const STAT_CONFIG = [
    { key: 'leadership',     label: '통솔', color: '#eab308' },  // yellow
    { key: 'command',        label: '지휘', color: '#f97316' },  // orange
    { key: 'intelligence',   label: '정보', color: '#06b6d4' },  // cyan
    { key: 'politics',       label: '정치', color: '#22c55e' },  // green
    { key: 'administration', label: '운영', color: '#a855f7' },  // purple
    { key: 'mobility',       label: '기동', color: '#0ea5e9' },  // sky
    { key: 'attack',         label: '공격', color: '#ef4444' },  // red
    { key: 'defense',        label: '방어', color: '#3b82f6' },  // blue
] as const;

type StatKey = typeof STAT_CONFIG[number]['key'];

interface OfficerInfoPanelProps {
    officer: Officer;
}

export function OfficerInfoPanel({ officer }: OfficerInfoPanelProps) {
    return (
        <div className="bg-slate-900 border border-slate-700 rounded w-full text-sm">
            {/* Header: name + rank */}
            <div className="px-3 py-2 border-b border-slate-700 flex items-center justify-between gap-2">
                <span className="font-bold text-white truncate">{officer.name}</span>
                <span className="shrink-0 text-[11px] bg-slate-700 text-slate-300 px-1.5 py-0.5 rounded">
                    {officer.rankTitle}
                </span>
            </div>

            {/* 8-stat bars */}
            <div className="px-3 py-2 space-y-1.5">
                {STAT_CONFIG.map(({ key, label, color }) => {
                    const value = officer[key as StatKey] as number;
                    const pct = Math.min(100, Math.max(0, value));
                    return (
                        <div key={key} className="flex items-center gap-2">
                            <span className="text-[11px] text-slate-400 w-8 shrink-0 text-right">{label}</span>
                            <div className="flex-1 bg-slate-800 rounded-full h-2 overflow-hidden">
                                <div
                                    className="h-full rounded-full transition-all"
                                    style={{ width: `${pct}%`, backgroundColor: color }}
                                />
                            </div>
                            <span className="text-[11px] text-slate-300 w-7 text-right tabular-nums">{value}</span>
                        </div>
                    );
                })}
            </div>

            {/* Position card summary */}
            <div className="px-3 py-2 border-t border-slate-700">
                <span className="text-[11px] text-slate-400">
                    직무카드 <span className="text-white font-bold">{officer.positionCards.length}</span>장
                </span>
            </div>
        </div>
    );
}
