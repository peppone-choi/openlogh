'use client';

import { MOCK_RESULT_WAR } from '@/data/tutorial/mock-commands';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { Badge } from '@/components/ui/8bit/badge';

export default function TutorialBattlePage() {
    const logs = MOCK_RESULT_WAR.logs;

    return (
        <div className="max-w-2xl mx-auto p-4 space-y-4 pb-20">
            <h1 className="text-xl font-bold">전투</h1>

            {/* 전투 요약 */}
            <Card>
                <CardHeader>
                    <CardTitle className="text-base flex items-center gap-2">
                        전투 요약
                        <Badge variant="secondary">승리</Badge>
                    </CardTitle>
                </CardHeader>
                <CardContent className="grid grid-cols-3 gap-4 text-center text-sm">
                    <div>
                        <p className="text-muted-foreground text-xs">공격</p>
                        <p className="font-bold text-red-400">유비 (촉)</p>
                        <p className="text-xs">창병 1,500</p>
                    </div>
                    <div className="flex items-center justify-center text-lg font-bold text-muted-foreground">VS</div>
                    <div>
                        <p className="text-muted-foreground text-xs">수비</p>
                        <p className="font-bold text-gray-400">수비대 (중립)</p>
                        <p className="text-xs">수비병 500</p>
                    </div>
                </CardContent>
            </Card>

            {/* 전투 로그 */}
            <Card data-tutorial="battle-log">
                <CardHeader>
                    <CardTitle className="text-base">전투 로그</CardTitle>
                </CardHeader>
                <CardContent>
                    <div className="space-y-1 text-sm font-mono bg-muted/50 rounded p-3">
                        {logs.map((log, i) => (
                            <p
                                key={i}
                                className={
                                    log.includes('승리')
                                        ? 'text-green-400 font-bold'
                                        : log.includes('공격') || log.includes('강공')
                                          ? 'text-red-300'
                                          : log.includes('반격')
                                            ? 'text-blue-300'
                                            : 'text-muted-foreground'
                                }
                            >
                                {log}
                            </p>
                        ))}
                    </div>
                </CardContent>
            </Card>

            {/* 전투 결과 설명 */}
            <Card>
                <CardContent className="pt-4 text-xs text-muted-foreground space-y-2">
                    <p>전투 결과는 장수의 통솔/무력/지력과 병종 상성에 의해 결정됩니다.</p>
                    <p>
                        <span className="text-red-400">창병</span>은 <span className="text-green-400">기병</span>에
                        강하고, <span className="text-green-400">기병</span>은{' '}
                        <span className="text-blue-400">궁병</span>에 강하며,{' '}
                        <span className="text-blue-400">궁병</span>은 <span className="text-red-400">창병</span>에
                        강합니다.
                    </p>
                    <p>도시를 점령하면 해당 도시가 아국 소속이 됩니다.</p>
                </CardContent>
            </Card>
        </div>
    );
}
