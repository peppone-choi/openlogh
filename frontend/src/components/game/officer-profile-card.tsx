'use client';

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Separator } from '@/components/ui/separator';
import { OfficerPortrait } from '@/components/game/officer-portrait';
import { FactionBadge } from '@/components/game/faction-badge';
import { LoghBar } from '@/components/game/logh-bar';
import { PositionCardGrid } from '@/components/game/position-card-grid';
import { formatOfficerLevelText } from '@/lib/game-utils';
import type { Officer, OfficerPositionCard } from '@/types';

interface OfficerProfileCardProps {
    officer: Officer;
    positionCards: OfficerPositionCard[];
    faction: { type?: string; name?: string; color?: string; rank?: number } | null;
}

const ORIGIN_LABELS: Record<string, string> = {
    noble: '귀족',
    knight: '제국기사',
    commoner: '평민',
    citizen: '시민',
};

const CAREER_LABELS: Record<string, string> = {
    military: '군인',
    politician: '정치가',
};

const STAT_DEFS = [
    { key: 'leadership', expKey: 'leadershipExp', label: '통솔', color: 'red' },
    { key: 'command', expKey: 'commandExp', label: '지휘', color: 'orange' },
    { key: 'intelligence', expKey: 'intelligenceExp', label: '정보', color: 'dodgerblue' },
    { key: 'politics', expKey: 'politicsExp', label: '정치', color: 'limegreen' },
    { key: 'administration', expKey: 'administrationExp', label: '운영', color: 'mediumpurple' },
    { key: 'mobility', expKey: 'mobilityExp', label: '기동', color: 'cyan' },
    { key: 'attack', expKey: 'attackExp', label: '공격', color: '#ff6b6b' },
    { key: 'defense', expKey: 'defenseExp', label: '방어', color: '#74c0fc' },
] as const;

export function OfficerProfileCard({ officer, positionCards, faction }: OfficerProfileCardProps) {
    const g = officer;
    const factionRank = faction?.rank ?? 0;
    const hasFaction = (g.factionId > 0 || g.nationId > 0);
    const officerRankText = formatOfficerLevelText(
        g.officerLevel,
        factionRank,
        hasFaction,
        faction?.type,
        g.npcState
    );
    const originLabel = ORIGIN_LABELS[g.originType ?? ''] ?? g.originType ?? '-';
    const careerLabel = CAREER_LABELS[g.careerType ?? ''] ?? g.careerType ?? '-';

    return (
        <Card>
            <CardContent className="pt-4 space-y-0">
                {/* Section 1: Basic Info */}
                <div className="flex gap-4 items-start">
                    <OfficerPortrait picture={g.picture} name={g.name} size="lg" />
                    <div className="space-y-1.5 flex-1 min-w-0">
                        <p className="text-lg font-semibold truncate">{g.name}</p>
                        <div className="flex items-center gap-1.5 flex-wrap">
                            <Badge variant="outline">{officerRankText}</Badge>
                            <FactionBadge name={faction?.name} color={faction?.color} />
                            <Badge variant="ghost">{originLabel}</Badge>
                            <Badge variant="ghost">{careerLabel}</Badge>
                            <Badge variant="ghost">{g.age}세</Badge>
                        </div>
                    </div>
                </div>

                <Separator className="my-3" />

                {/* Section 2: Stats */}
                <div>
                    <CardTitle className="text-sm mb-2">능력치</CardTitle>
                    <div className="space-y-1.5">
                        {STAT_DEFS.map((s) => {
                            const value = g[s.key as keyof Officer] as number;
                            const exp = (g[s.expKey as keyof Officer] as number) ?? 0;
                            return (
                                <div key={s.key} className="flex items-center gap-2">
                                    <span className="w-8 text-xs text-right shrink-0" style={{ color: s.color }}>
                                        {s.label}
                                    </span>
                                    <span className="w-6 text-xs text-right font-mono tabular-nums shrink-0">
                                        {value}
                                    </span>
                                    <div className="flex-1">
                                        <LoghBar height={7} percent={value} altText={`${value}/100`} />
                                    </div>
                                    {exp > 0 && (
                                        <span className="text-[10px] text-yellow-500 w-12 text-right shrink-0">
                                            +{exp} exp
                                        </span>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                </div>

                <Separator className="my-3" />

                {/* Section 3: Position Cards */}
                <div>
                    <CardTitle className="text-sm mb-2">직무권한카드</CardTitle>
                    <PositionCardGrid cards={positionCards} />
                </div>

                <Separator className="my-3" />

                {/* Section 4: Location / Status */}
                <div>
                    <CardTitle className="text-sm mb-2">위치 / 상태</CardTitle>
                    <div className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-sm">
                        <div>
                            <span className="text-muted-foreground">위치: </span>
                            <span>{g.planetId > 0 ? `행성 #${g.planetId}` : (g.starSystemId > 0 ? `성계 #${g.starSystemId}` : '-')}</span>
                        </div>
                        <div>
                            <span className="text-muted-foreground">소속 함대: </span>
                            <span>{g.fleetId > 0 ? `함대 #${g.fleetId}` : '-'}</span>
                        </div>
                        <div>
                            <span className="text-muted-foreground">부상: </span>
                            {g.injury === 0 ? (
                                <span className="text-green-400">건강</span>
                            ) : (
                                <span className="text-red-400">부상 ({g.injury})</span>
                            )}
                        </div>
                        <div>
                            <span className="text-muted-foreground">PCP: </span>
                            <span className="tabular-nums">{g.pcp}</span>
                        </div>
                        <div>
                            <span className="text-muted-foreground">MCP: </span>
                            <span className="tabular-nums">{g.mcp}</span>
                        </div>
                    </div>
                </div>
            </CardContent>
        </Card>
    );
}
