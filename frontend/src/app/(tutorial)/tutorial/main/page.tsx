'use client';

import { useWorldStore } from '@/stores/worldStore';
import { useGeneralStore } from '@/stores/generalStore';
import { useGameStore } from '@/stores/gameStore';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { StatBar } from '@/components/game/stat-bar';

export default function TutorialMainPage() {
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const myGeneral = useGeneralStore((s) => s.myGeneral);
    const nations = useGameStore((s) => s.nations);
    const cities = useGameStore((s) => s.cities);

    const myNation = nations.find((n) => n.id === myGeneral?.nationId);
    const myCity = cities.find((c) => c.id === myGeneral?.cityId);

    return (
        <div className="max-w-4xl mx-auto p-4 space-y-4 pb-20">
            {/* 상단 정보 */}
            <div className="flex items-center justify-between">
                <h1 className="text-xl font-bold">
                    {currentWorld?.name ?? '튜토리얼'} - {currentWorld?.currentYear}년 {currentWorld?.currentMonth}월
                </h1>
                {myNation && <Badge style={{ backgroundColor: myNation.color, color: '#fff' }}>{myNation.name}</Badge>}
            </div>

            <div className="grid gap-4 md:grid-cols-2">
                {/* 사이드바 역할 — 메뉴 안내 */}
                <Card data-tutorial="sidebar">
                    <CardHeader>
                        <CardTitle className="text-base">메뉴</CardTitle>
                    </CardHeader>
                    <CardContent className="space-y-2 text-sm">
                        <div className="flex items-center gap-2 p-2 rounded bg-muted/50">
                            <span className="font-medium">커맨드</span>
                            <span className="text-xs text-muted-foreground">턴 행동 설정</span>
                        </div>
                        <div className="flex items-center gap-2 p-2 rounded bg-muted/50">
                            <span className="font-medium">현재 도시</span>
                            <span className="text-xs text-muted-foreground">도시 정보 확인</span>
                        </div>
                        <div className="flex items-center gap-2 p-2 rounded bg-muted/50">
                            <span className="font-medium">세력 정보</span>
                            <span className="text-xs text-muted-foreground">국가 현황</span>
                        </div>
                        <div className="flex items-center gap-2 p-2 rounded bg-muted/50">
                            <span className="font-medium">외교부</span>
                            <span className="text-xs text-muted-foreground">외교 관계 관리</span>
                        </div>
                        <div className="flex items-center gap-2 p-2 rounded bg-muted/50">
                            <span className="font-medium">인사부</span>
                            <span className="text-xs text-muted-foreground">관직 임명</span>
                        </div>
                    </CardContent>
                </Card>

                {/* 장수 정보 */}
                {myGeneral && (
                    <Card>
                        <CardHeader>
                            <CardTitle className="text-base">내 장수 - {myGeneral.name}</CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-2">
                            <StatBar label="통솔" value={myGeneral.leadership} color="bg-red-500" />
                            <StatBar label="무력" value={myGeneral.strength} color="bg-orange-500" />
                            <StatBar label="지력" value={myGeneral.intel} color="bg-blue-500" />
                            <StatBar label="정치" value={myGeneral.politics} color="bg-green-500" />
                            <StatBar label="매력" value={myGeneral.charm} color="bg-purple-500" />
                            <div className="mt-3 grid grid-cols-2 gap-2 text-xs">
                                <div>
                                    <span className="text-muted-foreground">병력: </span>
                                    <span className="font-medium">{myGeneral.crew}</span>
                                </div>
                                <div>
                                    <span className="text-muted-foreground">훈련: </span>
                                    <span className="font-medium">{myGeneral.train}</span>
                                </div>
                                <div>
                                    <span className="text-muted-foreground">금: </span>
                                    <span className="font-medium">{myGeneral.gold}</span>
                                </div>
                                <div>
                                    <span className="text-muted-foreground">쌀: </span>
                                    <span className="font-medium">{myGeneral.rice}</span>
                                </div>
                            </div>
                        </CardContent>
                    </Card>
                )}

                {/* 도시 정보 요약 */}
                {myCity && (
                    <Card>
                        <CardHeader>
                            <CardTitle className="text-base">현재 도시 - {myCity.name}</CardTitle>
                        </CardHeader>
                        <CardContent className="grid grid-cols-2 gap-2 text-xs">
                            <div>
                                <span className="text-muted-foreground">인구: </span>
                                <span>
                                    {myCity.pop.toLocaleString()} / {myCity.popMax.toLocaleString()}
                                </span>
                            </div>
                            <div>
                                <span className="text-muted-foreground">농업: </span>
                                <span>
                                    {myCity.agri.toLocaleString()} / {myCity.agriMax.toLocaleString()}
                                </span>
                            </div>
                            <div>
                                <span className="text-muted-foreground">상업: </span>
                                <span>
                                    {myCity.comm.toLocaleString()} / {myCity.commMax.toLocaleString()}
                                </span>
                            </div>
                            <div>
                                <span className="text-muted-foreground">치안: </span>
                                <span>
                                    {myCity.secu} / {myCity.secuMax}
                                </span>
                            </div>
                        </CardContent>
                    </Card>
                )}

                {/* 국가 정보 요약 */}
                {myNation && (
                    <Card>
                        <CardHeader>
                            <CardTitle className="text-base flex items-center gap-2">
                                <span
                                    className="inline-block w-3 h-3 rounded-full"
                                    style={{ backgroundColor: myNation.color }}
                                />
                                {myNation.name}
                            </CardTitle>
                        </CardHeader>
                        <CardContent className="grid grid-cols-2 gap-2 text-xs">
                            <div>
                                <span className="text-muted-foreground">금: </span>
                                <span>{myNation.gold.toLocaleString()}</span>
                            </div>
                            <div>
                                <span className="text-muted-foreground">쌀: </span>
                                <span>{myNation.rice.toLocaleString()}</span>
                            </div>
                            <div>
                                <span className="text-muted-foreground">기술: </span>
                                <span>{myNation.tech}</span>
                            </div>
                            <div>
                                <span className="text-muted-foreground">국력: </span>
                                <span>{myNation.power}</span>
                            </div>
                        </CardContent>
                    </Card>
                )}
            </div>
        </div>
    );
}
