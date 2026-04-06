'use client';

import { useCallback, useEffect, useState } from 'react';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { useTacticalStore } from '@/stores/tacticalStore';
import { subscribeWebSocket } from '@/lib/websocket';
import { buildBattleCommandPayload } from '@/lib/tacticalApi';
import { BattleMap } from '@/components/tactical/BattleMap';
import { EnergyPanel } from '@/components/tactical/EnergyPanel';
import { FormationSelector } from '@/components/tactical/FormationSelector';
import { BattleStatus } from '@/components/tactical/BattleStatus';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { EmptyState } from '@/components/game/empty-state';
import { Button } from '@/components/ui/8bit/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { Crosshair, AlertTriangle } from 'lucide-react';
import type { EnergyAllocation, Formation, BattleTickBroadcast, TacticalBattle } from '@/types/tactical';

export default function TacticalPage() {
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const { myOfficer, fetchMyOfficer } = useOfficerStore();
    const {
        currentBattle,
        units,
        recentEvents,
        activeBattles,
        loading,
        myEnergy,
        myFormation,
        loadActiveBattles,
        loadBattle,
        onBattleTick,
        setMyOfficerId,
        setEnergy,
        setFormation,
        clearBattle,
    } = useTacticalStore();

    const [stompClient, setStompClient] = useState<{ publish: (params: { destination: string; body: string }) => void } | null>(null);

    useEffect(() => {
        if (currentWorld) {
            loadActiveBattles(currentWorld.id);
            if (!myOfficer) fetchMyOfficer(currentWorld.id).catch(() => {});
        }
    }, [currentWorld, loadActiveBattles, myOfficer, fetchMyOfficer]);

    useEffect(() => {
        if (myOfficer) {
            setMyOfficerId(myOfficer.id);
        }
    }, [myOfficer, setMyOfficerId]);

    // Subscribe to battle updates
    useEffect(() => {
        if (!currentWorld || !currentBattle) return;
        return subscribeWebSocket(
            `/topic/world/${currentWorld.id}/tactical-battle/${currentBattle.id}`,
            (data) => onBattleTick(data as BattleTickBroadcast)
        );
    }, [currentWorld, currentBattle, onBattleTick]);

    const handleSelectBattle = useCallback(
        (battle: TacticalBattle) => {
            if (currentWorld) {
                loadBattle(currentWorld.id, battle.id);
            }
        },
        [currentWorld, loadBattle]
    );

    const sendCommand = useCallback(
        (type: 'energy' | 'formation' | 'retreat', payload?: { energy?: EnergyAllocation; formation?: Formation }) => {
            if (!currentWorld || !currentBattle || !myOfficer || !stompClient) return;
            const command = buildBattleCommandPayload({
                battleId: currentBattle.id,
                officerId: myOfficer.id,
                commandType: type,
                energy: payload?.energy,
                formation: payload?.formation,
            });
            stompClient.publish({
                destination: `/app/battle/${currentWorld.id}/command`,
                body: command,
            });
        },
        [currentWorld, currentBattle, myOfficer, stompClient]
    );

    const handleEnergyChange = useCallback(
        (energy: EnergyAllocation) => {
            setEnergy(energy);
            sendCommand('energy', { energy });
        },
        [setEnergy, sendCommand]
    );

    const handleFormationChange = useCallback(
        (formation: Formation) => {
            setFormation(formation);
            sendCommand('formation', { formation });
        },
        [setFormation, sendCommand]
    );

    const handleRetreat = useCallback(() => {
        sendCommand('retreat');
    }, [sendCommand]);

    if (!currentWorld) return <div className="p-4 text-muted-foreground">월드를 선택해주세요.</div>;
    if (loading) return <LoadingState />;

    // No active battle selected — show battle list
    if (!currentBattle) {
        return (
            <div className="space-y-4 max-w-4xl mx-auto">
                <PageHeader icon={Crosshair} title="전술전" description="실시간 함대 교전" />

                {activeBattles.length === 0 ? (
                    <EmptyState icon={Crosshair} title="진행 중인 전투가 없습니다." />
                ) : (
                    <div className="space-y-2">
                        {activeBattles.map((battle) => (
                            <Card
                                key={battle.id}
                                className="cursor-pointer hover:border-yellow-600 transition-colors"
                                onClick={() => handleSelectBattle(battle)}
                            >
                                <CardContent className="flex items-center justify-between py-3">
                                    <div>
                                        <span className="text-sm font-bold">전투 #{battle.id}</span>
                                        <span className="text-xs text-muted-foreground ml-2">
                                            성계 {battle.starSystemId}
                                        </span>
                                    </div>
                                    <div className="flex items-center gap-2 text-xs">
                                        <span className="text-red-400">
                                            공격 {battle.attackerFleetIds.length}함대
                                        </span>
                                        <span>vs</span>
                                        <span className="text-blue-400">
                                            방어 {battle.defenderFleetIds.length}함대
                                        </span>
                                        <span className="text-muted-foreground">T{battle.tickCount}</span>
                                    </div>
                                </CardContent>
                            </Card>
                        ))}
                    </div>
                )}
            </div>
        );
    }

    // Active battle view
    const isMyUnitAlive = units.some((u) => u.officerId === myOfficer?.id && u.isAlive);
    const isEnded = currentBattle.phase === 'ENDED';

    return (
        <div className="space-y-4 max-w-6xl mx-auto">
            <div className="flex items-center justify-between">
                <PageHeader icon={Crosshair} title={`전술전 #${currentBattle.id}`} description="실시간 함대 교전" />
                <Button variant="outline" size="sm" onClick={() => clearBattle()}>
                    목록으로
                </Button>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
                {/* Left: Battle Map (spans 2 cols on large screens) */}
                <div className="lg:col-span-2 space-y-4">
                    <BattleMap
                        units={units}
                        myOfficerId={myOfficer?.id}
                    />

                    <BattleStatus
                        tickCount={currentBattle.tickCount}
                        phase={currentBattle.phase}
                        result={currentBattle.result}
                        units={units}
                        events={recentEvents}
                    />
                </div>

                {/* Right: Controls */}
                <div className="space-y-4">
                    <EnergyPanel
                        energy={myEnergy}
                        onChange={handleEnergyChange}
                        disabled={!isMyUnitAlive || isEnded}
                    />

                    <FormationSelector
                        current={myFormation}
                        onChange={handleFormationChange}
                        disabled={!isMyUnitAlive || isEnded}
                    />

                    {isMyUnitAlive && !isEnded && (
                        <Card>
                            <CardContent className="py-3">
                                <Button
                                    variant="destructive"
                                    size="sm"
                                    className="w-full"
                                    onClick={handleRetreat}
                                >
                                    <AlertTriangle className="size-4 mr-1" />
                                    퇴각 (WARP 50% 필요)
                                </Button>
                            </CardContent>
                        </Card>
                    )}
                </div>
            </div>
        </div>
    );
}
