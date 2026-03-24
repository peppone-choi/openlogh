'use client';

import { useEffect, useState } from 'react';
import { Shield, Lock, Unlock, Star } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { EmptyState } from '@/components/game/empty-state';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';

/** Maximum position cards per officer (gin7 rule) */
const MAX_POSITION_CARDS = 16;

/**
 * Position card definition.
 * Each card grants a specific authority/permission to the officer.
 */
interface PositionCard {
    id: string;
    name: string;
    category: 'political' | 'military' | 'administrative' | 'special';
    grantedBy: string;
    permissions: string[];
    cpType: 'PCP' | 'MCP' | 'both';
    description: string;
}

/** Static position card catalog based on gin7 command system */
const POSITION_CARD_CATALOG: Record<string, PositionCard> = {
    sovereign_decree: {
        id: 'sovereign_decree',
        name: '원수령',
        category: 'political',
        grantedBy: '원수/의장',
        permissions: ['외교 선포', '전쟁 선포', '휴전 협상', '진영 정책 변경'],
        cpType: 'PCP',
        description: '진영 최고 권한. 외교/전쟁 선포 등 국가 운영 전반.',
    },
    supreme_command: {
        id: 'supreme_command',
        name: '총사령관',
        category: 'military',
        grantedBy: '원수/의장',
        permissions: ['함대 편성', '대규모 작전 지시', '전략 요충지 지정'],
        cpType: 'MCP',
        description: '군사 최고 지휘권. 함대 편성 및 전략 지시.',
    },
    fleet_command: {
        id: 'fleet_command',
        name: '함대사령',
        category: 'military',
        grantedBy: '총사령관',
        permissions: ['함대 지휘', '전투 개시', '철퇴 명령', '진형 변경'],
        cpType: 'MCP',
        description: '함대 단위 지휘권. 전투 및 기동 전반.',
    },
    patrol_command: {
        id: 'patrol_command',
        name: '순찰대사령',
        category: 'military',
        grantedBy: '함대사령',
        permissions: ['순찰 임무', '색적', '소규모 교전'],
        cpType: 'MCP',
        description: '순찰대 지휘. 정찰 및 소규모 교전.',
    },
    diplomacy: {
        id: 'diplomacy',
        name: '외교관',
        category: 'political',
        grantedBy: '원수/의장',
        permissions: ['외교 교섭', '동맹 제안', '불가침 조약'],
        cpType: 'PCP',
        description: '외교 업무 수행 권한.',
    },
    personnel: {
        id: 'personnel',
        name: '인사관',
        category: 'administrative',
        grantedBy: '원수/의장',
        permissions: ['제독 등용', '직위 임명', '전출/전입 처리'],
        cpType: 'PCP',
        description: '인사 관리 업무 수행 권한.',
    },
    internal_affairs: {
        id: 'internal_affairs',
        name: '내무관',
        category: 'administrative',
        grantedBy: '원수/의장',
        permissions: ['행성 개발', '세율 조정', '치안 유지'],
        cpType: 'PCP',
        description: '행성 내정 관리 권한.',
    },
    logistics: {
        id: 'logistics',
        name: '군수관',
        category: 'administrative',
        grantedBy: '총사령관',
        permissions: ['물자 조달', '함선 건조', '보급 수송'],
        cpType: 'both',
        description: '군수 물자 관리 및 보급 업무.',
    },
    intelligence: {
        id: 'intelligence',
        name: '정보관',
        category: 'special',
        grantedBy: '총사령관',
        permissions: ['첩보 활동', '적 정보 수집', '암호 해독'],
        cpType: 'MCP',
        description: '정보 수집 및 첩보 활동 권한.',
    },
    fortress_command: {
        id: 'fortress_command',
        name: '요새사령',
        category: 'military',
        grantedBy: '총사령관',
        permissions: ['요새 방어', '요새포 운용', '주둔 함대 관리'],
        cpType: 'MCP',
        description: '요새 방어 및 운용 전반.',
    },
    ground_command: {
        id: 'ground_command',
        name: '지상부대사령',
        category: 'military',
        grantedBy: '함대사령',
        permissions: ['지상 작전', '행성 점령', '육전대 지휘'],
        cpType: 'MCP',
        description: '지상 부대 지휘 및 행성 점령 작전.',
    },
    transport_command: {
        id: 'transport_command',
        name: '수송함대사령',
        category: 'administrative',
        grantedBy: '총사령관',
        permissions: ['수송 작전', '보급로 확보', '피난민 수송'],
        cpType: 'both',
        description: '수송 함대 운용 및 보급로 관리.',
    },
};

const CATEGORY_CONFIG = {
    political: { label: '정략', color: 'text-blue-400', bg: 'bg-blue-900/20', border: 'border-blue-800/40' },
    military: { label: '군사', color: 'text-red-400', bg: 'bg-red-900/20', border: 'border-red-800/40' },
    administrative: { label: '행정', color: 'text-green-400', bg: 'bg-green-900/20', border: 'border-green-800/40' },
    special: { label: '특수', color: 'text-purple-400', bg: 'bg-purple-900/20', border: 'border-purple-800/40' },
};

function getOfficerPositionCards(_permission: string, officerLevel: number): PositionCard[] {
    const cards: PositionCard[] = [];

    // Level 12 (sovereign): all cards
    if (officerLevel >= 12) {
        cards.push(POSITION_CARD_CATALOG.sovereign_decree);
        cards.push(POSITION_CARD_CATALOG.supreme_command);
    }

    // Level 5+ (high rank): command cards
    if (officerLevel >= 5) {
        cards.push(POSITION_CARD_CATALOG.fleet_command);
    }

    // Level 4+ (mid-high): patrol + diplomacy
    if (officerLevel >= 4) {
        cards.push(POSITION_CARD_CATALOG.patrol_command);
        if (officerLevel >= 8) cards.push(POSITION_CARD_CATALOG.diplomacy);
    }

    // Level 2+ (secret access): internal cards
    if (officerLevel >= 2) {
        cards.push(POSITION_CARD_CATALOG.personnel);
        cards.push(POSITION_CARD_CATALOG.internal_affairs);
        cards.push(POSITION_CARD_CATALOG.logistics);
    }

    // Level 3+: intelligence
    if (officerLevel >= 3) {
        cards.push(POSITION_CARD_CATALOG.intelligence);
    }

    // Level 6+: fortress / ground / transport
    if (officerLevel >= 6) {
        cards.push(POSITION_CARD_CATALOG.fortress_command);
        cards.push(POSITION_CARD_CATALOG.ground_command);
        cards.push(POSITION_CARD_CATALOG.transport_command);
    }

    return cards.slice(0, MAX_POSITION_CARDS);
}

export default function PositionCardsPage() {
    const { currentWorld } = useWorldStore();
    const { myOfficer } = useOfficerStore();
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        // Simulate load time for consistency with other pages
        const timer = setTimeout(() => setLoading(false), 200);
        return () => clearTimeout(timer);
    }, []);

    if (!currentWorld) return <LoadingState message="월드를 불러오는 중..." />;
    if (loading) return <LoadingState />;
    if (!myOfficer) {
        return (
            <>
                <PageHeader icon={Shield} title="직무권한카드" description="현재 제독의 직무 권한을 확인합니다." />
                <EmptyState title="제독이 없습니다. 먼저 제독을 등록하세요." />
            </>
        );
    }

    const cards = getOfficerPositionCards(myOfficer.permission, myOfficer.officerLevel);

    return (
        <div className="space-y-2">
            <PageHeader icon={Shield} title="직무권한카드" description="현재 제독의 직무 권한을 확인합니다." />

            {/* Summary */}
            <div className="border border-gray-700/50 bg-gray-900/30 rounded p-3">
                <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                        <Star className="h-4 w-4 text-amber-400" />
                        <span className="text-sm font-bold" style={{ color: 'var(--empire-gold, #c9a84c)' }}>
                            {myOfficer.name}
                        </span>
                        <Badge variant="outline" className="text-[9px] font-mono">
                            Lv.{myOfficer.officerLevel}
                        </Badge>
                    </div>
                    <span className="text-[10px] font-mono text-gray-500">
                        {cards.length}/{MAX_POSITION_CARDS} 카드
                    </span>
                </div>
            </div>

            {/* Cards Grid */}
            {cards.length === 0 ? (
                <EmptyState title="보유한 직무권한카드가 없습니다." />
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2">
                    {cards.map((card) => {
                        const catConfig = CATEGORY_CONFIG[card.category];
                        return (
                            <Card key={card.id} className={`border ${catConfig.border} ${catConfig.bg} bg-opacity-50`}>
                                <CardHeader className="py-2 px-3">
                                    <CardTitle className="flex items-center justify-between">
                                        <div className="flex items-center gap-1.5">
                                            <Unlock className={`h-3.5 w-3.5 ${catConfig.color}`} />
                                            <span className={`text-xs font-bold ${catConfig.color}`}>{card.name}</span>
                                        </div>
                                        <div className="flex items-center gap-1">
                                            <Badge
                                                variant="outline"
                                                className={`text-[8px] font-mono ${catConfig.color} border-current`}
                                            >
                                                {catConfig.label}
                                            </Badge>
                                            <Badge
                                                variant="outline"
                                                className="text-[8px] font-mono text-gray-400 border-gray-600"
                                            >
                                                {card.cpType}
                                            </Badge>
                                        </div>
                                    </CardTitle>
                                </CardHeader>
                                <CardContent className="py-1.5 px-3 space-y-1.5">
                                    <p className="text-[9px] text-gray-500">{card.description}</p>
                                    <div className="text-[8px] text-gray-600">
                                        <span className="text-gray-500">부여자:</span> {card.grantedBy}
                                    </div>
                                    <div className="space-y-0.5">
                                        <span className="text-[8px] text-gray-500 font-mono">허가 권한:</span>
                                        <div className="flex flex-wrap gap-1">
                                            {card.permissions.map((perm) => (
                                                <span
                                                    key={perm}
                                                    className="text-[8px] font-mono px-1.5 py-0.5 rounded border border-gray-700/60 text-gray-400 bg-gray-900/40"
                                                >
                                                    {perm}
                                                </span>
                                            ))}
                                        </div>
                                    </div>
                                </CardContent>
                            </Card>
                        );
                    })}
                </div>
            )}

            {/* Locked cards indicator */}
            {cards.length < MAX_POSITION_CARDS && (
                <div className="border border-gray-800/40 bg-gray-900/20 rounded p-3 space-y-1">
                    <div className="flex items-center gap-1.5">
                        <Lock className="h-3.5 w-3.5 text-gray-600" />
                        <span className="text-[10px] font-mono text-gray-600">
                            잠긴 카드 {MAX_POSITION_CARDS - cards.length}장 - 직위 승진 시 해제
                        </span>
                    </div>
                </div>
            )}
        </div>
    );
}
