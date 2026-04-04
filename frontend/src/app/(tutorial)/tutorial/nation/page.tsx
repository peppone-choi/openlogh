'use client';

import { useGameStore } from '@/stores/gameStore';
import { useGeneralStore } from '@/stores/generalStore';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { Badge } from '@/components/ui/8bit/badge';

const OFFICER_LEVELS: Record<number, string> = {
    12: '군주',
    8: '대장군',
    5: '승상',
    1: '일반',
};

export default function TutorialNationPage() {
    const nations = useGameStore((s) => s.nations);
    const generals = useGameStore((s) => s.generals);
    const cities = useGameStore((s) => s.cities);
    const myGeneral = useGeneralStore((s) => s.myGeneral);

    const myNation = nations.find((n) => n.id === myGeneral?.nationId);
    const nationGenerals = generals.filter((g) => g.nationId === myNation?.id);
    const nationCities = cities.filter((c) => c.nationId === myNation?.id);

    if (!myNation) return null;

    return (
        <div className="max-w-2xl mx-auto p-4 space-y-4 pb-20">
            <h1 className="text-xl font-bold flex items-center gap-2">
                <span className="inline-block w-4 h-4 rounded-full" style={{ backgroundColor: myNation.color }} />
                {myNation.name}
            </h1>

            {/* 세력 정보 */}
            <Card data-tutorial="nation-info">
                <CardHeader>
                    <CardTitle className="text-base">세력 현황</CardTitle>
                </CardHeader>
                <CardContent className="grid grid-cols-2 gap-3 text-sm">
                    <div>
                        <span className="text-muted-foreground">금: </span>
                        <span className="font-medium">{myNation.gold.toLocaleString()}</span>
                    </div>
                    <div>
                        <span className="text-muted-foreground">쌀: </span>
                        <span className="font-medium">{myNation.rice.toLocaleString()}</span>
                    </div>
                    <div>
                        <span className="text-muted-foreground">기술: </span>
                        <span className="font-medium">{myNation.tech}</span>
                    </div>
                    <div>
                        <span className="text-muted-foreground">국력: </span>
                        <span className="font-medium">{myNation.power}</span>
                    </div>
                    <div>
                        <span className="text-muted-foreground">도시 수: </span>
                        <span className="font-medium">{nationCities.length}</span>
                    </div>
                    <div>
                        <span className="text-muted-foreground">장수 수: </span>
                        <span className="font-medium">{nationGenerals.length}</span>
                    </div>
                </CardContent>
            </Card>

            {/* 인사부 — 장수 목록 + 관직 */}
            <Card>
                <CardHeader>
                    <CardTitle className="text-base">인사부 - 장수 관직</CardTitle>
                </CardHeader>
                <CardContent className="space-y-2">
                    <p className="text-xs text-muted-foreground mb-3">
                        장수에게 관직을 임명하면 능력치 보너스를 받을 수 있습니다.
                    </p>
                    {nationGenerals.map((g) => (
                        <div key={g.id} className="flex items-center justify-between p-2 rounded bg-muted/50">
                            <div className="flex items-center gap-2">
                                <span className="font-medium">{g.name}</span>
                                {g.id === myGeneral?.id && (
                                    <Badge variant="secondary" className="text-[10px]">
                                        나
                                    </Badge>
                                )}
                            </div>
                            <Badge variant="outline">{OFFICER_LEVELS[g.officerLevel] ?? `Lv.${g.officerLevel}`}</Badge>
                        </div>
                    ))}
                </CardContent>
            </Card>

            {/* 내무부 — 세율/정책 */}
            <Card>
                <CardHeader>
                    <CardTitle className="text-base">내무부 - 국가 정책</CardTitle>
                </CardHeader>
                <CardContent className="space-y-3 text-sm">
                    <p className="text-xs text-muted-foreground">
                        군주 또는 고위 관직자는 국가 정책을 설정할 수 있습니다.
                    </p>
                    <div className="grid grid-cols-2 gap-2">
                        <div className="p-2 rounded bg-muted/50">
                            <span className="text-muted-foreground text-xs">세율</span>
                            <p className="font-medium">{myNation.rate}%</p>
                        </div>
                        <div className="p-2 rounded bg-muted/50">
                            <span className="text-muted-foreground text-xs">봉급</span>
                            <p className="font-medium">{myNation.bill}</p>
                        </div>
                    </div>
                    <p className="text-xs text-muted-foreground">
                        세율이 높으면 국고 수입이 늘지만 민심(치안)이 떨어집니다.
                    </p>
                </CardContent>
            </Card>

            {/* 도시 목록 */}
            <Card>
                <CardHeader>
                    <CardTitle className="text-base">보유 도시</CardTitle>
                </CardHeader>
                <CardContent className="space-y-2">
                    {nationCities.map((c) => (
                        <div key={c.id} className="flex items-center justify-between p-2 rounded bg-muted/50 text-sm">
                            <span className="font-medium">
                                {c.name}
                                {c.id === myNation.capitalCityId && (
                                    <Badge variant="secondary" className="ml-2 text-[10px]">
                                        수도
                                    </Badge>
                                )}
                            </span>
                            <span className="text-xs text-muted-foreground">인구 {c.pop.toLocaleString()}</span>
                        </div>
                    ))}
                </CardContent>
            </Card>
        </div>
    );
}
