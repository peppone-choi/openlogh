'use client';

interface HealthBarProps {
    label: string;
    current: number;
    max: number;
    color: string;
    side: 'left' | 'right';
}

export function HealthBar({ label, current, max, color, side }: HealthBarProps) {
    const pct = max > 0 ? Math.max(0, Math.min(100, (current / max) * 100)) : 0;

    return (
        <div
            className={`flex flex-col gap-0.5 min-w-[140px] max-w-[200px] ${side === 'right' ? 'items-end' : 'items-start'}`}
        >
            <div
                className={`flex items-center gap-1 text-xs text-white font-medium ${side === 'right' ? 'flex-row-reverse' : ''}`}
            >
                <span>{label}</span>
                <span className="text-gray-300 tabular-nums">
                    {current.toLocaleString()}/{max.toLocaleString()}
                </span>
            </div>
            <div className="w-full h-3 bg-gray-800 rounded-full overflow-hidden">
                <div
                    className="h-full rounded-full transition-all duration-500 ease-out"
                    style={{
                        width: `${pct}%`,
                        backgroundColor: color,
                        ...(side === 'right' ? { marginLeft: 'auto' } : {}),
                    }}
                />
            </div>
            <div className={`text-[10px] text-gray-400 tabular-nums ${side === 'right' ? 'text-right' : ''}`}>
                {pct.toFixed(1)}%
            </div>
        </div>
    );
}
