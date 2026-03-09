'use client';

import { useEffect, useState, useMemo, useCallback } from 'react';
import {
    LayoutDashboard,
    Plus,
    Trash2,
    Globe,
    Play,
    Pause,
    RotateCcw,
    MessageSquarePlus,
    Info,
    Zap,
    Server,
    AlertTriangle,
    GitBranch,
} from 'lucide-react';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { adminApi, adminEventApi, worldApi, scenarioApi, gameVersionApi } from '@/lib/gameApi';
import { toast } from 'sonner';
import type { WorldState, Scenario, AdminDashboard } from '@/types';
import { useAdminWorld } from '@/contexts/AdminWorldContext';

export default function AdminDashboardPage() {
    const { worldId, refreshWorlds: refreshWorldsContext } = useAdminWorld();
    // Gateway-level world list (always available)
    const [worlds, setWorlds] = useState<WorldState[]>([]);
    const [loading, setLoading] = useState(true);

    // Scenarios
    const [scenarios, setScenarios] = useState<Scenario[]>([]);
    const scenarioMap = useMemo(() => new Map(scenarios.map((s) => [s.code, s.title])), [scenarios]);

    // Per-world dashboard (only when game instance is running)
    const [dashboard, setDashboard] = useState<AdminDashboard | null>(null);
    const [dashboardAvailable, setDashboardAvailable] = useState(false);
    const [notice, setNotice] = useState('');
    const [turnTerm, setTurnTerm] = useState('');
    const [locked, setLocked] = useState(false);
    const [logMessage, setLogMessage] = useState('');
    const [eventLoading, setEventLoading] = useState<string | null>(null);
    const [eventLogMsg, setEventLogMsg] = useState('');
    const [eventDeleteId, setEventDeleteId] = useState('');

    // Global (gateway) system flags
    const [allowLogin, setAllowLogin] = useState<boolean | null>(null);
    const [allowJoin, setAllowJoin] = useState<boolean | null>(null);
    const [savingSystemFlags, setSavingSystemFlags] = useState(false);

    const [availableVersions, setAvailableVersions] = useState<string[]>([]);
    const [maxGeneral, setMaxGeneral] = useState('');
    const [maxNation, setMaxNation] = useState('');
    const [npcMode, setNpcMode] = useState('1');
    const [isFiction, setIsFiction] = useState(false);
    const [joinMode, setJoinMode] = useState('standard');
    const [blockGeneralCreate, setBlockGeneralCreate] = useState(0);
    const [realtimeMode, setRealtimeMode] = useState(false);
    const [commandPointRegenRate, setCommandPointRegenRate] = useState('');
    const [bettingActive, setBettingActive] = useState(false);
    const [tournamentAuto, setTournamentAuto] = useState(false);
    const [allowDomestic, setAllowDomestic] = useState(true);
    const [allowTeleport, setAllowTeleport] = useState(true);
    const [allowRecruit, setAllowRecruit] = useState(true);
    const [allowTraining, setAllowTraining] = useState(true);
    const [allowMoraleBoost, setAllowMoraleBoost] = useState(true);
    const [allowDispatch, setAllowDispatch] = useState(true);
    const [commanderTurnEnabled, setCommanderTurnEnabled] = useState(true);
    const [turnValidityHours, setTurnValidityHours] = useState('24');
    const [sync, setSync] = useState(true);
    const [extend, setExtend] = useState(true);
    const [showImgLevel, setShowImgLevel] = useState(3);
    const [autorunMinutes, setAutorunMinutes] = useState(0);
    const [reserveOpen, setReserveOpen] = useState('');
    const [preReserveOpen, setPreReserveOpen] = useState('');
    const [allowConscript, setAllowConscript] = useState(true);

    // Create world form
    const [showCreateForm, setShowCreateForm] = useState(false);
    const [newScenario, setNewScenario] = useState('');
    const [newWorldName, setNewWorldName] = useState('');
    const [newTurnTerm, setNewTurnTerm] = useState('300');
    const [newGameVersion, setNewGameVersion] = useState('latest');
    const [creating, setCreating] = useState(false);

    // Reset dialog
    const [resetTarget, setResetTarget] = useState<{
        id: number;
        name: string;
    } | null>(null);
    const [resetScenario, setResetScenario] = useState('');

    const loadWorlds = useCallback(() => {
        worldApi
            .list()
            .then((res) => setWorlds(res.data))
            .catch(() => {});
    }, []);

    useEffect(() => {
        const init = async () => {
            // 1. Load worlds from gateway (always works)
            loadWorlds();

            // 2. Load scenarios
            scenarioApi
                .list()
                .then(({ data }) => {
                    setScenarios(data);
                    if (data.length > 0) setNewScenario(data[0].code);
                })
                .catch(() => {});

            gameVersionApi
                .available()
                .then((res) => setAvailableVersions(res.data))
                .catch(() => {});

            adminApi
                .getSystemFlags()
                .then((res) => {
                    setAllowLogin(res.data.allowLogin);
                    setAllowJoin(res.data.allowJoin);
                })
                .catch(() => {});

            // 4. Try loading per-world dashboard (may fail if no game instance)
            if (worldId != null) {
                adminApi
                    .getDashboard(worldId)
                    .then((res) => {
                        const d = res.data;
                        setDashboard(d);
                        setDashboardAvailable(true);
                        if (d.currentWorld) {
                            const cfg = d.currentWorld.config;
                            setNotice((cfg?.notice as string) ?? '');
                            setTurnTerm(String(cfg?.turnTerm ?? ''));
                            setLocked(Boolean(cfg?.locked));
                            setMaxGeneral(String(cfg?.maxGeneral ?? ''));
                            setMaxNation(String(cfg?.maxNation ?? ''));
                            setNpcMode(String(cfg?.npcMode ?? '1'));
                            setIsFiction(Boolean(cfg?.isFiction));
                            setJoinMode(String(cfg?.joinMode ?? 'standard'));
                            setBlockGeneralCreate(Number(cfg?.blockGeneralCreate ?? 0));
                            setRealtimeMode(Boolean(d.currentWorld.realtimeMode));
                            setCommandPointRegenRate(String(d.currentWorld.commandPointRegenRate ?? ''));
                            setBettingActive(Boolean(cfg?.bettingActive));
                            setTournamentAuto(Boolean(cfg?.tournamentAuto));
                            setAllowDomestic(cfg?.allowDomestic !== false);
                            setAllowTeleport(cfg?.allowTeleport !== false);
                            setAllowRecruit(cfg?.allowRecruit !== false);
                            setAllowTraining(cfg?.allowTraining !== false);
                            setAllowMoraleBoost(cfg?.allowMoraleBoost !== false);
                            setAllowDispatch(cfg?.allowDispatch !== false);
                            setCommanderTurnEnabled(cfg?.commanderTurnEnabled !== false);
                            setTurnValidityHours(String(cfg?.turnValidityHours ?? '24'));
                            setSync(cfg?.sync !== false);
                            setExtend(cfg?.extend !== false);
                            setShowImgLevel(Number(cfg?.showImgLevel ?? 3));
                            setAutorunMinutes(Number(cfg?.autorunMinutes ?? 0));
                            setReserveOpen(String(cfg?.reserveOpen ?? ''));
                            setPreReserveOpen(String(cfg?.preReserveOpen ?? ''));
                            setAllowConscript(cfg?.allowConscript !== false);
                        }
                    })
                    .catch(() => {
                        setDashboardAvailable(false);
                    })
                    .finally(() => {
                        setLoading(false);
                    });
            } else {
                setLoading(false);
            }
        };
        init();
    }, [loadWorlds, worldId]);

    // ── Handlers ────────────────────────────────────────────────

    const handleCreateWorld = async () => {
        if (!newScenario) {
            toast.error('시나리오를 선택하세요.');
            return;
        }
        setCreating(true);
        try {
            const res = await adminApi.createWorld({
                scenarioCode: newScenario,
                name: newWorldName.trim() || undefined,
                tickSeconds: newTurnTerm ? Number(newTurnTerm) : undefined,
                gameVersion: newGameVersion.trim() || undefined,
            });
            toast.success(`월드 생성 완료 (ID: ${res.data.id})`);
            setNewWorldName('');
            setShowCreateForm(false);
            loadWorlds();
            refreshWorldsContext();
        } catch {
            toast.error('월드 생성 실패');
        } finally {
            setCreating(false);
        }
    };

    const handleDeleteWorld = async (worldId: number) => {
        if (!confirm(`월드 #${worldId}를 정말 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.`)) return;
        try {
            await adminApi.deleteWorld(worldId);
            toast.success(`월드 #${worldId} 삭제 완료`);
            loadWorlds();
            refreshWorldsContext();
        } catch {
            toast.error('월드 삭제 실패');
        }
    };

    const handleWorldAction = async (worldId: number, action: 'open' | 'close') => {
        const labels = { open: '오픈', close: '폐쇄' };
        try {
            if (action === 'open') {
                await adminApi.activateWorld(worldId, {
                    gameVersion: newGameVersion.trim() || undefined,
                });
            } else {
                await adminApi.deactivateWorld(worldId);
            }
            toast.success(`월드 #${worldId} ${labels[action]} 완료`);
            loadWorlds();
            refreshWorldsContext();
        } catch {
            toast.error(`${labels[action]} 실패`);
        }
    };

    const handleOpenReset = (worldId: number, worldName: string) => {
        const world = worlds.find((w) => w.id === worldId);
        setResetTarget({ id: worldId, name: worldName });
        setResetScenario(world?.scenarioCode || scenarios[0]?.code || '');
    };

    const handleConfirmReset = async () => {
        if (!resetTarget) return;
        if (!confirm(`월드 "${resetTarget.name}"을 정말 리셋하시겠습니까? 모든 데이터가 초기화됩니다.`)) return;
        try {
            await adminApi.resetWorld(resetTarget.id, resetScenario || undefined, newGameVersion.trim() || undefined);
            toast.success(`월드 #${resetTarget.id} 리셋 완료`);
            setResetTarget(null);
            loadWorlds();
            refreshWorldsContext();
        } catch {
            toast.error('리셋 실패');
        }
    };

    const handleWriteLog = async () => {
        if (!logMessage.trim()) return;
        try {
            await adminApi.writeLog(logMessage.trim(), worldId);
            toast.success('중원정세 로그가 추가되었습니다.');
            setLogMessage('');
        } catch {
            toast.error('로그 쓰기 실패');
        }
    };

    const handleSaveSystemFlags = async () => {
        if (allowLogin === null || allowJoin === null) return;
        setSavingSystemFlags(true);
        try {
            await adminApi.patchSystemFlags({ allowLogin, allowJoin });
            toast.success('전역 스위치가 저장되었습니다.');
        } catch {
            toast.error('전역 스위치 저장 실패');
        } finally {
            setSavingSystemFlags(false);
        }
    };

    const handleSave = async () => {
        try {
            await adminApi.updateSettings(
                {
                    notice,
                    turnTerm: turnTerm ? Number(turnTerm) : undefined,
                    locked,
                    maxGeneral: maxGeneral ? Number(maxGeneral) : undefined,
                    maxNation: maxNation ? Number(maxNation) : undefined,
                    npcMode: Number(npcMode),
                    isFiction,
                    joinMode,
                    blockGeneralCreate,
                    realtimeMode,
                    commandPointRegenRate: commandPointRegenRate ? Number(commandPointRegenRate) : undefined,
                    bettingActive,
                    tournamentAuto,
                    allowDomestic,
                    allowTeleport,
                    allowRecruit,
                    allowTraining,
                    allowMoraleBoost,
                    allowDispatch,
                    commanderTurnEnabled,
                    turnValidityHours: turnValidityHours ? Number(turnValidityHours) : undefined,
                    sync,
                    extend,
                    showImgLevel,
                    autorunMinutes,
                    reserveOpen: reserveOpen || undefined,
                    preReserveOpen: preReserveOpen || undefined,
                    allowConscript,
                },
                worldId
            );
            toast.success('설정이 저장되었습니다.');
        } catch {
            toast.error('저장 실패');
        }
    };

    // ── Event Definitions ───────────────────────────────────────
    const EVENT_ACTIONS: {
        name: string;
        label: string;
        description: string;
        needsArg?: 'message' | 'eventId';
    }[] = [
        {
            name: 'ProcessIncome',
            label: '세금 징수',
            description: '도시 수입 처리 (골드/쌀)',
        },
        {
            name: 'ProcessSemiAnnual',
            label: '반기 처리',
            description: '인구 변동, 기술 퇴화, 장수 수명 등',
        },
        {
            name: 'UpdateCitySupply',
            label: '도시 보급',
            description: '도시 물자 갱신',
        },
        {
            name: 'UpdateNationLevel',
            label: '국가 등급',
            description: '국가 등급 재계산',
        },
        {
            name: 'RandomizeCityTradeRate',
            label: '교역률 변경',
            description: '도시 교역률 무작위 변경',
        },
        {
            name: 'RaiseInvader',
            label: '이민족 침입',
            description: '이민족 NPC 국가 발생',
        },
        { name: 'RaiseNPCNation', label: 'NPC 건국', description: 'NPC 국가 생성' },
        {
            name: 'RegNeutralNPC',
            label: '중립 NPC 배치',
            description: '중립 NPC 장수를 빈 도시에 배치',
        },
        {
            name: 'NoticeToHistoryLog',
            label: '중원정세 기록',
            description: '중원정세에 메시지 기록',
            needsArg: 'message',
        },
        {
            name: 'DeleteEvent',
            label: '이벤트 삭제',
            description: '예정된 이벤트를 ID로 삭제',
            needsArg: 'eventId',
        },
    ];

    const handleRaiseEvent = async (eventName: string, needsArg?: 'message' | 'eventId') => {
        let args: unknown[] | undefined;
        if (needsArg === 'message') {
            if (!eventLogMsg.trim()) {
                toast.error('메시지를 입력하세요.');
                return;
            }
            args = [eventLogMsg.trim()];
        } else if (needsArg === 'eventId') {
            const id = Number(eventDeleteId);
            if (!id || isNaN(id)) {
                toast.error('이벤트 ID를 입력하세요.');
                return;
            }
            args = [id];
        }

        setEventLoading(eventName);
        try {
            const res = await adminEventApi.raise(eventName, args, worldId);
            if (res.data.result) {
                toast.success(`${eventName} 실행 완료`);
                if (needsArg === 'message') setEventLogMsg('');
                if (needsArg === 'eventId') setEventDeleteId('');
            } else {
                toast.error(res.data.reason || '이벤트 실행 실패');
            }
        } catch {
            toast.error('이벤트 실행 중 오류 발생');
        } finally {
            setEventLoading(null);
        }
    };

    if (loading) return <LoadingState />;

    return (
        <div className="space-y-4">
            <PageHeader icon={LayoutDashboard} title="관리자 대시보드" />

            {/* ── 전역 스위치 (Gateway) ─────────────────────────── */}
            <Card>
                <CardHeader>
                    <CardTitle className="text-sm">전역 스위치</CardTitle>
                </CardHeader>
                <CardContent className="space-y-3">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                        <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                            <span>로그인 허용</span>
                            <input
                                type="checkbox"
                                checked={Boolean(allowLogin)}
                                disabled={allowLogin === null}
                                onChange={(e) => setAllowLogin(e.target.checked)}
                            />
                        </label>
                        <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                            <span>가입 허용</span>
                            <input
                                type="checkbox"
                                checked={Boolean(allowJoin)}
                                disabled={allowJoin === null}
                                onChange={(e) => setAllowJoin(e.target.checked)}
                            />
                        </label>
                    </div>
                    <Button
                        size="sm"
                        variant="outline"
                        disabled={savingSystemFlags || allowLogin === null || allowJoin === null}
                        onClick={handleSaveSystemFlags}
                    >
                        저장
                    </Button>
                </CardContent>
            </Card>

            {/* ── 월드 관리 (Gateway) ───────────────────────────── */}
            <Card>
                <CardHeader className="flex flex-row items-center justify-between">
                    <CardTitle className="flex items-center gap-2">
                        <Globe className="size-5" />
                        월드 관리
                    </CardTitle>
                    <Button size="sm" variant="outline" onClick={() => setShowCreateForm(!showCreateForm)}>
                        <Plus className="size-4 mr-1" />새 월드 생성
                    </Button>
                </CardHeader>
                <CardContent className="space-y-4">
                    {/* Create Form */}
                    {showCreateForm && (
                        <div className="p-4 border rounded-md space-y-3 bg-muted/20">
                            <h4 className="text-sm font-medium">새 월드 생성</h4>
                            <div className="grid grid-cols-2 gap-3">
                                <div className="space-y-1">
                                    <label className="text-xs text-muted-foreground">시나리오</label>
                                    <select
                                        value={newScenario}
                                        onChange={(e) => setNewScenario(e.target.value)}
                                        className="w-full px-3 py-2 bg-background border border-input rounded-md text-sm"
                                    >
                                        <option value="">시나리오 선택</option>
                                        {scenarios.map((s) => (
                                            <option key={s.code} value={s.code}>
                                                {s.title} ({s.startYear}년)
                                            </option>
                                        ))}
                                    </select>
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs text-muted-foreground">월드 이름</label>
                                    <Input
                                        value={newWorldName}
                                        onChange={(e) => setNewWorldName(e.target.value)}
                                        placeholder="선택사항"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs text-muted-foreground">턴 간격 (초 단위, 예: 300초 = 5분)</label>
                                    <Input
                                        type="number"
                                        value={newTurnTerm}
                                        onChange={(e) => setNewTurnTerm(e.target.value)}
                                        placeholder="300"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs text-muted-foreground">게임 버전</label>
                                    <select
                                        value={newGameVersion}
                                        onChange={(e) => setNewGameVersion(e.target.value)}
                                        className="w-full px-3 py-2 bg-background border border-input rounded-md text-sm"
                                    >
                                        <option value="latest">latest</option>
                                        {availableVersions
                                            .filter((v) => v !== 'latest')
                                            .map((v) => (
                                                <option key={v} value={v}>
                                                    {v}
                                                </option>
                                            ))}
                                    </select>
                                </div>
                            </div>
                            <div className="flex gap-2">
                                <Button size="sm" onClick={handleCreateWorld} disabled={creating || !newScenario}>
                                    {creating ? '생성 중...' : '생성'}
                                </Button>
                                <Button size="sm" variant="ghost" onClick={() => setShowCreateForm(false)}>
                                    취소
                                </Button>
                            </div>
                        </div>
                    )}

                    {/* Reset Dialog */}
                    {resetTarget && (
                        <div className="p-4 border border-destructive/50 rounded-md space-y-3 bg-destructive/5">
                            <h4 className="text-sm font-medium flex items-center gap-2">
                                <RotateCcw className="size-4" />
                                월드 리셋: {resetTarget.name}
                            </h4>
                            <p className="text-xs text-muted-foreground">
                                시나리오를 선택하면 해당 시나리오로 월드가 초기화됩니다. 모든 진행 상황이 삭제됩니다.
                            </p>
                            <div className="grid grid-cols-2 gap-3">
                                <div className="space-y-1">
                                    <label className="text-xs text-muted-foreground">시나리오</label>
                                    <select
                                        value={resetScenario}
                                        onChange={(e) => setResetScenario(e.target.value)}
                                        className="w-full px-3 py-2 bg-background border border-input rounded-md text-sm"
                                    >
                                        {scenarios.map((s) => (
                                            <option key={s.code} value={s.code}>
                                                {s.title} ({s.startYear}년)
                                            </option>
                                        ))}
                                    </select>
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs text-muted-foreground">게임 버전</label>
                                    <select
                                        value={newGameVersion}
                                        onChange={(e) => setNewGameVersion(e.target.value)}
                                        className="w-full px-3 py-2 bg-background border border-input rounded-md text-sm"
                                    >
                                        <option value="latest">latest</option>
                                        {availableVersions
                                            .filter((v) => v !== 'latest')
                                            .map((v) => (
                                                <option key={v} value={v}>
                                                    {v}
                                                </option>
                                            ))}
                                    </select>
                                </div>
                            </div>
                            <div className="flex gap-2">
                                <Button
                                    size="sm"
                                    variant="destructive"
                                    onClick={handleConfirmReset}
                                    disabled={!resetScenario}
                                >
                                    리셋 확인
                                </Button>
                                <Button size="sm" variant="ghost" onClick={() => setResetTarget(null)}>
                                    취소
                                </Button>
                            </div>
                        </div>
                    )}

                    {/* World Table */}
                    {worlds.length === 0 ? (
                        <p className="text-sm text-muted-foreground">월드가 없습니다.</p>
                    ) : (
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>ID</TableHead>
                                    <TableHead>이름</TableHead>
                                    <TableHead>시나리오</TableHead>
                                    <TableHead>시점</TableHead>
                                    <TableHead>게임버전</TableHead>
                                    <TableHead>상태</TableHead>
                                    <TableHead>액션</TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {worlds.map((w) => {
                                    const displayName = w.name || scenarioMap.get(w.scenarioCode) || w.scenarioCode;
                                    return (
                                        <TableRow key={w.id}>
                                            <TableCell>{w.id}</TableCell>
                                            <TableCell className="font-medium">{displayName}</TableCell>
                                            <TableCell>{scenarioMap.get(w.scenarioCode) || w.scenarioCode}</TableCell>
                                            <TableCell>
                                                {w.currentYear}년 {w.currentMonth}월
                                            </TableCell>
                                            <TableCell>
                                                <Badge variant="secondary" className="text-xs">
                                                    {w.gameVersion}
                                                </Badge>
                                            </TableCell>
                                            <TableCell>
                                                <Badge variant={w.meta?.gatewayActive ? 'outline' : 'destructive'}>
                                                    {w.meta?.gatewayActive ? '운영중' : '비활성'}
                                                </Badge>
                                            </TableCell>
                                            <TableCell>
                                                <div className="flex flex-wrap gap-1">
                                                    {w.meta?.gatewayActive ? (
                                                        <Button
                                                            size="sm"
                                                            variant="outline"
                                                            onClick={() => handleWorldAction(w.id, 'close')}
                                                        >
                                                            <Pause className="size-3.5 mr-1" />
                                                            폐쇄
                                                        </Button>
                                                    ) : (
                                                        <Button
                                                            size="sm"
                                                            variant="outline"
                                                            onClick={() => handleWorldAction(w.id, 'open')}
                                                        >
                                                            <Play className="size-3.5 mr-1" />
                                                            오픈
                                                        </Button>
                                                    )}
                                                    <Button
                                                        size="sm"
                                                        variant="secondary"
                                                        onClick={() => handleOpenReset(w.id, displayName)}
                                                    >
                                                        <RotateCcw className="size-3.5 mr-1" />
                                                        리셋
                                                    </Button>
                                                    <Button
                                                        size="sm"
                                                        variant="destructive"
                                                        onClick={() => handleDeleteWorld(w.id)}
                                                    >
                                                        <Trash2 className="size-3.5 mr-1" />
                                                        삭제
                                                    </Button>
                                                </div>
                                            </TableCell>
                                        </TableRow>
                                    );
                                })}
                            </TableBody>
                        </Table>
                    )}
                </CardContent>
            </Card>

            {/* ── 게임 설정 (Per-world, requires game instance) ── */}
            <Card>
                <CardHeader>
                    <CardTitle className="text-sm">게임 설정</CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                    {dashboardAvailable && dashboard?.currentWorld ? (
                        <>
                            <div className="grid grid-cols-3 gap-4 text-center">
                                <div>
                                    <p className="text-xs text-muted-foreground">현재 시점</p>
                                    <p className="text-lg font-bold">
                                        {dashboard.currentWorld.year}년 {dashboard.currentWorld.month}월
                                    </p>
                                </div>
                                <div>
                                    <p className="text-xs text-muted-foreground">시나리오</p>
                                    <p className="text-lg font-bold">
                                        {scenarioMap.get(dashboard.currentWorld.scenarioCode) ||
                                            dashboard.currentWorld.scenarioCode}
                                    </p>
                                </div>
                                <div>
                                    <p className="text-xs text-muted-foreground">월드 수</p>
                                    <p className="text-lg font-bold">{dashboard.worldCount}</p>
                                </div>
                            </div>

                            <div className="space-y-1">
                                <label className="text-sm text-muted-foreground">공지사항</label>
                                <Input
                                    value={notice}
                                    onChange={(e) => setNotice(e.target.value)}
                                    placeholder="공지사항 입력"
                                />
                            </div>
                            <div className="space-y-1">
                                <label className="text-sm text-muted-foreground">중원정세 추가</label>
                                <div className="flex gap-2">
                                    <Input
                                        value={logMessage}
                                        onChange={(e) => setLogMessage(e.target.value)}
                                        placeholder="중원정세 메시지 입력"
                                        onKeyDown={(e) => e.key === 'Enter' && handleWriteLog()}
                                    />
                                    <Button size="sm" variant="outline" onClick={handleWriteLog}>
                                        <MessageSquarePlus className="size-4 mr-1" />
                                        로그쓰기
                                    </Button>
                                </div>
                            </div>
                            <div className="space-y-1">
                                <label className="text-sm text-muted-foreground">턴 간격 (분 단위)</label>
                                <Input
                                    type="number"
                                    value={turnTerm}
                                    onChange={(e) => setTurnTerm(e.target.value)}
                                    placeholder="5"
                                />
                                <div className="flex flex-wrap gap-1 pt-1">
                                    {[
                                        { label: '1분', value: 1 },
                                        { label: '2분', value: 2 },
                                        { label: '5분', value: 5 },
                                        { label: '10분', value: 10 },
                                        { label: '20분', value: 20 },
                                        { label: '30분', value: 30 },
                                        { label: '60분', value: 60 },
                                        { label: '120분', value: 120 },
                                    ].map((p) => (
                                        <Button
                                            key={p.value}
                                            size="sm"
                                            variant={turnTerm === String(p.value) ? 'default' : 'outline'}
                                            onClick={() => setTurnTerm(String(p.value))}
                                            className="text-xs"
                                        >
                                            {p.label}
                                        </Button>
                                    ))}
                                </div>
                            </div>
                            <div className="flex items-center gap-2">
                                <input
                                    type="checkbox"
                                    checked={locked}
                                    onChange={(e) => setLocked(e.target.checked)}
                                    id="locked"
                                    className="accent-red-400"
                                />
                                <label htmlFor="locked" className="text-sm">
                                    서버 잠금
                                </label>
                            </div>

                            <div className="border-t pt-4 mt-4 space-y-3">
                                <h4 className="text-sm font-medium">게임 규칙</h4>
                                <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">최대 장수</label>
                                        <Input
                                            type="number"
                                            value={maxGeneral}
                                            onChange={(e) => setMaxGeneral(e.target.value)}
                                            placeholder="600"
                                        />
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">최대 국가</label>
                                        <Input
                                            type="number"
                                            value={maxNation}
                                            onChange={(e) => setMaxNation(e.target.value)}
                                            placeholder="24"
                                        />
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">NPC 모드</label>
                                        <select
                                            value={npcMode}
                                            onChange={(e) => setNpcMode(e.target.value)}
                                            className="w-full px-3 py-2 bg-background border border-input rounded-md text-sm"
                                        >
                                            <option value="0">불가</option>
                                            <option value="1">가능</option>
                                            <option value="2">선택 생성</option>
                                        </select>
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">가입 모드</label>
                                        <select
                                            value={joinMode}
                                            onChange={(e) => setJoinMode(e.target.value)}
                                            className="w-full px-3 py-2 bg-background border border-input rounded-md text-sm"
                                        >
                                            <option value="standard">일반</option>
                                            <option value="onlyRandom">랜덤 전용</option>
                                        </select>
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">사령 포인트 회복률</label>
                                        <Input
                                            type="number"
                                            value={commandPointRegenRate}
                                            onChange={(e) => setCommandPointRegenRate(e.target.value)}
                                            placeholder="100"
                                        />
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">턴 유효시간 (시간)</label>
                                        <Input
                                            type="number"
                                            value={turnValidityHours}
                                            onChange={(e) => setTurnValidityHours(e.target.value)}
                                            placeholder="24"
                                        />
                                    </div>
                                </div>
                                <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">장수 생성</label>
                                        <select
                                            value={blockGeneralCreate}
                                            onChange={(e) => setBlockGeneralCreate(Number(e.target.value))}
                                            className="w-full px-3 py-2 bg-background border border-input rounded-md text-sm"
                                        >
                                            <option value={0}>가능</option>
                                            <option value={1}>불가</option>
                                            <option value={2}>장수명 무작위</option>
                                        </select>
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">이미지 표기 수준</label>
                                        <select
                                            value={showImgLevel}
                                            onChange={(e) => setShowImgLevel(Number(e.target.value))}
                                            className="w-full px-3 py-2 bg-background border border-input rounded-md text-sm"
                                        >
                                            <option value={0}>없음 (0)</option>
                                            <option value={1}>최소 (1)</option>
                                            <option value={2}>보통 (2)</option>
                                            <option value={3}>전체 (3)</option>
                                        </select>
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">자동턴 유효시간 (분)</label>
                                        <select
                                            value={autorunMinutes}
                                            onChange={(e) => setAutorunMinutes(Number(e.target.value))}
                                            className="w-full px-3 py-2 bg-background border border-input rounded-md text-sm"
                                        >
                                            <option value={0}>사용안함</option>
                                            <option value={20}>20분</option>
                                            <option value={30}>30분</option>
                                            <option value={60}>60분</option>
                                            <option value={120}>120분</option>
                                        </select>
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">오픈 예약 일시</label>
                                        <Input
                                            type="datetime-local"
                                            value={reserveOpen}
                                            onChange={(e) => setReserveOpen(e.target.value)}
                                        />
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">가오픈 예약 일시</label>
                                        <Input
                                            type="datetime-local"
                                            value={preReserveOpen}
                                            onChange={(e) => setPreReserveOpen(e.target.value)}
                                        />
                                    </div>
                                </div>
                                <div className="grid grid-cols-2 md:grid-cols-3 gap-2">
                                    {[
                                        {
                                            label: '가상 모드',
                                            checked: isFiction,
                                            onChange: setIsFiction,
                                        },
                                        {
                                            label: '실시간 모드',
                                            checked: realtimeMode,
                                            onChange: setRealtimeMode,
                                        },
                                        {
                                            label: '베팅 활성화',
                                            checked: bettingActive,
                                            onChange: setBettingActive,
                                        },
                                        {
                                            label: '자동 토너먼트',
                                            checked: tournamentAuto,
                                            onChange: setTournamentAuto,
                                        },
                                        {
                                            label: '시간 동기화',
                                            checked: sync,
                                            onChange: setSync,
                                        },
                                        {
                                            label: '확장 NPC',
                                            checked: extend,
                                            onChange: setExtend,
                                        },
                                    ].map((opt) => (
                                        <label
                                            key={opt.label}
                                            className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm"
                                        >
                                            <span>{opt.label}</span>
                                            <input
                                                type="checkbox"
                                                checked={opt.checked}
                                                onChange={(e) => opt.onChange(e.target.checked)}
                                            />
                                        </label>
                                    ))}
                                </div>
                            </div>

                            <div className="border-t pt-4 mt-4 space-y-3">
                                <h4 className="text-sm font-medium">게임 기능 설정</h4>
                                <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
                                    {[
                                        {
                                            label: '내정',
                                            checked: allowDomestic,
                                            onChange: setAllowDomestic,
                                        },
                                        {
                                            label: '순간이동',
                                            checked: allowTeleport,
                                            onChange: setAllowTeleport,
                                        },
                                        {
                                            label: '모병',
                                            checked: allowRecruit,
                                            onChange: setAllowRecruit,
                                        },
                                        {
                                            label: '징병',
                                            checked: allowConscript,
                                            onChange: setAllowConscript,
                                        },
                                        {
                                            label: '훈련',
                                            checked: allowTraining,
                                            onChange: setAllowTraining,
                                        },
                                        {
                                            label: '사기진작',
                                            checked: allowMoraleBoost,
                                            onChange: setAllowMoraleBoost,
                                        },
                                        {
                                            label: '출병',
                                            checked: allowDispatch,
                                            onChange: setAllowDispatch,
                                        },
                                        {
                                            label: '사령턴',
                                            checked: commanderTurnEnabled,
                                            onChange: setCommanderTurnEnabled,
                                        },
                                    ].map((opt) => (
                                        <label
                                            key={opt.label}
                                            className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm"
                                        >
                                            <span>{opt.label}</span>
                                            <input
                                                type="checkbox"
                                                checked={opt.checked}
                                                onChange={(e) => opt.onChange(e.target.checked)}
                                            />
                                        </label>
                                    ))}
                                </div>
                            </div>

                            <Button onClick={handleSave} className="bg-red-400 hover:bg-red-500 text-white">
                                저장
                            </Button>
                        </>
                    ) : (
                        <div className="flex items-center gap-3 text-sm text-muted-foreground py-4">
                            <Info className="size-5 shrink-0" />
                            <p>
                                게임 인스턴스가 실행 중이 아닙니다. 월드를 오픈(활성화)하면 공지사항, 턴 간격, 서버 잠금
                                등 상세 설정이 가능합니다.
                            </p>
                        </div>
                    )}
                </CardContent>
            </Card>

            {/* ── 이벤트 발동 (Per-world, requires game instance) ── */}
            <Card>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2 text-sm">
                        <Zap className="size-4" />
                        이벤트 발동
                    </CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                    {dashboardAvailable ? (
                        <>
                            <p className="text-xs text-muted-foreground">
                                게임 이벤트를 수동으로 발동합니다. 현재 활성 월드에 적용됩니다.
                            </p>
                            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-2">
                                {EVENT_ACTIONS.filter((e) => !e.needsArg).map((evt) => (
                                    <Button
                                        key={evt.name}
                                        size="sm"
                                        variant="outline"
                                        className="flex flex-col items-start h-auto py-2 px-3"
                                        disabled={eventLoading !== null}
                                        onClick={() => handleRaiseEvent(evt.name)}
                                    >
                                        <span className="text-xs font-medium">{evt.label}</span>
                                        <span className="text-[10px] text-muted-foreground font-normal">
                                            {evt.description}
                                        </span>
                                        {eventLoading === evt.name && (
                                            <span className="text-[10px] text-blue-400 mt-0.5">실행 중...</span>
                                        )}
                                    </Button>
                                ))}
                            </div>

                            {/* NoticeToHistoryLog with message input */}
                            <div className="flex gap-2 items-end">
                                <div className="flex-1 space-y-1">
                                    <label className="text-xs text-muted-foreground">중원정세 기록</label>
                                    <Input
                                        value={eventLogMsg}
                                        onChange={(e) => setEventLogMsg(e.target.value)}
                                        placeholder="기록할 메시지 입력"
                                        onKeyDown={(e) =>
                                            e.key === 'Enter' && handleRaiseEvent('NoticeToHistoryLog', 'message')
                                        }
                                    />
                                </div>
                                <Button
                                    size="sm"
                                    variant="outline"
                                    disabled={eventLoading !== null || !eventLogMsg.trim()}
                                    onClick={() => handleRaiseEvent('NoticeToHistoryLog', 'message')}
                                >
                                    {eventLoading === 'NoticeToHistoryLog' ? '실행 중...' : '기록'}
                                </Button>
                            </div>

                            {/* DeleteEvent with ID input */}
                            <div className="flex gap-2 items-end">
                                <div className="flex-1 space-y-1">
                                    <label className="text-xs text-muted-foreground">이벤트 삭제 (ID)</label>
                                    <Input
                                        type="number"
                                        value={eventDeleteId}
                                        onChange={(e) => setEventDeleteId(e.target.value)}
                                        placeholder="삭제할 이벤트 ID"
                                        onKeyDown={(e) =>
                                            e.key === 'Enter' && handleRaiseEvent('DeleteEvent', 'eventId')
                                        }
                                    />
                                </div>
                                <Button
                                    size="sm"
                                    variant="destructive"
                                    disabled={eventLoading !== null || !eventDeleteId}
                                    onClick={() => handleRaiseEvent('DeleteEvent', 'eventId')}
                                >
                                    {eventLoading === 'DeleteEvent' ? '실행 중...' : '삭제'}
                                </Button>
                            </div>
                        </>
                    ) : (
                        <div className="flex items-center gap-3 text-sm text-muted-foreground py-4">
                            <Info className="size-5 shrink-0" />
                            <p>
                                게임 인스턴스가 실행 중이 아닙니다. 월드를 오픈(활성화)해야 이벤트를 발동할 수 있습니다.
                            </p>
                        </div>
                    )}
                </CardContent>
            </Card>

            {/* ── 서버 관리 ─────────────────────────────────────── */}
            <Card>
                <CardHeader>
                    <CardTitle className="flex items-center gap-2 text-sm">
                        <Server className="size-4" />
                        서버 관리
                    </CardTitle>
                </CardHeader>
                <CardContent className="space-y-4">
                    {dashboardAvailable && dashboard?.currentWorld ? (
                        <>
                            <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-sm">
                                <div className="rounded-md border border-border p-3">
                                    <p className="text-xs text-muted-foreground">게임 버전</p>
                                    <p className="font-mono font-medium">
                                        {worlds.find((w) => w.id === worldId)?.gameVersion ?? '-'}
                                    </p>
                                </div>
                                <div className="rounded-md border border-border p-3">
                                    <p className="text-xs text-muted-foreground">커밋</p>
                                    <p className="font-mono font-medium text-xs">
                                        <GitBranch className="inline size-3 mr-1" />
                                        {(dashboard.currentWorld.config?.commitSha as string)?.slice(0, 8) ?? '-'}
                                    </p>
                                </div>
                            </div>
                            <div className="flex flex-wrap gap-2">
                                <Button
                                    size="sm"
                                    variant="destructive"
                                    onClick={async () => {
                                        if (
                                            !confirm(
                                                '하드 리셋: DB를 완전히 초기화합니다. 복구 불가능합니다. 정말 실행할까요?'
                                            )
                                        )
                                            return;
                                        if (!confirm('정말로 하드 리셋을 실행합니까? 이 작업은 되돌릴 수 없습니다.'))
                                            return;
                                        try {
                                            await adminApi.resetWorld(worldId!, dashboard.currentWorld?.scenarioCode);
                                            toast.success('하드 리셋 완료');
                                            loadWorlds();
                                        } catch {
                                            toast.error('하드 리셋 실패');
                                        }
                                    }}
                                >
                                    <RotateCcw className="mr-1 size-4" /> 하드 리셋
                                </Button>
                                <Button
                                    size="sm"
                                    variant="destructive"
                                    onClick={async () => {
                                        if (
                                            !confirm(
                                                '긴급 119: 서버를 즉시 잠금하고 현재 상태를 스냅샷합니다. 계속할까요?'
                                            )
                                        )
                                            return;
                                        try {
                                            await adminApi.updateSettings({ locked: true }, worldId);
                                            setLocked(true);
                                            toast.success('서버 119: 서버가 잠금되었습니다.');
                                        } catch {
                                            toast.error('긴급 잠금 실패');
                                        }
                                    }}
                                >
                                    <AlertTriangle className="mr-1 size-4" /> 서버 119
                                </Button>
                            </div>
                            <div className="text-xs text-muted-foreground">
                                에러 로그는{' '}
                                <a href="/admin/logs" className="text-blue-400 underline hover:text-blue-300">
                                    로그 관리
                                </a>{' '}
                                페이지에서 확인할 수 있습니다.
                            </div>
                        </>
                    ) : (
                        <div className="flex items-center gap-3 text-sm text-muted-foreground py-4">
                            <Info className="size-5 shrink-0" />
                            <p>
                                게임 인스턴스가 실행 중이 아닙니다. 월드를 오픈(활성화)해야 서버 관리 기능을 사용할 수
                                있습니다.
                            </p>
                        </div>
                    )}
                </CardContent>
            </Card>
        </div>
    );
}
