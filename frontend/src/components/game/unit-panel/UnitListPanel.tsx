'use client';

import { useEffect, useState } from 'react';
import type { MilitaryUnit, FormationCaps, UnitType } from '@/types';
import { UNIT_TYPE_INFO } from '@/types';
import { unitApi } from '@/lib/gameApi';
import FormationCapBar from './FormationCapBar';

interface UnitListPanelProps {
    factionId: number;
    onSelectUnit: (unit: MilitaryUnit) => void;
}

const UNIT_ORDER: UnitType[] = ['FLEET', 'PATROL', 'TRANSPORT', 'GROUND', 'GARRISON', 'SOLO'];

export default function UnitListPanel({ factionId, onSelectUnit }: UnitListPanelProps) {
    const [units, setUnits] = useState<MilitaryUnit[]>([]);
    const [caps, setCaps] = useState<FormationCaps | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        let cancelled = false;
        async function load() {
            setLoading(true);
            try {
                const [unitsRes, capsRes] = await Promise.all([
                    unitApi.listByFaction(factionId),
                    unitApi.getFormationCaps(factionId),
                ]);
                if (cancelled) return;
                setUnits(unitsRes.data);
                setCaps(capsRes.data);
            } catch (err) {
                console.error('Failed to load units', err);
            } finally {
                if (!cancelled) setLoading(false);
            }
        }
        load();
        return () => { cancelled = true; };
    }, [factionId]);

    const grouped = UNIT_ORDER.reduce<Record<UnitType, MilitaryUnit[]>>((acc, type) => {
        acc[type] = units.filter((u) => u.unitType === type);
        return acc;
    }, {} as Record<UnitType, MilitaryUnit[]>);

    if (loading) {
        return <div className="p-4 text-gray-400 text-sm">부대 목록 불러오는 중...</div>;
    }

    return (
        <div className="space-y-4 p-4">
            {caps && <FormationCapBar caps={caps} />}

            {UNIT_ORDER.map((unitType) => {
                const list = grouped[unitType];
                if (list.length === 0) return null;

                const info = UNIT_TYPE_INFO[unitType];
                return (
                    <div key={unitType}>
                        <h3 className="text-sm font-semibold text-gray-300 border-b border-gray-700 pb-1 mb-2">
                            {info.nameKo} ({list.length})
                        </h3>
                        <div className="space-y-1">
                            {list.map((unit) => {
                                const commander = unit.crew.find((c) => c.slotRole === 'COMMANDER');
                                return (
                                    <button
                                        key={unit.id}
                                        type="button"
                                        onClick={() => onSelectUnit(unit)}
                                        className="w-full flex items-center justify-between px-3 py-2 rounded hover:bg-gray-800 transition-colors text-left text-sm"
                                    >
                                        <div>
                                            <span className="text-gray-200">{unit.name}</span>
                                            <span className="ml-2 text-gray-500">
                                                {commander ? commander.officerName : '없음'}
                                            </span>
                                        </div>
                                        <span className="text-gray-400 tabular-nums">
                                            {unit.currentUnits}/{unit.maxUnits}
                                        </span>
                                    </button>
                                );
                            })}
                        </div>
                    </div>
                );
            })}

            {units.length === 0 && (
                <p className="text-gray-500 text-sm text-center">편성된 부대가 없습니다.</p>
            )}
        </div>
    );
}
