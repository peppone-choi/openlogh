'use client';

import { useEffect, useState, useCallback, useMemo } from 'react';
import { Vote, Plus, History, BarChart3, MessageSquare, Trash2, Send } from 'lucide-react';
import Link from 'next/link';
import { useWorldStore } from '@/stores/worldStore';
import { useGeneralStore } from '@/stores/generalStore';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { EmptyState } from '@/components/game/empty-state';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { Button } from '@/components/ui/8bit/button';
import { Badge } from '@/components/ui/8bit/badge';
import { Input } from '@/components/ui/8bit/input';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/8bit/tabs';
import type { Message, VoteComment } from '@/types';
import { voteApi } from '@/lib/gameApi';
import { useGameStore } from '@/stores/gameStore';
import { Textarea } from '@/components/ui/8bit/textarea';

interface DraftVoteOption {
    id: number;
    value: string;
}

/* ── payload shape ── */
interface VotePayload {
    title?: string;
    options?: string[];
    ballots?: Record<string, number | number[]>;
    state?: string; // "open" | "closed"
    creatorId?: number;
    creatorName?: string;
    deadline?: string;
    reward?: string;
    maxSelections?: number;
}

function vp(msg: Message): VotePayload {
    return (msg.payload ?? {}) as VotePayload;
}

function isOpen(v: VotePayload): boolean {
    if (v.state === 'closed') return false;
    if (v.deadline && new Date(v.deadline).getTime() <= Date.now()) return false;
    return true;
}

function formatDeadline(iso?: string): string {
    if (!iso) return '-';
    try {
        const d = new Date(iso);
        return `${d.getMonth() + 1}/${d.getDate()} ${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`;
    } catch {
        return iso;
    }
}

export default function VotePage() {
    const { currentWorld } = useWorldStore();
    const { myGeneral } = useGeneralStore();
    const { generals, loadAll } = useGameStore();
    const [votes, setVotes] = useState<Message[]>([]);
    const [loading, setLoading] = useState(true);

    // create form
    const [showCreate, setShowCreate] = useState(false);
    const [createTitle, setCreateTitle] = useState('');
    const [createOptions, setCreateOptions] = useState<DraftVoteOption[]>([
        { id: 1, value: '' },
        { id: 2, value: '' },
    ]);
    const [creating, setCreating] = useState(false);
    const [createDeadline, setCreateDeadline] = useState('');
    const [createReward, setCreateReward] = useState('');
    const [createMaxSelections, setCreateMaxSelections] = useState(1);
    const [nextCreateOptionId, setNextCreateOptionId] = useState(3);

    const load = useCallback(async () => {
        if (!currentWorld) return;
        try {
            const { data } = await voteApi.list(currentWorld.id);
            setVotes(data);
        } catch {
            /* ignore */
        } finally {
            setLoading(false);
        }
    }, [currentWorld]);

    useEffect(() => {
        load();
        if (currentWorld) loadAll(currentWorld.id);
    }, [currentWorld, load, loadAll]);

    const handleVote = async (voteId: number, optionIndex: number) => {
        if (!myGeneral) return;
        try {
            await voteApi.cast(voteId, myGeneral.id, optionIndex);
            await load();
        } catch {
            /* ignore */
        }
    };

    const handleClose = async (voteId: number) => {
        try {
            await voteApi.close(voteId);
            await load();
        } catch {
            /* ignore */
        }
    };

    const handleCreate = async () => {
        if (!currentWorld || !myGeneral || !createTitle.trim()) return;
        const opts = createOptions.map((o) => o.value.trim()).filter(Boolean);
        if (opts.length < 2) return;
        setCreating(true);
        try {
            await voteApi.create(currentWorld.id, {
                title: createTitle.trim(),
                options: opts,
                creatorId: myGeneral.id,
                ...(createDeadline ? { deadline: new Date(createDeadline).toISOString() } : {}),
                ...(createReward.trim() ? { reward: createReward.trim() } : {}),
                ...(createMaxSelections > 1 ? { maxSelections: createMaxSelections } : {}),
            });
            setShowCreate(false);
            setCreateTitle('');
            setCreateOptions([
                { id: nextCreateOptionId, value: '' },
                { id: nextCreateOptionId + 1, value: '' },
            ]);
            setNextCreateOptionId((prev) => prev + 2);
            await load();
        } catch {
            /* ignore */
        } finally {
            setCreating(false);
        }
    };

    const addOption = () => {
        setCreateOptions((prev) => [...prev, { id: nextCreateOptionId, value: '' }]);
        setNextCreateOptionId((prev) => prev + 1);
    };
    const updateOption = (id: number, val: string) =>
        setCreateOptions((prev) => prev.map((opt) => (opt.id === id ? { ...opt, value: val } : opt)));
    const removeOption = (id: number) =>
        setCreateOptions((prev) => (prev.length <= 2 ? prev : prev.filter((opt) => opt.id !== id)));

    if (!currentWorld) return <div className="p-4 text-muted-foreground">월드를 선택해주세요.</div>;
    if (loading) return <LoadingState />;

    const activeVotes = votes.filter((v) => isOpen(vp(v)));
    const closedVotes = votes.filter((v) => !isOpen(vp(v)));
    const closedVotesSorted = [...closedVotes].sort((a, b) => b.id - a.id);

    // Chief or higher can create
    const canCreate = myGeneral && myGeneral.officerLevel >= 5;

    return (
        <div className="space-y-0 max-w-4xl mx-auto">
            <PageHeader icon={Vote} title="설문 조사" />

            <Tabs defaultValue="active" className="legacy-page-wrap">
                <TabsList className="w-full justify-start border-b border-gray-600">
                    <TabsTrigger value="active">
                        <BarChart3 className="size-3.5 mr-1" />
                        진행중 투표
                    </TabsTrigger>
                    <TabsTrigger value="history">
                        <History className="size-3.5 mr-1" />
                        지난 투표
                    </TabsTrigger>
                </TabsList>

                {/* ═══ Active ═══ */}
                <TabsContent value="active" className="mt-4 space-y-4 px-2">
                    {/* create button */}
                    {canCreate && (
                        <div className="flex justify-end">
                            <Button
                                size="sm"
                                variant={showCreate ? 'outline' : 'default'}
                                onClick={() => setShowCreate(!showCreate)}
                            >
                                <Plus className="size-3.5 mr-1" />
                                {showCreate ? '취소' : '투표 만들기'}
                            </Button>
                        </div>
                    )}

                    {/* create form */}
                    {showCreate && (
                        <Card>
                            <CardHeader className="pb-2">
                                <CardTitle className="text-sm">새 투표 만들기</CardTitle>
                            </CardHeader>
                            <CardContent className="space-y-3">
                                <div>
                                    <label
                                        htmlFor="create-vote-title"
                                        className="block text-xs text-muted-foreground mb-1"
                                    >
                                        제목
                                    </label>
                                    <Input
                                        id="create-vote-title"
                                        value={createTitle}
                                        onChange={(e) => setCreateTitle(e.target.value)}
                                        placeholder="투표 제목"
                                    />
                                </div>
                                <div>
                                    <p className="block text-xs text-muted-foreground mb-1">선택지</p>
                                    <div className="space-y-2">
                                        {createOptions.map((opt, i) => (
                                            <div key={opt.id} className="flex items-center gap-2">
                                                <Input
                                                    id={`create-vote-option-${opt.id}`}
                                                    value={opt.value}
                                                    onChange={(e) => updateOption(opt.id, e.target.value)}
                                                    placeholder={`선택지 ${i + 1}`}
                                                    className="text-sm"
                                                />
                                                {createOptions.length > 2 && (
                                                    <Button
                                                        size="sm"
                                                        variant="ghost"
                                                        onClick={() => removeOption(opt.id)}
                                                        className="h-8 px-2 text-xs text-destructive"
                                                    >
                                                        삭제
                                                    </Button>
                                                )}
                                            </div>
                                        ))}
                                        <Button size="sm" variant="outline" onClick={addOption} className="text-xs">
                                            <Plus className="size-3 mr-1" />
                                            선택지 추가
                                        </Button>
                                    </div>
                                </div>
                                <div>
                                    <label
                                        htmlFor="create-vote-deadline"
                                        className="block text-xs text-muted-foreground mb-1"
                                    >
                                        마감 시간 (선택)
                                    </label>
                                    <Input
                                        id="create-vote-deadline"
                                        type="datetime-local"
                                        value={createDeadline}
                                        onChange={(e) => setCreateDeadline(e.target.value)}
                                        className="text-sm w-56"
                                    />
                                </div>
                                <div>
                                    <label
                                        htmlFor="create-vote-reward"
                                        className="block text-xs text-muted-foreground mb-1"
                                    >
                                        보상 (선택)
                                    </label>
                                    <Input
                                        id="create-vote-reward"
                                        value={createReward}
                                        onChange={(e) => setCreateReward(e.target.value)}
                                        placeholder="추첨 보상 (예: 금 1000)"
                                        className="text-sm"
                                    />
                                </div>
                                <div>
                                    <label
                                        htmlFor="create-vote-max-selections"
                                        className="block text-xs text-muted-foreground mb-1"
                                    >
                                        다중 선택 제약
                                    </label>
                                    <div className="flex items-center gap-2">
                                        <select
                                            id="create-vote-max-selections"
                                            value={createMaxSelections}
                                            onChange={(e) => setCreateMaxSelections(Number(e.target.value))}
                                            className="h-8 border border-gray-600 bg-[#111] px-2 text-xs text-white rounded"
                                        >
                                            <option value={1}>단일 선택</option>
                                            <option value={2}>최대 2개</option>
                                            <option value={3}>최대 3개</option>
                                        </select>
                                        <span className="text-[10px] text-muted-foreground">
                                            {createMaxSelections > 1
                                                ? `투표자는 최대 ${createMaxSelections}개 선택 가능`
                                                : '하나만 선택 가능'}
                                        </span>
                                    </div>
                                </div>
                                <Button
                                    onClick={handleCreate}
                                    disabled={
                                        creating ||
                                        !createTitle.trim() ||
                                        createOptions.filter((o) => o.value.trim()).length < 2
                                    }
                                >
                                    {creating ? '생성 중...' : '생성'}
                                </Button>
                            </CardContent>
                        </Card>
                    )}

                    {activeVotes.length === 0 ? (
                        <EmptyState icon={Vote} title="진행중인 투표가 없습니다." />
                    ) : (
                        <div className="space-y-3">
                            {activeVotes.map((vote) => (
                                <VoteCard
                                    key={vote.id}
                                    vote={vote}
                                    myGeneralId={myGeneral?.id}
                                    isChief={canCreate ?? false}
                                    onVote={handleVote}
                                    onClose={handleClose}
                                    generals={generals}
                                />
                            ))}
                        </div>
                    )}
                </TabsContent>

                {/* ═══ History ═══ */}
                <TabsContent value="history" className="mt-4 space-y-4 px-2">
                    {closedVotes.length === 0 ? (
                        <EmptyState icon={History} title="지난 투표가 없습니다." />
                    ) : (
                        <div className="space-y-3">
                            <Card>
                                <CardHeader className="pb-2">
                                    <CardTitle className="text-sm">지난 투표 목록</CardTitle>
                                </CardHeader>
                                <CardContent className="pt-0">
                                    <div className="space-y-1 text-sm">
                                        {closedVotesSorted.map((vote) => {
                                            const payload = vp(vote);
                                            return (
                                                <div key={`history-link-${vote.id}`}>
                                                    <Link
                                                        href={`/vote/${vote.id}`}
                                                        className="hover:underline text-cyan-300"
                                                    >
                                                        #{vote.id} {payload.title ?? '(제목 없음)'}
                                                    </Link>
                                                </div>
                                            );
                                        })}
                                    </div>
                                </CardContent>
                            </Card>

                            {closedVotesSorted.map((vote) => (
                                <VoteCard
                                    key={vote.id}
                                    vote={vote}
                                    myGeneralId={myGeneral?.id}
                                    isChief={false}
                                    onVote={handleVote}
                                    onClose={handleClose}
                                    generals={generals}
                                />
                            ))}
                        </div>
                    )}
                </TabsContent>
            </Tabs>
        </div>
    );
}

/* ── Vote card ── */
function VoteCard({
    vote,
    myGeneralId,
    isChief,
    onVote,
    onClose,
    generals,
}: {
    vote: Message;
    myGeneralId?: number;
    isChief: boolean;
    onVote: (voteId: number, idx: number) => void;
    onClose: (voteId: number) => void;
    generals: { id: number; name: string }[];
}) {
    const d = vp(vote);
    const title = d.title ?? '(제목 없음)';
    const options = d.options ?? [];
    const ballots = d.ballots ?? {};
    const maxSelections = Math.max(1, d.maxSelections ?? 1);
    const open = isOpen(d);
    const optionRows = options.map((option, optionIdx) => ({
        key: `${vote.id}-${option}-${optionIdx + 1}`,
        option,
        optionIdx,
    }));

    const myVoteSelection = myGeneralId != null ? ballots[myGeneralId.toString()] : undefined;
    const hasVoted = myVoteSelection !== undefined;

    // count per option
    const counts = new Array(options.length).fill(0) as number[];
    Object.values(ballots).forEach((selection) => {
        if (Array.isArray(selection)) {
            selection.forEach((idx) => {
                if (typeof idx === 'number' && idx >= 0 && idx < counts.length) counts[idx]++;
            });
            return;
        }
        if (typeof selection === 'number' && selection >= 0 && selection < counts.length) counts[selection]++;
    });
    const total = counts.reduce((a, b) => a + b, 0);
    const maxCount = Math.max(...counts, 1);
    const totalGenerals = generals.length;
    const voteRate = totalGenerals > 0 ? (total / totalGenerals) * 100 : 0;

    // ── Comments ──
    const [comments, setComments] = useState<VoteComment[]>([]);
    const [commentText, setCommentText] = useState('');
    const [sendingComment, setSendingComment] = useState(false);
    const [showComments, setShowComments] = useState(false);

    const generalMap = useMemo(() => new Map(generals.map((g) => [g.id, g])), [generals]);

    const loadComments = useCallback(async () => {
        try {
            const { data } = await voteApi.listComments(vote.id);
            setComments(data);
        } catch {
            /* ignore */
        }
    }, [vote.id]);

    useEffect(() => {
        if (showComments) loadComments();
    }, [showComments, loadComments]);

    const handleAddComment = async () => {
        if (!commentText.trim() || myGeneralId == null) return;
        setSendingComment(true);
        try {
            await voteApi.createComment(vote.id, myGeneralId, commentText.trim());
            setCommentText('');
            await loadComments();
        } catch {
            /* ignore */
        } finally {
            setSendingComment(false);
        }
    };

    const handleDeleteComment = async (commentId: number) => {
        if (myGeneralId == null) return;
        try {
            await voteApi.deleteComment(vote.id, commentId, myGeneralId);
            await loadComments();
        } catch {
            /* ignore */
        }
    };

    return (
        <Card>
            <CardContent className="space-y-3 pt-4">
                <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2 flex-wrap">
                        <Link href={`/vote/${vote.id}`} className="font-semibold text-sm hover:underline">
                            {title}
                        </Link>
                        {maxSelections > 1 && (
                            <span className="text-xs text-cyan-300">(최대 {maxSelections}개 선택 가능)</span>
                        )}
                    </div>
                    <div className="flex items-center gap-2">
                        {d.deadline && (
                            <span className="text-[10px] text-muted-foreground">
                                마감: {formatDeadline(d.deadline)}
                            </span>
                        )}
                        <Badge variant={open ? 'default' : 'outline'}>{open ? '진행중' : '종료'}</Badge>
                    </div>
                </div>

                {d.reward && (
                    <div className="flex items-center gap-1.5 bg-amber-400/10 border border-amber-400/20 rounded px-2.5 py-1.5">
                        <span className="text-xs font-bold text-amber-400">설문 조사 보상</span>
                        <span className="text-xs font-semibold text-amber-300">{d.reward}</span>
                    </div>
                )}

                <p className="text-xs text-muted-foreground">총 {total}명 참여</p>
                <p className="text-xs text-muted-foreground">
                    투표율: {voteRate.toFixed(1)}% ({total}/{totalGenerals})
                </p>

                {/* results with progress bars */}
                <div className="space-y-2">
                    {optionRows.map((row) => {
                        const pct = total > 0 ? (counts[row.optionIdx] / total) * 100 : 0;
                        const isMyVote = Array.isArray(myVoteSelection)
                            ? myVoteSelection.includes(row.optionIdx)
                            : myVoteSelection === row.optionIdx;
                        const isWinner = !open && counts[row.optionIdx] === maxCount && maxCount > 0;
                        return (
                            <div key={row.key} className="space-y-1">
                                <div className="flex justify-between text-sm">
                                    <span
                                        className={
                                            isMyVote ? 'text-amber-400' : isWinner ? 'text-green-400 font-bold' : ''
                                        }
                                    >
                                        {row.option}
                                        {isMyVote && (
                                            <Badge variant="outline" className="ml-2 text-[10px]">
                                                내 투표
                                            </Badge>
                                        )}
                                        {isWinner && !open && (
                                            <Badge variant="secondary" className="ml-2 text-[10px]">
                                                최다
                                            </Badge>
                                        )}
                                    </span>
                                    <span className="text-xs text-muted-foreground">
                                        {counts[row.optionIdx]}명 ({pct.toFixed(1)}%)
                                    </span>
                                </div>
                                <div className="h-2 w-full rounded-full bg-gray-800 overflow-hidden">
                                    <div
                                        className={`h-full rounded-full transition-all ${
                                            isMyVote
                                                ? 'bg-amber-400'
                                                : isWinner && !open
                                                  ? 'bg-green-500'
                                                  : 'bg-primary'
                                        }`}
                                        style={{ width: `${pct}%` }}
                                    />
                                </div>
                            </div>
                        );
                    })}
                </div>

                {/* vote buttons */}
                {open && !hasVoted && myGeneralId != null && (
                    <div className="flex flex-wrap gap-2 pt-1">
                        {optionRows.map((row) => (
                            <Button
                                key={`vote-btn-${row.key}`}
                                variant="outline"
                                size="sm"
                                onClick={() => onVote(vote.id, row.optionIdx)}
                                className="text-xs"
                            >
                                {row.option}
                            </Button>
                        ))}
                    </div>
                )}

                {/* close button for chief */}
                {open && isChief && (
                    <div className="flex justify-end pt-1">
                        <Button size="sm" variant="destructive" onClick={() => onClose(vote.id)} className="text-xs">
                            투표 종료
                        </Button>
                    </div>
                )}

                {/* ── Comments Section ── */}
                <div className="border-t border-gray-700 mt-3 pt-2">
                    <button
                        type="button"
                        className="flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition-colors"
                        onClick={() => setShowComments(!showComments)}
                    >
                        <MessageSquare className="size-3.5" />
                        댓글 {comments.length > 0 ? `(${comments.length})` : ''}
                    </button>

                    {showComments && (
                        <div className="mt-2 space-y-2">
                            {comments.length === 0 ? (
                                <p className="text-xs text-muted-foreground">댓글이 없습니다.</p>
                            ) : (
                                <div className="space-y-1.5">
                                    {comments.map((c) => {
                                        const author = generalMap.get(c.authorGeneralId);
                                        const authorName = author?.name ?? `장수#${c.authorGeneralId}`;
                                        const dateStr = formatDeadline(c.createdAt);
                                        return (
                                            <div key={c.id} className="flex items-start gap-2 text-xs">
                                                <div className="flex-1 min-w-0">
                                                    <span className="font-medium text-cyan-400">{authorName}</span>
                                                    <span className="text-muted-foreground ml-2">{dateStr}</span>
                                                    <p className="mt-0.5 text-foreground whitespace-pre-wrap break-words">
                                                        {c.content}
                                                    </p>
                                                </div>
                                                {myGeneralId != null && c.authorGeneralId === myGeneralId && (
                                                    <button
                                                        type="button"
                                                        className="text-muted-foreground hover:text-destructive shrink-0"
                                                        onClick={() => handleDeleteComment(c.id)}
                                                    >
                                                        <Trash2 className="size-3" />
                                                    </button>
                                                )}
                                            </div>
                                        );
                                    })}
                                </div>
                            )}

                            {/* Comment input */}
                            {myGeneralId != null && (
                                <div className="flex gap-2 mt-2">
                                    <Textarea
                                        value={commentText}
                                        onChange={(e) => setCommentText(e.target.value)}
                                        placeholder="댓글 입력..."
                                        className="text-xs min-h-[2rem] h-8 resize-none"
                                        onKeyDown={(e) => {
                                            if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
                                                e.preventDefault();
                                                handleAddComment();
                                            }
                                        }}
                                    />
                                    <Button
                                        size="sm"
                                        variant="outline"
                                        disabled={sendingComment || !commentText.trim()}
                                        onClick={handleAddComment}
                                        className="shrink-0 h-8"
                                    >
                                        <Send className="size-3" />
                                    </Button>
                                </div>
                            )}
                        </div>
                    )}
                </div>
            </CardContent>
        </Card>
    );
}
