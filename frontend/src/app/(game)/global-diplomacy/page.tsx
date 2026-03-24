'use client';

import { useEffect, useMemo } from 'react';
import { Globe } from 'lucide-react';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { useGameStore } from '@/stores/gameStore';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { GlobalDiplomacyOverview } from '@/components/game/global-diplomacy-overview';

export default function GlobalDiplomacyPage() {
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const { myGeneral, fetchMyGeneral } = useOfficerStore();
    const { nations, diplomacy, generals, cities, loading, loadAll } = useGameStore();

    useEffect(() => {
        if (!currentWorld) return;
        if (!myGeneral) fetchMyGeneral(currentWorld.id).catch(() => {});
        loadAll(currentWorld.id);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [currentWorld?.id]);

    const nationStats = useMemo(
        () =>
            nations.map((nation) => {
                const nationGenerals = generals.filter((general) => general.nationId === nation.id);
                const nationCities = cities.filter((city) => city.nationId === nation.id);
                return {
                    nation,
                    genCount: nationGenerals.length,
                    cityCount: nationCities.length,
                    totalPop: nationCities.reduce((sum, city) => sum + city.pop, 0),
                    totalCrew: nationGenerals.reduce((sum, general) => sum + general.crew, 0),
                };
            }),
        [nations, generals, cities]
    );

    if (!currentWorld) return <div className="p-4 text-muted-foreground">월드를 선택해주세요.</div>;
    if (loading && nations.length === 0) return <LoadingState />;

    return (
        <div className="mx-auto max-w-4xl space-y-0">
            <PageHeader icon={Globe} title="은하 정보" />
            <div className="legacy-page-wrap">
                <GlobalDiplomacyOverview
                    worldId={currentWorld.id}
                    nations={nations}
                    diplomacy={diplomacy}
                    cities={cities}
                    myNationId={myGeneral?.nationId}
                    nationStats={nationStats}
                />
            </div>
        </div>
    );
}
