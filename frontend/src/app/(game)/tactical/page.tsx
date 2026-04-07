'use client';

import { useCallback, useEffect, useState } from 'react';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { useTacticalStore } from '@/stores/tacticalStore';
import { subscribeWebSocket } from '@/lib/websocket';
import { buildBattleCommandPayload } from '@/lib/tacticalApi';
import { BattleMap } from '@/components/tactical/BattleMap';
import { MiniMap } from '@/components/tactical/MiniMap';
import { InfoPanel } from '@/components/tactical/InfoPanel';
import { EnergyPanel } from '@/components/tactical/EnergyPanel';
import { FormationSelector } from '@/components/tactical/FormationSelector';
import { BattleStatus } from '@/components/tactical/BattleStatus';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { EmptyState } from '@/components/game/empty-state';
import { Button } from '@/components/ui/8bit/button';
import { Card, CardContent } from '@/components/ui/8bit/card';
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

    const [selectedUnitId, setSelectedUnitId] = useState<number | null>(null);
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

    // Suppress unused warning — stompClient setter used via WebSocket init (future integration)
    void setStompClient;

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
                            <div
                                key={battle.id}
                                className="cursor-pointer hover:border-yellow-600 border border-gray-700 rounded p-3 transition-colors"
                                onClick={() => handleSelectBattle(battle)}
                            >
                                <div className="flex items-center justify-between">
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
                                </div>
                            </div>
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
        <div className="flex flex-col h-screen max-h-screen overflow-hidden bg-black">
            {/* Toolbar */}
            <div className="flex items-center gap-2 px-3 py-2 bg-[#0a0a14] border-b border-[#222] shrink-0">
                <div className="flex gap-1">
                    {['작전조회', '함대정보', '성계정보', '자함대', '해결'].map((label) => (
                        <button
                            key={label}
                            className="px-3 py-1 text-xs font-mono bg-[#111] border border-[#333] text-gray-300 hover:bg-[#1a1a2e] hover:border-[#4466ff] transition-colors"
                        >
                            {label}
                        </button>
                    ))}
                </div>
                <div className="flex-1" />
                <Button variant="outline" size="sm" onClick={() => clearBattle()}>
                    목록으로
                </Button>
            </div>

            {/* Main area */}
            <div className="flex flex-1 overflow-hidden">
                {/* Battle map area — relative container for minimap/infopanel overlays */}
                <div className="relative flex-1 overflow-hidden">
                    <BattleMap
                        units={units}
                        myOfficerId={myOfficer?.id}
                        selectedUnitId={selectedUnitId}
                        onSelectUnit={setSelectedUnitId}
                        width={1000}
                        height={600}
                    />

                    {/* MiniMap overlay — top-right */}
                    <MiniMap
                        units={units}
                        myOfficerId={myOfficer?.id}
                    />

                    {/* InfoPanel overlay — bottom-right */}
                    <InfoPanel
                        battle={currentBattle}
                        units={units}
                        myOfficerId={myOfficer?.id}
                    />
                </div>

                {/* Right sidebar: controls */}
                <div className="w-64 shrink-0 overflow-y-auto bg-[#0a0a14] border-l border-[#222] space-y-3 p-3">
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

                    <BattleStatus
                        tickCount={currentBattle.tickCount}
                        phase={currentBattle.phase}
                        result={currentBattle.result}
                        units={units}
                        events={recentEvents}
                    />
                </div>
            </div>
        </div>
    );
}
