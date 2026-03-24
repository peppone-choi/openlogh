'use client';

import { useState, useEffect, useCallback } from 'react';
import { ArrowLeftFromLine, AlertTriangle, Loader2, CheckCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { useBattleStore } from '@/stores/battleStore';

const WARP_PREP_SECONDS = 150; // 2.5 minutes

interface RetreatRequirement {
    label: string;
    met: boolean;
    detail: string;
}

export function RetreatPanel() {
    const { myFleets, pendingEnergy, phase } = useBattleStore();
    const [retreating, setRetreating] = useState(false);
    const [countdown, setCountdown] = useState(0);
    const [warpStartTime, setWarpStartTime] = useState<number | null>(null);

    const myFleet = myFleets[0] ?? null;

    // Check retreat requirements
    const warpEnergy = pendingEnergy.warp;
    const isWarpMaxed = warpEnergy >= 80;
    const isInCombat = phase === 'combat';

    // Simulated radar range check (in real implementation, server would validate)
    const isOutsideRadar = true; // placeholder - server validates

    const requirements: RetreatRequirement[] = [
        {
            label: '레이더 범위 이탈',
            met: isOutsideRadar,
            detail: isOutsideRadar ? '적 레이더 범위 밖' : '적 레이더 범위 내 - 이탈 필요',
        },
        {
            label: 'WARP 에너지 최대',
            met: isWarpMaxed,
            detail: isWarpMaxed ? `WARP ${warpEnergy}% (충족)` : `WARP ${warpEnergy}% (80% 필요)`,
        },
        {
            label: '전투 중',
            met: isInCombat,
            detail: isInCombat ? '전투 상태 확인' : '전투 상태가 아닙니다',
        },
    ];

    const canRetreat = requirements.every((r) => r.met);

    // Warp countdown
    useEffect(() => {
        if (!warpStartTime) return;
        const interval = setInterval(() => {
            const elapsed = Math.floor((Date.now() - warpStartTime) / 1000);
            const remaining = Math.max(0, WARP_PREP_SECONDS - elapsed);
            setCountdown(remaining);
            if (remaining <= 0) {
                setRetreating(false);
                setWarpStartTime(null);
                // In real implementation: call server to execute retreat
                clearInterval(interval);
            }
        }, 1000);
        return () => clearInterval(interval);
    }, [warpStartTime]);

    const handleRetreat = useCallback(() => {
        if (!canRetreat) return;
        setRetreating(true);
        setWarpStartTime(Date.now());
        setCountdown(WARP_PREP_SECONDS);
    }, [canRetreat]);

    const handleCancelRetreat = useCallback(() => {
        setRetreating(false);
        setWarpStartTime(null);
        setCountdown(0);
    }, []);

    if (!myFleet) return null;

    const countdownMinutes = Math.floor(countdown / 60);
    const countdownSeconds = countdown % 60;
    const warpProgress = warpStartTime ? ((WARP_PREP_SECONDS - countdown) / WARP_PREP_SECONDS) * 100 : 0;

    return (
        <Card className="border-amber-900/30">
            <CardHeader className="pb-2">
                <CardTitle className="text-sm flex items-center gap-2">
                    <ArrowLeftFromLine className="size-4 text-amber-400" />
                    전술 철퇴
                    <Badge variant="outline" className="text-[9px] ml-auto">
                        현재 전대 + 지휘 범위 내 유닛
                    </Badge>
                </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
                {/* Requirements */}
                <div className="space-y-1.5">
                    <div className="text-[10px] font-mono text-muted-foreground uppercase tracking-wider">
                        철퇴 조건
                    </div>
                    {requirements.map((req) => (
                        <div key={req.label} className="flex items-center gap-2 text-xs">
                            {req.met ? (
                                <CheckCircle className="size-3.5 text-green-400 shrink-0" />
                            ) : (
                                <AlertTriangle className="size-3.5 text-red-400 shrink-0" />
                            )}
                            <span className={req.met ? 'text-green-400' : 'text-red-400'}>{req.label}</span>
                            <span className="text-muted-foreground ml-auto text-[10px]">{req.detail}</span>
                        </div>
                    ))}
                </div>

                {/* Warp preparation countdown */}
                {retreating && (
                    <div className="space-y-2 bg-amber-950/20 border border-amber-900/30 rounded p-3">
                        <div className="flex items-center gap-2">
                            <Loader2 className="size-4 text-purple-400 animate-spin" />
                            <span className="text-sm font-medium text-purple-300">워프 준비 중...</span>
                        </div>
                        <div className="flex items-center justify-between text-xs">
                            <span className="text-muted-foreground">잔여 시간</span>
                            <span className="font-mono text-purple-300 text-lg tabular-nums">
                                {countdownMinutes}:{countdownSeconds.toString().padStart(2, '0')}
                            </span>
                        </div>
                        <Progress value={warpProgress} className="h-1.5" />
                        <div className="text-[10px] text-muted-foreground">
                            워프 준비 완료 시 현재 전대 및 최대 지휘 범위 내 유닛이 함께 철퇴합니다.
                        </div>
                    </div>
                )}

                {/* Action buttons */}
                <div className="flex gap-2">
                    {!retreating ? (
                        <Button
                            onClick={handleRetreat}
                            disabled={!canRetreat}
                            variant="outline"
                            size="sm"
                            className="w-full"
                        >
                            <ArrowLeftFromLine className="size-4 mr-1" />
                            철퇴 개시 (워프 준비 2분 30초)
                        </Button>
                    ) : (
                        <Button onClick={handleCancelRetreat} variant="destructive" size="sm" className="w-full">
                            철퇴 취소
                        </Button>
                    )}
                </div>
            </CardContent>
        </Card>
    );
}
