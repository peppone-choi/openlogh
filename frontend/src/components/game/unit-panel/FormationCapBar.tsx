'use client';

import type { FormationCaps, UnitType } from '@/types';
import { UNIT_TYPE_INFO } from '@/types';

interface FormationCapBarProps {
    caps: FormationCaps;
}

const DISPLAY_TYPES: UnitType[] = ['FLEET', 'PATROL', 'TRANSPORT', 'GROUND', 'GARRISON'];

export default function FormationCapBar({ caps }: FormationCapBarProps) {
    return (
        <div className="space-y-2">
            <h3 className="text-sm font-semibold text-gray-300">편성 현황</h3>
            {DISPLAY_TYPES.map((unitType) => {
                const info = UNIT_TYPE_INFO[unitType];
                const cap = caps.caps[unitType];
                if (!cap) return null;

                const { current, max, available } = cap;
                const ratio = max > 0 ? (current / max) * 100 : 0;
                const isFull = current >= max;

                return (
                    <div key={unitType} className="flex items-center gap-2 text-sm">
                        <span className="w-20 shrink-0 text-gray-400">
                            {info.nameKo}
                        </span>
                        {max === 0 ? (
                            <span className="text-gray-500">--</span>
                        ) : (
                            <>
                                <div className="flex-1 h-3 rounded bg-gray-700 overflow-hidden">
                                    <div
                                        className={`h-full rounded transition-all ${isFull ? 'bg-red-500' : 'bg-blue-600'}`}
                                        style={{ width: `${Math.min(ratio, 100)}%` }}
                                    />
                                </div>
                                <span className="w-16 text-right tabular-nums text-gray-300">
                                    {current}/{max}
                                </span>
                                <span
                                    className={`w-12 text-right tabular-nums ${available > 0 ? 'text-green-400' : 'text-red-400'}`}
                                >
                                    +{available}
                                </span>
                            </>
                        )}
                    </div>
                );
            })}
        </div>
    );
}
