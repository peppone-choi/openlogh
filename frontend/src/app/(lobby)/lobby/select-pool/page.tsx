'use client';

import { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { characterApi, factionApi } from '@/lib/gameApi';
import { characterCreationSchema, STAT_TOTAL, STAT_MIN, STAT_MAX } from '@/lib/schemas/character-creation';
import type { Officer, Faction, StatKey8 } from '@/types';
import { STAT_KEYS_8 } from '@/types';
import { Users, ArrowLeft } from 'lucide-react';
import { toast } from 'sonner';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { EmptyState } from '@/components/game/empty-state';
import { ErrorState } from '@/components/game/error-state';
import { CharacterPickGrid } from '@/components/game/character-pick-grid';
import { StatAllocator } from '@/components/game/stat-allocator';
import { OriginSelector } from '@/components/game/origin-selector';

const DEFAULT_STATS: Record<StatKey8, number> = {
    leadership: 50,
    command: 50,
    intelligence: 50,
    politics: 50,
    administration: 50,
    mobility: 50,
    attack: 50,
    defense: 50,
};

export default function LobbySelectPoolPage() {
    const router = useRouter();
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const { fetchMyGeneral } = useOfficerStore();

    const [tab, setTab] = useState<string>('original');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(false);
    const [pool, setPool] = useState<Officer[]>([]);
    const [selecting, setSelecting] = useState<number | null>(null);

    // Faction context
    const [factions, setFactions] = useState<Faction[]>([]);
    const [activeFactionId, setActiveFactionId] = useState<number | null>(null);

    // Generate mode state
    const [customName, setCustomName] = useState('');
    const [customStats, setCustomStats] = useState<Record<StatKey8, number>>({ ...DEFAULT_STATS });
    const [originType, setOriginType] = useState('noble');
    const [generating, setGenerating] = useState(false);

    const activeFaction = factions.find((f) => f.id === activeFactionId);
    const factionType = activeFaction?.factionType ?? activeFaction?.faction_type ?? 'empire';

    // When factionType changes, reset origin appropriately
    useEffect(() => {
        if (factionType === 'empire') {
            setOriginType('noble');
        } else {
            setOriginType('citizen');
        }
    }, [factionType]);

    const loadData = useCallback(async () => {
        if (!currentWorld) return;
        setLoading(true);
        setError(false);
        try {
            const { data: factionList } = await factionApi.listByWorld(currentWorld.id);
            setFactions(factionList);

            // Default to first faction
            const firstFaction = factionList[0];
            if (firstFaction && !activeFactionId) {
                setActiveFactionId(firstFaction.id);
            }

            const fid = activeFactionId ?? firstFaction?.id;
            if (fid) {
                const { data: originals } = await characterApi.getAvailableOriginals(
                    currentWorld.id,
                    fid,
                );
                setPool(originals);
            }
        } catch {
            setError(true);
        } finally {
            setLoading(false);
        }
    }, [currentWorld, activeFactionId]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    const handleSelectOriginal = async (officerId: number) => {
        if (!currentWorld) return;
        setSelecting(officerId);
        try {
            await characterApi.selectOriginal(currentWorld.id, officerId);
            await fetchMyGeneral(currentWorld.id);
            toast.success('제독을 선택했습니다.');
            router.push('/');
        } catch {
            toast.error('제독 선택에 실패했습니다.');
        } finally {
            setSelecting(null);
        }
    };

    const statTotal = STAT_KEYS_8.reduce((sum, key) => sum + customStats[key], 0);
    const statRemaining = STAT_TOTAL - statTotal;
    const canSubmitGenerate = customName.trim().length >= 2 && statRemaining === 0;

    const handleGenerate = async () => {
        if (!currentWorld || !activeFactionId) return;

        const parsed = characterCreationSchema.safeParse({
            name: customName.trim(),
            originType,
            stats: customStats,
        });

        if (!parsed.success) {
            const firstError = parsed.error.issues[0];
            toast.error(firstError?.message ?? '입력을 확인해주세요.');
            return;
        }

        setGenerating(true);
        try {
            await characterApi.generate({
                sessionId: currentWorld.id,
                factionId: activeFactionId,
                name: parsed.data.name,
                originType: parsed.data.originType,
                stats: parsed.data.stats,
                planetId: activeFaction?.capitalPlanetId ?? 0,
            });
            await fetchMyGeneral(currentWorld.id);
            toast.success('제독을 생성했습니다.');
            router.push('/');
        } catch {
            toast.error('제독 생성에 실패했습니다.');
        } finally {
            setGenerating(false);
        }
    };

    if (!currentWorld) {
        return <div className="p-4 text-muted-foreground">월드를 선택해주세요.</div>;
    }

    return (
        <div className="p-4 space-y-4 max-w-3xl mx-auto">
            <Button variant="ghost" size="sm" onClick={() => router.push('/lobby')} className="mb-2">
                <ArrowLeft className="size-4 mr-1" /> 로비로 돌아가기
            </Button>

            <PageHeader icon={Users} title="제독 선택" />

            {/* Faction toggle */}
            {factions.length > 1 && (
                <div className="flex gap-2">
                    {factions.map((f) => (
                        <Button
                            key={f.id}
                            type="button"
                            variant={activeFactionId === f.id ? 'default' : 'outline'}
                            size="sm"
                            onClick={() => setActiveFactionId(f.id)}
                        >
                            {f.name}
                        </Button>
                    ))}
                </div>
            )}

            <Tabs value={tab} onValueChange={setTab}>
                <TabsList>
                    <TabsTrigger value="original">
                        <Users className="size-3.5 mr-1" />
                        오리지널 캐릭터
                    </TabsTrigger>
                    <TabsTrigger value="generate">
                        제네레이트
                    </TabsTrigger>
                </TabsList>

                {/* === Original characters tab === */}
                <TabsContent value="original" className="mt-4">
                    {loading ? (
                        <LoadingState />
                    ) : error ? (
                        <ErrorState
                            title="캐릭터 목록을 불러오지 못했습니다."
                            description="네트워크 연결을 확인하고 다시 시도해주세요."
                            onRetry={loadData}
                        />
                    ) : pool.length === 0 ? (
                        <EmptyState
                            icon={Users}
                            title="선택 가능한 캐릭터가 없습니다"
                            description="모든 오리지널 캐릭터가 선택되었습니다. 제네레이트 탭에서 새 캐릭터를 생성해주세요."
                        />
                    ) : (
                        <CharacterPickGrid
                            characters={pool}
                            onSelect={handleSelectOriginal}
                            selecting={selecting}
                        />
                    )}
                </TabsContent>

                {/* === Generate character tab === */}
                <TabsContent value="generate" className="mt-4 space-y-4">
                    <Card>
                        <CardHeader>
                            <CardTitle className="text-sm">캐릭터 생성</CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-4">
                            <div className="space-y-1">
                                <label htmlFor="gen-name" className="text-sm text-muted-foreground">
                                    제독명
                                </label>
                                <Input
                                    id="gen-name"
                                    value={customName}
                                    onChange={(e) => setCustomName(e.target.value)}
                                    placeholder="제독 이름 입력 (2~20자)"
                                    maxLength={20}
                                />
                            </div>

                            <OriginSelector
                                factionType={factionType}
                                value={originType}
                                onChange={setOriginType}
                            />

                            <StatAllocator
                                budget={STAT_TOTAL}
                                stats={customStats}
                                onChange={setCustomStats}
                                min={STAT_MIN}
                                max={STAT_MAX}
                            />

                            <Button
                                className="w-full"
                                onClick={handleGenerate}
                                disabled={generating || !canSubmitGenerate}
                            >
                                {generating ? '생성 중...' : '제독 생성'}
                            </Button>
                        </CardContent>
                    </Card>
                </TabsContent>
            </Tabs>
        </div>
    );
}
