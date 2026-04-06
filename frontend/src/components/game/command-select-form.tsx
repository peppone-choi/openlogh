'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Card, CardContent } from '@/components/ui/8bit/card';
import { Badge } from '@/components/ui/8bit/badge';
import { Button } from '@/components/ui/8bit/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/8bit/tabs';
import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogFooter,
    DialogDescription,
} from '@/components/ui/8bit/dialog';
import { CommandArgForm, COMMAND_ARGS } from '@/components/game/command-arg-form';
import { proposalApi } from '@/lib/gameApi';
import { toast } from 'sonner';
import type { CommandArg, CommandTableEntry, EligibleApprover, SubmitProposalRequest } from '@/types';

/** Map commandGroup codes to Korean labels */
const COMMAND_GROUP_LABELS: Record<string, string> = {
    OPERATIONS: '작전',
    PERSONAL: '개인',
    COMMAND: '지휘',
    LOGISTICS: '병참',
    PERSONNEL: '인사',
    POLITICS: '정치',
    INTELLIGENCE: '첩보',
};

interface CommandSelectFormProps {
    commandTable: Record<string, CommandTableEntry[]>;
    onSelect: (actionCode: string, arg?: CommandArg) => void;
    onCancel: () => void;
    realtimeMode: boolean;
    generalId: number;
}

export function CommandSelectForm({
    commandTable,
    onSelect,
    onCancel,
    realtimeMode,
    generalId,
}: CommandSelectFormProps) {
    const [selectedCmd, setSelectedCmd] = useState('');
    const [proposalTarget, setProposalTarget] = useState<CommandTableEntry | null>(null);
    const [approvers, setApprovers] = useState<EligibleApprover[]>([]);
    const [selectedApprover, setSelectedApprover] = useState<number | null>(null);
    const [proposalReason, setProposalReason] = useState('');
    const [loadingApprovers, setLoadingApprovers] = useState(false);
    const [submittingProposal, setSubmittingProposal] = useState(false);

    // Group commands by commandGroup if available, otherwise fall back to category
    const groupedCommands = useMemo(() => {
        const allCommands: CommandTableEntry[] = Object.values(commandTable).flat();
        const groups: Record<string, CommandTableEntry[]> = {};

        for (const cmd of allCommands) {
            const groupKey = cmd.commandGroup ?? cmd.category;
            if (!groups[groupKey]) {
                groups[groupKey] = [];
            }
            groups[groupKey].push(cmd);
        }

        return groups;
    }, [commandTable]);

    // Determine tab order: commandGroup keys first (in defined order), then any remaining category keys
    const groupKeys = useMemo(() => {
        const keys = Object.keys(groupedCommands);
        const orderedGroupCodes = Object.keys(COMMAND_GROUP_LABELS);
        const ordered: string[] = [];
        for (const code of orderedGroupCodes) {
            if (keys.includes(code)) {
                ordered.push(code);
            }
        }
        // Append any remaining keys not in COMMAND_GROUP_LABELS
        for (const key of keys) {
            if (!ordered.includes(key)) {
                ordered.push(key);
            }
        }
        return ordered;
    }, [groupedCommands]);

    const hasArgForm = !!(selectedCmd && COMMAND_ARGS[selectedCmd]);

    const handleArgSubmit = (arg: CommandArg) => {
        if (!selectedCmd) return;
        onSelect(selectedCmd, arg);
    };

    const handleSelectCmd = (actionCode: string) => {
        if (!COMMAND_ARGS[actionCode]) {
            onSelect(actionCode);
            return;
        }
        setSelectedCmd(actionCode);
    };

    const getGroupLabel = (key: string): string => {
        return COMMAND_GROUP_LABELS[key] ?? key;
    };

    // Open proposal dialog for a disabled command
    const openProposalDialog = useCallback(async (cmd: CommandTableEntry) => {
        setProposalTarget(cmd);
        setSelectedApprover(null);
        setProposalReason('');
        setLoadingApprovers(true);
        try {
            const { data } = await proposalApi.eligibleApprovers(generalId, cmd.actionCode);
            setApprovers(data);
        } catch {
            setApprovers([]);
            toast.error('승인자 목록을 불러오지 못했습니다');
        } finally {
            setLoadingApprovers(false);
        }
    }, [generalId]);

    const submitProposal = useCallback(async () => {
        if (!proposalTarget || selectedApprover === null) return;
        setSubmittingProposal(true);
        try {
            const data: SubmitProposalRequest = {
                approverId: selectedApprover,
                actionCode: proposalTarget.actionCode,
                reason: proposalReason || undefined,
            };
            await proposalApi.submit(generalId, data);
            toast.success('제안이 전송되었습니다');
            setProposalTarget(null);
        } catch {
            toast.error('제안 전송에 실패했습니다');
        } finally {
            setSubmittingProposal(false);
        }
    }, [proposalTarget, selectedApprover, proposalReason, generalId]);

    // Reset proposal dialog state when closing
    useEffect(() => {
        if (!proposalTarget) {
            setApprovers([]);
            setSelectedApprover(null);
            setProposalReason('');
        }
    }, [proposalTarget]);

    return (
        <>
            <Card className="border-amber-400/30">
                <CardContent className="space-y-3 pt-3">
                    <Tabs defaultValue={groupKeys[0] ?? ''}>
                        <TabsList className="flex-wrap h-auto">
                            {groupKeys.map((key) => (
                                <TabsTrigger key={key} value={key} className="text-xs">
                                    {getGroupLabel(key)}
                                </TabsTrigger>
                            ))}
                        </TabsList>
                        {groupKeys.map((key) => (
                            <TabsContent key={key} value={key}>
                                <div className="flex flex-wrap gap-1">
                                    {groupedCommands[key]
                                        .filter((cmd) => !cmd.actionCode.startsWith('NPC'))
                                        .map((cmd) => {
                                            const needsArgs = !!COMMAND_ARGS[cmd.actionCode];
                                            const isDisabledByCard = !cmd.enabled && cmd.reason?.includes('권한');

                                            return (
                                                <div key={cmd.actionCode} className="inline-flex items-center gap-0.5">
                                                    <Badge
                                                        variant={selectedCmd === cmd.actionCode ? 'default' : 'secondary'}
                                                        className={`cursor-pointer text-xs ${
                                                            !cmd.enabled ? 'border border-red-500/50 text-red-400' : ''
                                                        }`}
                                                        onClick={() => {
                                                            if (cmd.enabled) {
                                                                handleSelectCmd(cmd.actionCode);
                                                            }
                                                        }}
                                                        title={
                                                            cmd.reason ??
                                                            (needsArgs ? '클릭하여 세부 설정' : '클릭하여 즉시 예약')
                                                        }
                                                    >
                                                        {cmd.name}
                                                        {needsArgs && <span className="ml-0.5 text-amber-300">&#9881;</span>}
                                                        {realtimeMode && (
                                                            <span className="ml-1 text-[10px] text-gray-300">
                                                                ({cmd.commandPointCost}CP/{cmd.durationSeconds}s)
                                                                {cmd.poolType && (
                                                                    <span className={cmd.poolType === 'MCP' ? 'ml-0.5 text-red-400' : 'ml-0.5 text-blue-400'}>
                                                                        [{cmd.poolType}]
                                                                    </span>
                                                                )}
                                                            </span>
                                                        )}
                                                    </Badge>
                                                    {isDisabledByCard && (
                                                        <button
                                                            type="button"
                                                            className="text-[9px] text-yellow-400 hover:text-yellow-300 border border-yellow-500/40 rounded px-1 py-0.5 leading-none"
                                                            title="상관에게 제안하기"
                                                            onClick={() => void openProposalDialog(cmd)}
                                                        >
                                                            제안
                                                        </button>
                                                    )}
                                                </div>
                                            );
                                        })}
                                </div>
                            </TabsContent>
                        ))}
                    </Tabs>

                    {selectedCmd && hasArgForm && (
                        <>
                            <CommandArgForm actionCode={selectedCmd} onSubmit={handleArgSubmit} />
                            <div className="flex gap-2">
                                <Button size="sm" variant="ghost" onClick={onCancel}>
                                    취소
                                </Button>
                            </div>
                        </>
                    )}
                </CardContent>
            </Card>

            {/* Proposal submission dialog */}
            <Dialog open={proposalTarget !== null} onOpenChange={(open) => { if (!open) setProposalTarget(null); }}>
                <DialogContent className="max-w-md">
                    <DialogHeader>
                        <DialogTitle>명령 제안</DialogTitle>
                        <DialogDescription>
                            {proposalTarget?.name} 명령을 상관에게 제안합니다.
                        </DialogDescription>
                    </DialogHeader>

                    <div className="space-y-4 py-2">
                        <div>
                            <p className="text-xs text-muted-foreground mb-1">명령</p>
                            <Badge variant="secondary">{proposalTarget?.name}</Badge>
                            {proposalTarget?.poolType && (
                                <span className={`ml-2 text-xs ${proposalTarget.poolType === 'MCP' ? 'text-red-400' : 'text-blue-400'}`}>
                                    {proposalTarget.commandPointCost}CP [{proposalTarget.poolType}]
                                </span>
                            )}
                        </div>

                        <div>
                            <p className="text-xs text-muted-foreground mb-1">승인자 선택</p>
                            {loadingApprovers ? (
                                <p className="text-xs text-gray-400">불러오는 중...</p>
                            ) : approvers.length === 0 ? (
                                <p className="text-xs text-red-400">승인 가능한 상관이 없습니다</p>
                            ) : (
                                <div className="space-y-1 max-h-40 overflow-y-auto">
                                    {approvers.map((a) => (
                                        <button
                                            key={a.officerId}
                                            type="button"
                                            className={`w-full text-left px-2 py-1.5 text-xs rounded border transition-colors ${
                                                selectedApprover === a.officerId
                                                    ? 'border-amber-400 bg-amber-400/10 text-amber-200'
                                                    : 'border-gray-700 hover:border-gray-500 text-gray-300'
                                            }`}
                                            onClick={() => setSelectedApprover(a.officerId)}
                                        >
                                            <span className="font-medium">{a.officerName}</span>
                                            <span className="ml-2 text-gray-500 text-[10px]">
                                                {a.cards.slice(0, 3).join(', ')}
                                                {a.cards.length > 3 && ` +${a.cards.length - 3}`}
                                            </span>
                                        </button>
                                    ))}
                                </div>
                            )}
                        </div>

                        <div>
                            <p className="text-xs text-muted-foreground mb-1">사유 (선택)</p>
                            <textarea
                                className="w-full h-16 rounded border border-gray-700 bg-background px-2 py-1 text-xs resize-none"
                                placeholder="제안 사유를 입력하세요..."
                                value={proposalReason}
                                onChange={(e) => setProposalReason(e.target.value)}
                            />
                        </div>
                    </div>

                    <DialogFooter>
                        <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => setProposalTarget(null)}
                        >
                            취소
                        </Button>
                        <Button
                            size="sm"
                            disabled={selectedApprover === null || submittingProposal}
                            onClick={() => void submitProposal()}
                        >
                            {submittingProposal ? '전송 중...' : '제안 전송'}
                        </Button>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </>
    );
}
