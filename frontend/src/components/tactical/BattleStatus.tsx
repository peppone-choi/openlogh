'use client';

import type { TacticalUnit, BattleTickEvent } from '@/types/tactical';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { Badge } from '@/components/ui/8bit/badge';

interface BattleStatusProps {
    tickCount: number;
    phase: string;
    result?: string;
    units: TacticalUnit[];
    events: BattleTickEvent[];
}

export function BattleStatus({ tickCount, phase, result, units, events }: BattleStatusProps) {
    const attackers = units.filter((u) => u.side === 'ATTACKER');
    const defenders = units.filter((u) => u.side === 'DEFENDER');

    const attackerAlive = attackers.filter((u) => u.isAlive);
    const defenderAlive = defenders.filter((u) => u.isAlive);

    const attackerHp = attackers.reduce((sum, u) => sum + u.hp, 0);
    const attackerMaxHp = attackers.reduce((sum, u) => sum + u.maxHp, 0);
    const defenderHp = defenders.reduce((sum, u) => sum + u.hp, 0);
    const defenderMaxHp = defenders.reduce((sum, u) => sum + u.maxHp, 0);

    const attackerPercent = attackerMaxHp > 0 ? Math.round((attackerHp / attackerMaxHp) * 100) : 0;
    const defenderPercent = defenderMaxHp > 0 ? Math.round((defenderHp / defenderMaxHp) * 100) : 0;

    const elapsed = Math.floor(tickCount / 60);
    const seconds = tickCount % 60;

    return (
        <Card>
            <CardHeader className="pb-2">
                <CardTitle className="flex items-center justify-between text-sm">
                    <span>전투 현황</span>
                    <div className="flex items-center gap-2">
                        <Badge variant={phase === 'ACTIVE' ? 'destructive' : 'secondary'}>
                            {phase === 'ACTIVE' ? '교전 중' : phase === 'ENDED' ? '종료' : phase}
                        </Badge>
                        <span className="font-mono text-xs text-muted-foreground">
                            {elapsed}:{seconds.toString().padStart(2, '0')}
                        </span>
                        <span className="font-mono text-xs text-muted-foreground">T{tickCount}</span>
                    </div>
                </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
                {result && (
                    <div className="text-center py-2 border border-yellow-600 bg-yellow-950/30 text-yellow-400 text-sm">
                        {result === 'attacker_win'
                            ? '공격측 승리'
                            : result === 'defender_win'
                              ? '방어측 승리'
                              : '무승부'}
                    </div>
                )}

                {/* Attacker HP bar */}
                <div className="space-y-1">
                    <div className="flex justify-between text-xs">
                        <span className="text-red-400">
                            공격측 ({attackerAlive.length}/{attackers.length})
                        </span>
                        <span className="tabular-nums">
                            {attackerHp.toLocaleString()} / {attackerMaxHp.toLocaleString()}
                        </span>
                    </div>
                    <div className="h-3 bg-gray-800 rounded overflow-hidden">
                        <div
                            className="h-full bg-red-500 transition-all duration-300"
                            style={{ width: `${attackerPercent}%` }}
                        />
                    </div>
                </div>

                {/* Defender HP bar */}
                <div className="space-y-1">
                    <div className="flex justify-between text-xs">
                        <span className="text-blue-400">
                            방어측 ({defenderAlive.length}/{defenders.length})
                        </span>
                        <span className="tabular-nums">
                            {defenderHp.toLocaleString()} / {defenderMaxHp.toLocaleString()}
                        </span>
                    </div>
                    <div className="h-3 bg-gray-800 rounded overflow-hidden">
                        <div
                            className="h-full bg-blue-500 transition-all duration-300"
                            style={{ width: `${defenderPercent}%` }}
                        />
                    </div>
                </div>

                {/* Recent events log */}
                {events.length > 0 && (
                    <div className="max-h-32 overflow-y-auto text-[10px] font-mono space-y-0.5 bg-black/50 p-2 rounded border border-gray-800">
                        {events.slice(0, 20).map((event, i) => (
                            <div
                                key={i}
                                className={
                                    event.type === 'damage'
                                        ? 'text-orange-400'
                                        : event.type === 'destroy'
                                          ? 'text-red-500'
                                          : event.type === 'fortress_fire'
                                            ? 'text-yellow-400'
                                            : event.type === 'retreat'
                                              ? 'text-gray-400'
                                              : 'text-gray-500'
                                }
                            >
                                {event.detail}
                                {event.value > 0 && ` (-${event.value})`}
                            </div>
                        ))}
                    </div>
                )}
            </CardContent>
        </Card>
    );
}
