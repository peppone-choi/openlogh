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
    RotateCw,
    MessageSquarePlus,
    Info,
    Zap,
    Server,
    AlertTriangle,
    GitBranch,
    Clock,
    Coins,
    Wheat,
    Timer,
    Gavel,
    Lock,
    Unlock,
    Activity,
} from 'lucide-react';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/8bit/card';
import { Button } from '@/components/ui/8bit/button';
import { Input } from '@/components/ui/8bit/input';
import { Badge } from '@/components/ui/8bit/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/8bit/table';
import { adminApi, adminEventApi, worldApi, scenarioApi, gameVersionApi, turnApi } from '@/lib/gameApi';
import { toast } from 'sonner';
import type { WorldState, Scenario, AdminDashboard } from '@/types';
import { useAdminWorld } from '@/contexts/AdminWorldContext';

const TURN_PRESETS = [1, 2, 5, 10, 20, 30, 60, 120];

const DAEMON_STATE_CONFIG: Record<string, { label: string; color: string; icon: typeof Activity }> = {
    IDLE: { label: '대기', color: 'bg-green-500/20 text-green-400 border-green-500/40', icon: Activity },
    RUNNING: { label: '실행중', color: 'bg-blue-500/20 text-blue-400 border-blue-500/40', icon: RotateCw },
    PAUSED: { label: '일시정지', color: 'bg-yellow-500/20 text-yellow-400 border-yellow-500/40', icon: Pause },
    FLUSHING: { label: '정리중', color: 'bg-orange-500/20 text-orange-400 border-orange-500/40', icon: RotateCw },
    STOPPING: { label: '중지중', color: 'bg-red-500/20 text-red-400 border-red-500/40', icon: Pause },
};

/** ISO 문자열을 datetime-local 포맷 (로컬 시간)으로 변환 */
function toLocalDatetime(iso: string | undefined | null): string {
    if (!iso) return '';
    const d = new Date(iso);
    if (isNaN(d.getTime())) return '';
    const p = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

/** datetime-local 포맷으로 현재시각 + hours 시간 후 반환 (로컬 시간) */
function futureLocal(hours: number): string {
    const d = new Date(Date.now() + hours * 3600_000);
    d.setMinutes(0, 0, 0);
    const p = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

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

    const [daemonState, setDaemonState] = useState<string>('UNKNOWN');
    const [daemonReason, setDaemonReason] = useState<string>('');
    const [daemonLoading, setDaemonLoading] = useState(false);
    const [lastTurnTime, setLastTurnTime] = useState('');
    const [customTurnTerm, setCustomTurnTerm] = useState('');
    const [year, setYear] = useState('');
    const [month, setMonth] = useState('');
    const [startYear, setStartYear] = useState('');
    const [goldAmount, setGoldAmount] = useState('');
    const [riceAmount, setRiceAmount] = useState('');
    const [distributeTarget, setDistributeTarget] = useState<'all' | 'nations'>('all');
    const [auctionSyncEnabled, setAuctionSyncEnabled] = useState(false);
    const [auctionCloseMinutes, setAuctionCloseMinutes] = useState('60');

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
    const [allowNpcNationSpawn, setAllowNpcNationSpawn] = useState(true);
    const [allowInvaderSpawn, setAllowInvaderSpawn] = useState(true);

    // Create world form
    const [showCreateForm, setShowCreateForm] = useState(false);
    const [newScenario, setNewScenario] = useState('');
    const [newWorldName, setNewWorldName] = useState('');
    const [newTurnTerm, setNewTurnTerm] = useState('300');
    const [newGameVersion, setNewGameVersion] = useState('latest');
    const [creating, setCreating] = useState(false);
    // Create/Reset form — extended config fields
    const [formExtend, setFormExtend] = useState(false);
    const [formNpcMode, setFormNpcMode] = useState('1');
    const [formFiction, setFormFiction] = useState('');
    const [formMaxGeneral, setFormMaxGeneral] = useState('500');
    const [formMaxNation, setFormMaxNation] = useState('55');
    const [formJoinMode, setFormJoinMode] = useState('full');
    const [formBlockGeneralCreate, setFormBlockGeneralCreate] = useState(false);
    const [formShowImgLevel, setFormShowImgLevel] = useState(false);
    const [formRealtimeMode, setFormRealtimeMode] = useState(false);
    const [formCommandPointRegenRate, setFormCommandPointRegenRate] = useState('');
    const [formIsFiction, setFormIsFiction] = useState(false);
    const [formBettingActive, setFormBettingActive] = useState(false);
    const [formTournamentAuto, setFormTournamentAuto] = useState(false);
    const [formAllowDomestic, setFormAllowDomestic] = useState(true);
    const [formAllowTeleport, setFormAllowTeleport] = useState(true);
    const [formAllowRecruit, setFormAllowRecruit] = useState(true);
    const [formAllowTraining, setFormAllowTraining] = useState(true);
    const [formAllowMoraleBoost, setFormAllowMoraleBoost] = useState(true);
    const [formAllowDispatch, setFormAllowDispatch] = useState(true);
    const [formAllowConscript, setFormAllowConscript] = useState(true);
    const [formAllowNpcNationSpawn, setFormAllowNpcNationSpawn] = useState(true);
    const [formAllowInvaderSpawn, setFormAllowInvaderSpawn] = useState(true);
    const [formPreReserveOpen, setFormPreReserveOpen] = useState(() => futureLocal(1));
    const [formOpentime, setFormOpentime] = useState(() => futureLocal(24));

    // Reset dialog
    const [resetTarget, setResetTarget] = useState<{
        id: number;
        name: string;
    } | null>(null);
    const [resetScenario, setResetScenario] = useState('');
    const [resetOpentime, setResetOpentime] = useState(() => futureLocal(24));
    const [resetStartTime, setResetStartTime] = useState(() => futureLocal(1));

    const fetchDaemonStatus = useCallback(async () => {
        try {
            const { data } = await turnApi.getStatus();
            setDaemonState(data.state ?? 'UNKNOWN');
            setDaemonReason(data.reason ?? '');
        } catch {
            setDaemonState('UNREACHABLE');
        }
    }, []);

    useEffect(() => {
        fetchDaemonStatus();
        const interval = setInterval(fetchDaemonStatus, 5000);
        return () => clearInterval(interval);
    }, [fetchDaemonStatus]);

    const handleDaemonAction = useCallback(
        async (action: 'run' | 'pause' | 'resume') => {
            setDaemonLoading(true);
            try {
                if (action === 'run') {
                    const { data } = await turnApi.run();
                    toast.success(data.result === 'triggered' ? '턴 실행 트리거됨' : `결과: ${data.result}`);
                } else if (action === 'pause') {
                    await turnApi.pause();
                    toast.success('턴 데몬 일시정지됨');
                } else {
                    await turnApi.resume();
                    toast.success('턴 데몬 재개됨');
                }
                await fetchDaemonStatus();
            } catch {
                toast.error('턴 데몬 제어 실패');
            } finally {
                setDaemonLoading(false);
            }
        },
        [fetchDaemonStatus]
    );

    const handleTimeSubmit = useCallback(async () => {
        try {
            await adminApi.timeControl(
                {
                    year: year ? Number(year) : undefined,
                    month: month ? Number(month) : undefined,
                    startYear: startYear ? Number(startYear) : undefined,
                    locked,
                },
                worldId
            );
            toast.success('시간 설정이 변경되었습니다.');
        } catch {
            toast.error('변경 실패');
        }
    }, [year, month, startYear, locked, worldId]);

    const handleTurnTermChange = useCallback(
        async (minutes: number) => {
            try {
                await adminApi.timeControl({ turnTerm: minutes }, worldId);
                setTurnTerm(String(minutes));
                toast.success(`턴 시간이 ${minutes}분으로 변경되었습니다.`);
            } catch {
                toast.error('턴 시간 변경 실패');
            }
        },
        [worldId]
    );

    const handleCustomTurnTerm = useCallback(async () => {
        const minutes = Number(customTurnTerm);
        if (!minutes || minutes < 1 || minutes > 1440) {
            toast.error('1~1440분 사이의 값을 입력하세요.');
            return;
        }
        await handleTurnTermChange(minutes);
        setCustomTurnTerm('');
    }, [customTurnTerm, handleTurnTermChange]);

    const handleDistribute = useCallback(async () => {
        const gold = Number(goldAmount) || 0;
        const rice = Number(riceAmount) || 0;
        if (gold === 0 && rice === 0) {
            toast.error('자금 또는 물자 수량을 입력하세요.');
            return;
        }
        try {
            await adminApi.timeControl(
                {
                    distribute: { gold, rice, target: distributeTarget },
                },
                worldId
            );
            toast.success(
                `금 ${gold.toLocaleString()}, 쌀 ${rice.toLocaleString()} 지급 완료 (${distributeTarget === 'all' ? '전체 장교' : '진영별'})`
            );
            setGoldAmount('');
            setRiceAmount('');
        } catch {
            toast.error('지급 실패');
        }
    }, [goldAmount, riceAmount, distributeTarget, worldId]);

    const handleAuctionSync = useCallback(async () => {
        try {
            await adminApi.timeControl(
                {
                    auctionSync: auctionSyncEnabled,
                    auctionCloseMinutes: Number(auctionCloseMinutes) || 60,
                },
                worldId
            );
            toast.success('경매 시간 설정이 변경되었습니다.');
        } catch {
            toast.error('경매 시간 설정 실패');
        }
    }, [auctionSyncEnabled, auctionCloseMinutes, worldId]);

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
                            setReserveOpen(
                                String(cfg?.reserveOpen ?? '') || toLocalDatetime(cfg?.opentime as string | undefined)
                            );
                            setPreReserveOpen(
                                String(cfg?.preReserveOpen ?? '') ||
                                    toLocalDatetime(cfg?.startTime as string | undefined)
                            );
                            setAllowConscript(cfg?.allowConscript !== false);
                            setAllowNpcNationSpawn(cfg?.allowNpcNationSpawn !== false);
                            setAllowInvaderSpawn(cfg?.allowInvaderSpawn !== false);
                            setYear(String(d.currentWorld.year ?? ''));
                            setMonth(String(d.currentWorld.month ?? ''));
                            setStartYear(String(cfg?.startyear ?? ''));
                            setLastTurnTime(String(cfg?.turntime ?? ''));
                            setAuctionSyncEnabled(Boolean(cfg?.auctionSync));
                            setAuctionCloseMinutes(String(cfg?.auctionCloseMinutes ?? 60));
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
                extend: formExtend,
                npcMode: Number(formNpcMode),
                fiction: formFiction ? Number(formFiction) : undefined,
                isFiction: formIsFiction,
                maxGeneral: formMaxGeneral ? Number(formMaxGeneral) : undefined,
                maxNation: formMaxNation ? Number(formMaxNation) : undefined,
                joinMode: formJoinMode,
                blockGeneralCreate: formBlockGeneralCreate ? 1 : 0,
                showImgLevel: formShowImgLevel ? 1 : 0,
                realtimeMode: formRealtimeMode,
                commandPointRegenRate: formCommandPointRegenRate ? Number(formCommandPointRegenRate) : undefined,
                bettingActive: formBettingActive,
                tournamentAuto: formTournamentAuto,
                allowDomestic: formAllowDomestic,
                allowTeleport: formAllowTeleport,
                allowRecruit: formAllowRecruit,
                allowTraining: formAllowTraining,
                allowMoraleBoost: formAllowMoraleBoost,
                allowDispatch: formAllowDispatch,
                allowConscript: formAllowConscript,
                allowNpcNationSpawn: formAllowNpcNationSpawn,
                allowInvaderSpawn: formAllowInvaderSpawn,
                opentime: formOpentime ? new Date(formOpentime).toISOString() : undefined,
                startTime: formPreReserveOpen ? new Date(formPreReserveOpen).toISOString() : undefined,
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

    const handleToggleLock = async (wId: number, currentlyLocked: boolean) => {
        const next = !currentlyLocked;
        try {
            await adminApi.updateSettings({ locked: next }, wId);
            toast.success(next ? '턴 정지됨' : '턴 재개됨');
            loadWorlds();
        } catch {
            toast.error('턴 상태 변경 실패');
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
            await adminApi.resetWorld(resetTarget.id, resetScenario || undefined, newGameVersion.trim() || undefined, {
                extend: formExtend,
                npcMode: Number(formNpcMode),
                fiction: formFiction ? Number(formFiction) : undefined,
                isFiction: formIsFiction,
                maxGeneral: formMaxGeneral ? Number(formMaxGeneral) : undefined,
                maxNation: formMaxNation ? Number(formMaxNation) : undefined,
                joinMode: formJoinMode,
                blockGeneralCreate: formBlockGeneralCreate ? 1 : 0,
                showImgLevel: formShowImgLevel ? 1 : 0,
                realtimeMode: formRealtimeMode,
                commandPointRegenRate: formCommandPointRegenRate ? Number(formCommandPointRegenRate) : undefined,
                bettingActive: formBettingActive,
                tournamentAuto: formTournamentAuto,
                allowDomestic: formAllowDomestic,
                allowTeleport: formAllowTeleport,
                allowRecruit: formAllowRecruit,
                allowTraining: formAllowTraining,
                allowMoraleBoost: formAllowMoraleBoost,
                allowDispatch: formAllowDispatch,
                allowConscript: formAllowConscript,
                allowNpcNationSpawn: formAllowNpcNationSpawn,
                allowInvaderSpawn: formAllowInvaderSpawn,
                opentime: resetOpentime ? new Date(resetOpentime).toISOString() : undefined,
                startTime: resetStartTime ? new Date(resetStartTime).toISOString() : undefined,
            });
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
            toast.success('은하정세 로그가 추가되었습니다.');
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
                    opentime: reserveOpen ? new Date(reserveOpen).toISOString() : undefined,
                    startTime: preReserveOpen ? new Date(preReserveOpen).toISOString() : undefined,
                    allowConscript,
                    allowNpcNationSpawn,
                    allowInvaderSpawn,
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
            description: '행성 수입 처리 (자금/물자)',
        },
        {
            name: 'ProcessSemiAnnual',
            label: '반기 처리',
            description: '인구 변동, 기술 퇴화, 장교 수명 등',
        },
        {
            name: 'UpdateCitySupply',
            label: '행성 보급',
            description: '행성 물자 갱신',
        },
        {
            name: 'UpdateNationLevel',
            label: '진영 등급',
            description: '진영 등급 재계산',
        },
        {
            name: 'RandomizeCityTradeRate',
            label: '교역률 변경',
            description: '행성 교역률 무작위 변경',
        },
        {
            name: 'RaiseInvader',
            label: '이민족 침입',
            description: '이민족 NPC 진영 발생',
        },
        { name: 'RaiseNPCNation', label: 'NPC 건국', description: 'NPC 진영 생성' },
        {
            name: 'RegNeutralNPC',
            label: '재야 NPC 배치',
            description: '재야 NPC 장교를 빈 행성에 배치',
        },
        {
            name: 'NoticeToHistoryLog',
            label: '은하정세 기록',
            description: '은하정세에 메시지 기록',
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
                        <div className="p-4 border rounded-md space-y-4 bg-muted/20">
                            <h4 className="text-sm font-medium">새 월드 생성</h4>
                            {/* 기본 설정 */}
                            <div>
                                <p className="text-xs text-muted-foreground mb-2 font-medium">기본 설정</p>
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
                                        <label className="text-xs text-muted-foreground">턴 간격 (초, 예: 300)</label>
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
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">가오픈 일시</label>
                                        <input
                                            type="datetime-local"
                                            value={formPreReserveOpen}
                                            onChange={(e) => setFormPreReserveOpen(e.target.value)}
                                            className="w-full px-3 py-2 bg-background border border-input rounded-md text-sm"
                                        />
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">정식오픈 일시</label>
                                        <input
                                            type="datetime-local"
                                            value={formOpentime}
                                            onChange={(e) => setFormOpentime(e.target.value)}
                                            className="w-full px-3 py-2 bg-background border border-input rounded-md text-sm"
                                        />
                                    </div>
                                </div>
                            </div>
                            {/* 게임 설정 */}
                            <div>
                                <p className="text-xs text-muted-foreground mb-2 font-medium">게임 설정</p>
                                <div className="grid grid-cols-2 gap-3">
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">NPC 모드</label>
                                        <select
                                            value={formNpcMode}
                                            onChange={(e) => setFormNpcMode(e.target.value)}
                                            className="w-full px-3 py-2 bg-background border border-input rounded-md text-sm"
                                        >
                                            <option value="0">0 - 없음</option>
                                            <option value="1">1 - 빙의모드</option>
                                        </select>
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">가입 모드</label>
                                        <select
                                            value={formJoinMode}
                                            onChange={(e) => setFormJoinMode(e.target.value)}
                                            className="w-full px-3 py-2 bg-background border border-input rounded-md text-sm"
                                        >
                                            <option value="full">full - 전체</option>
                                            <option value="npc_only">npc_only - NPC만</option>
                                        </select>
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">최대 장교 수</label>
                                        <Input
                                            type="number"
                                            value={formMaxGeneral}
                                            onChange={(e) => setFormMaxGeneral(e.target.value)}
                                            placeholder="500"
                                        />
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">최대 진영 수</label>
                                        <Input
                                            type="number"
                                            value={formMaxNation}
                                            onChange={(e) => setFormMaxNation(e.target.value)}
                                            placeholder="55"
                                        />
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">가상 모드 (fiction)</label>
                                        <Input
                                            type="number"
                                            value={formFiction}
                                            onChange={(e) => setFormFiction(e.target.value)}
                                            placeholder="시나리오 기본값"
                                        />
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">커맨드 포인트 재생율</label>
                                        <Input
                                            type="number"
                                            value={formCommandPointRegenRate}
                                            onChange={(e) => setFormCommandPointRegenRate(e.target.value)}
                                            placeholder="기본값"
                                        />
                                    </div>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">확장 장교 활성화</span>
                                        <input
                                            type="checkbox"
                                            checked={formExtend}
                                            onChange={(e) => setFormExtend(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">가상 모드 (isFiction)</span>
                                        <input
                                            type="checkbox"
                                            checked={formIsFiction}
                                            onChange={(e) => setFormIsFiction(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">실시간 모드</span>
                                        <input
                                            type="checkbox"
                                            checked={formRealtimeMode}
                                            onChange={(e) => setFormRealtimeMode(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">베팅 활성화</span>
                                        <input
                                            type="checkbox"
                                            checked={formBettingActive}
                                            onChange={(e) => setFormBettingActive(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">토너먼트 자동</span>
                                        <input
                                            type="checkbox"
                                            checked={formTournamentAuto}
                                            onChange={(e) => setFormTournamentAuto(e.target.checked)}
                                        />
                                    </label>
                                </div>
                            </div>
                            {/* 제한 설정 */}
                            <div>
                                <p className="text-xs text-muted-foreground mb-2 font-medium">제한 설정</p>
                                <div className="grid grid-cols-2 gap-3">
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">장교 생성 차단</span>
                                        <input
                                            type="checkbox"
                                            checked={formBlockGeneralCreate}
                                            onChange={(e) => setFormBlockGeneralCreate(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">이미지 레벨 표시</span>
                                        <input
                                            type="checkbox"
                                            checked={formShowImgLevel}
                                            onChange={(e) => setFormShowImgLevel(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">징집 허용</span>
                                        <input
                                            type="checkbox"
                                            checked={formAllowConscript}
                                            onChange={(e) => setFormAllowConscript(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">NPC 진영 스폰</span>
                                        <input
                                            type="checkbox"
                                            checked={formAllowNpcNationSpawn}
                                            onChange={(e) => setFormAllowNpcNationSpawn(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">이민족 스폰</span>
                                        <input
                                            type="checkbox"
                                            checked={formAllowInvaderSpawn}
                                            onChange={(e) => setFormAllowInvaderSpawn(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">행성관리 허용</span>
                                        <input
                                            type="checkbox"
                                            checked={formAllowDomestic}
                                            onChange={(e) => setFormAllowDomestic(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">이동 허용</span>
                                        <input
                                            type="checkbox"
                                            checked={formAllowTeleport}
                                            onChange={(e) => setFormAllowTeleport(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">모집 허용</span>
                                        <input
                                            type="checkbox"
                                            checked={formAllowRecruit}
                                            onChange={(e) => setFormAllowRecruit(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">훈련 허용</span>
                                        <input
                                            type="checkbox"
                                            checked={formAllowTraining}
                                            onChange={(e) => setFormAllowTraining(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">사기 고양 허용</span>
                                        <input
                                            type="checkbox"
                                            checked={formAllowMoraleBoost}
                                            onChange={(e) => setFormAllowMoraleBoost(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">파견 허용</span>
                                        <input
                                            type="checkbox"
                                            checked={formAllowDispatch}
                                            onChange={(e) => setFormAllowDispatch(e.target.checked)}
                                        />
                                    </label>
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
                        <div className="p-4 border border-destructive/50 rounded-md space-y-4 bg-destructive/5">
                            <h4 className="text-sm font-medium flex items-center gap-2">
                                <RotateCcw className="size-4" />
                                월드 리셋: {resetTarget.name}
                            </h4>
                            <p className="text-xs text-muted-foreground">
                                시나리오를 선택하면 해당 시나리오로 월드가 초기화됩니다. 모든 진행 상황이 삭제됩니다.
                            </p>
                            {/* 기본 설정 */}
                            <div>
                                <p className="text-xs text-muted-foreground mb-2 font-medium">기본 설정</p>
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
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">가오픈 일시</label>
                                        <input
                                            type="datetime-local"
                                            value={resetStartTime}
                                            onChange={(e) => setResetStartTime(e.target.value)}
                                            className="w-full px-3 py-2 bg-background border border-input rounded-md text-sm"
                                        />
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">정식오픈 일시</label>
                                        <input
                                            type="datetime-local"
                                            value={resetOpentime}
                                            onChange={(e) => setResetOpentime(e.target.value)}
                                            className="w-full px-3 py-2 bg-background border border-input rounded-md text-sm"
                                        />
                                    </div>
                                </div>
                            </div>
                            {/* 게임 설정 */}
                            <div>
                                <p className="text-xs text-muted-foreground mb-2 font-medium">게임 설정</p>
                                <div className="grid grid-cols-2 gap-3">
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">NPC 모드</label>
                                        <select
                                            value={formNpcMode}
                                            onChange={(e) => setFormNpcMode(e.target.value)}
                                            className="w-full px-3 py-2 bg-background border border-input rounded-md text-sm"
                                        >
                                            <option value="0">0 - 없음</option>
                                            <option value="1">1 - 빙의모드</option>
                                        </select>
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">가입 모드</label>
                                        <select
                                            value={formJoinMode}
                                            onChange={(e) => setFormJoinMode(e.target.value)}
                                            className="w-full px-3 py-2 bg-background border border-input rounded-md text-sm"
                                        >
                                            <option value="full">full - 전체</option>
                                            <option value="npc_only">npc_only - NPC만</option>
                                        </select>
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">최대 장교 수</label>
                                        <Input
                                            type="number"
                                            value={formMaxGeneral}
                                            onChange={(e) => setFormMaxGeneral(e.target.value)}
                                            placeholder="500"
                                        />
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">최대 진영 수</label>
                                        <Input
                                            type="number"
                                            value={formMaxNation}
                                            onChange={(e) => setFormMaxNation(e.target.value)}
                                            placeholder="55"
                                        />
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">가상 모드 (fiction)</label>
                                        <Input
                                            type="number"
                                            value={formFiction}
                                            onChange={(e) => setFormFiction(e.target.value)}
                                            placeholder="시나리오 기본값"
                                        />
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">커맨드 포인트 재생율</label>
                                        <Input
                                            type="number"
                                            value={formCommandPointRegenRate}
                                            onChange={(e) => setFormCommandPointRegenRate(e.target.value)}
                                            placeholder="기본값"
                                        />
                                    </div>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">확장 장교 활성화</span>
                                        <input
                                            type="checkbox"
                                            checked={formExtend}
                                            onChange={(e) => setFormExtend(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">가상 모드 (isFiction)</span>
                                        <input
                                            type="checkbox"
                                            checked={formIsFiction}
                                            onChange={(e) => setFormIsFiction(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">실시간 모드</span>
                                        <input
                                            type="checkbox"
                                            checked={formRealtimeMode}
                                            onChange={(e) => setFormRealtimeMode(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">베팅 활성화</span>
                                        <input
                                            type="checkbox"
                                            checked={formBettingActive}
                                            onChange={(e) => setFormBettingActive(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">토너먼트 자동</span>
                                        <input
                                            type="checkbox"
                                            checked={formTournamentAuto}
                                            onChange={(e) => setFormTournamentAuto(e.target.checked)}
                                        />
                                    </label>
                                </div>
                            </div>
                            {/* 제한 설정 */}
                            <div>
                                <p className="text-xs text-muted-foreground mb-2 font-medium">제한 설정</p>
                                <div className="grid grid-cols-2 gap-3">
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">장교 생성 차단</span>
                                        <input
                                            type="checkbox"
                                            checked={formBlockGeneralCreate}
                                            onChange={(e) => setFormBlockGeneralCreate(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">이미지 레벨 표시</span>
                                        <input
                                            type="checkbox"
                                            checked={formShowImgLevel}
                                            onChange={(e) => setFormShowImgLevel(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">징집 허용</span>
                                        <input
                                            type="checkbox"
                                            checked={formAllowConscript}
                                            onChange={(e) => setFormAllowConscript(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">NPC 진영 스폰</span>
                                        <input
                                            type="checkbox"
                                            checked={formAllowNpcNationSpawn}
                                            onChange={(e) => setFormAllowNpcNationSpawn(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">이민족 스폰</span>
                                        <input
                                            type="checkbox"
                                            checked={formAllowInvaderSpawn}
                                            onChange={(e) => setFormAllowInvaderSpawn(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">행성관리 허용</span>
                                        <input
                                            type="checkbox"
                                            checked={formAllowDomestic}
                                            onChange={(e) => setFormAllowDomestic(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">이동 허용</span>
                                        <input
                                            type="checkbox"
                                            checked={formAllowTeleport}
                                            onChange={(e) => setFormAllowTeleport(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">모집 허용</span>
                                        <input
                                            type="checkbox"
                                            checked={formAllowRecruit}
                                            onChange={(e) => setFormAllowRecruit(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">훈련 허용</span>
                                        <input
                                            type="checkbox"
                                            checked={formAllowTraining}
                                            onChange={(e) => setFormAllowTraining(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">사기 고양 허용</span>
                                        <input
                                            type="checkbox"
                                            checked={formAllowMoraleBoost}
                                            onChange={(e) => setFormAllowMoraleBoost(e.target.checked)}
                                        />
                                    </label>
                                    <label className="flex items-center justify-between rounded-md border border-border px-3 py-2 text-sm">
                                        <span className="text-xs text-muted-foreground">파견 허용</span>
                                        <input
                                            type="checkbox"
                                            checked={formAllowDispatch}
                                            onChange={(e) => setFormAllowDispatch(e.target.checked)}
                                        />
                                    </label>
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
                                                <div className="flex flex-col gap-1">
                                                    <Badge variant={w.meta?.gatewayActive ? 'outline' : 'destructive'}>
                                                        {w.meta?.gatewayActive ? '운영중' : '비활성'}
                                                    </Badge>
                                                    {Boolean(
                                                        (w.config as Record<string, unknown> | undefined)?.locked
                                                    ) && (
                                                        <Badge
                                                            variant="outline"
                                                            className="text-orange-400 border-orange-400/40"
                                                        >
                                                            <Lock className="size-3 mr-0.5" />턴 정지
                                                        </Badge>
                                                    )}
                                                    {(() => {
                                                        const phase = w.meta?.phase as string | undefined;
                                                        const config = (w.config ?? {}) as Record<string, unknown>;
                                                        const now = new Date();
                                                        const startTime = config.startTime as string | undefined;
                                                        const opentime = config.opentime as string | undefined;
                                                        let phaseLabel = '';
                                                        let phaseColor = '';
                                                        if (w.meta?.finished || w.meta?.isFinished) {
                                                            phaseLabel = '종료';
                                                            phaseColor = 'text-gray-400';
                                                        } else if (phase === 'united') {
                                                            phaseLabel = '통일';
                                                            phaseColor = 'text-yellow-400';
                                                        } else if (phase === 'paused') {
                                                            phaseLabel = '정지';
                                                            phaseColor = 'text-red-400';
                                                        } else if (startTime && new Date(startTime) > now) {
                                                            phaseLabel = '폐쇄';
                                                            phaseColor = 'text-red-500';
                                                        } else if (
                                                            phase === 'pre_open' ||
                                                            phase === 'closed' ||
                                                            (opentime && new Date(opentime) > now)
                                                        ) {
                                                            phaseLabel = '가오픈';
                                                            phaseColor = 'text-orange-400';
                                                        } else {
                                                            phaseLabel = '오픈';
                                                            phaseColor = 'text-green-400';
                                                        }
                                                        return (
                                                            <>
                                                                <Badge
                                                                    variant="outline"
                                                                    className={`text-[10px] ${phaseColor}`}
                                                                >
                                                                    {phaseLabel}
                                                                </Badge>
                                                                {(phaseLabel === '폐쇄' || phaseLabel === '가오픈') &&
                                                                    (startTime || opentime) && (
                                                                        <div className="text-[9px] text-muted-foreground mt-0.5">
                                                                            {startTime && phaseLabel === '폐쇄' && (
                                                                                <div>
                                                                                    가오픈:{' '}
                                                                                    {new Date(startTime).toLocaleString(
                                                                                        'ko-KR'
                                                                                    )}
                                                                                </div>
                                                                            )}
                                                                            {opentime && (
                                                                                <div>
                                                                                    오픈:{' '}
                                                                                    {new Date(opentime).toLocaleString(
                                                                                        'ko-KR'
                                                                                    )}
                                                                                </div>
                                                                            )}
                                                                        </div>
                                                                    )}
                                                            </>
                                                        );
                                                    })()}
                                                </div>
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
                                                        variant={
                                                            Boolean(
                                                                (w.config as Record<string, unknown> | undefined)
                                                                    ?.locked
                                                            )
                                                                ? 'default'
                                                                : 'outline'
                                                        }
                                                        onClick={() =>
                                                            handleToggleLock(
                                                                w.id,
                                                                Boolean(
                                                                    (w.config as Record<string, unknown> | undefined)
                                                                        ?.locked
                                                                )
                                                            )
                                                        }
                                                    >
                                                        {Boolean(
                                                            (w.config as Record<string, unknown> | undefined)?.locked
                                                        ) ? (
                                                            <>
                                                                <Unlock className="size-3.5 mr-1" />턴 재개
                                                            </>
                                                        ) : (
                                                            <>
                                                                <Lock className="size-3.5 mr-1" />턴 정지
                                                            </>
                                                        )}
                                                    </Button>
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
                                <label className="text-sm text-muted-foreground">은하정세 추가</label>
                                <div className="flex gap-2">
                                    <Input
                                        value={logMessage}
                                        onChange={(e) => setLogMessage(e.target.value)}
                                        placeholder="은하정세 메시지 입력"
                                        onKeyDown={(e) => e.key === 'Enter' && handleWriteLog()}
                                    />
                                    <Button size="sm" variant="outline" onClick={handleWriteLog}>
                                        <MessageSquarePlus className="size-4 mr-1" />
                                        로그쓰기
                                    </Button>
                                </div>
                            </div>
                            <Card className="border-muted">
                                <CardHeader className="py-3">
                                    <CardTitle className="flex items-center gap-2 text-sm">
                                        <Coins className="size-4 text-amber-400" />
                                        자금물자 지급
                                    </CardTitle>
                                </CardHeader>
                                <CardContent className="space-y-3 pt-0">
                                    <div className="grid grid-cols-2 gap-3">
                                        <div className="space-y-1">
                                            <span className="text-xs text-muted-foreground flex items-center gap-1">
                                                <Coins className="size-3 text-amber-400" /> 금
                                            </span>
                                            <Input
                                                type="number"
                                                placeholder="0"
                                                value={goldAmount}
                                                onChange={(e) => setGoldAmount(e.target.value)}
                                            />
                                        </div>
                                        <div className="space-y-1">
                                            <span className="text-xs text-muted-foreground flex items-center gap-1">
                                                <Wheat className="size-3 text-green-400" /> 쌀
                                            </span>
                                            <Input
                                                type="number"
                                                placeholder="0"
                                                value={riceAmount}
                                                onChange={(e) => setRiceAmount(e.target.value)}
                                            />
                                        </div>
                                    </div>
                                    <div className="flex items-center gap-2">
                                        <div className="flex border border-gray-600 rounded-md overflow-hidden">
                                            {(['all', 'nations'] as const).map((target) => (
                                                <button
                                                    type="button"
                                                    key={target}
                                                    onClick={() => setDistributeTarget(target)}
                                                    className={`px-3 py-1.5 text-xs transition-colors ${distributeTarget === target ? 'bg-[#141c65] text-white' : 'text-gray-400 hover:text-white'}`}
                                                >
                                                    {target === 'all' ? '전체 장교' : '진영별'}
                                                </button>
                                            ))}
                                        </div>
                                    </div>
                                    <div className="flex items-center gap-2">
                                        <Button
                                            onClick={handleDistribute}
                                            variant="outline"
                                            size="sm"
                                            className="border-amber-500/40 text-amber-400 hover:bg-amber-500/10"
                                        >
                                            <Coins className="size-4 mr-1" /> 지급 실행
                                        </Button>
                                        <span className="text-xs text-muted-foreground flex items-center gap-1">
                                            <AlertTriangle className="size-3" /> 되돌릴 수 없습니다
                                        </span>
                                    </div>
                                </CardContent>
                            </Card>

                            <Card className="border-muted">
                                <CardHeader className="py-3">
                                    <CardTitle className="flex items-center gap-2 text-sm">
                                        <Gavel className="size-4 text-purple-400" />
                                        경매 시간 동기
                                    </CardTitle>
                                </CardHeader>
                                <CardContent className="space-y-3 pt-0">
                                    <div className="flex items-center gap-3">
                                        <button
                                            type="button"
                                            onClick={() => setAuctionSyncEnabled(!auctionSyncEnabled)}
                                            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm transition-colors ${auctionSyncEnabled ? 'bg-purple-500/20 text-purple-400 border border-purple-500/40' : 'bg-muted text-muted-foreground border border-muted'}`}
                                        >
                                            {auctionSyncEnabled ? '동기화 활성' : '동기화 비활성'}
                                        </button>
                                    </div>
                                    <div className="space-y-1">
                                        <span className="text-xs text-muted-foreground">경매 마감 시간 (분)</span>
                                        <Input
                                            type="number"
                                            value={auctionCloseMinutes}
                                            onChange={(e) => setAuctionCloseMinutes(e.target.value)}
                                            min={1}
                                            max={10080}
                                            className="w-40"
                                        />
                                    </div>
                                    <Button
                                        onClick={handleAuctionSync}
                                        variant="outline"
                                        size="sm"
                                        className="border-purple-500/40 text-purple-400 hover:bg-purple-500/10"
                                    >
                                        <Gavel className="size-4 mr-1" /> 경매 설정 적용
                                    </Button>
                                </CardContent>
                            </Card>

                            <div className="border-t pt-4 mt-4 space-y-3">
                                <h4 className="text-sm font-medium">게임 규칙</h4>
                                <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">최대 장교</label>
                                        <Input
                                            type="number"
                                            value={maxGeneral}
                                            onChange={(e) => setMaxGeneral(e.target.value)}
                                            placeholder="600"
                                        />
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs text-muted-foreground">최대 진영</label>
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
                                        <label className="text-xs text-muted-foreground">장교 생성</label>
                                        <select
                                            value={blockGeneralCreate}
                                            onChange={(e) => setBlockGeneralCreate(Number(e.target.value))}
                                            className="w-full px-3 py-2 bg-background border border-input rounded-md text-sm"
                                        >
                                            <option value={0}>가능</option>
                                            <option value={1}>불가</option>
                                            <option value={2}>장교명 무작위</option>
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
                                        {
                                            label: 'NPC 건국 이벤트',
                                            checked: allowNpcNationSpawn,
                                            onChange: setAllowNpcNationSpawn,
                                        },
                                        {
                                            label: '이민족 이벤트',
                                            checked: allowInvaderSpawn,
                                            onChange: setAllowInvaderSpawn,
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
                                            label: '행성 관리',
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
                                            label: '징집',
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
                                    <label className="text-xs text-muted-foreground">은하정세 기록</label>
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
                            <Card className="border-muted">
                                <CardHeader className="py-3">
                                    <CardTitle className="flex items-center gap-2 text-sm">
                                        <Activity className="size-4" />턴 데몬
                                    </CardTitle>
                                    <CardDescription className="flex items-center gap-2">
                                        상태:{' '}
                                        {(() => {
                                            const cfg = DAEMON_STATE_CONFIG[daemonState];
                                            if (!cfg) return <Badge variant="outline">{daemonState}</Badge>;
                                            const DIcon = cfg.icon;
                                            return (
                                                <span
                                                    className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs border ${cfg.color}`}
                                                >
                                                    <DIcon
                                                        className={`size-3 ${daemonState === 'RUNNING' ? 'animate-spin' : ''}`}
                                                    />
                                                    {cfg.label}
                                                </span>
                                            );
                                        })()}
                                        {daemonReason && (
                                            <span className="text-xs text-muted-foreground">({daemonReason})</span>
                                        )}
                                    </CardDescription>
                                </CardHeader>
                                <CardContent className="space-y-2 pt-0">
                                    <div className="flex gap-2">
                                        <Button
                                            variant="outline"
                                            size="sm"
                                            disabled={daemonLoading || daemonState === 'RUNNING'}
                                            onClick={() => handleDaemonAction('run')}
                                            className="border-green-500/40 text-green-400 hover:bg-green-500/10"
                                        >
                                            <Play className="size-3.5 mr-1" /> 수동 실행
                                        </Button>
                                        {daemonState === 'PAUSED' ? (
                                            <Button
                                                variant="outline"
                                                size="sm"
                                                disabled={daemonLoading}
                                                onClick={() => handleDaemonAction('resume')}
                                                className="border-blue-500/40 text-blue-400 hover:bg-blue-500/10"
                                            >
                                                <Play className="size-3.5 mr-1" /> 재개
                                            </Button>
                                        ) : (
                                            <Button
                                                variant="outline"
                                                size="sm"
                                                disabled={daemonLoading || daemonState === 'PAUSED'}
                                                onClick={() => handleDaemonAction('pause')}
                                                className="border-yellow-500/40 text-yellow-400 hover:bg-yellow-500/10"
                                            >
                                                <Pause className="size-3.5 mr-1" /> 일시정지
                                            </Button>
                                        )}
                                        <Button
                                            variant="ghost"
                                            size="sm"
                                            disabled={daemonLoading}
                                            onClick={fetchDaemonStatus}
                                        >
                                            <RotateCw className={`size-3.5 ${daemonLoading ? 'animate-spin' : ''}`} />
                                        </Button>
                                    </div>
                                </CardContent>
                            </Card>

                            <Card className="border-muted">
                                <CardHeader className="py-3">
                                    <CardTitle className="flex items-center gap-2 text-sm">
                                        <Clock className="size-4" />
                                        게임 시간
                                    </CardTitle>
                                    {lastTurnTime && <CardDescription>최근 갱신: {lastTurnTime}</CardDescription>}
                                </CardHeader>
                                <CardContent className="space-y-3 pt-0">
                                    <div className="grid grid-cols-3 gap-3">
                                        <div className="space-y-1">
                                            <span className="text-xs text-muted-foreground">시작 년도</span>
                                            <Input
                                                type="number"
                                                value={startYear}
                                                onChange={(e) => setStartYear(e.target.value)}
                                            />
                                        </div>
                                        <div className="space-y-1">
                                            <span className="text-xs text-muted-foreground">현재 년</span>
                                            <Input
                                                type="number"
                                                value={year}
                                                onChange={(e) => setYear(e.target.value)}
                                            />
                                        </div>
                                        <div className="space-y-1">
                                            <span className="text-xs text-muted-foreground">현재 월</span>
                                            <Input
                                                type="number"
                                                value={month}
                                                onChange={(e) => setMonth(e.target.value)}
                                                min={1}
                                                max={12}
                                            />
                                        </div>
                                    </div>
                                    <div className="flex items-center gap-3">
                                        <button
                                            type="button"
                                            onClick={() => setLocked(!locked)}
                                            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm transition-colors ${locked ? 'bg-red-500/20 text-red-400 border border-red-500/40' : 'bg-green-500/20 text-green-400 border border-green-500/40'}`}
                                        >
                                            {locked ? <Lock className="size-3.5" /> : <Unlock className="size-3.5" />}
                                            {locked ? '서버 잠금됨' : '서버 열림'}
                                        </button>
                                    </div>
                                    <Button
                                        onClick={handleTimeSubmit}
                                        size="sm"
                                        className="bg-red-400 hover:bg-red-500 text-white"
                                    >
                                        시간 설정 적용
                                    </Button>
                                </CardContent>
                            </Card>

                            <Card className="border-muted">
                                <CardHeader className="py-3">
                                    <CardTitle className="flex items-center gap-2 text-sm">
                                        <Timer className="size-4" />턴 시간 조정
                                    </CardTitle>
                                    <CardDescription>
                                        현재: <Badge variant="secondary">{turnTerm}분</Badge>
                                    </CardDescription>
                                </CardHeader>
                                <CardContent className="space-y-3 pt-0">
                                    <div className="flex flex-wrap gap-2">
                                        {TURN_PRESETS.map((min) => (
                                            <Button
                                                key={min}
                                                variant={turnTerm === String(min) ? 'default' : 'outline'}
                                                size="sm"
                                                onClick={() => handleTurnTermChange(min)}
                                            >
                                                {min}분턴
                                            </Button>
                                        ))}
                                    </div>
                                    <div className="flex items-center gap-2">
                                        <Input
                                            type="number"
                                            placeholder="분 단위 직접 입력 (1~1440)"
                                            value={customTurnTerm}
                                            onChange={(e) => setCustomTurnTerm(e.target.value)}
                                            min={1}
                                            max={1440}
                                            className="w-56"
                                        />
                                        <Button variant="outline" size="sm" onClick={handleCustomTurnTerm}>
                                            적용
                                        </Button>
                                    </div>
                                </CardContent>
                            </Card>

                            <Card className="border-muted">
                                <CardHeader className="py-3">
                                    <CardTitle className="flex items-center gap-2 text-sm">
                                        <Globe className="size-4" />
                                        서버 페이즈
                                    </CardTitle>
                                    <CardDescription>
                                        현재:{' '}
                                        <Badge variant="secondary">
                                            {(() => {
                                                const w = worlds.find((w) => w.id === worldId);
                                                const st = w?.config?.startTime as string | undefined;
                                                if (st && new Date(st) > new Date()) return '예약중';
                                                const opentime = w?.config?.opentime as string | undefined;
                                                if (opentime && new Date(opentime) > new Date()) return '가오픈';
                                                return '정식오픈';
                                            })()}
                                        </Badge>
                                    </CardDescription>
                                </CardHeader>
                                <CardContent className="space-y-3 pt-0">
                                    <div className="flex gap-2">
                                        <Button
                                            variant="outline"
                                            size="sm"
                                            onClick={async () => {
                                                const futureDate = new Date();
                                                futureDate.setFullYear(futureDate.getFullYear() + 1);
                                                try {
                                                    await adminApi.updateSettings(
                                                        { opentime: futureDate.toISOString() },
                                                        worldId
                                                    );
                                                    toast.success('가오픈 상태로 전환되었습니다.');
                                                    loadWorlds();
                                                } catch {
                                                    toast.error('페이즈 전환 실패');
                                                }
                                            }}
                                            className="border-orange-500/40 text-orange-400 hover:bg-orange-500/10"
                                        >
                                            가오픈 전환
                                        </Button>
                                        <Button
                                            variant="outline"
                                            size="sm"
                                            onClick={async () => {
                                                try {
                                                    await adminApi.updateSettings(
                                                        { opentime: new Date().toISOString() },
                                                        worldId
                                                    );
                                                    toast.success('정식오픈 상태로 전환되었습니다.');
                                                    loadWorlds();
                                                } catch {
                                                    toast.error('페이즈 전환 실패');
                                                }
                                            }}
                                            className="border-green-500/40 text-green-400 hover:bg-green-500/10"
                                        >
                                            정식오픈 전환
                                        </Button>
                                    </div>
                                    <p className="text-xs text-muted-foreground">
                                        가오픈: 장교 생성/삭제, 사전 거병만 가능. 정식오픈: 모든 기능 활성화.
                                    </p>
                                </CardContent>
                            </Card>

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
                                    variant="outline"
                                    onClick={async () => {
                                        if (
                                            !confirm(
                                                '강제 리홀: 천통 이후 40세 이상 장교의 명예의전당 기록과 상속 포인트를 재정산합니다. 계속할까요?'
                                            )
                                        )
                                            return;
                                        try {
                                            const res = await adminApi.forceRehall(worldId!);
                                            toast.success(
                                                `강제 리홀 완료 (장교 ${res.data.processedGenerals}명, 유저 ${res.data.updatedUsers}명)`
                                            );
                                        } catch {
                                            toast.error('강제 리홀 실패');
                                        }
                                    }}
                                >
                                    <Zap className="mr-1 size-4" /> 강제 리홀
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
