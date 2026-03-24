'use client';

interface PhaseCounterProps {
    current: number;
    total: number;
}

export function PhaseCounter({ current, total }: PhaseCounterProps) {
    return (
        <div className="flex items-center justify-center">
            <div className="bg-black/70 border border-gray-600 rounded px-3 py-1">
                <span className="text-xs text-gray-400 mr-1">Phase</span>
                <span className="font-mono text-white font-bold tabular-nums">
                    {current + 1}/{total}
                </span>
            </div>
        </div>
    );
}
