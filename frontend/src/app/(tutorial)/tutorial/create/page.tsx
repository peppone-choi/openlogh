'use client';

import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { Button } from '@/components/ui/8bit/button';
import { StatBar } from '@/components/game/stat-bar';
import { Badge } from '@/components/ui/8bit/badge';

const CREW_TYPES = [
    { id: 1, name: '창병', description: '균형 잡힌 보병. 기병에 강하다.' },
    { id: 2, name: '궁병', description: '원거리 공격. 창병에 강하다.' },
    { id: 3, name: '기병', description: '빠른 기동력. 궁병에 강하다.' },
];

export default function TutorialCreatePage() {
    const [stats] = useState({
        leadership: 75,
        strength: 70,
        intel: 65,
        politics: 80,
        charm: 95,
    });

    const [selectedCrew, setSelectedCrew] = useState<number>(1);

    return (
        <div className="max-w-2xl mx-auto p-4 space-y-6 pb-20">
            <h1 className="text-xl font-bold text-center">장수 생성</h1>

            {/* 스탯 배분 */}
            <Card data-tutorial="stat-form">
                <CardHeader>
                    <CardTitle className="text-base">능력치 배분</CardTitle>
                </CardHeader>
                <CardContent className="space-y-3">
                    <p className="text-xs text-muted-foreground mb-4">
                        장수의 5가지 능력치입니다. 각 능력치는 게임 내 다양한 행동에 영향을 줍니다.
                    </p>
                    <StatBar label="통솔" value={stats.leadership} color="bg-red-500" />
                    <StatBar label="무력" value={stats.strength} color="bg-orange-500" />
                    <StatBar label="지력" value={stats.intel} color="bg-blue-500" />
                    <StatBar label="정치" value={stats.politics} color="bg-green-500" />
                    <StatBar label="매력" value={stats.charm} color="bg-purple-500" />

                    <div className="mt-4 grid grid-cols-2 gap-2 text-xs text-muted-foreground">
                        <div>
                            <span className="font-semibold text-red-400">통솔</span> - 전투 지휘, 징병 효율
                        </div>
                        <div>
                            <span className="font-semibold text-orange-400">무력</span> - 전투 데미지, 일기토
                        </div>
                        <div>
                            <span className="font-semibold text-blue-400">지력</span> - 계략, 방어 판정
                        </div>
                        <div>
                            <span className="font-semibold text-green-400">정치</span> - 내정 효율
                        </div>
                        <div>
                            <span className="font-semibold text-purple-400">매력</span> - 외교, 징병 성공률
                        </div>
                    </div>
                </CardContent>
            </Card>

            {/* 병종 선택 */}
            <Card data-tutorial="crew-select">
                <CardHeader>
                    <CardTitle className="text-base">병종 선택</CardTitle>
                </CardHeader>
                <CardContent className="space-y-3">
                    <p className="text-xs text-muted-foreground mb-4">
                        병종에 따라 전투 스타일이 달라집니다. 상성 관계가 있어 전략적 선택이 중요합니다.
                    </p>
                    <div className="grid gap-2">
                        {CREW_TYPES.map((crew) => (
                            <Button
                                key={crew.id}
                                variant={selectedCrew === crew.id ? 'default' : 'outline'}
                                className="justify-start h-auto py-3"
                                onClick={() => setSelectedCrew(crew.id)}
                            >
                                <div className="text-left">
                                    <div className="flex items-center gap-2">
                                        <span className="font-bold">{crew.name}</span>
                                        {selectedCrew === crew.id && (
                                            <Badge variant="secondary" className="text-[10px]">
                                                선택됨
                                            </Badge>
                                        )}
                                    </div>
                                    <p className="text-xs text-muted-foreground mt-0.5">{crew.description}</p>
                                </div>
                            </Button>
                        ))}
                    </div>
                </CardContent>
            </Card>
        </div>
    );
}
