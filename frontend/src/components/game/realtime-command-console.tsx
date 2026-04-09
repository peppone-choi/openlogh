'use client';

import { useCallback, useEffect, useState } from 'react';
import { Swords, Clock3 } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { commandApi } from '@/lib/gameApi';
import { subscribeWebSocket } from '@/lib/websocket';
import { useDebouncedCallback } from '@/hooks/useDebouncedCallback';
import { CpDisplay } from '@/components/game/cp-display';
import { PositionCardPanel } from '@/components/game/position-card-panel';
import { CommandExecutionPanel } from '@/components/game/command-execution-panel';
import { LoadingState } from '@/components/game/loading-state';
import { useOfficerStore } from '@/stores/officerStore';
import type { CommandTableEntry } from '@/types';
import type { Officer } from '@/types/officer';

type CommandGroup =
    | 'OPERATION'
    | 'PERSONAL'
    | 'COMMAND'
    | 'LOGISTICS'
    | 'PERSONNEL'
    | 'POLITICS'
    | 'INTELLIGENCE';

interface RealtimeCommandConsoleProps {
    officer: Officer;
    sessionId: number;
    regenRate?: number;
}

/**
 * Drop-in realtime command console for the dashboard sidebar. Replaces the
 * legacy turn-queue CommandPanel with CP display + position card filter +
 * immediate-execute command list.
 */
export function RealtimeCommandConsole({
    officer,
    sessionId,
    regenRate = 1,
}: RealtimeCommandConsoleProps) {
    const [selectedGroup, setSelectedGroup] = useState<CommandGroup | null>(null);
    const [commandEntries, setCommandEntries] = useState<CommandTableEntry[]>([]);
    const [loadingCommands, setLoadingCommands] = useState(false);
    const [serverClock, setServerClock] = useState('');
    const fetchMyOfficer = useOfficerStore((s) => s.fetchMyOfficer);

    // Tick clock every second
    useEffect(() => {
        const update = () => {
            setServerClock(
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

    const fetchCommands = useCallback(async () => {
        setLoadingCommands(true);
        try {
            const { data } = await commandApi.getCommandTable(officer.id);
            const flat: CommandTableEntry[] = Object.values(data).flat();
            setCommandEntries(flat);
        } catch (err) {
            console.error('Failed to fetch command table:', err);
        } finally {
            setLoadingCommands(false);
        }
    }, [officer.id]);

    useEffect(() => {
        fetchCommands().catch(() => {});
    }, [fetchCommands]);

    const debouncedRefresh = useDebouncedCallback(
        useCallback(() => {
            fetchMyOfficer(sessionId).catch(() => {});
            fetchCommands().catch(() => {});
        }, [sessionId, fetchMyOfficer, fetchCommands]),
        500
    );

    useEffect(() => {
        const unsubTurn = subscribeWebSocket(`/topic/world/${sessionId}/turn`, debouncedRefresh);
        const unsubCommand = subscribeWebSocket(`/topic/world/${sessionId}/command`, debouncedRefresh);
        const unsubEvents = subscribeWebSocket(`/topic/world/${sessionId}/events`, debouncedRefresh);
        return () => {
            unsubTurn();
            unsubCommand();
            unsubEvents();
        };
    }, [sessionId, debouncedRefresh]);

    return (
        <Card className="border-gray-700" data-tutorial="command-panel">
            <CardHeader className="space-y-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                    <CardTitle className="flex items-center gap-1.5 text-base text-[#00d4ff]">
                        <Swords className="size-4" />
                        커맨드 콘솔
                    </CardTitle>
                    <span className="flex items-center gap-1 text-xs text-muted-foreground">
                        <Clock3 className="size-3.5 text-amber-300" />
                        {serverClock}
                    </span>
                </div>
                <CpDisplay
                    pcpCurrent={officer.pcpPool}
                    pcpMax={officer.pcpMax}
                    mcpCurrent={officer.mcpPool}
                    mcpMax={officer.mcpMax}
                    regenRate={regenRate}
                />
                <PositionCardPanel
                    positionCards={officer.positionCards}
                    selectedGroup={selectedGroup}
                    onSelectGroup={setSelectedGroup}
                />
            </CardHeader>
            <CardContent className="p-0">
                {loadingCommands ? (
                    <LoadingState message="명령 목록 불러오는 중..." />
                ) : (
                    <CommandExecutionPanel
                        commands={commandEntries}
                        officerId={officer.id}
                        sessionId={sessionId}
                        pcpCurrent={officer.pcpPool}
                        mcpCurrent={officer.mcpPool}
                        selectedGroup={selectedGroup}
                        onResult={() => {
                            fetchMyOfficer(sessionId).catch(() => {});
                        }}
                    />
                )}
            </CardContent>
        </Card>
    );
}
