'use client';

import { useState } from 'react';
import axios from 'axios';
import { toast } from 'sonner';
import type { Fleet, FleetUnit, Formation } from '@/types';
import { FleetUnitCard } from './fleet-unit-card';

// 진형 한국어 이름 매핑
const FORMATION_NAMES: Record<Formation, string> = {
    SPINDLE: '방추형',
    BY_CLASS: '함종별',
    MIXED: '혼성',
    THREE_COLUMN: '삼열종대',
};

const FORMATIONS: Formation[] = ['SPINDLE', 'BY_CLASS', 'MIXED', 'THREE_COLUMN'];

// 최대 유닛 슬롯 수 (gin7 기준 최대 8 부대)
const MAX_UNITS = 8;

interface FleetCompositionPanelProps {
    fleet: Fleet;
    units: FleetUnit[];
    sessionId: number;
    officerName?: string;
    rankTitle?: string;
    onRefresh?: () => void;
}

export function FleetCompositionPanel({
    fleet,
    units,
    sessionId,
    officerName,
    rankTitle,
    onRefresh,
}: FleetCompositionPanelProps) {
    const [formation, setFormation] = useState<Formation>(fleet.formation);
    const [savingFormation, setSavingFormation] = useState(false);

    const totalShips = units.reduce((sum, u) => sum + u.ships, 0);
    const avgMorale =
        units.length > 0 ? Math.round(units.reduce((sum, u) => sum + u.morale, 0) / units.length) : 0;
    const avgTraining =
        units.length > 0 ? Math.round(units.reduce((sum, u) => sum + u.training, 0) / units.length) : 0;

    const handleFormationChange = async (newFormation: Formation) => {
        setFormation(newFormation);
        setSavingFormation(true);
        try {
            await axios.patch(`/api/${sessionId}/fleets/${fleet.id}/formation`, {
                formation: newFormation,
            });
            toast.success(`진형을 ${FORMATION_NAMES[newFormation]}으로 변경했습니다.`);
            onRefresh?.();
        } catch {
            toast.error('진형 변경에 실패했습니다.');
            setFormation(fleet.formation);
        } finally {
            setSavingFormation(false);
        }
    };

    const handleAssignUnits = () => {
        toast.info('편성 변경 준비 중');
    };

    // 슬롯 배열 구성: 최대 MAX_UNITS 슬롯, 빈 슬롯은 null
    const slots: (FleetUnit | null)[] = Array.from({ length: MAX_UNITS }, (_, i) => units[i] ?? null);

    return (
        <div className="border border-gray-700 bg-gray-950/60 rounded-none">
            {/* Header */}
            <div className="border-b border-gray-700 px-4 py-3">
                <div className="flex items-start justify-between">
                    <div>
                        <h3 className="font-bold text-sm text-white">{fleet.name}</h3>
                        {officerName && (
                            <p className="text-xs text-gray-400 mt-0.5">
                                {rankTitle ? `[${rankTitle}] ` : ''}{officerName}
                            </p>
                        )}
                    </div>
                    <span className="text-xs border border-blue-700 text-blue-400 px-2 py-0.5 rounded-none">
                        {FORMATION_NAMES[formation]}
                    </span>
                </div>
            </div>

            {/* Formation selector */}
            <div className="border-b border-gray-700 px-4 py-2">
                <p className="text-[10px] text-gray-500 mb-1.5">진형 선택</p>
                <div className="flex gap-2 flex-wrap">
                    {FORMATIONS.map((f) => (
                        <button
                            key={f}
                            type="button"
                            disabled={savingFormation}
                            onClick={() => handleFormationChange(f)}
                            className={`text-xs px-2.5 py-1 rounded-none border transition-colors ${
                                formation === f
                                    ? 'border-blue-500 bg-blue-900/40 text-blue-300'
                                    : 'border-gray-600 text-gray-400 hover:border-gray-500 hover:text-gray-300'
                            }`}
                        >
                            {FORMATION_NAMES[f]}
                        </button>
                    ))}
                </div>
            </div>

            {/* Unit grid */}
            <div className="p-4 grid grid-cols-1 sm:grid-cols-2 gap-3">
                {slots.map((unit, idx) =>
                    unit ? (
                        <FleetUnitCard key={unit.id} unit={unit} unitNumber={idx + 1} />
                    ) : (
                        <div
                            key={`empty-${idx}`}
                            className="border border-dashed border-gray-700 bg-gray-900/30 rounded-none p-3 flex items-center justify-center text-xs text-gray-600"
                        >
                            {idx + 1}번 빈 부대 슬롯
                        </div>
                    )
                )}
            </div>

            {/* Stats summary */}
            <div className="border-t border-gray-700 px-4 py-2 flex items-center gap-6 text-xs text-gray-400">
                <span>
                    총 함선:{' '}
                    <span className="text-blue-400 tabular-nums font-bold">{totalShips.toLocaleString()}</span>
                </span>
                <span>
                    평균 사기: <span className="text-yellow-400 tabular-nums">{avgMorale}</span>
                </span>
                <span>
                    평균 훈련도: <span className="text-green-400 tabular-nums">{avgTraining}</span>
                </span>
            </div>

            {/* Action */}
            <div className="border-t border-gray-700 px-4 py-3">
                <button
                    type="button"
                    onClick={handleAssignUnits}
                    className="text-xs border border-gray-600 text-gray-300 hover:border-gray-400 px-3 py-1.5 rounded-none transition-colors"
                >
                    부대 편성 변경
                </button>
            </div>
        </div>
    );
}
