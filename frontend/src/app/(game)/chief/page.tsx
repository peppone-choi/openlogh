'use client';

import { useCallback, useEffect, useMemo, useState, type MouseEvent, type DragEvent } from 'react';
import {
    Crown,
    Users,
    GripVertical,
    ClipboardCopy,
    Copy,
    ClipboardPaste,
    Save,
    FolderOpen,
    Trash2,
} from 'lucide-react';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { generalApi, commandApi, nationApi, nationManagementApi, cityApi } from '@/lib/gameApi';
import type {
    CommandArg,
    General,
    Nation,
    City,
    NationTurn,
    CommandResult,
    CommandTableEntry,
    OfficerInfo,
} from '@/types';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { ResourceDisplay } from '@/components/game/resource-display';
import { GeneralPortrait } from '@/components/game/general-portrait';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { Badge } from '@/components/ui/8bit/badge';
import { Button } from '@/components/ui/8bit/button';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/8bit/tabs';
import { formatOfficerLevelText, CREW_TYPE_NAMES, REGION_NAMES } from '@/lib/game-utils';
import { CommandArgForm, COMMAND_ARGS } from '@/components/game/command-arg-form';

const CHIEF_STAT_MIN = 65;
const NATION_TURN_COUNT = 12;
const CHIEF_OFFICER_ORDER = [12, 10, 8, 6, 11, 9, 7, 5];

function getMinNationChiefLevel(nationLevel: number): number {
    // Nation level determines the minimum officer level available
    // Higher nation level => more officer slots
    if (nationLevel >= 7) return 5;
    if (nationLevel >= 6) return 5;
    if (nationLevel >= 5) return 7;
    if (nationLevel >= 4) return 7;
    if (nationLevel >= 3) return 9;
    return 9;
}

interface NationPreset {
    name: string;
    items: {
        offset: number;
        actionCode: string;
        arg?: CommandArg;
        brief?: string | null;
    }[];
}

function isCommandArg(value: unknown): value is CommandArg {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function toCommandArg(value: unknown): CommandArg | null {
    return isCommandArg(value) ? value : null;
}

function parseNationPresets(raw: string): NationPreset[] {
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed
        .map((entry): NationPreset | null => {
            const record = toCommandArg(entry);
            if (!record) return null;
            if (typeof record.name !== 'string' || !Array.isArray(record.items)) {
                return null;
            }
            const items = record.items
                .map((item): NationPreset['items'][number] | null => {
                    const row = toCommandArg(item);
                    if (!row) return null;
                    if (typeof row.offset !== 'number' || typeof row.actionCode !== 'string') {
                        return null;
                    }
                    if (row.arg !== undefined && row.arg !== null && !isCommandArg(row.arg)) {
                        return null;
                    }
                    if (row.brief !== undefined && row.brief !== null && typeof row.brief !== 'string') {
                        return null;
                    }
                    return {
                        offset: row.offset,
                        actionCode: row.actionCode,
                        arg: isCommandArg(row.arg) ? row.arg : undefined,
                        brief: typeof row.brief === 'string' || row.brief === null ? row.brief : undefined,
                    };
                })
                .filter((v): v is NationPreset['items'][number] => v !== null);
            return { name: record.name, items };
        })
        .filter((v): v is NationPreset => v !== null);
}

function formatTurnClock(raw?: string): string {
    if (!raw) return '-';
    const value = raw.includes('T') ? raw : raw.replace(' ', 'T');
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return raw;
    return date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
}

export default function ChiefPage() {
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const { myOfficer, fetchMyOfficer } = useOfficerStore();
    const [nation, setNation] = useState<Nation | null>(null);
    const [nationGenerals, setNationGenerals] = useState<General[]>([]);
    const [nationCities, setNationCities] = useState<City[]>([]);
    const [nationTurns, setNationTurns] = useState<NationTurn[]>([]);
    const [selectedCmd, setSelectedCmd] = useState<CommandTableEntry | null>(null);
    const [selectedNationSlots, setSelectedNationSlots] = useState<Set<number>>(new Set([0]));
    const [lastNationClickedSlot, setLastNationClickedSlot] = useState(0);
    const [showNationReserveForm, setShowNationReserveForm] = useState(false);
    const [reservingNation, setReservingNation] = useState(false);
    const [nationReserveResult, setNationReserveResult] = useState<CommandResult | null>(null);
    const [nationCommandTable, setNationCommandTable] = useState<Record<string, CommandTableEntry[]>>({});
    const [executing, setExecuting] = useState(false);
    const [cmdResult, setCmdResult] = useState<CommandResult | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [allOfficerTurns, setAllOfficerTurns] = useState<NationTurn[]>([]);
    const [allOfficerLoading, setAllOfficerLoading] = useState(false);
    const [officerOverview, setOfficerOverview] = useState<OfficerInfo[]>([]);

    // Drag & Drop for nation turns
    const [nationDragFrom, setNationDragFrom] = useState<number | null>(null);
    const [nationDragOver, setNationDragOver] = useState<number | null>(null);

    // Clipboard for nation turns
    const [nationClipboard, setNationClipboard] = useState<
        | {
              offset: number;
              actionCode: string;
              arg?: CommandArg;
              brief?: string | null;
          }[]
        | null
    >(null);

    // Preset save/load for nation commands
    const [nationPresets, setNationPresets] = useState<NationPreset[]>([]);
    const [selectedPreset, setSelectedPreset] = useState('');

    useEffect(() => {
        if (!currentWorld) return;
        fetchMyOfficer(currentWorld.id);
    }, [currentWorld, fetchMyOfficer]);

    const reload = useCallback(async () => {
        if (!myOfficer || myOfficer.officerLevel < 5) return;
        setLoading(true);
        try {
            const [natRes, gRes, tRes, cmdRes, cRes, oRes] = await Promise.all([
                nationApi.get(myOfficer.nationId),
                generalApi.listByNation(myOfficer.nationId),
                commandApi.getNationReserved(myOfficer.nationId, myOfficer.officerLevel),
                commandApi.getNationCommandTable(myOfficer.id),
                cityApi.listByNation(myOfficer.nationId),
                nationManagementApi.getOfficers(myOfficer.nationId),
            ]);
            setNation(natRes.data);
            setNationGenerals(gRes.data);
            setNationTurns(tRes.data);
            setNationCommandTable(cmdRes.data);
            setNationCities(cRes.data);
            setOfficerOverview(oRes.data);
        } catch {
            setError('데이터를 불러올 수 없습니다.');
        } finally {
            setLoading(false);
        }
    }, [myOfficer]);

    useEffect(() => {
        void reload();
    }, [reload]);

    const loadAllOfficerTurns = useCallback(async () => {
        if (!myOfficer?.nationId || !nation) return;
        setAllOfficerLoading(true);
        try {
            const minLv = getMinNationChiefLevel(nation.level);
            const levels: number[] = [];
            for (let lv = minLv; lv <= 12; lv++) levels.push(lv);
            const turns = await commandApi.getAllOfficerTurns(myOfficer.nationId, levels);
            setAllOfficerTurns(turns);
        } catch {
            /* ignore */
        } finally {
            setAllOfficerLoading(false);
        }
    }, [myOfficer?.nationId, nation]);

    // Auto-load all officer turns when nation data is ready
    useEffect(() => {
        if (nation && myOfficer?.nationId) {
            void loadAllOfficerTurns();
        }
    }, [nation, myOfficer?.nationId, loadAllOfficerTurns]);

    const getNationTurn = (idx: number) => nationTurns.find((turn) => turn.turnIdx === idx);

    // Preset localStorage key
    const nationPresetKey = myOfficer?.nationId ? `openlogh:nation-presets:${myOfficer.nationId}` : null;

    // Load presets from localStorage
    useEffect(() => {
        if (!nationPresetKey || typeof window === 'undefined') return;
        try {
            const raw = window.localStorage.getItem(nationPresetKey);
            if (raw) {
                setNationPresets(parseNationPresets(raw));
            }
        } catch {
            /* ignore */
        }
    }, [nationPresetKey]);

    const persistNationPresets = useCallback(
        (presets: NationPreset[]) => {
            setNationPresets(presets);
            if (nationPresetKey && typeof window !== 'undefined') {
                window.localStorage.setItem(nationPresetKey, JSON.stringify(presets));
            }
        },
        [nationPresetKey]
    );

    // Filled nation turns helper
    const filledNationTurns = useMemo(() => {
        return Array.from({ length: NATION_TURN_COUNT }, (_, idx) => {
            const turn = nationTurns.find((t) => t.turnIdx === idx);
            return {
                turnIdx: idx,
                actionCode: turn?.actionCode ?? '휴식',
                arg: turn?.arg ?? {},
                brief: turn?.brief ?? null,
            };
        });
    }, [nationTurns]);

    // Nation turn clipboard operations
    const nationCopySelected = useCallback(() => {
        const slots = [...selectedNationSlots].sort((a, b) => a - b);
        if (slots.length === 0) return;
        const min = slots[0];
        setNationClipboard(
            slots.map((idx) => {
                const t = filledNationTurns[idx];
                return {
                    offset: idx - min,
                    actionCode: t.actionCode,
                    arg: t.arg,
                    brief: t.brief,
                };
            })
        );
    }, [filledNationTurns, selectedNationSlots]);

    const nationPasteClipboard = useCallback(async () => {
        if (!nationClipboard || !myOfficer?.nationId) return;
        const slots = [...selectedNationSlots].sort((a, b) => a - b);
        if (slots.length === 0) return;
        const anchor = slots[0];
        const items = nationClipboard
            .map((item) => ({ ...item, target: anchor + item.offset }))
            .filter((item) => item.target >= 0 && item.target < NATION_TURN_COUNT);
        if (items.length === 0) return;
        const turns = items.map((item) => ({
            turnIdx: item.target,
            actionCode: item.actionCode,
            arg: item.arg,
        }));
        await commandApi.reserveNation(myOfficer.nationId, myOfficer.id, turns);
        await reload();
    }, [nationClipboard, myOfficer, selectedNationSlots, reload]);

    const nationCopyAsText = useCallback(() => {
        const slots = [...selectedNationSlots].sort((a, b) => a - b);
        if (slots.length === 0) return;
        const lines = slots.map((idx) => {
            const t = filledNationTurns[idx];
            const brief = t.brief?.replace(/<[^>]*>/g, '') ?? t.actionCode;
            return `${idx + 1}턴 ${brief}`;
        });
        void navigator.clipboard.writeText(lines.join('\n'));
    }, [filledNationTurns, selectedNationSlots]);

    // Save preset
    const saveNationPreset = useCallback(() => {
        const slots = [...selectedNationSlots].sort((a, b) => a - b);
        if (slots.length === 0) return;
        const min = slots[0];
        const items = slots.map((idx) => {
            const t = filledNationTurns[idx];
            return {
                offset: idx - min,
                actionCode: t.actionCode,
                arg: t.arg,
                brief: t.brief,
            };
        });
        const defaultName = items.map((i) => (i.actionCode === '휴식' ? '휴' : i.actionCode.charAt(0))).join('');
        const name = window.prompt('프리셋 이름을 입력하세요', defaultName);
        if (!name?.trim()) return;
        const trimmed = name.trim();
        const deduped = nationPresets.filter((p) => p.name !== trimmed);
        persistNationPresets([...deduped, { name: trimmed, items }]);
        setSelectedPreset(trimmed);
    }, [filledNationTurns, nationPresets, persistNationPresets, selectedNationSlots]);

    // Load preset
    const loadNationPreset = useCallback(async () => {
        if (!selectedPreset || !myOfficer?.nationId) return;
        const preset = nationPresets.find((p) => p.name === selectedPreset);
        if (!preset) return;
        const slots = [...selectedNationSlots].sort((a, b) => a - b);
        const anchor = slots.length > 0 ? slots[0] : 0;
        const items = preset.items
            .map((item) => ({ ...item, target: anchor + item.offset }))
            .filter((item) => item.target >= 0 && item.target < NATION_TURN_COUNT);
        if (items.length === 0) return;
        const turns = items.map((item) => ({
            turnIdx: item.target,
            actionCode: item.actionCode,
            arg: item.arg,
        }));
        await commandApi.reserveNation(myOfficer.nationId, myOfficer.id, turns);
        await reload();
    }, [selectedPreset, myOfficer, nationPresets, selectedNationSlots, reload]);

    const deleteNationPreset = useCallback(() => {
        if (!selectedPreset) return;
        persistNationPresets(nationPresets.filter((p) => p.name !== selectedPreset));
        setSelectedPreset('');
    }, [selectedPreset, nationPresets, persistNationPresets]);

    // Nation drag & drop handlers
    const handleNationDragStart = useCallback((idx: number, e: DragEvent<HTMLElement>) => {
        setNationDragFrom(idx);
        e.dataTransfer.effectAllowed = 'move';
        e.dataTransfer.setData('text/plain', String(idx));
    }, []);

    const handleNationDragOver = useCallback((idx: number, e: DragEvent<HTMLElement>) => {
        e.preventDefault();
        e.dataTransfer.dropEffect = 'move';
        setNationDragOver(idx);
    }, []);

    const handleNationDragLeave = useCallback(() => {
        setNationDragOver(null);
    }, []);

    const handleNationDrop = useCallback(
        async (targetIdx: number, e: DragEvent<HTMLElement>) => {
            e.preventDefault();
            setNationDragOver(null);
            const fromIdx = nationDragFrom;
            setNationDragFrom(null);
            if (fromIdx === null || fromIdx === targetIdx || !myOfficer?.nationId) return;

            const ordered = [...filledNationTurns];
            const [moved] = ordered.splice(fromIdx, 1);
            ordered.splice(targetIdx, 0, moved);

            const minIdx = Math.min(fromIdx, targetIdx);
            const maxIdx = Math.max(fromIdx, targetIdx);
            const turns = [];
            for (let i = minIdx; i <= maxIdx; i++) {
                turns.push({
                    turnIdx: i,
                    actionCode: ordered[i].actionCode,
                    arg: ordered[i].arg,
                });
            }
            await commandApi.reserveNation(myOfficer.nationId, myOfficer.id, turns);
            await reload();
            setSelectedNationSlots(new Set([targetIdx]));
            setLastNationClickedSlot(targetIdx);
        },
        [nationDragFrom, filledNationTurns, myOfficer, reload]
    );

    const handleNationDragEnd = useCallback(() => {
        setNationDragFrom(null);
        setNationDragOver(null);
    }, []);

    const handleNationSlotClick = (idx: number, e: MouseEvent<HTMLButtonElement>) => {
        if (e.shiftKey && lastNationClickedSlot !== idx) {
            const start = Math.min(lastNationClickedSlot, idx);
            const end = Math.max(lastNationClickedSlot, idx);
            const next = new Set<number>();
            for (let i = start; i <= end; i += 1) {
                next.add(i);
            }
            setSelectedNationSlots(next);
        } else if (e.ctrlKey || e.metaKey) {
            const next = new Set(selectedNationSlots);
            if (next.has(idx)) {
                next.delete(idx);
                if (next.size === 0) {
                    next.add(idx);
                }
            } else {
                next.add(idx);
            }
            setSelectedNationSlots(next);
        } else {
            setSelectedNationSlots(new Set([idx]));
            setShowNationReserveForm(true);
        }
        setLastNationClickedSlot(idx);
    };

    const handleNationReserve = async (actionCode: string, arg?: CommandArg) => {
        if (!myOfficer?.nationId) return;

        setReservingNation(true);
        setNationReserveResult(null);
        try {
            const turns = [...selectedNationSlots]
                .sort((a, b) => a - b)
                .map((turnIdx) => ({ turnIdx, actionCode, arg }));
            await commandApi.reserveNation(myOfficer.nationId, myOfficer.id, turns);
            await reload();
            setNationReserveResult({
                success: true,
                logs: ['국가 명령 예약을 저장했습니다.'],
            });
            setShowNationReserveForm(false);

            const maxSlot = Math.max(...selectedNationSlots);
            if (maxSlot < NATION_TURN_COUNT - 1) {
                setSelectedNationSlots(new Set([maxSlot + 1]));
                setLastNationClickedSlot(maxSlot + 1);
            }
        } catch {
            setNationReserveResult({
                success: false,
                logs: ['국가 명령 예약 저장에 실패했습니다.'],
            });
        } finally {
            setReservingNation(false);
        }
    };

    const nationCommandCategories = Object.keys(nationCommandTable);

    // City map for lookups
    const cityMap = useMemo(() => new Map(nationCities.map((c) => [c.id, c])), [nationCities]);

    const chiefOverviewRows = useMemo(() => {
        const grouped = new Map<number, OfficerInfo[]>();
        for (const officer of officerOverview) {
            const prev = grouped.get(officer.officerLevel) ?? [];
            prev.push(officer);
            grouped.set(officer.officerLevel, prev);
        }

        const turnMapByLevel = new Map<number, Map<number, NationTurn>>();
        for (const turn of allOfficerTurns) {
            const prev = turnMapByLevel.get(turn.officerLevel) ?? new Map<number, NationTurn>();
            prev.set(turn.turnIdx, turn);
            turnMapByLevel.set(turn.officerLevel, prev);
        }

        const levels = [...grouped.keys()].sort((a, b) => b - a);
        return levels.map((level) => {
            const officers = grouped.get(level) ?? [];
            const slotTurns = turnMapByLevel.get(level);
            const reservedCount = slotTurns
                ? Array.from(slotTurns.values()).filter((turn) => turn.actionCode !== '휴식').length
                : 0;

            return {
                level,
                officers: officers
                    .sort((a, b) => a.id - b.id)
                    .map((officer) => {
                        const matchedGeneral = nationGenerals.find((general) => general.id === officer.id);
                        return {
                            ...officer,
                            cityName: cityMap.get(officer.cityId)?.name ?? '-',
                            commandStatus:
                                reservedCount > 0 ? `예약 ${reservedCount}/${NATION_TURN_COUNT}` : '모두 휴식',
                            turnTime: formatTurnClock(matchedGeneral?.turnTime),
                        };
                    }),
            };
        });
    }, [allOfficerTurns, cityMap, nationGenerals, officerOverview]);

    if (!currentWorld) return <div className="p-4 text-muted-foreground">월드를 선택해주세요.</div>;
    if (!myOfficer) return <LoadingState />;
    if (myOfficer.officerLevel < 5)
        return <div className="p-4 text-muted-foreground">관직 Lv.5 이상만 사용 가능합니다.</div>;
    if (loading) return <LoadingState />;
    if (error) return <div className="p-4 text-destructive">{error}</div>;

    const npcGenerals = nationGenerals.filter((g) => g.npcState > 0);
    const playerGenerals = nationGenerals.filter((g) => g.npcState === 0);
    const totalCrew = nationGenerals.reduce((sum, g) => sum + g.crew, 0);
    const isChief = myOfficer.officerLevel === 20;

    return (
        <div className="p-4 space-y-4 max-w-3xl mx-auto">
            <PageHeader
                icon={Crown}
                title="사령부"
                description={`현재 턴: ${currentWorld.currentYear}년 ${currentWorld.currentMonth}월`}
            />

            <Tabs defaultValue="chief">
                <TabsList>
                    <TabsTrigger value="chief">사령부</TabsTrigger>
                    <TabsTrigger value="overview">전체 턴</TabsTrigger>
                    <TabsTrigger value="generals">소속 장수</TabsTrigger>
                </TabsList>

                {/* ===== Tab 1: Chief Center (사령부) ===== */}
                <TabsContent value="chief" className="mt-4 space-y-4">
                    {/* Nation Resources */}
                    {nation && (
                        <Card>
                            <CardHeader>
                                <CardTitle>{nation.name} 국가 자원</CardTitle>
                            </CardHeader>
                            <CardContent>
                                <ResourceDisplay gold={nation.gold} rice={nation.rice} crew={totalCrew} />
                            </CardContent>
                        </Card>
                    )}

                    <Tabs defaultValue="reservation" className="space-y-3">
                        <TabsList>
                            <TabsTrigger value="reservation">국가 명령 예약</TabsTrigger>
                            <TabsTrigger value="execute">즉시 실행</TabsTrigger>
                        </TabsList>

                        <TabsContent value="reservation" className="space-y-3 mt-0">
                            <Card>
                                <CardHeader>
                                    <CardTitle>국가 명령 예약 (12턴)</CardTitle>
                                </CardHeader>
                                <CardContent className="space-y-2">
                                    {/* Toolbar */}
                                    <div className="flex flex-wrap items-center gap-1.5">
                                        <Button
                                            size="sm"
                                            variant="outline"
                                            onClick={nationCopySelected}
                                            disabled={selectedNationSlots.size === 0}
                                        >
                                            <Copy className="size-3 mr-1" />
                                            복사
                                        </Button>
                                        <Button
                                            size="sm"
                                            variant="outline"
                                            onClick={() => void nationPasteClipboard()}
                                            disabled={!nationClipboard}
                                        >
                                            <ClipboardPaste className="size-3 mr-1" />
                                            붙여넣기
                                        </Button>
                                        <Button
                                            size="sm"
                                            variant="outline"
                                            onClick={nationCopyAsText}
                                            disabled={selectedNationSlots.size === 0}
                                        >
                                            <ClipboardCopy className="size-3 mr-1" />
                                            텍스트 복사
                                        </Button>
                                        <span className="text-[10px] text-muted-foreground">|</span>
                                        <Button
                                            size="sm"
                                            variant="outline"
                                            onClick={saveNationPreset}
                                            disabled={selectedNationSlots.size === 0}
                                        >
                                            <Save className="size-3 mr-1" />
                                            보관
                                        </Button>
                                        <select
                                            value={selectedPreset}
                                            onChange={(e) => setSelectedPreset(e.target.value)}
                                            className="h-8 min-w-[120px] rounded-none border border-input bg-background px-2 text-xs"
                                        >
                                            <option value="">프리셋 선택</option>
                                            {nationPresets.map((p) => (
                                                <option key={p.name} value={p.name}>
                                                    {p.name}
                                                </option>
                                            ))}
                                        </select>
                                        <Button
                                            size="sm"
                                            variant="outline"
                                            onClick={() => void loadNationPreset()}
                                            disabled={!selectedPreset}
                                        >
                                            <FolderOpen className="size-3 mr-1" />
                                            불러오기
                                        </Button>
                                        <Button
                                            size="sm"
                                            variant="outline"
                                            className="text-red-300"
                                            onClick={deleteNationPreset}
                                            disabled={!selectedPreset}
                                        >
                                            <Trash2 className="size-3 mr-1" />
                                            삭제
                                        </Button>
                                        <span className="text-[10px] text-muted-foreground">|</span>
                                        <Button
                                            size="sm"
                                            variant="ghost"
                                            className="h-6 text-[10px] px-1.5"
                                            onClick={() => {
                                                const s = new Set<number>();
                                                for (let i = 0; i < NATION_TURN_COUNT; i += 2) s.add(i);
                                                setSelectedNationSlots(s);
                                            }}
                                        >
                                            홀수턴
                                        </Button>
                                        <Button
                                            size="sm"
                                            variant="ghost"
                                            className="h-6 text-[10px] px-1.5"
                                            onClick={() => {
                                                const s = new Set<number>();
                                                for (let i = 1; i < NATION_TURN_COUNT; i += 2) s.add(i);
                                                setSelectedNationSlots(s);
                                            }}
                                        >
                                            짝수턴
                                        </Button>
                                        <Button
                                            size="sm"
                                            variant="ghost"
                                            className="h-6 text-[10px] px-1.5"
                                            onClick={() => {
                                                const s = new Set<number>();
                                                for (let i = 0; i < NATION_TURN_COUNT; i++) s.add(i);
                                                setSelectedNationSlots(s);
                                            }}
                                        >
                                            전체
                                        </Button>
                                    </div>

                                    <div className="space-y-[1px] bg-gray-600">
                                        {Array.from({ length: NATION_TURN_COUNT }, (_, n) => n).map((slot) => {
                                            const turn = getNationTurn(slot);
                                            const isSelected = selectedNationSlots.has(slot);
                                            const actionCode = turn?.actionCode ?? '휴식';
                                            const brief = turn?.brief;
                                            const isRest = actionCode === '휴식';
                                            const isDragTarget =
                                                nationDragOver === slot &&
                                                nationDragFrom !== null &&
                                                nationDragFrom !== slot;
                                            return (
                                                <div
                                                    key={slot}
                                                    className={`flex w-full items-center text-left text-xs transition-colors ${
                                                        isDragTarget
                                                            ? 'bg-[#1a3a2a] border-t-2 border-t-emerald-500'
                                                            : isSelected
                                                              ? 'bg-[#141c65] text-white'
                                                              : 'bg-[#111] hover:bg-[#191919]'
                                                    } ${nationDragFrom === slot ? 'opacity-40' : ''}`}
                                                >
                                                    <button
                                                        type="button"
                                                        draggable
                                                        onDragStart={(e) => handleNationDragStart(slot, e)}
                                                        onDragEnd={handleNationDragEnd}
                                                        className="flex items-center justify-center cursor-grab active:cursor-grabbing w-6 h-8 text-gray-500 hover:text-gray-300 shrink-0"
                                                        title="드래그하여 순서 변경"
                                                        aria-label="드래그하여 턴 순서 변경"
                                                    >
                                                        <GripVertical className="size-3" />
                                                    </button>
                                                    <button
                                                        type="button"
                                                        onClick={(e) => handleNationSlotClick(slot, e)}
                                                        onDragOver={(e) => handleNationDragOver(slot, e)}
                                                        onDragLeave={handleNationDragLeave}
                                                        onDrop={(e) => void handleNationDrop(slot, e)}
                                                        className="flex flex-1 items-center gap-2 px-1 py-1.5"
                                                    >
                                                        <span className="w-6 shrink-0 tabular-nums text-gray-400">
                                                            #{slot + 1}
                                                        </span>
                                                        <span
                                                            className={`shrink-0 border px-1 py-0 text-[10px] ${
                                                                isRest
                                                                    ? 'border-gray-600 text-gray-400'
                                                                    : 'border-cyan-700 text-cyan-300'
                                                            }`}
                                                        >
                                                            {actionCode}
                                                        </span>
                                                        {brief && (
                                                            <span className="flex-1 truncate text-gray-300">
                                                                {brief}
                                                            </span>
                                                        )}
                                                    </button>
                                                </div>
                                            );
                                        })}
                                    </div>

                                    <div className="text-[11px] text-gray-400">
                                        {selectedNationSlots.size > 1
                                            ? `${selectedNationSlots.size}개 턴 선택됨`
                                            : 'Shift+클릭: 범위선택, Ctrl/Cmd+클릭: 다중선택, 드래그: 순서변경'}
                                    </div>

                                    {showNationReserveForm && (
                                        <NationCommandSelectForm
                                            commandTable={nationCommandTable}
                                            reserving={reservingNation}
                                            onReserve={handleNationReserve}
                                            onCancel={() => setShowNationReserveForm(false)}
                                        />
                                    )}

                                    {nationReserveResult && (
                                        <div
                                            className={`p-3 rounded text-sm ${nationReserveResult.success ? 'bg-green-900/50 text-green-300' : 'bg-red-900/50 text-red-300'}`}
                                        >
                                            <Badge
                                                variant={nationReserveResult.success ? 'secondary' : 'destructive'}
                                                className="mb-2"
                                            >
                                                {nationReserveResult.success ? '성공' : '실패'}
                                            </Badge>
                                            {nationReserveResult.logs.map((log) => (
                                                <p key={log}>{log}</p>
                                            ))}
                                        </div>
                                    )}
                                </CardContent>
                            </Card>
                        </TabsContent>

                        <TabsContent value="execute" className="space-y-3 mt-0">
                            <Card>
                                <CardHeader>
                                    <CardTitle>국가 명령</CardTitle>
                                </CardHeader>
                                <CardContent className="space-y-3">
                                    <div className="flex flex-wrap gap-2">
                                        {nationCommandCategories.map((category) => (
                                            <div key={category} className="w-full">
                                                <div className="mb-1 text-xs text-gray-400">{category}</div>
                                                <div className="flex flex-wrap gap-2">
                                                    {(nationCommandTable[category] ?? []).map((cmd) => (
                                                        <Button
                                                            key={cmd.actionCode}
                                                            variant={
                                                                selectedCmd?.actionCode === cmd.actionCode
                                                                    ? 'default'
                                                                    : 'outline'
                                                            }
                                                            size="sm"
                                                            disabled={!cmd.enabled}
                                                            title={cmd.reason}
                                                            onClick={() => {
                                                                if (!cmd.enabled) return;
                                                                setSelectedCmd((prev) =>
                                                                    prev?.actionCode === cmd.actionCode ? null : cmd
                                                                );
                                                            }}
                                                        >
                                                            {cmd.name}
                                                        </Button>
                                                    ))}
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                    {selectedCmd && (
                                        <div className="rounded border p-4 text-sm space-y-3">
                                            <p className="text-muted-foreground">
                                                선택된 명령: <Badge>{selectedCmd.name}</Badge>
                                            </p>
                                            <p className="text-xs text-gray-400">
                                                소모: {selectedCmd.commandPointCost}CP / 실행 지연:{' '}
                                                {selectedCmd.durationSeconds}초
                                            </p>
                                            <Button
                                                onClick={async () => {
                                                    if (!myOfficer) return;
                                                    setExecuting(true);
                                                    setCmdResult(null);
                                                    try {
                                                        const { data } = await commandApi.executeNation(
                                                            myOfficer.id,
                                                            selectedCmd.actionCode
                                                        );
                                                        setCmdResult(data);
                                                    } catch {
                                                        setCmdResult({
                                                            success: false,
                                                            logs: ['실행 중 오류가 발생했습니다.'],
                                                        });
                                                    } finally {
                                                        setExecuting(false);
                                                    }
                                                }}
                                                disabled={executing}
                                                size="sm"
                                            >
                                                {executing ? '실행중...' : '명령 실행'}
                                            </Button>
                                            {cmdResult && (
                                                <div
                                                    className={`p-3 rounded text-sm ${cmdResult.success ? 'bg-green-900/50 text-green-300' : 'bg-red-900/50 text-red-300'}`}
                                                >
                                                    <Badge
                                                        variant={cmdResult.success ? 'secondary' : 'destructive'}
                                                        className="mb-2"
                                                    >
                                                        {cmdResult.success ? '성공' : '실패'}
                                                    </Badge>
                                                    {cmdResult.logs.map((log) => (
                                                        <p key={log}>{log}</p>
                                                    ))}
                                                </div>
                                            )}
                                        </div>
                                    )}
                                </CardContent>
                            </Card>
                        </TabsContent>
                    </Tabs>

                    <Card>
                        <CardHeader>
                            <CardTitle>수관 요약</CardTitle>
                        </CardHeader>
                        <CardContent className="space-y-3">
                            {chiefOverviewRows.length === 0 ? (
                                <p className="text-sm text-muted-foreground">표시할 수관 정보가 없습니다.</p>
                            ) : (
                                chiefOverviewRows.map((group) => (
                                    <div key={group.level} className="space-y-1.5">
                                        <div className="text-xs font-semibold text-muted-foreground">
                                            {formatOfficerLevelText(group.level, nation?.level, true, nation?.typeCode)}
                                        </div>
                                        <div className="space-y-1">
                                            {group.officers.map((officer) => (
                                                <div
                                                    key={officer.id}
                                                    className="flex flex-wrap items-center gap-2 rounded border border-gray-800 px-2 py-1.5 text-xs"
                                                >
                                                    <span className="font-medium">{officer.name}</span>
                                                    <span className="text-muted-foreground">
                                                        도시: {officer.cityName}
                                                    </span>
                                                    <span className="text-muted-foreground">
                                                        상태: {officer.commandStatus}
                                                    </span>
                                                    <span className="text-muted-foreground">
                                                        턴: {officer.turnTime}
                                                    </span>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                ))
                            )}
                        </CardContent>
                    </Card>
                </TabsContent>

                {/* ===== Tab: All Officer Turn Overview (전체 턴) ===== */}
                <TabsContent value="overview" className="mt-4 space-y-4">
                    <Card>
                        <CardHeader>
                            <div className="flex items-center justify-between">
                                <CardTitle className="flex items-center gap-2">
                                    <Users className="size-4" />
                                    전체 수관 턴 현황
                                </CardTitle>
                                <Button
                                    size="sm"
                                    variant="outline"
                                    onClick={() => void loadAllOfficerTurns()}
                                    disabled={allOfficerLoading}
                                >
                                    {allOfficerLoading ? '불러오는 중...' : '새로고침'}
                                </Button>
                            </div>
                        </CardHeader>
                        <CardContent>
                            {allOfficerTurns.length === 0 && !allOfficerLoading ? (
                                <div className="text-center text-sm text-muted-foreground py-4">
                                    <Button size="sm" onClick={() => void loadAllOfficerTurns()}>
                                        전체 턴 불러오기
                                    </Button>
                                </div>
                            ) : allOfficerLoading ? (
                                <LoadingState />
                            ) : (
                                <div className="space-y-3">
                                    {(() => {
                                        const officerOrder = CHIEF_OFFICER_ORDER.filter(
                                            (lv) => lv >= (nation ? getMinNationChiefLevel(nation.level) : 5)
                                        );
                                        const turnsByOfficer = new Map<number, NationTurn[]>();
                                        for (const t of allOfficerTurns) {
                                            const list = turnsByOfficer.get(t.officerLevel) ?? [];
                                            list.push(t);
                                            turnsByOfficer.set(t.officerLevel, list);
                                        }

                                        return officerOrder.map((lv) => {
                                            const officer = nationGenerals.find((g) => g.officerLevel === lv);
                                            const turns = turnsByOfficer.get(lv) ?? [];
                                            const turnMap = new Map(turns.map((t) => [t.turnIdx, t]));
                                            const isMe = lv === myOfficer.officerLevel;
                                            const reservedCount = turns.filter((t) => t.actionCode !== '휴식').length;

                                            return (
                                                <Card key={lv} className={isMe ? 'border-blue-700/50' : ''}>
                                                    <CardHeader className="py-2 px-3">
                                                        <div className="flex items-center gap-2">
                                                            {officer && (
                                                                <GeneralPortrait
                                                                    picture={officer.picture}
                                                                    name={officer.name}
                                                                    size="sm"
                                                                />
                                                            )}
                                                            <div className="leading-tight">
                                                                <div
                                                                    className={`text-sm font-medium ${isMe ? 'text-yellow-300' : ''}`}
                                                                >
                                                                    {nation
                                                                        ? formatOfficerLevelText(
                                                                              lv,
                                                                              nation.level,
                                                                              true,
                                                                              nation.typeCode
                                                                          )
                                                                        : `Lv.${lv}`}
                                                                </div>
                                                                <div className="text-[10px] text-muted-foreground">
                                                                    {officer?.name ?? '(공석)'}
                                                                    {reservedCount > 0 && (
                                                                        <span className="ml-1 text-cyan-400">
                                                                            예약 {reservedCount}/{NATION_TURN_COUNT}
                                                                        </span>
                                                                    )}
                                                                </div>
                                                            </div>
                                                            {isMe && (
                                                                <Badge
                                                                    variant="secondary"
                                                                    className="text-[9px] ml-auto"
                                                                >
                                                                    나
                                                                </Badge>
                                                            )}
                                                        </div>
                                                    </CardHeader>
                                                    <CardContent className="px-3 pb-2">
                                                        <div className="grid grid-cols-6 gap-[2px]">
                                                            {Array.from({ length: NATION_TURN_COUNT }, (_, i) => i).map(
                                                                (turnIdx) => {
                                                                    const turn = turnMap.get(turnIdx);
                                                                    const code = turn?.actionCode ?? '휴식';
                                                                    const isRest = code === '휴식';
                                                                    return (
                                                                        <div
                                                                            key={turnIdx}
                                                                            className="text-center"
                                                                            title={turn?.brief ?? code}
                                                                        >
                                                                            <div className="text-[9px] text-muted-foreground">
                                                                                {turnIdx + 1}턴
                                                                            </div>
                                                                            <span
                                                                                className={`inline-block w-full px-0.5 py-0.5 rounded text-[10px] leading-tight ${
                                                                                    isRest
                                                                                        ? 'text-gray-500'
                                                                                        : 'bg-cyan-950/50 text-cyan-300 border border-cyan-800/50'
                                                                                }`}
                                                                            >
                                                                                {code}
                                                                            </span>
                                                                        </div>
                                                                    );
                                                                }
                                                            )}
                                                        </div>
                                                    </CardContent>
                                                </Card>
                                            );
                                        });
                                    })()}
                                </div>
                            )}
                        </CardContent>
                    </Card>
                </TabsContent>

                {/* ===== Tab 3: Generals List ===== */}
                <TabsContent value="generals" className="mt-4">
                    <Card>
                        <CardHeader>
                            <CardTitle className="flex items-center gap-2">
                                <Users className="size-4" />
                                소속 장수 (플레이어 {playerGenerals.length}명 / NPC {npcGenerals.length}명)
                            </CardTitle>
                        </CardHeader>
                        <CardContent>
                            <div className="overflow-x-auto">
                                <table className="w-full text-sm">
                                    <thead>
                                        <tr className="border-b border-gray-700 text-xs text-muted-foreground">
                                            <th className="px-2 py-1 text-left">장수</th>
                                            <th className="px-2 py-1 text-left">관직</th>
                                            <th className="px-2 py-1 text-left">도시</th>
                                            <th className="px-2 py-1 text-right">병력</th>
                                            <th className="px-2 py-1 text-right">병종</th>
                                            <th className="px-2 py-1 text-right">훈련</th>
                                            <th className="px-2 py-1 text-right">사기</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {nationGenerals.map((g) => {
                                            const city = cityMap.get(g.cityId);
                                            return (
                                                <tr key={g.id} className="border-b border-gray-800">
                                                    <td className="px-2 py-1">
                                                        <div className="flex items-center gap-2">
                                                            <GeneralPortrait
                                                                picture={g.picture}
                                                                name={g.name}
                                                                size="sm"
                                                            />
                                                            <span className="font-medium truncate max-w-[80px]">
                                                                {g.name}
                                                            </span>
                                                            {g.npcState > 0 && (
                                                                <Badge variant="outline" className="text-[10px] px-1">
                                                                    NPC
                                                                </Badge>
                                                            )}
                                                        </div>
                                                    </td>
                                                    <td className="px-2 py-1 text-xs">
                                                        {formatOfficerLevelText(
                                                            g.officerLevel,
                                                            nation?.level,
                                                            true,
                                                            nation?.typeCode,
                                                            g.npcState
                                                        )}
                                                    </td>
                                                    <td className="px-2 py-1 text-xs">{city?.name ?? '-'}</td>
                                                    <td className="px-2 py-1 text-xs text-right tabular-nums">
                                                        {g.crew.toLocaleString()}
                                                    </td>
                                                    <td className="px-2 py-1 text-xs text-right">
                                                        {CREW_TYPE_NAMES[g.crewType] ?? g.crewType}
                                                    </td>
                                                    <td className="px-2 py-1 text-xs text-right tabular-nums">
                                                        {g.train}
                                                    </td>
                                                    <td className="px-2 py-1 text-xs text-right tabular-nums">
                                                        {g.atmos}
                                                    </td>
                                                </tr>
                                            );
                                        })}
                                    </tbody>
                                </table>
                            </div>
                        </CardContent>
                    </Card>
                </TabsContent>
            </Tabs>
        </div>
    );
}

interface NationCommandSelectFormProps {
    commandTable: Record<string, CommandTableEntry[]>;
    reserving: boolean;
    onReserve: (actionCode: string, arg?: CommandArg) => void;
    onCancel: () => void;
}

function NationCommandSelectForm({ commandTable, reserving, onReserve, onCancel }: NationCommandSelectFormProps) {
    const [selectedCmd, setSelectedCmd] = useState('');
    const [pendingArg, setPendingArg] = useState<CommandArg | undefined>();

    const categories = Object.keys(commandTable);
    const hasArgForm = !!(selectedCmd && COMMAND_ARGS[selectedCmd]);

    const selectedEntry = useMemo(() => {
        for (const list of Object.values(commandTable)) {
            const found = list.find((cmd) => cmd.actionCode === selectedCmd);
            if (found) return found;
        }
        return null;
    }, [commandTable, selectedCmd]);

    const handleReserve = () => {
        if (!selectedCmd) return;
        onReserve(selectedCmd, pendingArg);
    };

    return (
        <Card className="border-amber-400/30">
            <CardContent className="space-y-3 pt-3">
                <Tabs defaultValue={categories[0] ?? ''}>
                    <TabsList className="flex-wrap h-auto">
                        {categories.map((cat) => (
                            <TabsTrigger key={cat} value={cat} className="text-xs">
                                {cat}
                            </TabsTrigger>
                        ))}
                    </TabsList>
                    {categories.map((cat) => (
                        <TabsContent key={cat} value={cat}>
                            <div className="flex flex-wrap gap-1">
                                {commandTable[cat].map((cmd) => (
                                    <Badge
                                        key={cmd.actionCode}
                                        variant={selectedCmd === cmd.actionCode ? 'default' : 'secondary'}
                                        className={`cursor-pointer text-xs ${
                                            !cmd.enabled ? 'opacity-40 cursor-not-allowed' : ''
                                        }`}
                                        title={cmd.reason ?? undefined}
                                        onClick={() => {
                                            if (!cmd.enabled) return;
                                            setSelectedCmd(cmd.actionCode);
                                            setPendingArg(undefined);
                                        }}
                                    >
                                        {cmd.name}
                                    </Badge>
                                ))}
                            </div>
                        </TabsContent>
                    ))}
                </Tabs>

                {selectedEntry && (
                    <div className="rounded border p-3 text-xs text-muted-foreground">
                        <p>
                            소모: {selectedEntry.commandPointCost}CP / 실행 지연: {selectedEntry.durationSeconds}초
                        </p>
                    </div>
                )}

                {hasArgForm && <CommandArgForm actionCode={selectedCmd} onSubmit={setPendingArg} />}

                <div className="flex gap-2">
                    <Button
                        size="sm"
                        onClick={handleReserve}
                        disabled={!selectedCmd || reserving || (hasArgForm && !pendingArg)}
                    >
                        {reserving ? '저장중...' : '예약'}
                    </Button>
                    <Button size="sm" variant="ghost" onClick={onCancel}>
                        취소
                    </Button>
                </div>
            </CardContent>
        </Card>
    );
}
