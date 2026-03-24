'use client';

import { useEffect, useMemo, useState } from 'react';
import { Castle, Shield, Crosshair, Ship, Package, Users } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { EmptyState } from '@/components/game/empty-state';
import { useWorldStore } from '@/stores/worldStore';
import { useGameStore } from '@/stores/gameStore';

/**
 * Fortress definition based on gin7 manual.
 * Fortresses are immovable strongholds that function as strategic chokepoints.
 * Key examples: Iserlohn Fortress, Geiersburg Fortress.
 */
interface FortressInfo {
    id: number;
    name: string;
    factionId: number;
    factionName: string;
    factionColor: string;
    /** Fortress HP / structural integrity */
    hp: number;
    maxHp: number;
    /** Fortress gun (Thor Hammer / Artemis Necklace) status */
    fortressGunReady: boolean;
    fortressGunCooldown: number;
    /** Garrison fleet count */
    garrisonFleets: number;
    /** Docked ships total */
    dockedShips: number;
    /** Resource stockpile */
    funds: number;
    supplies: number;
    /** Personnel count */
    personnelCount: number;
    /** Location star system */
    starSystemName: string;
}

/**
 * Build fortress data from the game store's starSystems and factions.
 * A star system with fortress > 0 is considered a fortress location.
 */
function buildFortressData(
    starSystems: {
        id: number;
        name: string;
        factionId: number;
        fortress: number;
        fortressMax: number;
        population: number;
        orbitalDefense: number;
        orbitalDefenseMax: number;
        production: number;
        commerce: number;
        security: number;
    }[],
    factions: { id: number; name: string; color: string; funds: number; supplies: number }[]
): FortressInfo[] {
    return starSystems
        .filter((s) => s.fortress > 0)
        .map((s) => {
            const faction = factions.find((f) => f.id === s.factionId);
            return {
                id: s.id,
                name: `${s.name} 요새`,
                factionId: s.factionId,
                factionName: faction?.name ?? '독립',
                factionColor: faction?.color ?? '#666',
                hp: s.fortress,
                maxHp: s.fortressMax || 1000,
                fortressGunReady: s.orbitalDefense > 50,
                fortressGunCooldown: s.orbitalDefense > 50 ? 0 : 3,
                garrisonFleets: Math.floor(s.population / 5000),
                dockedShips: s.orbitalDefense * 30,
                funds: Math.floor(s.commerce * 10),
                supplies: Math.floor(s.production * 10),
                personnelCount: Math.floor(s.population / 100),
                starSystemName: s.name,
            };
        })
        .sort((a, b) => b.hp - a.hp);
}

function FortressCard({ fortress }: { fortress: FortressInfo }) {
    const hpPercent = fortress.maxHp > 0 ? (fortress.hp / fortress.maxHp) * 100 : 0;
    const hpColor = hpPercent > 60 ? 'text-emerald-400' : hpPercent > 30 ? 'text-amber-400' : 'text-red-400';
    const hpBarColor = hpPercent > 60 ? 'bg-emerald-500' : hpPercent > 30 ? 'bg-amber-500' : 'bg-red-500';

    return (
        <Card className="border-gray-700/50 bg-gray-900/30">
            <CardHeader className="py-2 px-3">
                <CardTitle className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                        <Castle className="h-4 w-4 text-amber-400" />
                        <span className="text-sm font-bold" style={{ color: 'var(--empire-gold, #c9a84c)' }}>
                            {fortress.name}
                        </span>
                    </div>
                    <Badge
                        variant="outline"
                        className="text-[9px] font-mono"
                        style={{ color: fortress.factionColor, borderColor: fortress.factionColor }}
                    >
                        {fortress.factionName}
                    </Badge>
                </CardTitle>
            </CardHeader>
            <CardContent className="py-2 px-3 space-y-3">
                {/* Structural Integrity */}
                <div className="space-y-1">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-1.5">
                            <Shield className="h-3 w-3 text-gray-500" />
                            <span className="text-[10px] font-mono text-gray-400">구조 건전성</span>
                        </div>
                        <span className={`text-[10px] font-mono font-bold ${hpColor}`}>
                            {fortress.hp.toLocaleString()}/{fortress.maxHp.toLocaleString()}
                        </span>
                    </div>
                    <div className="relative h-2 bg-gray-800 rounded-full overflow-hidden">
                        <div
                            className={`absolute inset-y-0 left-0 rounded-full ${hpBarColor} transition-all duration-300`}
                            style={{ width: `${hpPercent}%` }}
                        />
                    </div>
                </div>

                {/* Fortress Gun Status */}
                <div className="flex items-center justify-between border-t border-gray-800/50 pt-2">
                    <div className="flex items-center gap-1.5">
                        <Crosshair className="h-3 w-3 text-gray-500" />
                        <span className="text-[10px] font-mono text-gray-400">요새포</span>
                    </div>
                    {fortress.fortressGunReady ? (
                        <Badge className="text-[8px] font-mono bg-emerald-900/30 text-emerald-400 border-emerald-800/50">
                            발사 가능
                        </Badge>
                    ) : (
                        <Badge className="text-[8px] font-mono bg-red-900/30 text-red-400 border-red-800/50">
                            충전 중 ({fortress.fortressGunCooldown}턴)
                        </Badge>
                    )}
                </div>

                {/* Stats Grid */}
                <div className="grid grid-cols-2 gap-2 border-t border-gray-800/50 pt-2">
                    <div className="flex items-center gap-1.5">
                        <Ship className="h-3 w-3 text-gray-500" />
                        <div>
                            <div className="text-[8px] text-gray-600">주둔 함대</div>
                            <div className="text-[10px] font-mono font-bold text-gray-300">
                                {fortress.garrisonFleets}개
                            </div>
                        </div>
                    </div>
                    <div className="flex items-center gap-1.5">
                        <Ship className="h-3 w-3 text-gray-500" />
                        <div>
                            <div className="text-[8px] text-gray-600">정박 함선</div>
                            <div className="text-[10px] font-mono font-bold text-gray-300">
                                {fortress.dockedShips.toLocaleString()}척
                            </div>
                        </div>
                    </div>
                    <div className="flex items-center gap-1.5">
                        <Package className="h-3 w-3 text-gray-500" />
                        <div>
                            <div className="text-[8px] text-gray-600">자금 / 물자</div>
                            <div className="text-[10px] font-mono font-bold text-gray-300">
                                {fortress.funds.toLocaleString()} / {fortress.supplies.toLocaleString()}
                            </div>
                        </div>
                    </div>
                    <div className="flex items-center gap-1.5">
                        <Users className="h-3 w-3 text-gray-500" />
                        <div>
                            <div className="text-[8px] text-gray-600">인원</div>
                            <div className="text-[10px] font-mono font-bold text-gray-300">
                                {fortress.personnelCount.toLocaleString()}명
                            </div>
                        </div>
                    </div>
                </div>

                {/* Location */}
                <div className="border-t border-gray-800/50 pt-1.5">
                    <span className="text-[8px] font-mono text-gray-600">위치: {fortress.starSystemName} 성계</span>
                </div>
            </CardContent>
        </Card>
    );
}

export default function FortressPage() {
    const { currentWorld } = useWorldStore();
    const { starSystems, factions } = useGameStore();
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const timer = setTimeout(() => setLoading(false), 300);
        return () => clearTimeout(timer);
    }, []);

    const fortresses = useMemo(() => {
        if (!starSystems || !factions) return [];
        return buildFortressData(
            starSystems as unknown as Parameters<typeof buildFortressData>[0],
            factions as unknown as Parameters<typeof buildFortressData>[1]
        );
    }, [starSystems, factions]);

    if (!currentWorld) return <LoadingState message="월드를 불러오는 중..." />;
    if (loading) return <LoadingState />;

    return (
        <div className="space-y-2">
            <PageHeader
                icon={Castle}
                title="요새 관리"
                description="은하의 전략 거점 요새 현황을 확인합니다. (이제르론, 가이에스부르크 등)"
            />

            {fortresses.length === 0 ? (
                <EmptyState title="현재 은하에 요새가 존재하지 않습니다." />
            ) : (
                <Tabs defaultValue="all" className="space-y-2">
                    <TabsList className="bg-gray-900/50">
                        <TabsTrigger value="all" className="text-xs font-mono">
                            전체 ({fortresses.length})
                        </TabsTrigger>
                        {Array.from(new Set(fortresses.map((f) => f.factionName))).map((name) => (
                            <TabsTrigger key={name} value={name} className="text-xs font-mono">
                                {name} ({fortresses.filter((f) => f.factionName === name).length})
                            </TabsTrigger>
                        ))}
                    </TabsList>

                    <TabsContent value="all" className="space-y-2">
                        <div className="grid grid-cols-1 lg:grid-cols-2 gap-2">
                            {fortresses.map((f) => (
                                <FortressCard key={f.id} fortress={f} />
                            ))}
                        </div>
                    </TabsContent>

                    {Array.from(new Set(fortresses.map((f) => f.factionName))).map((name) => (
                        <TabsContent key={name} value={name} className="space-y-2">
                            <div className="grid grid-cols-1 lg:grid-cols-2 gap-2">
                                {fortresses
                                    .filter((f) => f.factionName === name)
                                    .map((f) => (
                                        <FortressCard key={f.id} fortress={f} />
                                    ))}
                            </div>
                        </TabsContent>
                    ))}
                </Tabs>
            )}

            {/* gin7 Fortress Info */}
            <div className="border border-gray-800/40 bg-gray-900/20 rounded p-3 space-y-1">
                <span className="text-[9px] font-mono text-gray-600 block">
                    요새 (함종: fortress) - 이동 요새는 1유닛으로 구성. 요새포로 대규모 광역 공격 가능.
                </span>
                <span className="text-[9px] font-mono text-gray-600 block">
                    주요 요새: 이제르론 (동맹/제국 교전), 가이에스부르크 (제국), 렌텐베르크 (제국)
                </span>
            </div>
        </div>
    );
}
