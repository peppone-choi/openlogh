'use client';

import { useCallback, useEffect, useState } from 'react';
import axios from 'axios';
import { Shield } from 'lucide-react';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { EmptyState } from '@/components/game/empty-state';
import { FleetCompositionPanel } from '@/components/game/fleet-composition-panel';
import type { Fleet, FleetUnit } from '@/types';

interface FleetWithUnits {
    fleet: Fleet;
    units: FleetUnit[];
}

export default function TroopPage() {
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const { myOfficer, fetchMyOfficer } = useOfficerStore();
    const [fleets, setFleets] = useState<FleetWithUnits[]>([]);
    const [loading, setLoading] = useState(true);
    const [activeTab, setActiveTab] = useState(0);

    useEffect(() => {
        if (!currentWorld) return;
        if (!myOfficer) fetchMyOfficer(currentWorld.id).catch(() => {});
    }, [currentWorld, myOfficer, fetchMyOfficer]);

    const fetchFleets = useCallback(async () => {
        if (!currentWorld || !myOfficer) return;
        setLoading(true);
        try {
            const sessionId = currentWorld.id;
            const { data: fleetList } = await axios.get<Fleet[]>(
                `/api/${sessionId}/fleets?officerId=${myOfficer.id}`
            );

            const withUnits = await Promise.all(
                fleetList.map(async (fleet) => {
                    try {
                        const { data: units } = await axios.get<FleetUnit[]>(
                            `/api/${sessionId}/fleets/${fleet.id}/units`
                        );
                        return { fleet, units };
                    } catch {
                        return { fleet, units: [] };
                    }
                })
            );
            setFleets(withUnits);
        } catch (err) {
            console.error('함대 목록 조회 실패:', err);
        } finally {
            setLoading(false);
        }
    }, [currentWorld, myOfficer]);

    useEffect(() => {
        void fetchFleets();
    }, [fetchFleets]);

    if (!currentWorld) {
        return <div className="p-4 text-muted-foreground">월드를 선택해주세요.</div>;
    }
    if (loading) return <LoadingState />;
    if (!myOfficer) {
        return <div className="p-4 text-muted-foreground">장교 정보를 불러오는 중입니다.</div>;
    }

    return (
        <div className="p-4 space-y-4 max-w-3xl mx-auto">
            <PageHeader icon={Shield} title="함대 편성" />

            {fleets.length === 0 ? (
                <EmptyState icon={Shield} title="지휘 중인 함대가 없습니다." />
            ) : (
                <>
                    {/* Tab bar (복수 함대인 경우) */}
                    {fleets.length > 1 && (
                        <div className="flex gap-1 border-b border-gray-700 pb-0">
                            {fleets.map((fw, idx) => (
                                <button
                                    key={fw.fleet.id}
                                    type="button"
                                    onClick={() => setActiveTab(idx)}
                                    className={`text-xs px-3 py-1.5 rounded-none border-b-2 transition-colors ${
                                        activeTab === idx
                                            ? 'border-blue-500 text-blue-300'
                                            : 'border-transparent text-gray-500 hover:text-gray-300'
                                    }`}
                                >
                                    {fw.fleet.name}
                                </button>
                            ))}
                        </div>
                    )}

                    {fleets[activeTab] && (
                        <FleetCompositionPanel
                            fleet={fleets[activeTab].fleet}
                            units={fleets[activeTab].units}
                            sessionId={currentWorld.id}
                            officerName={myOfficer.name}
                            rankTitle={myOfficer.officerLevelText ?? `Lv.${myOfficer.officerLevel}`}
                            onRefresh={fetchFleets}
                        />
                    )}
                </>
            )}
        </div>
    );
}
