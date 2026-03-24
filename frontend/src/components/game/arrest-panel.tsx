'use client';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { AlertTriangle, Gavel, Shield, User } from 'lucide-react';

export interface ArrestWarrant {
    id: number;
    officerName: string;
    officerId: number;
    reason: string;
    issuedAt: string;
    issuedBy: string;
    status: 'pending' | 'captured' | 'released' | 'executed';
}

export interface Prisoner {
    id: number;
    officerName: string;
    officerId: number;
    capturedAt: string;
    capturedBy: string;
    status: 'prisoner' | 'awaiting_judgment';
}

interface ArrestPanelProps {
    warrants: ArrestWarrant[];
    prisoners: Prisoner[];
    canIssue?: boolean;
    canJudge?: boolean;
    onJudge?: (prisonerId: number, verdict: 'release' | 'execute' | 'recruit') => void;
    onRevokeWarrant?: (warrantId: number) => void;
}

const WARRANT_STATUS_CONF: Record<ArrestWarrant['status'], { label: string; className: string }> = {
    pending: { label: '수배 중', className: 'bg-red-500/20 text-red-400 border-red-500/40' },
    captured: { label: '체포됨', className: 'bg-orange-500/20 text-orange-400 border-orange-500/40' },
    released: { label: '석방', className: 'bg-green-500/20 text-green-400 border-green-500/40' },
    executed: { label: '처형됨', className: 'bg-gray-500/20 text-gray-400 border-gray-500/40' },
};

const PRISONER_STATUS_CONF: Record<Prisoner['status'], { label: string; className: string }> = {
    prisoner: { label: '수감 중', className: 'bg-orange-500/20 text-orange-400 border-orange-500/40' },
    awaiting_judgment: { label: '판결 대기', className: 'bg-yellow-500/20 text-yellow-400 border-yellow-500/40' },
};

export function ArrestPanel({
    warrants,
    prisoners,
    canIssue = false,
    canJudge = false,
    onJudge,
    onRevokeWarrant,
}: ArrestPanelProps) {
    return (
        <div className="space-y-4">
            {/* Arrest Warrants */}
            <Card>
                <CardHeader className="pb-2">
                    <CardTitle className="text-sm flex items-center gap-2">
                        <AlertTriangle className="size-4 text-red-400" />
                        체포 영장
                        {warrants.length > 0 && (
                            <Badge variant="outline" className="ml-auto text-xs">
                                {warrants.length}건
                            </Badge>
                        )}
                    </CardTitle>
                </CardHeader>
                <CardContent>
                    {warrants.length === 0 ? (
                        <div className="text-xs text-muted-foreground py-2">발급된 체포 영장이 없습니다.</div>
                    ) : (
                        <div className="space-y-2">
                            {warrants.map((w) => {
                                const conf = WARRANT_STATUS_CONF[w.status];
                                return (
                                    <div
                                        key={w.id}
                                        className="flex items-center gap-2 text-sm border-b border-muted/30 pb-2"
                                    >
                                        <User className="size-4 text-muted-foreground shrink-0" />
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-center gap-2">
                                                <span className="font-medium">{w.officerName}</span>
                                                <Badge
                                                    variant="outline"
                                                    className={`text-[10px] px-1 py-0 h-4 ${conf.className}`}
                                                >
                                                    {conf.label}
                                                </Badge>
                                            </div>
                                            <div className="text-xs text-muted-foreground mt-0.5">
                                                사유: {w.reason} · 발령: {w.issuedBy} · {w.issuedAt}
                                            </div>
                                        </div>
                                        {canIssue && w.status === 'pending' && onRevokeWarrant && (
                                            <Button
                                                size="sm"
                                                variant="outline"
                                                className="text-xs h-7"
                                                onClick={() => onRevokeWarrant(w.id)}
                                            >
                                                취소
                                            </Button>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </CardContent>
            </Card>

            {/* Prisoners */}
            <Card>
                <CardHeader className="pb-2">
                    <CardTitle className="text-sm flex items-center gap-2">
                        <Shield className="size-4 text-orange-400" />
                        포로 목록
                        {prisoners.length > 0 && (
                            <Badge variant="outline" className="ml-auto text-xs">
                                {prisoners.length}명
                            </Badge>
                        )}
                    </CardTitle>
                </CardHeader>
                <CardContent>
                    {prisoners.length === 0 ? (
                        <div className="text-xs text-muted-foreground py-2">수감 중인 포로가 없습니다.</div>
                    ) : (
                        <div className="space-y-2">
                            {prisoners.map((p) => {
                                const conf = PRISONER_STATUS_CONF[p.status];
                                return (
                                    <div
                                        key={p.id}
                                        className="flex items-center gap-2 text-sm border-b border-muted/30 pb-2"
                                    >
                                        <User className="size-4 text-muted-foreground shrink-0" />
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-center gap-2">
                                                <span className="font-medium">{p.officerName}</span>
                                                <Badge
                                                    variant="outline"
                                                    className={`text-[10px] px-1 py-0 h-4 ${conf.className}`}
                                                >
                                                    {conf.label}
                                                </Badge>
                                            </div>
                                            <div className="text-xs text-muted-foreground mt-0.5">
                                                체포: {p.capturedBy} · {p.capturedAt}
                                            </div>
                                        </div>
                                        {canJudge && onJudge && (
                                            <div className="flex gap-1 shrink-0">
                                                <Button
                                                    size="sm"
                                                    variant="outline"
                                                    className="text-xs h-7 text-green-400 border-green-700 hover:bg-green-900/20"
                                                    onClick={() => onJudge(p.id, 'release')}
                                                >
                                                    석방
                                                </Button>
                                                <Button
                                                    size="sm"
                                                    variant="outline"
                                                    className="text-xs h-7 text-blue-400 border-blue-700 hover:bg-blue-900/20"
                                                    onClick={() => onJudge(p.id, 'recruit')}
                                                >
                                                    등용
                                                </Button>
                                                <Button
                                                    size="sm"
                                                    variant="outline"
                                                    className="text-xs h-7 text-red-400 border-red-700 hover:bg-red-900/20"
                                                    onClick={() => onJudge(p.id, 'execute')}
                                                >
                                                    <Gavel className="size-3 mr-0.5" />
                                                    처형
                                                </Button>
                                            </div>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </CardContent>
            </Card>
        </div>
    );
}
