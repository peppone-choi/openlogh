'use client';

import { useEffect, useMemo, useState, useCallback } from 'react';
import { useWorldStore } from '@/stores/worldStore';
import { useGeneralStore } from '@/stores/generalStore';
import { useGameStore } from '@/stores/gameStore';
import { diplomacyLetterApi, historyApi } from '@/lib/gameApi';
import { subscribeWebSocket } from '@/lib/websocket';
import type { Message, Diplomacy } from '@/types';
import { Handshake, Send, History, ArrowRight } from 'lucide-react';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { EmptyState } from '@/components/game/empty-state';
import { NationBadge } from '@/components/game/nation-badge';
import { GeneralPortrait } from '@/components/game/general-portrait';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { Badge } from '@/components/ui/8bit/badge';
import { Button } from '@/components/ui/8bit/button';
import { Textarea } from '@/components/ui/8bit/textarea';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/8bit/tabs';
import { formatGameLogDate } from '@/lib/gameLogDate';
import { formatLog } from '@/lib/formatLog';

const STATE_LABELS: Record<string, string> = {
    war: '전쟁',
    ceasefire: '휴전',
    ceasefire_proposal: '종전제의',
    alliance: '동맹',
    nonaggression: '불가침',
    neutral: '중립',
};

const STATE_BADGE_VARIANT: Record<string, 'destructive' | 'default' | 'secondary' | 'outline'> = {
    war: 'destructive',
    ceasefire: 'outline',
    ceasefire_proposal: 'outline',
    alliance: 'default',
    nonaggression: 'secondary',
    neutral: 'outline',
};

const LETTER_TYPES = [
    { value: 'alliance', label: '동맹' },
    { value: 'nonaggression', label: '불가침' },
    { value: 'ceasefire', label: '종전' },
    { value: 'war', label: '선전포고' },
];

export default function DiplomacyPage() {
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const { myGeneral, fetchMyGeneral } = useGeneralStore();
    const { nations, diplomacy, generals, loading, loadAll } = useGameStore();

    // Letter state
    const [letters, setLetters] = useState<Message[]>([]);
    const [lettersLoading, setLettersLoading] = useState(false);
    const [showSend, setShowSend] = useState(false);
    const [destNationId, setDestNationId] = useState('');
    const [letterType, setLetterType] = useState('alliance');
    const [letterContent, setLetterContent] = useState('');
    const [letterDiplomaticContent, setLetterDiplomaticContent] = useState('');
    const [sending, setSending] = useState(false);
    const [showDualContent, setShowDualContent] = useState(false);

    // History state
    const [historyRecords, setHistoryRecords] = useState<Message[]>([]);
    const [historyLoading, setHistoryLoading] = useState(false);

    useEffect(() => {
        if (!currentWorld) return;
        if (!myGeneral) fetchMyGeneral(currentWorld.id).catch(() => {});
        loadAll(currentWorld.id);
    }, [currentWorld, myGeneral, fetchMyGeneral, loadAll]);

    const loadLetters = useCallback(() => {
        if (!myGeneral?.nationId) return;
        setLettersLoading(true);
        diplomacyLetterApi
            .list(myGeneral.nationId)
            .then(({ data }) => setLetters(data))
            .catch(() => {})
            .finally(() => setLettersLoading(false));
    }, [myGeneral?.nationId]);

    useEffect(() => {
        loadLetters();
    }, [loadLetters]);

    // Load history records
    useEffect(() => {
        if (!currentWorld) return;
        setHistoryLoading(true);
        historyApi
            .getWorldHistory(currentWorld.id)
            .then(({ data }) => setHistoryRecords(data))
            .catch(() => {})
            .finally(() => setHistoryLoading(false));
    }, [currentWorld]);

    // Auto-refresh on diplomacy events via WebSocket
    useEffect(() => {
        if (!currentWorld) return;
        return subscribeWebSocket(`/topic/world/${currentWorld.id}/diplomacy`, () => {
            loadAll(currentWorld.id);
            loadLetters();
        });
    }, [currentWorld, loadAll, loadLetters]);

    const nationMap = useMemo(() => new Map(nations.map((n) => [n.id, n])), [nations]);
    const generalMap = useMemo(() => new Map(generals.map((g) => [g.id, g])), [generals]);
    const letterIdSet = useMemo(() => new Set(letters.map((l) => String(l.id))), [letters]);

    const activeDiplomacy = useMemo(() => diplomacy.filter((d) => !d.isDead), [diplomacy]);

    // Group diplomacy by state for Tab 1
    const grouped = useMemo(() => {
        const groups: Record<string, Diplomacy[]> = {};
        for (const d of activeDiplomacy) {
            const key = d.stateCode;
            if (!groups[key]) groups[key] = [];
            groups[key].push(d);
        }
        return groups;
    }, [activeDiplomacy]);

    // Filter diplomacy-related history
    const diplomacyHistory = useMemo(() => {
        return historyRecords
            .filter((r) => {
                const msg = (r.payload?.content as string) ?? r.payload?.message;
                if (typeof msg !== 'string') return false;
                return (
                    msg.includes('동맹') ||
                    msg.includes('불가침') ||
                    msg.includes('선전포고') ||
                    msg.includes('종전') ||
                    msg.includes('휴전') ||
                    msg.includes('외교') ||
                    msg.includes('전쟁')
                );
            })
            .slice(0, 50);
    }, [historyRecords]);

    const handleSendLetter = async () => {
        if (!currentWorld || !myGeneral?.nationId || !destNationId) return;
        setSending(true);
        try {
            await diplomacyLetterApi.send(myGeneral.nationId, {
                worldId: currentWorld.id,
                destNationId: Number(destNationId),
                type: letterType,
                content: letterContent || undefined,
                diplomaticContent: letterDiplomaticContent || undefined,
            });
            setShowSend(false);
            setDestNationId('');
            setLetterContent('');
            setLetterDiplomaticContent('');
            setShowDualContent(false);
            const { data } = await diplomacyLetterApi.list(myGeneral.nationId);
            setLetters(data);
        } finally {
            setSending(false);
        }
    };

    // Execute a diplomacy agreement (after acceptance)
    const handleExecute = async (letterId: number) => {
        try {
            await diplomacyLetterApi.execute(letterId);
            if (myGeneral?.nationId) {
                const { data } = await diplomacyLetterApi.list(myGeneral.nationId);
                setLetters(data);
            }
            if (currentWorld) loadAll(currentWorld.id);
        } catch {
            /* handled by UI */
        }
    };

    const [rejectingLetterId, setRejectingLetterId] = useState<number | null>(null);
    const [rejectReason, setRejectReason] = useState('');

    const handleRespond = async (letterId: number, accept: boolean, reason?: string) => {
        await diplomacyLetterApi.respond(letterId, accept, reason);
        setRejectingLetterId(null);
        setRejectReason('');
        if (myGeneral?.nationId) {
            const { data } = await diplomacyLetterApi.list(myGeneral.nationId);
            setLetters(data);
        }
    };

    const handleRollback = async (letterId: number) => {
        await diplomacyLetterApi.rollback(letterId);
        if (myGeneral?.nationId) {
            const { data } = await diplomacyLetterApi.list(myGeneral.nationId);
            setLetters(data);
        }
    };

    const handleDestroy = async (letterId: number) => {
        await diplomacyLetterApi.destroy(letterId);
        if (myGeneral?.nationId) {
            const { data } = await diplomacyLetterApi.list(myGeneral.nationId);
            setLetters(data);
        }
    };

    if (!currentWorld) return <div className="p-4 text-muted-foreground">월드를 선택해주세요.</div>;
    if (loading) return <LoadingState />;

    const sections = [
        { key: 'war', label: '전쟁' },
        { key: 'ceasefire', label: '휴전' },
        { key: 'ceasefire_proposal', label: '종전제의' },
        { key: 'alliance', label: '동맹' },
        { key: 'nonaggression', label: '불가침' },
    ];

    const otherNations = nations.filter((n) => n.id !== myGeneral?.nationId);
    const canSendLetter = myGeneral && myGeneral.officerLevel >= 5 && myGeneral.nationId > 0;

    return (
        <div className="space-y-0 max-w-4xl mx-auto">
            <PageHeader icon={Handshake} title="외교부" />

            <Tabs defaultValue="letters" className="legacy-page-wrap">
                <TabsList className="w-full justify-start border-b border-gray-600">
                    <TabsTrigger value="letters">
                        <Handshake className="size-3.5 mr-1" />
                        외교부
                    </TabsTrigger>
                    <TabsTrigger value="history">
                        <History className="size-3.5 mr-1" />
                        외교 기록
                    </TabsTrigger>
                </TabsList>

                {/* Tab 1: 외교부 — Letters + Nation Diplomacy Status */}
                <TabsContent value="letters" className="mt-4 space-y-4 px-2">
                    {/* My nation's diplomacy status summary */}
                    {myGeneral?.nationId && (
                        <Card>
                            <CardHeader className="pb-2">
                                <CardTitle className="text-sm">외교 현황</CardTitle>
                            </CardHeader>
                            <CardContent className="space-y-2">
                                {activeDiplomacy.length === 0 ? (
                                    <p className="text-xs text-muted-foreground">현재 외교 관계가 없습니다.</p>
                                ) : (
                                    sections.map(({ key, label }) => {
                                        const items = grouped[key];
                                        if (!items || items.length === 0) return null;
                                        return (
                                            <div key={key} className="space-y-1">
                                                <div className="flex items-center gap-2">
                                                    <Badge variant={STATE_BADGE_VARIANT[key] ?? 'outline'}>
                                                        {label}
                                                    </Badge>
                                                    <span className="text-xs text-muted-foreground">
                                                        {items.length}건
                                                    </span>
                                                </div>
                                                {items.map((d) => {
                                                    const src = nationMap.get(d.srcNationId);
                                                    const dest = nationMap.get(d.destNationId);
                                                    const srcLeader = src ? generalMap.get(src.chiefGeneralId) : null;
                                                    const destLeader = dest
                                                        ? generalMap.get(dest.chiefGeneralId)
                                                        : null;
                                                    return (
                                                        <div
                                                            key={d.id}
                                                            className="flex items-center gap-2 rounded border border-gray-700 px-2 py-1.5 text-sm"
                                                        >
                                                            <span className="inline-flex items-center gap-1.5">
                                                                <GeneralPortrait
                                                                    picture={srcLeader?.picture}
                                                                    name={srcLeader?.name ?? src?.name ?? '발신국'}
                                                                    size="sm"
                                                                />
                                                                <NationBadge name={src?.name} color={src?.color} />
                                                            </span>
                                                            <ArrowRight className="size-3 text-muted-foreground" />
                                                            <span className="inline-flex items-center gap-1.5">
                                                                <GeneralPortrait
                                                                    picture={destLeader?.picture}
                                                                    name={destLeader?.name ?? dest?.name ?? '수신국'}
                                                                    size="sm"
                                                                />
                                                                <NationBadge name={dest?.name} color={dest?.color} />
                                                            </span>
                                                            <span className="ml-auto text-xs text-muted-foreground">
                                                                {d.term}턴
                                                            </span>
                                                        </div>
                                                    );
                                                })}
                                            </div>
                                        );
                                    })
                                )}
                            </CardContent>
                        </Card>
                    )}

                    {/* Send button */}
                    {canSendLetter && (
                        <div className="flex justify-end">
                            <Button
                                onClick={() => setShowSend(!showSend)}
                                variant={showSend ? 'outline' : 'default'}
                                size="sm"
                            >
                                <Send className="size-4 mr-1" />
                                {showSend ? '취소' : '서신 보내기'}
                            </Button>
                        </div>
                    )}

                    {/* Send form */}
                    {showSend && (
                        <Card>
                            <CardHeader className="pb-2">
                                <CardTitle className="text-sm">외교 서신 작성</CardTitle>
                            </CardHeader>
                            <CardContent className="space-y-3">
                                <div>
                                    <label htmlFor="destNationId" className="block text-xs text-muted-foreground mb-1">
                                        대상 국가
                                    </label>
                                    <select
                                        id="destNationId"
                                        value={destNationId}
                                        onChange={(e) => setDestNationId(e.target.value)}
                                        className="h-9 w-full min-w-0 rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-xs outline-none focus-visible:border-ring focus-visible:ring-ring/50 focus-visible:ring-[3px] md:text-sm"
                                    >
                                        <option value="">선택...</option>
                                        {otherNations.map((n) => (
                                            <option key={n.id} value={n.id}>
                                                {n.name}
                                            </option>
                                        ))}
                                    </select>
                                </div>
                                <div>
                                    <label htmlFor="letterType" className="block text-xs text-muted-foreground mb-1">
                                        유형
                                    </label>
                                    <select
                                        id="letterType"
                                        value={letterType}
                                        onChange={(e) => setLetterType(e.target.value)}
                                        className="h-9 w-full min-w-0 rounded-md border border-input bg-transparent px-3 py-1 text-base shadow-xs outline-none focus-visible:border-ring focus-visible:ring-ring/50 focus-visible:ring-[3px] md:text-sm"
                                    >
                                        {LETTER_TYPES.map((lt) => (
                                            <option key={lt.value} value={lt.value}>
                                                {lt.label}
                                            </option>
                                        ))}
                                    </select>
                                </div>
                                <div>
                                    <div className="flex items-center justify-between mb-1">
                                        <label htmlFor="letterContent" className="text-xs text-muted-foreground">
                                            공개 내용 {showDualContent ? '(모든 국가에 공개)' : '(선택)'}
                                        </label>
                                        <button
                                            type="button"
                                            className="text-[10px] text-cyan-400 hover:text-cyan-300"
                                            onClick={() => setShowDualContent(!showDualContent)}
                                        >
                                            {showDualContent ? '단일 모드' : '이중 콘텐츠 모드'}
                                        </button>
                                    </div>
                                    <Textarea
                                        id="letterContent"
                                        value={letterContent}
                                        onChange={(e) => setLetterContent(e.target.value)}
                                        placeholder={showDualContent ? '공개적으로 보이는 내용...' : '서신 내용...'}
                                        className="resize-none h-20"
                                    />
                                </div>
                                {showDualContent && (
                                    <div>
                                        <label
                                            htmlFor="letterDiplomaticContent"
                                            className="block text-xs text-muted-foreground mb-1"
                                        >
                                            외교 전용 내용 (당사국만 열람)
                                        </label>
                                        <Textarea
                                            id="letterDiplomaticContent"
                                            value={letterDiplomaticContent}
                                            onChange={(e) => setLetterDiplomaticContent(e.target.value)}
                                            placeholder="외교 당사국만 볼 수 있는 비밀 내용..."
                                            className="resize-none h-20 border-amber-700/50"
                                        />
                                        <p className="text-[10px] text-amber-400 mt-1">
                                            ⚠ 이 내용은 발신국과 수신국만 열람할 수 있습니다.
                                        </p>
                                    </div>
                                )}
                                <Button onClick={handleSendLetter} disabled={sending || !destNationId}>
                                    {sending ? '전송 중...' : '전송'}
                                </Button>
                            </CardContent>
                        </Card>
                    )}

                    {/* Letter list */}
                    <Card>
                        <CardHeader className="pb-2">
                            <CardTitle className="text-sm">외교 서신</CardTitle>
                        </CardHeader>
                        <CardContent>
                            {lettersLoading ? (
                                <LoadingState />
                            ) : letters.length === 0 ? (
                                <EmptyState icon={Handshake} title="외교 서신이 없습니다." />
                            ) : (
                                <div className="space-y-2">
                                    {letters.map((letter) => {
                                        const id = letter.id;
                                        const srcNation = nationMap.get(letter.srcId as number);
                                        const destNation = nationMap.get(letter.destId as number);
                                        const srcGeneralIdRaw =
                                            letter.payload.srcGeneralId ?? letter.payload.src_general_id ?? null;
                                        const destGeneralIdRaw =
                                            letter.payload.destGeneralId ?? letter.payload.dest_general_id ?? null;
                                        const srcGeneralId =
                                            typeof srcGeneralIdRaw === 'number'
                                                ? srcGeneralIdRaw
                                                : typeof srcGeneralIdRaw === 'string'
                                                  ? Number(srcGeneralIdRaw)
                                                  : null;
                                        const destGeneralId =
                                            typeof destGeneralIdRaw === 'number'
                                                ? destGeneralIdRaw
                                                : typeof destGeneralIdRaw === 'string'
                                                  ? Number(destGeneralIdRaw)
                                                  : null;
                                        const srcGeneral =
                                            srcGeneralId != null && Number.isFinite(srcGeneralId)
                                                ? generalMap.get(srcGeneralId)
                                                : null;
                                        const destGeneral =
                                            destGeneralId != null && Number.isFinite(destGeneralId)
                                                ? generalMap.get(destGeneralId)
                                                : null;
                                        const srcSigner =
                                            srcGeneral ?? (srcNation ? generalMap.get(srcNation.chiefGeneralId) : null);
                                        const destSigner =
                                            destGeneral ??
                                            (destNation ? generalMap.get(destNation.chiefGeneralId) : null);
                                        const prevLetterRaw =
                                            letter.payload.prevLetterId ??
                                            letter.payload.previousLetterId ??
                                            letter.payload.previousDocumentId ??
                                            letter.payload.parentId ??
                                            letter.payload.referenceId ??
                                            letter.payload.referenceNo ??
                                            letter.payload.prev_no ??
                                            letter.payload.prevNo ??
                                            letter.meta.prevLetterId ??
                                            letter.meta.previousLetterId ??
                                            letter.meta.previousDocumentId ??
                                            letter.meta.parentId ??
                                            letter.meta.referenceId ??
                                            letter.meta.referenceNo ??
                                            letter.meta.prev_no ??
                                            letter.meta.prevNo ??
                                            null;
                                        const hasExplicitPrevRef =
                                            typeof prevLetterRaw === 'number' || typeof prevLetterRaw === 'string';
                                        const prevLetterRef = hasExplicitPrevRef ? String(prevLetterRaw) : `${id}`;
                                        const hasPrevLetterLink = hasExplicitPrevRef && letterIdSet.has(prevLetterRef);
                                        const type = letter.messageType;
                                        const content = letter.payload.content as string | undefined;
                                        const diplomaticContent = letter.payload.diplomaticContent as
                                            | string
                                            | undefined;
                                        const state = letter.payload.state as string | undefined;
                                        const isOutgoing = myGeneral?.nationId === (letter.srcId as number);
                                        const isInvolved =
                                            myGeneral?.nationId === (letter.srcId as number) ||
                                            myGeneral?.nationId === (letter.destId as number);

                                        // Chain steps: 제안→수락→이행
                                        const chainSteps = [
                                            { key: 'proposed', label: '제안', done: true },
                                            {
                                                key: 'accepted',
                                                label: '수락',
                                                done: state === 'accepted' || state === 'executed',
                                            },
                                            {
                                                key: 'executed',
                                                label: '이행',
                                                done: state === 'executed',
                                            },
                                        ];

                                        return (
                                            <div
                                                id={`letter-${id}`}
                                                key={id}
                                                className="rounded border border-gray-700 p-3 space-y-2"
                                            >
                                                {/* Header */}
                                                <div className="flex items-center gap-2 flex-wrap">
                                                    <Badge variant="outline" className="text-xs">
                                                        {isOutgoing ? '발신' : '수신'}
                                                    </Badge>
                                                    <NationBadge name={srcNation?.name} color={srcNation?.color} />
                                                    <ArrowRight className="size-3 text-muted-foreground" />
                                                    <NationBadge name={destNation?.name} color={destNation?.color} />
                                                    <Badge variant="secondary">{STATE_LABELS[type] ?? type}</Badge>
                                                    {state && (
                                                        <Badge
                                                            variant={
                                                                state === 'pending'
                                                                    ? 'outline'
                                                                    : state === 'accepted'
                                                                      ? 'default'
                                                                      : state === 'executed'
                                                                        ? 'default'
                                                                        : 'destructive'
                                                            }
                                                        >
                                                            {state === 'pending'
                                                                ? '대기'
                                                                : state === 'accepted'
                                                                  ? '수락'
                                                                  : state === 'executed'
                                                                    ? '이행완료'
                                                                    : state === 'rejected'
                                                                      ? '거절'
                                                                      : state}
                                                        </Badge>
                                                    )}
                                                </div>

                                                {/* Rejection reason */}
                                                {state === 'rejected' && (letter.payload.reason as string) && (
                                                    <div className="text-xs bg-red-950/30 border border-red-900/50 rounded px-2 py-1 text-red-300">
                                                        <span className="text-red-400 font-medium">거절 사유:</span>{' '}
                                                        {letter.payload.reason as string}
                                                    </div>
                                                )}

                                                <div className="text-xs text-muted-foreground">
                                                    선행문서 참조:{' '}
                                                    {hasPrevLetterLink ? (
                                                        <a
                                                            href={`#letter-${prevLetterRef}`}
                                                            className="text-cyan-400 hover:underline"
                                                        >
                                                            #{prevLetterRef}
                                                        </a>
                                                    ) : (
                                                        <span className="text-foreground">
                                                            #{prevLetterRef}
                                                            {!hasExplicitPrevRef ? ' (참조번호)' : ''}
                                                        </span>
                                                    )}
                                                </div>

                                                {/* Document chain progress: 제안→수락→이행 */}
                                                {state && state !== 'rejected' && (
                                                    <div className="flex items-center gap-1 text-[10px]">
                                                        {chainSteps.map((step, i) => (
                                                            <span key={step.key} className="flex items-center gap-1">
                                                                {i > 0 && <span className="text-gray-600">→</span>}
                                                                <span
                                                                    className={`px-1.5 py-0.5 rounded ${
                                                                        step.done
                                                                            ? 'bg-emerald-900/50 text-emerald-300 border border-emerald-700'
                                                                            : 'bg-gray-800 text-gray-500 border border-gray-700'
                                                                    }`}
                                                                >
                                                                    {step.done ? '✓ ' : ''}
                                                                    {step.label}
                                                                </span>
                                                            </span>
                                                        ))}
                                                    </div>
                                                )}

                                                <div className="rounded border border-gray-700 bg-black/20 p-2">
                                                    <div className="text-[11px] text-muted-foreground mb-1">서명인</div>
                                                    <div className="grid gap-2 sm:grid-cols-2">
                                                        <div className="flex items-center gap-2 rounded border border-gray-800 px-2 py-1.5">
                                                            <GeneralPortrait
                                                                picture={srcSigner?.picture}
                                                                name={srcSigner?.name ?? '발신 장수'}
                                                                size="lg"
                                                            />
                                                            <div className="min-w-0 text-xs">
                                                                <div className="truncate text-muted-foreground">
                                                                    {srcNation?.name ?? '-'}
                                                                </div>
                                                                <div className="truncate text-foreground">
                                                                    {srcSigner?.name ?? '-'}
                                                                </div>
                                                            </div>
                                                        </div>
                                                        <div className="flex items-center gap-2 rounded border border-gray-800 px-2 py-1.5">
                                                            <GeneralPortrait
                                                                picture={destSigner?.picture}
                                                                name={destSigner?.name ?? '수신 장수'}
                                                                size="lg"
                                                            />
                                                            <div className="min-w-0 text-xs">
                                                                <div className="truncate text-muted-foreground">
                                                                    {destNation?.name ?? '-'}
                                                                </div>
                                                                <div className="truncate text-foreground">
                                                                    {destSigner?.name ?? '-'}
                                                                </div>
                                                            </div>
                                                        </div>
                                                    </div>
                                                </div>

                                                {/* Public content */}
                                                {content && (
                                                    <div className="text-sm text-muted-foreground">
                                                        {diplomaticContent && (
                                                            <span className="text-[10px] text-gray-500 block mb-0.5">
                                                                공개 내용:
                                                            </span>
                                                        )}
                                                        <p>{content}</p>
                                                    </div>
                                                )}

                                                {/* Diplomatic-only content (only visible to involved parties) */}
                                                {diplomaticContent && isInvolved && (
                                                    <div className="text-sm border-l-2 border-amber-700/50 pl-2 bg-amber-950/20 rounded-r py-1">
                                                        <span className="text-[10px] text-amber-400 block mb-0.5">
                                                            외교 전용 (당사국만 열람):
                                                        </span>
                                                        <p className="text-amber-200/80">{diplomaticContent}</p>
                                                    </div>
                                                )}

                                                {/* Actions */}
                                                <div className="flex gap-2">
                                                    {/* Pending: receiver can accept/reject */}
                                                    {state === 'pending' &&
                                                        myGeneral?.nationId === (letter.destId as number) && (
                                                            <>
                                                                <Button
                                                                    size="sm"
                                                                    onClick={() => handleRespond(id, true)}
                                                                >
                                                                    수락
                                                                </Button>
                                                                {rejectingLetterId === id ? (
                                                                    <div className="flex items-center gap-1">
                                                                        <input
                                                                            type="text"
                                                                            className="h-7 px-2 text-xs rounded border border-gray-600 bg-gray-900 text-white"
                                                                            placeholder="거절 사유 (선택)"
                                                                            value={rejectReason}
                                                                            onChange={(e) =>
                                                                                setRejectReason(e.target.value)
                                                                            }
                                                                            onKeyDown={(e) => {
                                                                                if (e.key === 'Enter')
                                                                                    void handleRespond(
                                                                                        id,
                                                                                        false,
                                                                                        rejectReason || undefined
                                                                                    );
                                                                            }}
                                                                        />
                                                                        <Button
                                                                            size="sm"
                                                                            variant="destructive"
                                                                            onClick={() =>
                                                                                void handleRespond(
                                                                                    id,
                                                                                    false,
                                                                                    rejectReason || undefined
                                                                                )
                                                                            }
                                                                        >
                                                                            확인
                                                                        </Button>
                                                                        <Button
                                                                            size="sm"
                                                                            variant="ghost"
                                                                            onClick={() => {
                                                                                setRejectingLetterId(null);
                                                                                setRejectReason('');
                                                                            }}
                                                                        >
                                                                            취소
                                                                        </Button>
                                                                    </div>
                                                                ) : (
                                                                    <Button
                                                                        size="sm"
                                                                        variant="destructive"
                                                                        onClick={() => setRejectingLetterId(id)}
                                                                    >
                                                                        거절
                                                                    </Button>
                                                                )}
                                                            </>
                                                        )}
                                                    {/* Pending: sender can withdraw */}
                                                    {state === 'pending' &&
                                                        myGeneral?.nationId === (letter.srcId as number) && (
                                                            <Button
                                                                size="sm"
                                                                variant="outline"
                                                                onClick={() => handleRollback(id)}
                                                            >
                                                                철회
                                                            </Button>
                                                        )}
                                                    {/* Accepted: either party can execute (이행) */}
                                                    {state === 'accepted' && isInvolved && (
                                                        <Button
                                                            size="sm"
                                                            variant="default"
                                                            className="bg-emerald-700 hover:bg-emerald-600"
                                                            onClick={() => void handleExecute(id)}
                                                        >
                                                            이행
                                                        </Button>
                                                    )}
                                                    {/* 갱신 (Renewal): auto-fill compose form with previous letter content */}
                                                    {isInvolved && (state === 'executed' || state === 'accepted') && (
                                                        <Button
                                                            size="sm"
                                                            variant="outline"
                                                            onClick={() => {
                                                                setShowSend(true);
                                                                setDestNationId(
                                                                    String(isOutgoing ? letter.destId : letter.srcId)
                                                                );
                                                                setLetterType(type || 'nonaggression');
                                                                setLetterContent(content || '');
                                                                if (diplomaticContent) {
                                                                    setShowDualContent(true);
                                                                    setLetterDiplomaticContent(diplomaticContent);
                                                                }
                                                            }}
                                                        >
                                                            갱신
                                                        </Button>
                                                    )}
                                                    <Button size="sm" variant="ghost" onClick={() => handleDestroy(id)}>
                                                        삭제
                                                    </Button>
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            )}
                        </CardContent>
                    </Card>
                </TabsContent>

                {/* Tab 3: 외교 기록 — History */}
                <TabsContent value="history" className="mt-4 space-y-4 px-2">
                    <Card>
                        <CardHeader className="pb-2">
                            <CardTitle className="text-sm">외교 기록</CardTitle>
                        </CardHeader>
                        <CardContent>
                            {historyLoading ? (
                                <LoadingState />
                            ) : diplomacyHistory.length === 0 ? (
                                <EmptyState icon={History} title="외교 관련 기록이 없습니다." />
                            ) : (
                                <div className="space-y-1">
                                    {diplomacyHistory.map((record) => {
                                        const msg =
                                            (record.payload?.content as string) ??
                                            (record.payload?.message as string) ??
                                            '';
                                        return (
                                            <div
                                                key={record.id}
                                                className="flex items-start gap-3 rounded border border-gray-800 px-3 py-2"
                                            >
                                                <span className="shrink-0 text-xs text-muted-foreground mt-0.5 w-24">
                                                    {formatGameLogDate(record) ?? '-'}
                                                </span>
                                                <span className="text-sm">{formatLog(msg)}</span>
                                            </div>
                                        );
                                    })}
                                </div>
                            )}
                        </CardContent>
                    </Card>
                </TabsContent>
            </Tabs>
        </div>
    );
}
