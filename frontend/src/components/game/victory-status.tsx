'use client';

import { useMemo } from 'react';
import { Trophy, Clock, Map, Users } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import type { Faction, StarSystem } from '@/types';

// Victory end date per LOGH universe: 801.7.27 (Imperial calendar)
const VICTORY_END_YEAR = 801;
const VICTORY_END_MONTH = 7;

interface VictoryStatusProps {
    factions: Faction[];
    starSystems: StarSystem[];
    currentYear: number;
    currentMonth: number;
}

interface FactionStats {
    faction: Faction;
    planetCount: number;
    totalPopulation: number;
    populationShare: number;
}

function getVictoryProjection(stats: FactionStats[], starSystems: StarSystem[]): string {
    if (stats.length === 0) return '진행 중';

    const top = stats[0];
    const totalPlanets = starSystems.length;

    // Capture condition: enemy down to 3 or fewer systems
    const smallest = stats[stats.length - 1];
    if (smallest.planetCount <= 3 && smallest.planetCount > 0) {
        return `${top.faction.name} 수도 점령 임박`;
    }

    // Population dominance
    if (top.populationShare >= 60) {
        return `${top.faction.name} 인구 지배 (${Math.round(top.populationShare)}%)`;
    }

    if (totalPlanets > 0 && top.planetCount >= totalPlanets * 0.7) {
        return `${top.faction.name} 영토 지배`;
    }

    return '진행 중';
}

export function VictoryStatus({ factions, starSystems, currentYear, currentMonth }: VictoryStatusProps) {
    const stats = useMemo<FactionStats[]>(() => {
        const totalPop = starSystems.reduce((s, c) => s + (c.population ?? 0), 0);
        return factions
            .map((f) => {
                const planets = starSystems.filter((c) => c.factionId === f.id);
                const pop = planets.reduce((s, c) => s + (c.population ?? 0), 0);
                return {
                    faction: f,
                    planetCount: planets.length,
                    totalPopulation: pop,
                    populationShare: totalPop > 0 ? (pop / totalPop) * 100 : 0,
                };
            })
            .filter((s) => s.planetCount > 0)
            .sort((a, b) => b.populationShare - a.populationShare);
    }, [factions, starSystems]);

    const totalPlanets = starSystems.length;

    // Months remaining until 801.7
    const remainingMonths = useMemo(() => {
        const endTotal = VICTORY_END_YEAR * 12 + VICTORY_END_MONTH;
        const curTotal = currentYear * 12 + currentMonth;
        return Math.max(0, endTotal - curTotal);
    }, [currentYear, currentMonth]);

    const projection = useMemo(() => getVictoryProjection(stats, starSystems), [stats, starSystems]);

    return (
        <Card>
            <CardHeader className="pb-2">
                <CardTitle className="text-sm flex items-center gap-2">
                    <Trophy className="size-4 text-amber-400" />
                    승리 조건 현황
                </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
                {/* Time remaining */}
                <div className="flex items-center gap-2 text-xs">
                    <Clock className="size-3.5 text-muted-foreground shrink-0" />
                    <span className="text-muted-foreground">종료 기한:</span>
                    <span className="font-medium">
                        {VICTORY_END_YEAR}년 {VICTORY_END_MONTH}월 ({remainingMonths}개월 남음)
                    </span>
                </div>

                {/* Projection */}
                <div className="flex items-center gap-2 text-xs">
                    <Trophy className="size-3.5 text-amber-400 shrink-0" />
                    <span className="text-muted-foreground">현재 예측:</span>
                    <Badge variant="outline" className="text-amber-300 border-amber-700 text-[10px]">
                        {projection}
                    </Badge>
                </div>

                {/* Per-faction territory */}
                <div className="space-y-1.5">
                    <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                        <Map className="size-3" />
                        영토 현황
                    </div>
                    {stats.map((s) => (
                        <div key={s.faction.id} className="space-y-0.5">
                            <div className="flex items-center justify-between text-xs">
                                <span className="font-medium" style={{ color: s.faction.color || undefined }}>
                                    {s.faction.name}
                                </span>
                                <span className="text-muted-foreground tabular-nums">
                                    {s.planetCount}/{totalPlanets} 행성
                                </span>
                            </div>
                            <Progress
                                value={totalPlanets > 0 ? (s.planetCount / totalPlanets) * 100 : 0}
                                className="h-1.5"
                            />
                        </div>
                    ))}
                </div>

                {/* Population share */}
                <div className="space-y-1.5">
                    <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                        <Users className="size-3" />
                        인구 지배율
                    </div>
                    {stats.map((s) => (
                        <div key={s.faction.id} className="flex items-center justify-between text-xs">
                            <span style={{ color: s.faction.color || undefined }}>{s.faction.name}</span>
                            <span className="tabular-nums text-muted-foreground">
                                {Math.round(s.populationShare)}% ({s.totalPopulation.toLocaleString()})
                            </span>
                        </div>
                    ))}
                </div>
            </CardContent>
        </Card>
    );
}
