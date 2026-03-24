'use client';

import { useGameStore } from '@/stores/gameStore';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { SammoBar } from '@/components/game/sammo-bar';

export default function TutorialCityPage() {
    const cities = useGameStore((s) => s.cities);
    const nations = useGameStore((s) => s.nations);

    const myCity = cities.find((c) => c.id === -1); // 성도

    if (!myCity) return null;

    const nation = nations.find((n) => n.id === myCity.nationId);

    return (
        <div className="max-w-2xl mx-auto p-4 space-y-4 pb-20">
            <h1 className="text-xl font-bold">
                현재 도시 - {myCity.name}
                {nation && (
                    <span className="text-sm font-normal ml-2" style={{ color: nation.color }}>
                        ({nation.name})
                    </span>
                )}
            </h1>

            <Card data-tutorial="city-stats">
                <CardHeader>
                    <CardTitle className="text-base">도시 현황</CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                    {/* Stats table */}
                    <div className="space-y-3">
                        <div>
                            <div className="flex justify-between text-sm mb-1">
                                <span>인구</span>
                                <span>
                                    {myCity.pop.toLocaleString()} / {myCity.popMax.toLocaleString()}
                                </span>
                            </div>
                            <SammoBar height={7} percent={(myCity.pop / myCity.popMax) * 100} />
                        </div>
                        <div>
                            <div className="flex justify-between text-sm mb-1">
                                <span>농업</span>
                                <span>
                                    {myCity.agri.toLocaleString()} / {myCity.agriMax.toLocaleString()}
                                </span>
                            </div>
                            <SammoBar height={7} percent={(myCity.agri / myCity.agriMax) * 100} />
                        </div>
                        <div>
                            <div className="flex justify-between text-sm mb-1">
                                <span>상업</span>
                                <span>
                                    {myCity.comm.toLocaleString()} / {myCity.commMax.toLocaleString()}
                                </span>
                            </div>
                            <SammoBar height={7} percent={(myCity.comm / myCity.commMax) * 100} />
                        </div>
                        <div>
                            <div className="flex justify-between text-sm mb-1">
                                <span>치안</span>
                                <span>
                                    {myCity.secu} / {myCity.secuMax}
                                </span>
                            </div>
                            <SammoBar height={7} percent={(myCity.secu / myCity.secuMax) * 100} />
                        </div>
                        <div>
                            <div className="flex justify-between text-sm mb-1">
                                <span>수비</span>
                                <span>
                                    {myCity.def.toLocaleString()} / {myCity.defMax.toLocaleString()}
                                </span>
                            </div>
                            <SammoBar height={7} percent={(myCity.def / myCity.defMax) * 100} />
                        </div>
                        <div>
                            <div className="flex justify-between text-sm mb-1">
                                <span>성벽</span>
                                <span>
                                    {myCity.wall.toLocaleString()} / {myCity.wallMax.toLocaleString()}
                                </span>
                            </div>
                            <SammoBar height={7} percent={(myCity.wall / myCity.wallMax) * 100} />
                        </div>
                    </div>

                    <div className="text-xs text-muted-foreground mt-4 space-y-1">
                        <p>농업/상업 수치가 높을수록 턴마다 더 많은 자원이 생산됩니다.</p>
                        <p>치안이 낮으면 인구가 줄고, 수비/성벽이 높으면 도시 방어에 유리합니다.</p>
                    </div>
                </CardContent>
            </Card>
        </div>
    );
}
