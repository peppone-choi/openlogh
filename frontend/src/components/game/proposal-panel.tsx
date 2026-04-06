'use client';

import { useCallback, useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { Badge } from '@/components/ui/8bit/badge';
import { Button } from '@/components/ui/8bit/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/8bit/tabs';
import { useCommandStore } from '@/stores/commandStore';
import { toast } from 'sonner';
import type { Proposal } from '@/types';

interface ProposalPanelProps {
    generalId: number;
}

const STATUS_LABELS: Record<Proposal['status'], string> = {
    pending: '대기',
    approved: '승인',
    rejected: '거부',
    expired: '만료',
};

const STATUS_COLORS: Record<Proposal['status'], string> = {
    pending: 'border-yellow-500/50 text-yellow-400',
    approved: 'border-green-500/50 text-green-400',
    rejected: 'border-red-500/50 text-red-400',
    expired: 'border-gray-500/50 text-gray-400',
};

function formatTime(isoString: string): string {
    try {
        const date = new Date(isoString);
        return date.toLocaleString('ko-KR', {
            month: 'numeric',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            hour12: false,
        });
    } catch {
        return isoString;
    }
}

export function ProposalPanel({ generalId }: ProposalPanelProps) {
    const {
        pendingProposals,
        myProposals,
        fetchPendingProposals,
        fetchMyProposals,
        resolveProposal,
    } = useCommandStore();

    const [rejectingId, setRejectingId] = useState<number | null>(null);
    const [rejectReason, setRejectReason] = useState('');
    const [processingId, setProcessingId] = useState<number | null>(null);

    // Load proposals on mount
    useEffect(() => {
        void fetchPendingProposals(generalId);
        void fetchMyProposals(generalId);
    }, [generalId, fetchPendingProposals, fetchMyProposals]);

    const handleApprove = useCallback(async (proposalId: number) => {
        setProcessingId(proposalId);
        try {
            await resolveProposal(generalId, proposalId, true);
            toast.success('제안을 승인했습니다');
        } catch {
            toast.error('승인 처리에 실패했습니다');
        } finally {
            setProcessingId(null);
        }
    }, [generalId, resolveProposal]);

    const handleReject = useCallback(async (proposalId: number) => {
        setProcessingId(proposalId);
        try {
            await resolveProposal(generalId, proposalId, false, rejectReason || undefined);
            toast.success('제안을 거부했습니다');
            setRejectingId(null);
            setRejectReason('');
        } catch {
            toast.error('거부 처리에 실패했습니다');
        } finally {
            setProcessingId(null);
        }
    }, [generalId, resolveProposal, rejectReason]);

    const handleRefresh = useCallback(() => {
        void fetchPendingProposals(generalId);
        void fetchMyProposals(generalId);
    }, [generalId, fetchPendingProposals, fetchMyProposals]);

    const pendingCount = pendingProposals.length;

    return (
        <Card className="border-gray-700">
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-base">
                    제안 관리
                    {pendingCount > 0 && (
                        <Badge variant="default" className="ml-2 text-[10px]">
                            {pendingCount}
                        </Badge>
                    )}
                </CardTitle>
                <Button size="sm" variant="ghost" className="h-7 text-xs" onClick={handleRefresh}>
                    새로고침
                </Button>
            </CardHeader>

            <CardContent>
                <Tabs defaultValue="received">
                    <TabsList>
                        <TabsTrigger value="received" className="text-xs">
                            받은 제안
                            {pendingCount > 0 && (
                                <span className="ml-1 text-yellow-400">({pendingCount})</span>
                            )}
                        </TabsTrigger>
                        <TabsTrigger value="sent" className="text-xs">
                            보낸 제안
                        </TabsTrigger>
                    </TabsList>

                    <TabsContent value="received" className="space-y-2 mt-2">
                        {pendingProposals.length === 0 ? (
                            <p className="text-xs text-muted-foreground py-4 text-center">
                                받은 제안이 없습니다
                            </p>
                        ) : (
                            pendingProposals.map((proposal) => (
                                <div
                                    key={proposal.id}
                                    className="border border-gray-700 rounded p-2 space-y-1.5"
                                >
                                    <div className="flex items-center justify-between">
                                        <div className="flex items-center gap-2">
                                            <span className="text-xs font-medium text-gray-200">
                                                {proposal.requesterName}
                                            </span>
                                            <Badge variant="secondary" className="text-[10px]">
                                                {proposal.actionCode}
                                            </Badge>
                                        </div>
                                        <span className="text-[10px] text-gray-500">
                                            {formatTime(proposal.createdAt)}
                                        </span>
                                    </div>
                                    {proposal.reason && (
                                        <p className="text-[11px] text-gray-400">
                                            사유: {proposal.reason}
                                        </p>
                                    )}

                                    {rejectingId === proposal.id ? (
                                        <div className="space-y-1">
                                            <textarea
                                                className="w-full h-12 rounded border border-gray-700 bg-background px-2 py-1 text-xs resize-none"
                                                placeholder="거부 사유 (선택)..."
                                                value={rejectReason}
                                                onChange={(e) => setRejectReason(e.target.value)}
                                            />
                                            <div className="flex gap-1">
                                                <Button
                                                    size="sm"
                                                    variant="destructive"
                                                    className="h-6 text-[10px]"
                                                    disabled={processingId === proposal.id}
                                                    onClick={() => void handleReject(proposal.id)}
                                                >
                                                    확인
                                                </Button>
                                                <Button
                                                    size="sm"
                                                    variant="ghost"
                                                    className="h-6 text-[10px]"
                                                    onClick={() => {
                                                        setRejectingId(null);
                                                        setRejectReason('');
                                                    }}
                                                >
                                                    취소
                                                </Button>
                                            </div>
                                        </div>
                                    ) : (
                                        <div className="flex gap-1">
                                            <Button
                                                size="sm"
                                                className="h-6 text-[10px]"
                                                disabled={processingId === proposal.id}
                                                onClick={() => void handleApprove(proposal.id)}
                                            >
                                                승인
                                            </Button>
                                            <Button
                                                size="sm"
                                                variant="outline"
                                                className="h-6 text-[10px] text-red-300"
                                                disabled={processingId === proposal.id}
                                                onClick={() => setRejectingId(proposal.id)}
                                            >
                                                거부
                                            </Button>
                                        </div>
                                    )}
                                </div>
                            ))
                        )}
                    </TabsContent>

                    <TabsContent value="sent" className="space-y-2 mt-2">
                        {myProposals.length === 0 ? (
                            <p className="text-xs text-muted-foreground py-4 text-center">
                                보낸 제안이 없습니다
                            </p>
                        ) : (
                            myProposals.map((proposal) => (
                                <div
                                    key={proposal.id}
                                    className="border border-gray-700 rounded p-2 space-y-1"
                                >
                                    <div className="flex items-center justify-between">
                                        <div className="flex items-center gap-2">
                                            <Badge variant="secondary" className="text-[10px]">
                                                {proposal.actionCode}
                                            </Badge>
                                            <span className="text-[10px] text-gray-400">
                                                &rarr; {proposal.approverName}
                                            </span>
                                        </div>
                                        <Badge
                                            variant="outline"
                                            className={`text-[10px] ${STATUS_COLORS[proposal.status]}`}
                                        >
                                            {STATUS_LABELS[proposal.status]}
                                        </Badge>
                                    </div>
                                    {proposal.reason && (
                                        <p className="text-[10px] text-gray-500">
                                            {proposal.reason}
                                        </p>
                                    )}
                                    <div className="flex items-center justify-between text-[10px] text-gray-600">
                                        <span>{formatTime(proposal.createdAt)}</span>
                                        {proposal.resolvedAt && (
                                            <span>처리: {formatTime(proposal.resolvedAt)}</span>
                                        )}
                                    </div>
                                </div>
                            ))
                        )}
                    </TabsContent>
                </Tabs>
            </CardContent>
        </Card>
    );
}
