'use client';

import { useState, useMemo, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { ArrowLeft, UserPlus } from 'lucide-react';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { officerApi } from '@/lib/gameApi';
import { toast } from 'sonner';
import { PageHeader } from '@/components/game/page-header';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { StatBar } from '@/components/game/stat-bar';

const TOTAL_STAT_POINTS = 300;
const STAT_MIN = 10;
const STAT_MAX = 100;

const STAT_KEYS = [
    'leadership',
    'command',
    'intelligence',
    'politics',
    'administration',
    'mobility',
    'attack',
    'defense',
] as const;

type StatKey = (typeof STAT_KEYS)[number];

const STAT_LABELS: Record<StatKey, string> = {
    leadership: '통솔',
    command: '지휘',
    intelligence: '정보',
    politics: '정치',
    administration: '운영',
    mobility: '기동',
    attack: '공격',
    defense: '방어',
};

const STAT_COLORS: Record<StatKey, string> = {
    leadership: 'bg-red-500',
    command: 'bg-orange-500',
    intelligence: 'bg-blue-500',
    politics: 'bg-green-500',
    administration: 'bg-purple-500',
    mobility: 'bg-cyan-500',
    attack: 'bg-yellow-500',
    defense: 'bg-slate-400',
};

const CAREER_TYPES = [
    { value: 'military', label: '군인 (Military)' },
    { value: 'politician', label: '정치가 (Politician)' },
];

const EMPIRE_ORIGINS = [
    { value: 'empire_noble', label: '귀족 (Noble)' },
    { value: 'empire_knight', label: '제국기사 (Imperial Knight)' },
    { value: 'empire_commoner', label: '평민 (Commoner)' },
    { value: 'empire_exile', label: '망명자 (Exile)' },
];

const ALLIANCE_ORIGINS = [
    { value: 'alliance_citizen', label: '시민 (Citizen)' },
    { value: 'alliance_exile', label: '망명자 (Exile)' },
];

const CURRENT_YEAR = 796; // Space Era default

function defaultStats(): Record<StatKey, number> {
    const perStat = Math.floor(TOTAL_STAT_POINTS / STAT_KEYS.length);
    const result = {} as Record<StatKey, number>;
    for (const k of STAT_KEYS) result[k] = perStat;
    return result;
}

export default function CreateCharacterPage() {
    const router = useRouter();
    const { currentWorld } = useWorldStore();
    const { fetchMyGeneral } = useOfficerStore();

    const [name, setName] = useState('');
    const [gender, setGender] = useState<'male' | 'female'>('male');
    const [birthYear, setBirthYear] = useState(String(CURRENT_YEAR - 20));
    const [careerType, setCareerType] = useState<'military' | 'politician'>('military');
    const [faction, setFaction] = useState<'empire' | 'alliance'>('empire');
    const [origin, setOrigin] = useState('empire_noble');
    const [stats, setStats] = useState<Record<StatKey, number>>(defaultStats);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const totalUsed = useMemo(() => STAT_KEYS.reduce((sum, k) => sum + stats[k], 0), [stats]);
    const remaining = TOTAL_STAT_POINTS - totalUsed;

    const originOptions = faction === 'empire' ? EMPIRE_ORIGINS : ALLIANCE_ORIGINS;

    // When faction changes, reset origin to first option of that faction
    const handleFactionChange = (val: 'empire' | 'alliance') => {
        setFaction(val);
        setOrigin(val === 'empire' ? 'empire_noble' : 'alliance_citizen');
    };

    const adjustStat = (key: StatKey, delta: number) => {
        setStats((prev) => {
            const next = Math.max(STAT_MIN, Math.min(STAT_MAX, prev[key] + delta));
            const newTotal = STAT_KEYS.reduce((s, k) => s + (k === key ? next : prev[k]), 0);
            if (newTotal > TOTAL_STAT_POINTS) return prev;
            return { ...prev, [key]: next };
        });
    };

    const handleStatInput = (key: StatKey, value: number) => {
        const clamped = Math.max(STAT_MIN, Math.min(STAT_MAX, isNaN(value) ? STAT_MIN : value));
        setStats((prev) => {
            const newTotal = STAT_KEYS.reduce((s, k) => s + (k === key ? clamped : prev[k]), 0);
            if (newTotal > TOTAL_STAT_POINTS) return prev;
            return { ...prev, [key]: clamped };
        });
    };

    const applyRandomStats = useCallback(() => {
        const weights = STAT_KEYS.map(() => Math.random() * 60 + 10);
        const total = weights.reduce((a, b) => a + b, 0);
        const vals = weights.map((w) => Math.floor((w / total) * TOTAL_STAT_POINTS));
        let sum = vals.reduce((a, b) => a + b, 0);
        for (let i = 0; sum < TOTAL_STAT_POINTS; i = (i + 1) % STAT_KEYS.length) {
            if (vals[i] < STAT_MAX) {
                vals[i]++;
                sum++;
            }
        }
        // Clamp
        for (let pass = 0; pass < 10; pass++) {
            let valid = true;
            for (let i = 0; i < STAT_KEYS.length; i++) {
                if (vals[i] < STAT_MIN) {
                    const def = STAT_MIN - vals[i];
                    vals[i] = STAT_MIN;
                    vals[(i + 1) % STAT_KEYS.length] -= def;
                    valid = false;
                }
                if (vals[i] > STAT_MAX) {
                    const ex = vals[i] - STAT_MAX;
                    vals[i] = STAT_MAX;
                    vals[(i + 1) % STAT_KEYS.length] += ex;
                    valid = false;
                }
            }
            if (valid) break;
        }
        const newStats = {} as Record<StatKey, number>;
        STAT_KEYS.forEach((k, i) => {
            newStats[k] = vals[i];
        });
        setStats(newStats);
    }, []);

    const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        if (!currentWorld) return;
        if (!name.trim()) {
            setError('이름을 입력해주세요.');
            return;
        }
        if (remaining !== 0) {
            setError(`능력치 합계가 정확히 ${TOTAL_STAT_POINTS}이어야 합니다. (현재: ${totalUsed})`);
            return;
        }

        setSubmitting(true);
        setError(null);
        try {
            await officerApi.create(currentWorld.id, {
                name: name.trim(),
                gender,
                bornYear: Number(birthYear),
                careerType,
                origin,
                factionType: faction,
                ...stats,
            });
            await fetchMyGeneral(currentWorld.id);
            toast.success('캐릭터가 생성되었습니다.');
            router.push('/');
        } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : '캐릭터 생성에 실패했습니다.';
            setError(msg);
        } finally {
            setSubmitting(false);
        }
    };

    if (!currentWorld) {
        return <div className="p-4 text-muted-foreground">월드를 선택해주세요.</div>;
    }

    return (
        <div className="p-4 max-w-2xl mx-auto space-y-6">
            <Button variant="ghost" size="sm" onClick={() => router.push('/lobby/join')} className="mb-2">
                <ArrowLeft className="size-4 mr-1" /> 로비로 돌아가기
            </Button>

            <PageHeader icon={UserPlus} title="캐릭터 생성" />

            {error && <div className="text-sm px-3 py-2 rounded bg-destructive/20 text-destructive">{error}</div>}

            <Card>
                <CardHeader>
                    <CardTitle>기본 정보</CardTitle>
                </CardHeader>
                <CardContent>
                    <form onSubmit={handleSubmit} className="space-y-5">
                        {/* Name */}
                        <div className="space-y-1.5">
                            <label htmlFor="char-name">이름</label>
                            <Input
                                id="char-name"
                                value={name}
                                onChange={(e) => setName(e.target.value)}
                                maxLength={20}
                                placeholder="캐릭터 이름 입력"
                            />
                        </div>

                        {/* Gender */}
                        <div className="space-y-1.5">
                            <label className="block text-sm text-muted-foreground">성별</label>
                            <div className="flex gap-3">
                                {(['male', 'female'] as const).map((g) => (
                                    <Button
                                        key={g}
                                        type="button"
                                        variant={gender === g ? 'default' : 'outline'}
                                        size="sm"
                                        onClick={() => setGender(g)}
                                    >
                                        {g === 'male' ? '남성 (Male)' : '여성 (Female)'}
                                    </Button>
                                ))}
                            </div>
                        </div>

                        {/* Birth Year */}
                        <div className="space-y-1.5">
                            <label htmlFor="birth-year">출생 연도 (우주력)</label>
                            <Input
                                id="birth-year"
                                type="number"
                                min={700}
                                max={794}
                                value={birthYear}
                                onChange={(e) => setBirthYear(e.target.value)}
                                className="w-40"
                            />
                            <p className="text-xs text-muted-foreground">현재: {CURRENT_YEAR}년 (우주력 기준)</p>
                        </div>

                        {/* Career Type */}
                        <div className="space-y-1.5">
                            <label className="block text-sm text-muted-foreground">직업 유형</label>
                            <Select
                                value={careerType}
                                onValueChange={(v) => setCareerType(v as 'military' | 'politician')}
                            >
                                <SelectTrigger className="w-full">
                                    <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                    {CAREER_TYPES.map((c) => (
                                        <SelectItem key={c.value} value={c.value}>
                                            {c.label}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>

                        {/* Faction (Empire / Alliance) */}
                        <div className="space-y-1.5">
                            <label className="block text-sm text-muted-foreground">출신 세력</label>
                            <div className="flex gap-3">
                                {(['empire', 'alliance'] as const).map((f) => (
                                    <Button
                                        key={f}
                                        type="button"
                                        variant={faction === f ? 'default' : 'outline'}
                                        size="sm"
                                        onClick={() => handleFactionChange(f)}
                                    >
                                        {f === 'empire' ? '은하제국 (Empire)' : '자유행성동맹 (Alliance)'}
                                    </Button>
                                ))}
                            </div>
                        </div>

                        {/* Origin */}
                        <div className="space-y-1.5">
                            <label className="block text-sm text-muted-foreground">출신 배경</label>
                            <Select value={origin} onValueChange={setOrigin}>
                                <SelectTrigger className="w-full">
                                    <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                    {originOptions.map((o) => (
                                        <SelectItem key={o.value} value={o.value}>
                                            {o.label}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        </div>

                        {/* Portrait placeholder */}
                        <div className="space-y-1.5">
                            <label className="block text-sm text-muted-foreground">초상화 (선택)</label>
                            <div className="w-20 h-24 border border-dashed border-border rounded flex items-center justify-center text-xs text-muted-foreground">
                                미설정
                            </div>
                            <p className="text-xs text-muted-foreground">
                                계정 설정에서 프로필 이미지를 등록하면 초상화로 사용됩니다.
                            </p>
                        </div>

                        {/* Stat Distribution */}
                        <div className="space-y-3">
                            <div className="flex items-center justify-between">
                                <label className="block text-sm text-muted-foreground">
                                    능력치 배분 (합계 {TOTAL_STAT_POINTS})
                                </label>
                                <Badge
                                    variant={remaining === 0 ? 'default' : remaining > 0 ? 'secondary' : 'destructive'}
                                    className={remaining === 0 ? 'bg-green-600' : remaining > 0 ? 'bg-amber-600' : ''}
                                >
                                    남은: {remaining}
                                </Badge>
                            </div>

                            <Button type="button" variant="outline" size="sm" onClick={applyRandomStats}>
                                랜덤 배분
                            </Button>

                            <div className="space-y-2">
                                {STAT_KEYS.map((key) => (
                                    <div key={key} className="flex items-center gap-2">
                                        <Button
                                            type="button"
                                            variant="outline"
                                            size="icon-xs"
                                            onClick={() => adjustStat(key, -5)}
                                        >
                                            -
                                        </Button>
                                        <div className="flex-1">
                                            <StatBar
                                                label={STAT_LABELS[key]}
                                                value={stats[key]}
                                                max={STAT_MAX}
                                                color={STAT_COLORS[key]}
                                            />
                                        </div>
                                        <Button
                                            type="button"
                                            variant="outline"
                                            size="icon-xs"
                                            onClick={() => adjustStat(key, 5)}
                                        >
                                            +
                                        </Button>
                                        <Input
                                            type="number"
                                            min={STAT_MIN}
                                            max={STAT_MAX}
                                            value={stats[key]}
                                            onChange={(e) => handleStatInput(key, Number(e.target.value))}
                                            className="w-14 text-center"
                                        />
                                    </div>
                                ))}
                            </div>
                        </div>

                        {/* Submit */}
                        <div className="flex gap-2">
                            <Button type="submit" disabled={submitting || remaining !== 0} className="flex-1">
                                {submitting ? '생성중...' : '캐릭터 생성'}
                            </Button>
                            <Button
                                type="button"
                                variant="outline"
                                onClick={() => {
                                    setStats(defaultStats());
                                    setError(null);
                                }}
                            >
                                초기화
                            </Button>
                        </div>
                    </form>
                </CardContent>
            </Card>
        </div>
    );
}
