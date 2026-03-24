'use client';

import { useState, useMemo } from 'react';
import { FileText, Send, Inbox, Check, X, ArrowUpCircle, ArrowDownCircle } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { PageHeader } from '@/components/game/page-header';
import { EmptyState } from '@/components/game/empty-state';
import { useOfficerStore } from '@/stores/officerStore';
import { useGameStore } from '@/stores/gameStore';

// ─── Types ───────────────────────────────────────────────────────────────────

interface Proposal {
    id: number;
    fromId: number;
    fromName: string;
    toId: number;
    toName: string;
    type: 'proposal' | 'order';
    category: string;
    content: string;
    status: 'pending' | 'accepted' | 'rejected';
    createdAt: string;
    acceptanceProbability?: number;
}

const PROPOSAL_CATEGORIES = [
    { value: 'military', label: '군사 작전' },
    { value: 'diplomacy', label: '외교' },
    { value: 'personnel', label: '인사' },
    { value: 'internal', label: '내정' },
    { value: 'research', label: '기술 개발' },
    { value: 'other', label: '기타' },
];

// ─── Main Page ───────────────────────────────────────────────────────────────

export default function ProposalsPage() {
    const { myOfficer } = useOfficerStore();
    const officers = useGameStore((s) => s.officers);

    const [activeTab, setActiveTab] = useState<string>('submit');
    const [category, setCategory] = useState('military');
    const [targetId, setTargetId] = useState('');
    const [content, setContent] = useState('');
    const [sending, setSending] = useState(false);

    // Get superior and subordinate officers
    const factionOfficers = useMemo(() => {
        if (!myOfficer) return [];
        const fid = myOfficer.factionId ?? myOfficer.nationId;
        return officers.filter((o) => (o.factionId ?? o.nationId) === fid && o.id !== myOfficer.id);
    }, [officers, myOfficer]);

    const superiors = useMemo(
        () => factionOfficers.filter((o) => o.officerLevel > (myOfficer?.officerLevel ?? 0)),
        [factionOfficers, myOfficer]
    );

    const subordinates = useMemo(
        () => factionOfficers.filter((o) => o.officerLevel < (myOfficer?.officerLevel ?? 0)),
        [factionOfficers, myOfficer]
    );

    // Simulated proposals from meta
    const proposals: Proposal[] = useMemo(() => {
        if (!myOfficer) return [];
        const meta = myOfficer.meta as Record<string, unknown> | undefined;
        return (meta?.proposals as Proposal[] | undefined) ?? [];
    }, [myOfficer]);

    const incomingProposals = proposals.filter((p) => p.toId === myOfficer?.id && p.type === 'proposal');
    const incomingOrders = proposals.filter((p) => p.toId === myOfficer?.id && p.type === 'order');
    const sentProposals = proposals.filter((p) => p.fromId === myOfficer?.id);

    const handleSubmitProposal = async () => {
        if (!content.trim() || !targetId) return;
        setSending(true);
        // API call would go here
        setTimeout(() => {
            setSending(false);
            setContent('');
            setTargetId('');
        }, 500);
    };

    if (!myOfficer) return null;

    return (
        <div className="p-4 space-y-4 max-w-3xl mx-auto">
            <PageHeader icon={FileText} title="제안/명령" description="상급자 제안 및 하급자 명령 시스템" />

            <Tabs value={activeTab} onValueChange={setActiveTab}>
                <TabsList className="grid w-full grid-cols-4">
                    <TabsTrigger value="submit">제안 작성</TabsTrigger>
                    <TabsTrigger value="incoming">
                        수신함
                        {incomingProposals.length + incomingOrders.length > 0 && (
                            <Badge variant="destructive" className="ml-1 text-[9px] px-1">
                                {incomingProposals.length + incomingOrders.length}
                            </Badge>
                        )}
                    </TabsTrigger>
                    <TabsTrigger value="orders">명령 하달</TabsTrigger>
                    <TabsTrigger value="sent">발신함</TabsTrigger>
                </TabsList>

                {/* Submit Proposal (subordinate to superior) */}
                <TabsContent value="submit">
                    <Card>
                        <CardHeader className="pb-2">
                            <CardTitle className="text-sm flex items-center gap-2">
                                <ArrowUpCircle className="size-4 text-blue-400" />
                                제안서 작성 (하급 -{'>'} 상급)
                            </CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-3">
                            <div>
                                <label className="text-xs text-muted-foreground mb-1 block">제안 대상 (상급자)</label>
                                <Select value={targetId} onValueChange={setTargetId}>
                                    <SelectTrigger>
                                        <SelectValue placeholder="상급자를 선택하세요" />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {superiors.map((o) => (
                                            <SelectItem key={o.id} value={String(o.id)}>
                                                {o.name} (Lv.{o.officerLevel})
                                            </SelectItem>
                                        ))}
                                        {superiors.length === 0 && (
                                            <SelectItem value="" disabled>
                                                상급자 없음
                                            </SelectItem>
                                        )}
                                    </SelectContent>
                                </Select>
                            </div>
                            <div>
                                <label className="text-xs text-muted-foreground mb-1 block">분류</label>
                                <Select value={category} onValueChange={setCategory}>
                                    <SelectTrigger>
                                        <SelectValue />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {PROPOSAL_CATEGORIES.map((c) => (
                                            <SelectItem key={c.value} value={c.value}>
                                                {c.label}
                                            </SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            </div>
                            <div>
                                <label className="text-xs text-muted-foreground mb-1 block">내용</label>
                                <Textarea
                                    value={content}
                                    onChange={(e) => setContent(e.target.value)}
                                    placeholder="제안 내용을 작성하세요..."
                                    rows={4}
                                />
                            </div>

                            {/* Acceptance factors */}
                            <div className="bg-muted/30 rounded p-3 space-y-1">
                                <div className="text-xs font-medium text-muted-foreground">채택 확률 요소</div>
                                <div className="grid grid-cols-2 gap-2 text-xs">
                                    <div>
                                        <span className="text-muted-foreground">계급 차이:</span>{' '}
                                        <span className="text-amber-400">보통</span>
                                    </div>
                                    <div>
                                        <span className="text-muted-foreground">공적:</span>{' '}
                                        <span className="text-green-400">
                                            {myOfficer.experience > 1000 ? '높음' : '보통'}
                                        </span>
                                    </div>
                                    <div>
                                        <span className="text-muted-foreground">우호도:</span>{' '}
                                        <span className="text-blue-400">보통</span>
                                    </div>
                                    <div>
                                        <span className="text-muted-foreground">상성:</span>{' '}
                                        <span className="text-purple-400">보통</span>
                                    </div>
                                </div>
                            </div>

                            <Button onClick={handleSubmitProposal} disabled={sending || !content.trim() || !targetId}>
                                <Send className="size-4 mr-1" />
                                {sending ? '전송 중...' : '제안서 제출'}
                            </Button>
                        </CardContent>
                    </Card>
                </TabsContent>

                {/* Incoming Proposals & Orders */}
                <TabsContent value="incoming">
                    <div className="space-y-4">
                        {/* Proposals */}
                        <Card>
                            <CardHeader className="pb-2">
                                <CardTitle className="text-sm flex items-center gap-2">
                                    <Inbox className="size-4" />
                                    수신 제안 ({incomingProposals.length})
                                </CardTitle>
                            </CardHeader>
                            <CardContent>
                                {incomingProposals.length === 0 ? (
                                    <EmptyState icon={Inbox} title="수신된 제안이 없습니다." />
                                ) : (
                                    <div className="space-y-2">
                                        {incomingProposals.map((p) => (
                                            <div key={p.id} className="border rounded p-3 space-y-2">
                                                <div className="flex items-center justify-between">
                                                    <div className="flex items-center gap-2">
                                                        <Badge variant="secondary">{p.category}</Badge>
                                                        <span className="text-sm font-medium">{p.fromName}</span>
                                                    </div>
                                                    <span className="text-xs text-muted-foreground">{p.createdAt}</span>
                                                </div>
                                                <div className="text-sm">{p.content}</div>
                                                <div className="flex gap-2">
                                                    <Button size="sm" variant="default">
                                                        <Check className="size-3 mr-1" />
                                                        채택
                                                    </Button>
                                                    <Button size="sm" variant="outline">
                                                        <X className="size-3 mr-1" />
                                                        반려
                                                    </Button>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </CardContent>
                        </Card>

                        {/* Orders */}
                        <Card>
                            <CardHeader className="pb-2">
                                <CardTitle className="text-sm flex items-center gap-2">
                                    <ArrowDownCircle className="size-4 text-red-400" />
                                    수신 명령 ({incomingOrders.length})
                                </CardTitle>
                            </CardHeader>
                            <CardContent>
                                {incomingOrders.length === 0 ? (
                                    <EmptyState icon={Inbox} title="수신된 명령이 없습니다." />
                                ) : (
                                    <div className="space-y-2">
                                        {incomingOrders.map((p) => (
                                            <div key={p.id} className="border border-red-900/30 rounded p-3 space-y-2">
                                                <div className="flex items-center justify-between">
                                                    <div className="flex items-center gap-2">
                                                        <Badge variant="destructive" className="text-[10px]">
                                                            명령
                                                        </Badge>
                                                        <span className="text-sm font-medium">{p.fromName}</span>
                                                    </div>
                                                    <span className="text-xs text-muted-foreground">{p.createdAt}</span>
                                                </div>
                                                <div className="text-sm">{p.content}</div>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </CardContent>
                        </Card>
                    </div>
                </TabsContent>

                {/* Issue Orders (superior to subordinate) */}
                <TabsContent value="orders">
                    <Card>
                        <CardHeader className="pb-2">
                            <CardTitle className="text-sm flex items-center gap-2">
                                <ArrowDownCircle className="size-4 text-red-400" />
                                명령 하달 (상급 -{'>'} 하급)
                            </CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-3">
                            <div>
                                <label className="text-xs text-muted-foreground mb-1 block">명령 대상 (하급자)</label>
                                <Select value={targetId} onValueChange={setTargetId}>
                                    <SelectTrigger>
                                        <SelectValue placeholder="하급자를 선택하세요" />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {subordinates.map((o) => (
                                            <SelectItem key={o.id} value={String(o.id)}>
                                                {o.name} (Lv.{o.officerLevel})
                                            </SelectItem>
                                        ))}
                                        {subordinates.length === 0 && (
                                            <SelectItem value="" disabled>
                                                하급자 없음
                                            </SelectItem>
                                        )}
                                    </SelectContent>
                                </Select>
                            </div>
                            <div>
                                <label className="text-xs text-muted-foreground mb-1 block">명령 내용</label>
                                <Textarea
                                    value={content}
                                    onChange={(e) => setContent(e.target.value)}
                                    placeholder="명령 내용을 작성하세요..."
                                    rows={4}
                                />
                            </div>
                            <Button onClick={handleSubmitProposal} disabled={sending || !content.trim() || !targetId}>
                                <Send className="size-4 mr-1" />
                                {sending ? '전송 중...' : '명령 하달'}
                            </Button>
                        </CardContent>
                    </Card>
                </TabsContent>

                {/* Sent */}
                <TabsContent value="sent">
                    <Card>
                        <CardHeader className="pb-2">
                            <CardTitle className="text-sm flex items-center gap-2">
                                <Send className="size-4" />
                                발신 내역 ({sentProposals.length})
                            </CardTitle>
                        </CardHeader>
                        <CardContent>
                            {sentProposals.length === 0 ? (
                                <EmptyState icon={Send} title="발신 내역이 없습니다." />
                            ) : (
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead>유형</TableHead>
                                            <TableHead>대상</TableHead>
                                            <TableHead>분류</TableHead>
                                            <TableHead>상태</TableHead>
                                            <TableHead>일시</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {sentProposals.map((p) => (
                                            <TableRow key={p.id}>
                                                <TableCell>
                                                    <Badge
                                                        variant={p.type === 'order' ? 'destructive' : 'secondary'}
                                                        className="text-[10px]"
                                                    >
                                                        {p.type === 'order' ? '명령' : '제안'}
                                                    </Badge>
                                                </TableCell>
                                                <TableCell className="text-sm">{p.toName}</TableCell>
                                                <TableCell className="text-xs">{p.category}</TableCell>
                                                <TableCell>
                                                    <Badge
                                                        variant="outline"
                                                        className={
                                                            p.status === 'accepted'
                                                                ? 'text-green-400 border-green-400/30'
                                                                : p.status === 'rejected'
                                                                  ? 'text-red-400 border-red-400/30'
                                                                  : 'text-amber-400 border-amber-400/30'
                                                        }
                                                    >
                                                        {p.status === 'accepted'
                                                            ? '채택'
                                                            : p.status === 'rejected'
                                                              ? '반려'
                                                              : '대기'}
                                                    </Badge>
                                                </TableCell>
                                                <TableCell className="text-xs text-muted-foreground">
                                                    {p.createdAt}
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            )}
                        </CardContent>
                    </Card>
                </TabsContent>
            </Tabs>
        </div>
    );
}
