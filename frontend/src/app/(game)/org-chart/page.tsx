'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { Building2, Crown, Shield, Users } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { PageHeader } from '@/components/game/page-header';
import { OrgTreeNodeLive } from '@/components/game/org-tree-node-live';
import type { OrgNodeWithHolder } from '@/components/game/org-tree-node-live';
import { useWorldStore } from '@/stores/worldStore';
import { useGameStore } from '@/stores/gameStore';
import api from '@/lib/api';
import type { OrgChartHolder } from '@/types';

// ─── Empire Org Structure ────────────────────────────────────────────────────

const EMPIRE_ORG: OrgNodeWithHolder = {
    title: 'Imperial Palace',
    titleKr: '황궁',
    description: '은하제국 최고 권력기관',
    positionType: 'emperor',
    children: [
        {
            title: 'Imperial Cabinet',
            titleKr: '내각',
            description: '제국 행정부',
            rankRequired: '국무상서',
            positionType: 'state_minister',
            children: [
                {
                    title: 'Ministry of Military Affairs',
                    titleKr: '군무성',
                    description: '군사 행정 총괄',
                    rankRequired: '군무상서',
                    positionType: 'military_minister',
                    children: [
                        {
                            title: 'Supreme Command HQ',
                            titleKr: '통수본부',
                            description: '작전 기획 및 전략 수립',
                            rankRequired: '통수본부장',
                            positionType: 'supreme_command_chief',
                            children: [
                                {
                                    title: 'Imperial Space Fleet Command',
                                    titleKr: '우주함대사령부',
                                    description: '제국 우주함대 총지휘',
                                    rankRequired: '우주함대사령장관',
                                    positionType: 'fleet_commander',
                                    children: [
                                        {
                                            title: '1st Fleet',
                                            titleKr: '제1함대',
                                            description: '정규 함대',
                                            rankRequired: '대장',
                                            positionType: 'empire_fleet_1_commander',
                                        },
                                        {
                                            title: '2nd Fleet',
                                            titleKr: '제2함대',
                                            description: '정규 함대',
                                            rankRequired: '대장',
                                            positionType: 'empire_fleet_2_commander',
                                        },
                                        {
                                            title: '3rd Fleet',
                                            titleKr: '제3함대',
                                            description: '정규 함대',
                                            rankRequired: '대장',
                                            positionType: 'empire_fleet_3_commander',
                                        },
                                        {
                                            title: 'Separate Fleet',
                                            titleKr: '독립분함대',
                                            description: '특수 임무 부대',
                                            rankRequired: '중장~소장',
                                            positionType: 'empire_separate_fleet_commander',
                                        },
                                        {
                                            title: 'Fortress Garrison',
                                            titleKr: '요새수비대',
                                            description: '이제르론/가이에스부르크',
                                            rankRequired: '대장~중장',
                                            positionType: 'empire_fortress_commander',
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
                    description: '내정 및 보안',
                    rankRequired: '내무상서',
                    positionType: 'internal_minister',
                },
                {
                    title: 'Ministry of Finance',
                    titleKr: '재무성',
                    description: '재정 관리',
                    rankRequired: '재무상서',
                    positionType: 'finance_minister',
                },
            ],
        },
    ],
};

const ALLIANCE_ORG: OrgNodeWithHolder = {
    title: 'Supreme Council',
    titleKr: '최고평의회',
    description: '자유행성동맹 최고 의결기관',
    positionType: 'chairman',
    children: [
        {
            title: 'National Defense Committee',
            titleKr: '국방위원회',
            description: '국방 정책 결정',
            rankRequired: '국방위원장',
            positionType: 'defense_committee_chair',
            children: [
                {
                    title: 'Joint Operations HQ',
                    titleKr: '통합작전본부',
                    description: '합동 작전 수립 및 지휘',
                    rankRequired: '통합작전본부장',
                    positionType: 'joint_ops_chief',
                    children: [
                        {
                            title: 'Space Fleet Command',
                            titleKr: '우주함대사령부',
                            description: '동맹 우주함대 총지휘',
                            rankRequired: '우주함대사령장관',
                            positionType: 'alliance_fleet_commander',
                            children: [
                                {
                                    title: '1st Fleet',
                                    titleKr: '제1함대',
                                    description: '정규 함대',
                                    rankRequired: '중장',
                                    positionType: 'alliance_fleet_1_commander',
                                },
                                {
                                    title: '2nd Fleet',
                                    titleKr: '제2함대',
                                    description: '정규 함대',
                                    rankRequired: '중장',
                                    positionType: 'alliance_fleet_2_commander',
                                },
                                {
                                    title: '3rd Fleet',
                                    titleKr: '제3함대',
                                    description: '정규 함대',
                                    rankRequired: '중장',
                                    positionType: 'alliance_fleet_3_commander',
                                },
                                {
                                    title: 'Iserlohn Patrol Fleet',
                                    titleKr: '이제르론 순찰함대',
                                    description: '요새 방어 전담',
                                    rankRequired: '중장',
                                    positionType: 'alliance_iserlohn_patrol_commander',
                                },
                                {
                                    title: 'Rear Area Fleet',
                                    titleKr: '후방 경비함대',
                                    description: '후방 수호',
                                    rankRequired: '소장',
                                    positionType: 'alliance_rear_fleet_commander',
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
            positionType: 'finance_committee_chair',
        },
        {
            title: 'Human Resources Committee',
            titleKr: '인적자원위원회',
            description: '인사 및 복지',
            rankRequired: '인적자원위원장',
            positionType: 'hr_committee_chair',
        },
    ],
};

// ─── Merge holders into org tree ────────────────────────────────────────────

function mergeHolders(node: OrgNodeWithHolder, holderMap: Map<string, OrgChartHolder>): OrgNodeWithHolder {
    const holder = node.positionType ? holderMap.get(node.positionType) : undefined;
    return {
        ...node,
        holder: holder?.officerId
            ? {
                  officerName: holder.officerName ?? '???',
                  officerPicture: holder.officerPicture ?? null,
                  officerRank: holder.officerRank ?? 0,
              }
            : null,
        children: node.children?.map((child) => mergeHolders(child, holderMap)),
    };
}

// ─── Main Page ───────────────────────────────────────────────────────────────

export default function OrgChartPage() {
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const factions = useGameStore((s) => s.factions);
    const [activeTab, setActiveTab] = useState<'empire' | 'alliance'>('empire');
    const [holders, setHolders] = useState<OrgChartHolder[]>([]);
    const [loading, setLoading] = useState(false);
    const scrollRef = useRef<HTMLDivElement>(null);

    const fetchHolders = useCallback(async () => {
        if (!currentWorld) return;
        setLoading(true);
        try {
            const res = await api.get<{ holders: OrgChartHolder[] }>(
                `/api/org-chart/${currentWorld.id}`
            );
            setHolders(res.data.holders ?? []);
        } catch {
            setHolders([]);
        } finally {
            setLoading(false);
        }
    }, [currentWorld]);

    useEffect(() => {
        fetchHolders();
    }, [fetchHolders]);

    const holderMap = new Map(holders.map((h) => [h.positionType, h]));
    const empireTree = mergeHolders(EMPIRE_ORG, holderMap);
    const allianceTree = mergeHolders(ALLIANCE_ORG, holderMap);

    const handleTabChange = (tab: 'empire' | 'alliance') => {
        // Preserve scroll position across tab switch
        const scrollTop = scrollRef.current?.scrollTop ?? 0;
        setActiveTab(tab);
        requestAnimationFrame(() => {
            if (scrollRef.current) {
                scrollRef.current.scrollTop = scrollTop;
            }
        });
    };

    return (
        <div ref={scrollRef} className="p-4 space-y-4 max-w-4xl mx-auto">
            <PageHeader icon={Building2} title="조직도" description="은하제국 / 자유행성동맹 조직 계층 구조" />

            <div className="flex gap-1 border-b pb-1">
                <Button
                    variant={activeTab === 'empire' ? 'default' : 'ghost'}
                    size="sm"
                    onClick={() => handleTabChange('empire')}
                >
                    <Crown className="size-4 mr-1" />
                    은하제국
                </Button>
                <Button
                    variant={activeTab === 'alliance' ? 'default' : 'ghost'}
                    size="sm"
                    onClick={() => handleTabChange('alliance')}
                >
                    <Shield className="size-4 mr-1" />
                    자유행성동맹
                </Button>
            </div>

            {loading ? (
                <div className="text-center text-muted-foreground py-8 text-sm">조직도 데이터 로딩 중...</div>
            ) : activeTab === 'empire' ? (
                <Card>
                    <CardHeader className="pb-2">
                        <CardTitle
                            className="text-sm flex items-center gap-2"
                            style={{ color: 'var(--empire-gold, #c9a84c)' }}
                        >
                            <Crown className="size-4" />
                            은하제국 (Galactic Empire) 조직 체계
                        </CardTitle>
                    </CardHeader>
                    <CardContent>
                        <OrgTreeNodeLive node={empireTree} depth={0} factionColor="empire" />
                    </CardContent>
                </Card>
            ) : (
                <Card>
                    <CardHeader className="pb-2">
                        <CardTitle
                            className="text-sm flex items-center gap-2"
                            style={{ color: 'var(--alliance-blue, #1e4a8a)' }}
                        >
                            <Users className="size-4" />
                            자유행성동맹 (Free Planets Alliance) 조직 체계
                        </CardTitle>
                    </CardHeader>
                    <CardContent>
                        <OrgTreeNodeLive node={allianceTree} depth={0} factionColor="alliance" />
                    </CardContent>
                </Card>
            )}

            {/* Legend */}
            <Card>
                <CardContent className="pt-4">
                    <div className="text-xs text-muted-foreground space-y-1">
                        <div className="font-semibold mb-2">범례</div>
                        <div>- 조직 단위를 클릭하면 하위 조직을 펼치거나 접을 수 있습니다.</div>
                        <div>
                            - <span className="text-muted-foreground">[&#x25A1; 공석]</span> 표시는 현재 보임자가 없는 직위를 나타냅니다.
                        </div>
                        <div>- 함대 편성은 진영 상황에 따라 변동될 수 있습니다.</div>
                    </div>
                </CardContent>
            </Card>
        </div>
    );
}
