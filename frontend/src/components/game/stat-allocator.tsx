'use client';

import { useCallback } from 'react';
import { STAT_KEYS_8, STAT_LABELS_KO, STAT_COLORS, type StatKey8 } from '@/types';
import { STAT_MIN, STAT_MAX } from '@/lib/schemas/character-creation';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { StatBar } from '@/components/game/stat-bar';
import { Dices } from 'lucide-react';

interface StatAllocatorProps {
    budget: number;
    stats: Record<StatKey8, number>;
    onChange: (stats: Record<StatKey8, number>) => void;
    min?: number;
    max?: number;
}

export function StatAllocator({
    budget,
    stats,
    onChange,
    min = STAT_MIN,
    max = STAT_MAX,
}: StatAllocatorProps) {
    const total = STAT_KEYS_8.reduce((sum, key) => sum + stats[key], 0);
    const remaining = budget - total;

    const setStat = useCallback(
        (key: StatKey8, value: number) => {
            const clamped = Math.max(min, Math.min(max, value));
            onChange({ ...stats, [key]: clamped });
        },
        [stats, onChange, min, max],
    );

    const handleBalanced = useCallback(() => {
        const base = Math.floor(budget / 8);
        let rem = budget - base * 8;
        const result = {} as Record<StatKey8, number>;
        for (const key of STAT_KEYS_8) {
            result[key] = Math.max(min, Math.min(max, base + (rem > 0 ? 1 : 0)));
            if (rem > 0) rem--;
        }
        onChange(result);
    }, [budget, min, max, onChange]);

    const handleRandom = useCallback(() => {
        const result = {} as Record<StatKey8, number>;
        const keys = [...STAT_KEYS_8];
        // Start with min for each stat
        let pool = budget - min * 8;
        for (const key of keys) {
            result[key] = min;
        }
        // Distribute remaining pool randomly
        for (let i = 0; i < pool; i++) {
            const candidates = keys.filter((k) => result[k] < max);
            if (candidates.length === 0) break;
            const pick = candidates[Math.floor(Math.random() * candidates.length)];
            result[pick]++;
        }
        onChange(result);
    }, [budget, min, max, onChange]);

    return (
        <div className="space-y-4">
            <div className="flex items-center justify-between">
                <span className="text-sm text-muted-foreground">
                    능력치 배분 (합계 {budget})
                </span>
                <Badge
                    variant={
                        remaining === 0
                            ? 'default'
                            : remaining > 0
                              ? 'secondary'
                              : 'destructive'
                    }
                >
                    {remaining === 0
                        ? '배분 완료'
                        : remaining > 0
                          ? `남은 포인트: ${remaining}`
                          : `초과: ${Math.abs(remaining)}`}
                </Badge>
            </div>

            <div className="flex gap-2">
                <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={handleBalanced}
                >
                    균형형
                </Button>
                <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={handleRandom}
                >
                    <Dices className="size-3.5 mr-1" />
                    랜덤
                </Button>
            </div>

            <div className="space-y-2">
                {STAT_KEYS_8.map((key) => (
                    <div key={key} className="flex items-center gap-2">
                        <Button
                            type="button"
                            variant="outline"
                            size="icon-xs"
                            disabled={stats[key] <= min}
                            onClick={() => setStat(key, stats[key] - 5)}
                        >
                            -
                        </Button>
                        <div className="flex-1">
                            <StatBar
                                label={STAT_LABELS_KO[key]}
                                value={stats[key]}
                                max={max}
                                color={STAT_COLORS[key]}
                            />
                        </div>
                        <Button
                            type="button"
                            variant="outline"
                            size="icon-xs"
                            disabled={stats[key] >= max || remaining <= 0}
                            onClick={() => {
                                const next = Math.min(max, stats[key] + 5);
                                const newTotal = STAT_KEYS_8.reduce(
                                    (s, k) => s + (k === key ? next : stats[k]),
                                    0,
                                );
                                if (newTotal > budget) return;
                                setStat(key, next);
                            }}
                        >
                            +
                        </Button>
                        <Input
                            type="number"
                            min={min}
                            max={max}
                            value={stats[key]}
                            onChange={(e) => setStat(key, Number(e.target.value))}
                            className="w-14 text-center"
                        />
                    </div>
                ))}
            </div>
        </div>
    );
}
