'use client';

import { useEffect, useState, useCallback, Suspense } from 'react';
import { useRouter } from 'next/navigation';
import { Clock, Swords } from 'lucide-react';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { EmptyState } from '@/components/game/empty-state';
import { OfficerInfoPanel } from '@/components/game/officer-info-panel';
import { CpDisplay } from '@/components/game/cp-display';
import { PositionCardPanel } from '@/components/game/position-card-panel';
import { CommandExecutionPanel } from '@/components/game/command-execution-panel';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { commandApi, generalApi } from '@/lib/gameApi';
import { subscribeWebSocket } from '@/lib/websocket';
import { useDebouncedCallback } from '@/hooks/useDebouncedCallback';
import type { CommandTableEntry } from '@/types';
import type { Officer, OfficerSummary } from '@/types/officer';

// CommandGroup as string union — matches legacy CommandTableEntry.commandGroup field
type CommandGroup =
    | 'OPERATION'
    | 'PERSONAL'
    | 'COMMAND'
    | 'LOGISTICS'
    | 'PERSONNEL'
    | 'POLITICS'
    | 'INTELLIGENCE';

/** Server clock — displays current local time */
function ServerClock() {
    const [time, setTime] = useState('');
    useEffect(() => {
        const update = () => {
            setTime(
                new Date().toLocaleTimeString('ko-KR', {
                    hour: '2-digit',
                    minute: '2-digit',
                    second: '2-digit',
                    hour12: false,
                })
            );
        };
        update();
        const id = setInterval(update, 1000);
        return () => clearInterval(id);
    }, []);
    return (
        <span className="flex items-center gap-1 text-xs text-muted-foreground">
            <Clock className="size-3" />
            {time}
        </span>
    );
}

export default function CommandsPage() {
    return (
        <Suspense fallback={<LoadingState message="명령 정보를 불러오는 중..." />}>
            <CommandsPageInner />
        </Suspense>
    );
}

function CommandsPageInner() {
    const router = useRouter();
    const currentWorld = useWorldStore((s) => s.currentWorld);
    const { myOfficer: myOfficerRaw, fetchMyOfficer } = useOfficerStore();
    // Cast to gin7 Officer (8-stat) — store uses legacy General type alias but API returns Officer
    const myOfficer = myOfficerRaw as unknown as Officer | null;

    const [selectedGroup, setSelectedGroup] = useState<CommandGroup | null>(null);
    const [commandEntries, setCommandEntries] = useState<CommandTableEntry[]>([]);
    const [colocatedOfficers, setColocatedOfficers] = useState<OfficerSummary[]>([]);
    const [loadingCommands, setLoadingCommands] = useState(false);

    // Fetch my officer on mount
    useEffect(() => {
        if (!currentWorld || myOfficer) return;
        fetchMyOfficer(currentWorld.id).catch(() => {});
    }, [currentWorld, myOfficer, fetchMyOfficer]);

    // Fetch command table for my officer
    const fetchCommands = useCallback(async () => {
        if (!myOfficer) return;
        setLoadingCommands(true);
        try {
            const { data } = await commandApi.getCommandTable(myOfficer.id);
            // Flatten the grouped table (Record<string, CommandTableEntry[]>) into a flat list
            const flat: CommandTableEntry[] = Object.values(data).flat();
            setCommandEntries(flat);
        } catch (err) {
            console.error('Failed to fetch command table:', err);
        } finally {
            setLoadingCommands(false);
        }
    }, [myOfficer]);

    useEffect(() => {
        fetchCommands().catch(() => {});
    }, [fetchCommands]);

    // Fetch co-located officers (same planet)
    const fetchColocated = useCallback(async () => {
        if (!myOfficer || !currentWorld) return;
        try {
            const { data } = await generalApi.listByCity(myOfficer.planetId);
            // Filter out self; cast to OfficerSummary since gin7 Officer is returned
            const others = (data as unknown as OfficerSummary[]).filter((o) => o.id !== myOfficer.id);
            setColocatedOfficers(others);
        } catch (err) {
            console.error('Failed to fetch co-located officers:', err);
        }
    }, [myOfficer, currentWorld]);

    useEffect(() => {
        fetchColocated().catch(() => {});
    }, [fetchColocated]);

    // Refresh on WebSocket events
    const debouncedRefresh = useDebouncedCallback(
        useCallback(() => {
            if (!currentWorld) return;
            fetchMyOfficer(currentWorld.id).catch(() => {});
            fetchCommands().catch(() => {});
        }, [currentWorld, fetchMyOfficer, fetchCommands]),
        500
    );

    useEffect(() => {
        if (!currentWorld) return;
        const unsubTurn = subscribeWebSocket(`/topic/world/${currentWorld.id}/turn`, debouncedRefresh);
        const unsubCommand = subscribeWebSocket(`/topic/world/${currentWorld.id}/command`, debouncedRefresh);
        const unsubEvents = subscribeWebSocket(`/topic/world/${currentWorld.id}/events`, debouncedRefresh);
        return () => {
            unsubTurn();
            unsubCommand();
            unsubEvents();
        };
    }, [currentWorld, debouncedRefresh]);

    if (!currentWorld) {
        return (
            <div className="p-4">
                <EmptyState title="월드를 선택해주세요" description="명령 실행은 월드 진입 후 이용할 수 있습니다." />
            </div>
        );
    }

    if (!myOfficer) {
        return <LoadingState message="장교 정보를 불러오는 중..." />;
    }

    const regenRate = currentWorld.commandPointRegenRate ?? 1;

    return (
        <div className="flex h-full min-h-screen bg-slate-950 text-white">
            {/* Left sidebar: officer info + CP display */}
            <aside className="w-64 shrink-0 flex flex-col gap-3 p-3 border-r border-slate-800">
                <div className="flex items-center justify-between mb-1">
                    <PageHeader icon={Swords} title="명령" description="" />
                    <ServerClock />
                </div>
                <OfficerInfoPanel officer={myOfficer} />
                <CpDisplay
                    pcpCurrent={myOfficer.pcpPool}
                    pcpMax={myOfficer.pcpMax}
                    mcpCurrent={myOfficer.mcpPool}
                    mcpMax={myOfficer.mcpMax}
                    regenRate={regenRate}
                />
            </aside>

            {/* Center: position card tabs + command list */}
            <main className="flex-1 flex flex-col min-w-0">
                {/* Position card tabs */}
                <div className="border-b border-slate-800 p-2">
                    <PositionCardPanel
                        positionCards={myOfficer.positionCards}
                        selectedGroup={selectedGroup}
                        onSelectGroup={setSelectedGroup}
                    />
                </div>

                {/* Command list */}
                <div className="flex-1 overflow-y-auto">
                    {loadingCommands ? (
                        <LoadingState message="명령 목록 불러오는 중..." />
                    ) : (
                        <CommandExecutionPanel
                            commands={commandEntries}
                            officerId={myOfficer.id}
                            sessionId={currentWorld.id}
                            pcpCurrent={myOfficer.pcpPool}
                            mcpCurrent={myOfficer.mcpPool}
                            selectedGroup={selectedGroup}
                            onResult={() => {
                                // Refresh officer CP after executing a command
                                fetchMyOfficer(currentWorld.id).catch(() => {});
                            }}
                        />
                    )}
                </div>
            </main>

            {/* Right sidebar: co-located officers (동스폿) */}
            <aside className="w-48 shrink-0 flex flex-col border-l border-slate-800 p-3">
                <h3 className="text-xs font-bold text-slate-400 mb-2 uppercase tracking-wider">동스폿 장교</h3>
                {colocatedOfficers.length === 0 ? (
                    <p className="text-[11px] text-slate-600">같은 행성에 다른 장교가 없습니다.</p>
                ) : (
                    <ul className="space-y-1">
                        {colocatedOfficers.map((officer) => (
                            <li key={officer.id}>
                                <button
                                    type="button"
                                    onClick={() => router.push(`/generals/${officer.id}`)}
                                    className="w-full text-left px-2 py-1.5 rounded text-xs bg-slate-800 hover:bg-slate-700 transition-colors"
                                >
                                    <div className="text-white font-medium truncate">{officer.name}</div>
                                    <div className="text-slate-500 text-[10px] truncate">{officer.rankTitle}</div>
                                </button>
                            </li>
                        ))}
                    </ul>
                )}
            </aside>
        </div>
    );
}
