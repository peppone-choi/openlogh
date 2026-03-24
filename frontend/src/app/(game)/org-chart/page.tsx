'use client';

import { useState } from 'react';
import { Building2, ChevronDown, ChevronRight, Crown, Shield, Users } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { PageHeader } from '@/components/game/page-header';
import { useOfficerStore } from '@/stores/officerStore';
import { useGameStore } from '@/stores/gameStore';

// ─── Empire Org Structure ────────────────────────────────────────────────────

interface OrgNode {
    title: string;
    titleKr: string;
    description: string;
    rankRequired?: string;
    children?: OrgNode[];
}

const EMPIRE_ORG: OrgNode = {
    title: 'Imperial Palace',
    titleKr: '황궁',
    description: '은하제국 최고 권력기관',
    children: [
        {
            title: 'Imperial Cabinet',
            titleKr: '내각',
            description: '제국 행정부',
            rankRequired: '국무상서',
            children: [
                {
                    title: 'Ministry of Military Affairs',
                    titleKr: '군무성',
                    description: '군사 행정 총괄',
                    rankRequired: '군무상서',
                    children: [
                        {
                            title: 'Supreme Command HQ',
                            titleKr: '통수본부',
                            description: '작전 기획 및 전략 수립',
                            rankRequired: '통수본부장',
                            children: [
                                {
                                    title: 'Imperial Space Fleet Command',
                                    titleKr: '우주함대사령부',
                                    description: '제국 우주함대 총지휘',
                                    rankRequired: '우주함대사령장관',
                                    children: [
                                        {
                                            title: '1st Fleet',
                                            titleKr: '제1함대',
                                            description: '정규 함대',
                                            rankRequired: '대장',
                                        },
                                        {
                                            title: '2nd Fleet',
                                            titleKr: '제2함대',
                                            description: '정규 함대',
                                            rankRequired: '대장',
                                        },
                                        {
                                            title: '3rd Fleet',
                                            titleKr: '제3함대',
                                            description: '정규 함대',
                                            rankRequired: '대장',
                                        },
                                        {
                                            title: 'Separate Fleet',
                                            titleKr: '독립분함대',
                                            description: '특수 임무 부대',
                                            rankRequired: '중장~소장',
                                        },
                                        {
                                            title: 'Fortress Garrison',
                                            titleKr: '요새수비대',
                                            description: '이제르론/가이에스부르크',
                                            rankRequired: '대장~중장',
                                        },
                                    ],
                                },
                            ],
                        },
                    ],
                },
                {
                    title: 'Ministry of Internal Affairs',
                    titleKr: '내무성',
                    description: '내정 및 치안',
                    rankRequired: '내무상서',
                },
                {
                    title: 'Ministry of Finance',
                    titleKr: '재무성',
                    description: '재정 관리',
                    rankRequired: '재무상서',
                },
            ],
        },
    ],
};

const ALLIANCE_ORG: OrgNode = {
    title: 'Supreme Council',
    titleKr: '최고평의회',
    description: '자유행성동맹 최고 의결기관',
    children: [
        {
            title: 'National Defense Committee',
            titleKr: '국방위원회',
            description: '국방 정책 결정',
            rankRequired: '국방위원장',
            children: [
                {
                    title: 'Joint Operations HQ',
                    titleKr: '통합작전본부',
                    description: '합동 작전 수립 및 지휘',
                    rankRequired: '통합작전본부장',
                    children: [
                        {
                            title: 'Space Fleet Command',
                            titleKr: '우주함대사령부',
                            description: '동맹 우주함대 총지휘',
                            rankRequired: '우주함대사령장관',
                            children: [
                                {
                                    title: '1st Fleet',
                                    titleKr: '제1함대',
                                    description: '정규 함대',
                                    rankRequired: '중장',
                                },
                                {
                                    title: '2nd Fleet',
                                    titleKr: '제2함대',
                                    description: '정규 함대',
                                    rankRequired: '중장',
                                },
                                {
                                    title: '3rd Fleet',
                                    titleKr: '제3함대',
                                    description: '정규 함대',
                                    rankRequired: '중장',
                                },
                                {
                                    title: 'Iserlohn Patrol Fleet',
                                    titleKr: '이제르론 순찰함대',
                                    description: '요새 방어 전담',
                                    rankRequired: '중장',
                                },
                                {
                                    title: 'Rear Area Fleet',
                                    titleKr: '후방 경비함대',
                                    description: '후방 수호',
                                    rankRequired: '소장',
                                },
                            ],
                        },
                    ],
                },
            ],
        },
        {
            title: 'Financial Committee',
            titleKr: '재정위원회',
            description: '재정 운용',
            rankRequired: '재정위원장',
        },
        {
            title: 'Human Resources Committee',
            titleKr: '인적자원위원회',
            description: '인사 및 복지',
            rankRequired: '인적자원위원장',
        },
    ],
};

// ─── Org Tree Node Component ─────────────────────────────────────────────────

function OrgTreeNode({ node, depth = 0, factionColor }: { node: OrgNode; depth?: number; factionColor: string }) {
    const [expanded, setExpanded] = useState(depth < 3);
    const hasChildren = node.children && node.children.length > 0;

    return (
        <div className="space-y-1">
            <button
                type="button"
                onClick={() => hasChildren && setExpanded(!expanded)}
                className="flex items-start gap-2 w-full text-left rounded-lg px-3 py-2 transition-colors hover:bg-muted/50"
                style={{ marginLeft: depth * 16 }}
            >
                {hasChildren ? (
                    expanded ? (
                        <ChevronDown className="size-4 mt-0.5 shrink-0" style={{ color: factionColor }} />
                    ) : (
                        <ChevronRight className="size-4 mt-0.5 shrink-0" style={{ color: factionColor }} />
                    )
                ) : (
                    <div className="size-4 shrink-0 flex items-center justify-center">
                        <div className="size-1.5 rounded-full" style={{ backgroundColor: factionColor }} />
                    </div>
                )}
                <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2 flex-wrap">
                        <span className="text-sm font-semibold" style={{ color: factionColor }}>
                            {node.titleKr}
                        </span>
                        <span className="text-[10px] text-muted-foreground font-mono">{node.title}</span>
                    </div>
                    <div className="text-xs text-muted-foreground mt-0.5">{node.description}</div>
                    {node.rankRequired && (
                        <Badge variant="outline" className="mt-1 text-[10px]">
                            {node.rankRequired}
                        </Badge>
                    )}
                </div>
            </button>
            {expanded && hasChildren && (
                <div className="border-l border-muted ml-5 pl-1">
                    {node.children!.map((child, i) => (
                        <OrgTreeNode
                            key={`${child.titleKr}-${i}`}
                            node={child}
                            depth={depth + 1}
                            factionColor={factionColor}
                        />
                    ))}
                </div>
            )}
        </div>
    );
}

// ─── Main Page ───────────────────────────────────────────────────────────────

export default function OrgChartPage() {
    const { myOfficer } = useOfficerStore();
    const factions = useGameStore((s) => s.factions);
    const [activeTab, setActiveTab] = useState<'empire' | 'alliance'>('empire');

    const empireFaction = factions.find((f) => f.factionType === 'empire' || f.faction_type === 'empire');
    const allianceFaction = factions.find((f) => f.factionType === 'alliance' || f.faction_type === 'alliance');

    const empireColor = empireFaction?.color ?? '#FFD700';
    const allianceColor = allianceFaction?.color ?? '#4488FF';

    return (
        <div className="p-4 space-y-4 max-w-4xl mx-auto">
            <PageHeader icon={Building2} title="조직도" description="은하제국 / 자유행성동맹 조직 계층 구조" />

            <div className="flex gap-1 border-b pb-1">
                <Button
                    variant={activeTab === 'empire' ? 'default' : 'ghost'}
                    size="sm"
                    onClick={() => setActiveTab('empire')}
                >
                    <Crown className="size-4 mr-1" />
                    은하제국
                </Button>
                <Button
                    variant={activeTab === 'alliance' ? 'default' : 'ghost'}
                    size="sm"
                    onClick={() => setActiveTab('alliance')}
                >
                    <Shield className="size-4 mr-1" />
                    자유행성동맹
                </Button>
            </div>

            {activeTab === 'empire' ? (
                <Card>
                    <CardHeader className="pb-2">
                        <CardTitle className="text-sm flex items-center gap-2" style={{ color: empireColor }}>
                            <Crown className="size-4" />
                            은하제국 (Galactic Empire) 조직 체계
                        </CardTitle>
                    </CardHeader>
                    <CardContent>
                        <OrgTreeNode node={EMPIRE_ORG} factionColor={empireColor} />
                    </CardContent>
                </Card>
            ) : (
                <Card>
                    <CardHeader className="pb-2">
                        <CardTitle className="text-sm flex items-center gap-2" style={{ color: allianceColor }}>
                            <Users className="size-4" />
                            자유행성동맹 (Free Planets Alliance) 조직 체계
                        </CardTitle>
                    </CardHeader>
                    <CardContent>
                        <OrgTreeNode node={ALLIANCE_ORG} factionColor={allianceColor} />
                    </CardContent>
                </Card>
            )}

            {/* Legend */}
            <Card>
                <CardContent className="pt-4">
                    <div className="text-xs text-muted-foreground space-y-1">
                        <div className="font-semibold mb-2">범례</div>
                        <div>- 각 조직 단위를 클릭하면 하위 조직을 펼치거나 접을 수 있습니다.</div>
                        <div>
                            -{' '}
                            <Badge variant="outline" className="text-[10px]">
                                직위명
                            </Badge>{' '}
                            배지는 해당 직위의 요구 계급/자격을 나타냅니다.
                        </div>
                        <div>- 함대 편성은 진영 상황에 따라 변동될 수 있습니다.</div>
                    </div>
                </CardContent>
            </Card>
        </div>
    );
}
