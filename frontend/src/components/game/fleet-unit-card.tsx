'use client';

import type { FleetUnit, ShipClass } from '@/types';

// 함종 한국어 이름 매핑
export const SHIP_CLASS_NAMES: Record<string, string> = {
    battleship: '전함',
    fast_battleship: '고속전함',
    cruiser: '순양함',
    strike_cruiser: '타격순항함',
    destroyer: '구축함',
    fighter_carrier: '전투정모함',
    torpedo_carrier: '뇌격정모함',
    carrier: '항모',
    engineering: '공작함',
    transport: '수송함',
    hospital: '병원선',
    landing: '양륙함',
    civilian: '민간선',
    fortress: '요새',
};

// 숙련도 한국어 이름 (훈련도 기준)
function getCrewProficiency(training: number): string {
    if (training >= 80) return '정예';
    if (training >= 60) return '노련';
    if (training >= 40) return '보통';
    return '신병';
}

interface FleetUnitCardProps {
    unit: FleetUnit;
    unitNumber: number;
    onRemove?: () => void;
}

export function FleetUnitCard({ unit, unitNumber, onRemove }: FleetUnitCardProps) {
    const shipClassName = SHIP_CLASS_NAMES[unit.shipClass] ?? unit.shipClass;
    const proficiency = getCrewProficiency(unit.training);

    return (
        <div className="relative border border-gray-700 bg-gray-900/80 rounded-none p-3 text-xs space-y-2"
             style={{ borderLeft: '4px solid #3b82f6' }}>
            {/* Header */}
            <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                    <span className="font-bold text-blue-300">{unitNumber}번 부대</span>
                    <span className="text-gray-400">{shipClassName}</span>
                </div>
                {onRemove && (
                    <button
                        type="button"
                        onClick={onRemove}
                        className="text-red-400 hover:text-red-300 text-[10px] border border-red-800 px-1.5 py-0.5 rounded-none"
                    >
                        제거
                    </button>
                )}
            </div>

            {/* 함종 / 함수 */}
            <div className="grid grid-cols-2 gap-x-4">
                <div className="flex justify-between">
                    <span className="text-gray-500">함종</span>
                    <span className="text-gray-200">{shipClassName}</span>
                </div>
                <div className="flex justify-between">
                    <span className="text-gray-500">함수</span>
                    <span className="text-blue-300 tabular-nums font-bold">{unit.ships.toLocaleString()}</span>
                </div>
            </div>

            {/* 사기 */}
            <div className="space-y-0.5">
                <div className="flex justify-between">
                    <span className="text-gray-500">사기</span>
                    <span className="text-yellow-400 tabular-nums">{unit.morale}</span>
                </div>
                <div className="w-full bg-gray-800 h-1.5 rounded-none">
                    <div
                        className="bg-yellow-500 h-1.5 rounded-none transition-all"
                        style={{ width: `${Math.min(unit.morale, 100)}%` }}
                    />
                </div>
            </div>

            {/* 훈련도 */}
            <div className="space-y-0.5">
                <div className="flex justify-between">
                    <span className="text-gray-500">훈련도</span>
                    <span className="flex items-center gap-1">
                        <span className="text-green-400 tabular-nums">{unit.training}</span>
                        <span className="text-gray-500">({proficiency})</span>
                    </span>
                </div>
                <div className="w-full bg-gray-800 h-1.5 rounded-none">
                    <div
                        className="bg-green-500 h-1.5 rounded-none transition-all"
                        style={{ width: `${Math.min(unit.training, 100)}%` }}
                    />
                </div>
            </div>
        </div>
    );
}
