'use client';

interface CpDisplayProps {
    pcpCurrent: number;
    pcpMax: number;
    mcpCurrent: number;
    mcpMax: number;
    regenRate: number;
}

export function CpDisplay({ pcpCurrent, pcpMax, mcpCurrent, mcpMax, regenRate }: CpDisplayProps) {
    const pcpPct = pcpMax > 0 ? Math.min(100, (pcpCurrent / pcpMax) * 100) : 0;
    const mcpPct = mcpMax > 0 ? Math.min(100, (mcpCurrent / mcpMax) * 100) : 0;

    return (
        <div className="bg-slate-900 border border-slate-700 rounded w-full text-sm px-3 py-2 space-y-2">
            {/* PCP row */}
            <div>
                <div className="flex items-center justify-between mb-1">
                    <span className="text-[11px] text-slate-400">PCP 정략</span>
                    <span className="text-[11px] text-slate-300 tabular-nums">
                        {pcpCurrent}/{pcpMax}
                    </span>
                </div>
                <div className="bg-slate-800 rounded-full h-2 overflow-hidden">
                    <div
                        className="h-full rounded-full bg-blue-500 transition-all"
                        style={{ width: `${pcpPct}%` }}
                    />
                </div>
            </div>

            {/* MCP row */}
            <div>
                <div className="flex items-center justify-between mb-1">
                    <span className="text-[11px] text-slate-400">MCP 군사</span>
                    <span className="text-[11px] text-slate-300 tabular-nums">
                        {mcpCurrent}/{mcpMax}
                    </span>
                </div>
                <div className="bg-slate-800 rounded-full h-2 overflow-hidden">
                    <div
                        className="h-full rounded-full bg-orange-500 transition-all"
                        style={{ width: `${mcpPct}%` }}
                    />
                </div>
            </div>

            {/* Regen hint */}
            <p className="text-[10px] text-slate-500">5분마다 {regenRate}씩 회복</p>
        </div>
    );
}
