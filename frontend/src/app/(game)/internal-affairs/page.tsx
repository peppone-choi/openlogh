'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { useGameStore } from '@/stores/gameStore';
import { frontApi, nationPolicyApi } from '@/lib/gameApi';
import type { NationFrontInfo } from '@/types';
import {
    Landmark,
    Bold,
    Italic,
    List,
    Heading2,
    Undo,
    Redo,
    Image as ImageIcon,
    Handshake,
    Calculator,
} from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { NationBadge } from '@/components/game/nation-badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Switch } from '@/components/ui/switch';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';

/* ── Simple Rich Text Editor (contentEditable-based WYSIWYG) ── */

function RichTextEditor({
    value,
    onChange,
    placeholder,
}: {
    value: string;
    onChange: (html: string) => void;
    placeholder?: string;
}) {
    const editorRef = useRef<HTMLDivElement>(null);
    const isInitRef = useRef(false);

    useEffect(() => {
        if (editorRef.current && !isInitRef.current) {
            editorRef.current.innerHTML = value;
            isInitRef.current = true;
        }
    }, [value]);

    const exec = useCallback(
        (cmd: string, val?: string) => {
            document.execCommand(cmd, false, val);
            if (editorRef.current) {
                onChange(editorRef.current.innerHTML);
            }
        },
        [onChange]
    );

    const handleInput = useCallback(() => {
        if (editorRef.current) {
            onChange(editorRef.current.innerHTML);
        }
    }, [onChange]);

    const handleInsertImage = useCallback(() => {
        const url = prompt('이미지 URL을 입력하세요:');
        if (url) {
            exec('insertImage', url);
        }
    }, [exec]);

    return (
        <div className="border rounded-md overflow-hidden">
            <div className="flex items-center gap-1 p-1 border-b bg-muted/30">
                <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    className="h-7 w-7 p-0"
                    onClick={() => exec('bold')}
                    title="굵게"
                >
                    <Bold className="size-3.5" />
                </Button>
                <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    className="h-7 w-7 p-0"
                    onClick={() => exec('italic')}
                    title="기울임"
                >
                    <Italic className="size-3.5" />
                </Button>
                <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    className="h-7 w-7 p-0"
                    onClick={() => exec('formatBlock', 'h3')}
                    title="제목"
                >
                    <Heading2 className="size-3.5" />
                </Button>
                <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    className="h-7 w-7 p-0"
                    onClick={() => exec('insertUnorderedList')}
                    title="목록"
                >
                    <List className="size-3.5" />
                </Button>
                <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    className="h-7 w-7 p-0"
                    onClick={handleInsertImage}
                    title="이미지 삽입"
                >
                    <ImageIcon className="size-3.5" />
                </Button>
                <div className="flex-1" />
                <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    className="h-7 w-7 p-0"
                    onClick={() => exec('undo')}
                    title="실행 취소"
                >
                    <Undo className="size-3.5" />
                </Button>
                <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    className="h-7 w-7 p-0"
                    onClick={() => exec('redo')}
                    title="다시 실행"
                >
                    <Redo className="size-3.5" />
                </Button>
            </div>
            <div
                ref={editorRef}
                contentEditable
                className="min-h-[160px] p-3 text-sm focus:outline-none prose prose-invert prose-sm max-w-none"
                onInput={handleInput}
                data-placeholder={placeholder}
                suppressContentEditableWarning
            />
        </div>
    );
}

const DIPLOMACY_STATES: Record<string, { label: string; color: string }> = {
    ally: { label: '동맹', color: 'text-blue-400' },
    war: { label: '전쟁', color: 'text-red-400' },
    ceasefire: { label: '휴전', color: 'text-yellow-400' },
    trade: { label: '교역', color: 'text-green-400' },
    nonaggression: { label: '불가침', color: 'text-cyan-400' },
    neutral: { label: '중립', color: 'text-gray-400' },
};

export default function InternalAffairsPage() {
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const { myOfficer, fetchMyGeneral } = useOfficerStore();
    const { cities, nations, generals, diplomacy, loadAll } = useGameStore();
    const [loading, setLoading] = useState(true);

    // Policy fields
    const [rate, setRate] = useState(15);
    const [bill, setBill] = useState(100);
    const [secretLimit, setSecretLimit] = useState(0);
    const [strategicCmdLimit, setStrategicCmdLimit] = useState(0);
    const [blockWar, setBlockWar] = useState(false);
    const [blockScout, setBlockScout] = useState(false);
    const [notice, setNotice] = useState('');
    const [scoutMsg, setScoutMsg] = useState('');
    const [saving, setSaving] = useState(false);
    const [msg, setMsg] = useState('');
    const [nationFrontInfo, setNationFrontInfo] = useState<NationFrontInfo | null>(null);

    useEffect(() => {
        if (!currentWorld) return;
        loadAll(currentWorld.id);
        if (!myOfficer) fetchMyGeneral(currentWorld.id).catch(() => {});
    }, [currentWorld, myOfficer, fetchMyGeneral, loadAll]);

    useEffect(() => {
        if (!currentWorld) return;
        frontApi
            .getInfo(currentWorld.id)
            .then(({ data }) => setNationFrontInfo(data.nation))
            .catch(() => setNationFrontInfo(null));
    }, [currentWorld]);

    const nationMap = useMemo(() => new Map(nations.map((n) => [n.id, n])), [nations]);

    // Diplomacy for my nation
    const myDiplomacy = useMemo(() => {
        if (!myOfficer?.nationId) return [];
        return diplomacy.filter(
            (d) => (d.srcNationId === myOfficer.nationId || d.destNationId === myOfficer.nationId) && !d.isDead
        );
    }, [diplomacy, myOfficer?.nationId]);

    // Financial calculator
    const myCities = useMemo(() => {
        if (!myOfficer?.nationId) return [];
        return cities.filter((c) => c.nationId === myOfficer.nationId);
    }, [cities, myOfficer?.nationId]);

    const myNation = myOfficer?.nationId ? nationMap.get(myOfficer.nationId) : null;

    const allNations = useMemo(() => {
        const cityCountByNation = new Map<number, number>();
        for (const city of cities) {
            cityCountByNation.set(city.nationId, (cityCountByNation.get(city.nationId) ?? 0) + 1);
        }

        const generalCountByNation = new Map<number, number>();
        for (const general of generals) {
            generalCountByNation.set(general.nationId, (generalCountByNation.get(general.nationId) ?? 0) + 1);
        }

        return [...nations]
            .sort((a, b) => b.power - a.power)
            .map((nation) => ({
                ...nation,
                cityCount: cityCountByNation.get(nation.id) ?? 0,
                generalCount: generalCountByNation.get(nation.id) ?? 0,
            }));
    }, [cities, generals, nations]);

    const financeSummary = useMemo(() => {
        let totalGoldIncome = 0;
        let totalRiceIncome = 0;
        let totalExpense = 0;
        for (const city of myCities) {
            const trustRatio = city.trust / 200 + 0.5;
            const goldIncome =
                city.commMax > 0 ? Math.round((city.pop * (city.comm / city.commMax) * trustRatio) / 30) : 0;
            const riceIncome =
                city.agriMax > 0 ? Math.round((city.pop * (city.agri / city.agriMax) * trustRatio) / 30) : 0;
            const expense = Math.round(city.pop * ((myNation?.bill ?? 100) / 1000));
            totalGoldIncome += goldIncome;
            totalRiceIncome += riceIncome;
            totalExpense += expense;
        }
        return {
            totalGoldIncome,
            totalRiceIncome,
            totalExpense,
            netGold: totalGoldIncome - totalExpense,
        };
    }, [myCities, myNation?.bill]);

    useEffect(() => {
        if (!myOfficer?.nationId) return;
        nationPolicyApi
            .getPolicy(myOfficer.nationId)
            .then(({ data }) => {
                setRate((data.rate as number) ?? 15);
                setBill((data.bill as number) ?? 100);
                setSecretLimit((data.secretLimit as number) ?? 0);
                setStrategicCmdLimit((data.strategicCmdLimit as number) ?? 0);
                setBlockWar(Boolean(data.blockWar));
                setBlockScout(Boolean(data.blockScout));
                setNotice((data.notice as string) ?? '');
                setScoutMsg((data.scoutMsg as string) ?? '');
            })
            .catch(() => {})
            .finally(() => setLoading(false));
    }, [myOfficer?.nationId]);

    const handleSavePolicy = async () => {
        if (!myOfficer?.nationId) return;
        setSaving(true);
        setMsg('');
        try {
            await nationPolicyApi.updatePolicy(myOfficer.nationId, {
                rate,
                bill,
                secretLimit,
                strategicCmdLimit,
            });
            await nationPolicyApi.setBlockWar(myOfficer.nationId, blockWar);
            await nationPolicyApi.setBlockScout(myOfficer.nationId, blockScout);
            setMsg('정책이 저장되었습니다.');
        } catch {
            setMsg('저장에 실패했습니다.');
        } finally {
            setSaving(false);
        }
    };

    const handleSaveNotice = async () => {
        if (!myOfficer?.nationId) return;
        setSaving(true);
        try {
            await nationPolicyApi.updateNotice(myOfficer.nationId, notice);
            setMsg('공지가 저장되었습니다.');
        } catch {
            setMsg('저장에 실패했습니다.');
        } finally {
            setSaving(false);
        }
    };

    const handleSaveScoutMsg = async () => {
        if (!myOfficer?.nationId) return;
        setSaving(true);
        try {
            await nationPolicyApi.updateScoutMsg(myOfficer.nationId, scoutMsg);
            setMsg('정찰 메시지가 저장되었습니다.');
        } catch {
            setMsg('저장에 실패했습니다.');
        } finally {
            setSaving(false);
        }
    };

    if (!currentWorld) return <div className="p-4 text-muted-foreground">월드를 선택해주세요.</div>;
    if (loading) return <LoadingState />;
    if (!myOfficer?.nationId) return <div className="p-4 text-muted-foreground">소속 진영이 없습니다.</div>;

    return (
        <div className="p-4 space-y-6 max-w-3xl mx-auto">
            <PageHeader icon={Landmark} title="내무부" />

            {msg && <p className="text-sm text-green-400">{msg}</p>}

            <Tabs defaultValue="policy">
                <TabsList>
                    <TabsTrigger value="policy">정책</TabsTrigger>
                    <TabsTrigger value="diplomacy">외교 현황</TabsTrigger>
                    <TabsTrigger value="finance">재정 계산</TabsTrigger>
                    <TabsTrigger value="notice">공지</TabsTrigger>
                    <TabsTrigger value="scout">정찰 메시지</TabsTrigger>
                </TabsList>

                <TabsContent value="policy" className="mt-4">
                    <Card>
                        <CardHeader>
                            <CardTitle>국가 정책</CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-4">
                            <div className="grid grid-cols-2 gap-4">
                                <div className="space-y-1">
                                    <span className="text-xs text-muted-foreground">교역율 (5-30)</span>
                                    <Input
                                        type="number"
                                        min={5}
                                        max={30}
                                        value={rate}
                                        onChange={(e) => setRate(Number(e.target.value))}
                                    />
                                </div>
                                <div className="space-y-1">
                                    <span className="text-xs text-muted-foreground">세율 (20-200)</span>
                                    <Input
                                        type="number"
                                        min={20}
                                        max={200}
                                        value={bill}
                                        onChange={(e) => setBill(Number(e.target.value))}
                                    />
                                </div>
                                <div className="space-y-1">
                                    <span className="text-xs text-muted-foreground">기밀 제한</span>
                                    <Input
                                        type="number"
                                        min={0}
                                        value={secretLimit}
                                        onChange={(e) => setSecretLimit(Number(e.target.value))}
                                    />
                                </div>
                                <div className="space-y-1">
                                    <span className="text-xs text-muted-foreground">전략명령 제한</span>
                                    <Input
                                        type="number"
                                        min={0}
                                        value={strategicCmdLimit}
                                        onChange={(e) => setStrategicCmdLimit(Number(e.target.value))}
                                    />
                                </div>
                            </div>
                            <div className="grid grid-cols-2 gap-4">
                                <div className="flex items-center justify-between rounded-md border p-3">
                                    <div className="space-y-0.5">
                                        <span className="text-sm font-medium">전쟁 차단</span>
                                        <p className="text-xs text-muted-foreground">
                                            소속 제독의 전쟁 명령을 차단합니다
                                        </p>
                                    </div>
                                    <Switch checked={blockWar} onCheckedChange={setBlockWar} />
                                </div>
                                <div className="flex items-center justify-between rounded-md border p-3">
                                    <div className="space-y-0.5">
                                        <span className="text-sm font-medium">임관 금지</span>
                                        <p className="text-xs text-muted-foreground">타 제독의 임관을 제한합니다</p>
                                    </div>
                                    <Switch checked={blockScout} onCheckedChange={setBlockScout} />
                                </div>
                            </div>
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                                <div className="rounded-md border p-3 space-y-1">
                                    <div className="text-muted-foreground">전쟁금지 잔여횟수</div>
                                    <div className="text-sm font-semibold tabular-nums">
                                        {nationFrontInfo ? `${nationFrontInfo.prohibitWar}회` : '-'}
                                    </div>
                                </div>
                                <div className="rounded-md border p-3 space-y-1">
                                    <div className="text-muted-foreground">임관금지 상태</div>
                                    <div className="text-sm font-semibold">
                                        {blockScout ? '금지' : '허용'}
                                        {nationFrontInfo ? ` (잔여 ${nationFrontInfo.prohibitScout}턴)` : ''}
                                    </div>
                                </div>
                            </div>
                            {nationFrontInfo?.impossibleStrategicCommand?.length ? (
                                <div className="rounded-md border p-3 text-xs space-y-1">
                                    <div className="text-muted-foreground">현재 불가능한 전략 명령</div>
                                    <div className="flex flex-wrap gap-1">
                                        {nationFrontInfo.impossibleStrategicCommand.map((cmd) => (
                                            <Badge key={cmd} variant="outline">
                                                {cmd}
                                            </Badge>
                                        ))}
                                    </div>
                                </div>
                            ) : null}
                            <Button onClick={handleSavePolicy} disabled={saving}>
                                {saving ? '저장 중...' : '정책 저장'}
                            </Button>
                        </CardContent>
                    </Card>
                </TabsContent>

                {/* Diplomacy Tab */}
                <TabsContent value="diplomacy" className="mt-4">
                    <Card>
                        <CardHeader>
                            <CardTitle className="flex items-center gap-2">
                                <Handshake className="size-4" />
                                외교 현황
                            </CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-3">
                            {myDiplomacy.length === 0 ? (
                                <p className="text-sm text-muted-foreground">외교 관계가 없습니다.</p>
                            ) : (
                                <div className="space-y-2">
                                    {myDiplomacy.map((d) => {
                                        const otherId =
                                            d.srcNationId === myOfficer!.nationId ? d.destNationId : d.srcNationId;
                                        const otherNation = nationMap.get(otherId);
                                        const stateInfo = DIPLOMACY_STATES[d.stateCode] ?? {
                                            label: d.stateCode,
                                            color: 'text-gray-400',
                                        };
                                        return (
                                            <div
                                                key={d.id}
                                                className="flex items-center justify-between border rounded p-2"
                                            >
                                                <div className="flex items-center gap-2">
                                                    {otherNation && (
                                                        <NationBadge
                                                            name={otherNation.name}
                                                            color={otherNation.color}
                                                        />
                                                    )}
                                                    <span className="text-sm">
                                                        {otherNation?.name ?? `국가#${otherId}`}
                                                    </span>
                                                </div>
                                                <div className="flex items-center gap-2">
                                                    <span className={`text-xs font-bold ${stateInfo.color}`}>
                                                        {stateInfo.label}
                                                    </span>
                                                    <span className="text-[10px] text-muted-foreground">
                                                        잔여 {d.term}턴
                                                    </span>
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            )}
                        </CardContent>
                    </Card>

                    <Card className="mt-4">
                        <CardHeader>
                            <CardTitle>모든 국가 목록</CardTitle>
                        </CardHeader>
                        <CardContent>
                            {allNations.length === 0 ? (
                                <p className="text-sm text-muted-foreground">국가 데이터가 없습니다.</p>
                            ) : (
                                <div className="space-y-2">
                                    {allNations.map((nation) => (
                                        <div
                                            key={nation.id}
                                            className="grid grid-cols-[1fr_auto_auto_auto] items-center gap-2 rounded border p-2"
                                        >
                                            <div className="flex items-center gap-2 min-w-0">
                                                <NationBadge name={nation.name} color={nation.color} />
                                                <span className="truncate text-sm">{nation.name}</span>
                                            </div>
                                            <span className="text-xs text-muted-foreground tabular-nums">
                                                국력 {nation.power.toLocaleString()}
                                            </span>
                                            <span className="text-xs text-muted-foreground tabular-nums">
                                                제독 {nation.generalCount}
                                            </span>
                                            <span className="text-xs text-muted-foreground tabular-nums">
                                                도시 {nation.cityCount}
                                            </span>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </CardContent>
                    </Card>
                </TabsContent>

                {/* Finance Tab */}
                <TabsContent value="finance" className="mt-4">
                    <Card>
                        <CardHeader>
                            <CardTitle className="flex items-center gap-2">
                                <Calculator className="size-4" />
                                재정 계산기
                            </CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-4">
                            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                                <div className="border rounded p-3 text-center">
                                    <div className="text-[10px] text-muted-foreground">금 수입</div>
                                    <div className="text-sm font-bold text-amber-400 tabular-nums">
                                        {financeSummary.totalGoldIncome.toLocaleString()}
                                    </div>
                                </div>
                                <div className="border rounded p-3 text-center">
                                    <div className="text-[10px] text-muted-foreground">쌀 수입</div>
                                    <div className="text-sm font-bold text-green-400 tabular-nums">
                                        {financeSummary.totalRiceIncome.toLocaleString()}
                                    </div>
                                </div>
                                <div className="border rounded p-3 text-center">
                                    <div className="text-[10px] text-muted-foreground">지출</div>
                                    <div className="text-sm font-bold text-red-400 tabular-nums">
                                        {financeSummary.totalExpense.toLocaleString()}
                                    </div>
                                </div>
                                <div className="border rounded p-3 text-center">
                                    <div className="text-[10px] text-muted-foreground">금 순수익</div>
                                    <div
                                        className={`text-sm font-bold tabular-nums ${financeSummary.netGold >= 0 ? 'text-green-400' : 'text-red-400'}`}
                                    >
                                        {financeSummary.netGold >= 0 ? '+' : ''}
                                        {financeSummary.netGold.toLocaleString()}
                                    </div>
                                </div>
                            </div>
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                                <div className="rounded-md border p-3">
                                    <div className="text-muted-foreground">금 세부</div>
                                    <div className="mt-1 space-y-0.5">
                                        <div>단기수입: -</div>
                                        <div>세금(추정): {financeSummary.totalGoldIncome.toLocaleString()}</div>
                                    </div>
                                </div>
                                <div className="rounded-md border p-3">
                                    <div className="text-muted-foreground">쌀 세부</div>
                                    <div className="mt-1 space-y-0.5">
                                        <div>둔전수입: -</div>
                                        <div>세금(추정): {financeSummary.totalRiceIncome.toLocaleString()}</div>
                                    </div>
                                </div>
                            </div>
                            <div className="text-xs text-muted-foreground">
                                행성 수: {myCities.length}개 / 보유금: {myNation?.gold?.toLocaleString() ?? 0} / 보유쌀:{' '}
                                {myNation?.rice?.toLocaleString() ?? 0}
                            </div>
                            <div className="text-[10px] text-muted-foreground">
                                ※ 단기수입/둔전수입은 현재 API 미제공으로 표시하지 않습니다. 세금은 추정치입니다.
                            </div>
                        </CardContent>
                    </Card>
                </TabsContent>

                <TabsContent value="notice" className="mt-4">
                    <Card>
                        <CardHeader>
                            <CardTitle>국가 공지</CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-3">
                            <RichTextEditor
                                value={notice}
                                onChange={setNotice}
                                placeholder="국가 공지를 입력하세요..."
                            />
                            <Button onClick={handleSaveNotice} disabled={saving}>
                                {saving ? '저장 중...' : '공지 저장'}
                            </Button>
                        </CardContent>
                    </Card>
                </TabsContent>

                <TabsContent value="scout" className="mt-4">
                    <Card>
                        <CardHeader>
                            <CardTitle>정찰 메시지</CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-3">
                            <Textarea
                                value={scoutMsg}
                                onChange={(e) => setScoutMsg(e.target.value)}
                                placeholder="정찰 메시지를 입력하세요..."
                                className="resize-none h-32"
                            />
                            <Button onClick={handleSaveScoutMsg} disabled={saving}>
                                {saving ? '저장 중...' : '정찰 메시지 저장'}
                            </Button>
                        </CardContent>
                    </Card>
                </TabsContent>
            </Tabs>
        </div>
    );
}
