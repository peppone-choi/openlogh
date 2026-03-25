'use client';

import { useGameStore } from '@/stores/gameStore';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { Button } from '@/components/ui/8bit/button';
import { Badge } from '@/components/ui/8bit/badge';

const DIPLOMACY_STATE_LABELS: Record<string, { label: string; color: string }> = {
    neutral: { label: '중립', color: 'text-gray-400' },
    alliance: { label: '동맹', color: 'text-green-400' },
    alliance_proposed: { label: '동맹 제안', color: 'text-yellow-400' },
    nonaggression: { label: '불가침', color: 'text-blue-400' },
    war: { label: '전쟁', color: 'text-red-400' },
};

export default function TutorialDiplomacyPage() {
    const nations = useGameStore((s) => s.nations);
    const diplomacy = useGameStore((s) => s.diplomacy);

    return (
        <div className="max-w-2xl mx-auto p-4 space-y-4 pb-20">
            <h1 className="text-xl font-bold">외교</h1>

            {/* 외교 관계 테이블 */}
            <Card data-tutorial="diplomacy-table">
                <CardHeader>
                    <CardTitle className="text-base">외교 관계</CardTitle>
                </CardHeader>
                <CardContent>
                    <div className="overflow-x-auto">
                        <table className="w-full text-sm">
                            <thead>
                                <tr className="border-b">
                                    <th className="text-left py-2 pr-4">국가</th>
                                    <th className="text-left py-2 pr-4">대상</th>
                                    <th className="text-left py-2">상태</th>
                                </tr>
                            </thead>
                            <tbody>
                                {diplomacy.map((d) => {
                                    const src = nations.find((n) => n.id === d.srcNationId);
                                    const dest = nations.find((n) => n.id === d.destNationId);
                                    const stateInfo = DIPLOMACY_STATE_LABELS[d.stateCode] ?? {
                                        label: d.stateCode,
                                        color: 'text-gray-400',
                                    };

                                    return (
                                        <tr key={d.id} className="border-b last:border-0">
                                            <td className="py-2 pr-4">
                                                <span style={{ color: src?.color }}>{src?.name ?? '?'}</span>
                                            </td>
                                            <td className="py-2 pr-4">
                                                <span style={{ color: dest?.color }}>{dest?.name ?? '?'}</span>
                                            </td>
                                            <td className="py-2">
                                                <Badge variant="outline" className={stateInfo.color}>
                                                    {stateInfo.label}
                                                </Badge>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                </CardContent>
            </Card>

            {/* 중원 정보 */}
            <Card>
                <CardHeader>
                    <CardTitle className="text-base">중원 정보</CardTitle>
                </CardHeader>
                <CardContent className="space-y-3">
                    {nations.map((n) => {
                        const nationCities = useGameStore.getState().cities.filter((c) => c.nationId === n.id);
                        const nationGenerals = useGameStore.getState().generals.filter((g) => g.nationId === n.id);

                        return (
                            <div key={n.id} className="flex items-center justify-between p-2 rounded bg-muted/50">
                                <div className="flex items-center gap-2">
                                    <span
                                        className="inline-block w-3 h-3 rounded-full"
                                        style={{ backgroundColor: n.color }}
                                    />
                                    <span className="font-medium">{n.name}</span>
                                </div>
                                <div className="flex gap-3 text-xs text-muted-foreground">
                                    <span>도시: {nationCities.length}</span>
                                    <span>장수: {nationGenerals.length}</span>
                                    <span>기술: {n.tech}</span>
                                </div>
                            </div>
                        );
                    })}
                </CardContent>
            </Card>

            {/* 서신 보내기 */}
            <Card data-tutorial="send-letter">
                <CardHeader>
                    <CardTitle className="text-base">외교 서신</CardTitle>
                </CardHeader>
                <CardContent className="space-y-3">
                    <p className="text-sm text-muted-foreground">위나라에 동맹을 제안하는 서신을 보낼 수 있습니다.</p>
                    <Button variant="outline" className="w-full">
                        위나라에 동맹 제안 서신 보내기
                    </Button>
                </CardContent>
            </Card>
        </div>
    );
}
