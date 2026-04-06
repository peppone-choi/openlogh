'use client';

import type { MilitaryUnit } from '@/types';
import { UNIT_TYPE_INFO } from '@/types';
import CrewRosterPanel from './CrewRosterPanel';

interface UnitDetailPanelProps {
    unit: MilitaryUnit;
}

export default function UnitDetailPanel({ unit }: UnitDetailPanelProps) {
    const typeInfo = UNIT_TYPE_INFO[unit.unitType];
    const shipCount = unit.currentUnits * 300; // 300 ships per unit (gin7 standard)

    return (
        <div className="border border-gray-700 rounded-lg p-4 space-y-4">
            <div>
                <h3 className="text-lg font-bold text-gray-100">{unit.name}</h3>
                <span className="text-sm text-gray-400">{typeInfo.nameKo} ({typeInfo.nameEn})</span>
            </div>

            <div className="space-y-1 text-sm">
                <h4 className="font-semibold text-gray-300">편성 현황</h4>
                {unit.unitType === 'SOLO' ? (
                    <p className="text-gray-400">기함 1척</p>
                ) : unit.unitType === 'GARRISON' ? (
                    <>
                        <p className="text-gray-400">
                            유닛: {unit.currentUnits}/{unit.maxUnits}
                        </p>
                        {unit.planetId != null && (
                            <p className="text-gray-400">
                                주둔 행성 ID: {unit.planetId}
                            </p>
                        )}
                    </>
                ) : (
                    <>
                        <p className="text-gray-400">
                            유닛: {unit.currentUnits}/{unit.maxUnits}
                        </p>
                        <p className="text-gray-400">
                            함선: {shipCount.toLocaleString()}/{typeInfo.maxShips.toLocaleString()}척
                        </p>
                    </>
                )}
            </div>

            <CrewRosterPanel unit={unit} />
        </div>
    );
}
