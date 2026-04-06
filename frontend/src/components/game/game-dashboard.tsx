'use client';

import { useEffect, useRef, useState, useCallback, useMemo } from 'react';
import { useDebouncedCallback } from '@/hooks/useDebouncedCallback';
import { RefreshCw, Map as MapIcon, Swords, User, ScrollText } from 'lucide-react';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { useGameStore } from '@/stores/gameStore';
import { frontApi } from '@/lib/gameApi';
import { subscribeWebSocket } from '@/lib/websocket';
import type { FrontInfoResponse } from '@/types';
import { MapViewer } from '@/components/game/map-viewer';
import { CommandPanel } from '@/components/game/command-panel';
import { CityBasicCard } from '@/components/game/city-basic-card';
import { NationBasicCard } from '@/components/game/nation-basic-card';
import { GeneralBasicCard } from '@/components/game/general-basic-card';
import { LoadingState } from '@/components/game/loading-state';
import { Button } from '@/components/ui/8bit/button';
import { toast } from 'sonner';
import { formatLog } from '@/lib/formatLog';

/** LOGH faction color mapping */
const FACTION_COLORS: Record<string, string> = {
    empire: '#c9a84c',
    alliance: '#1e4a8a',
    fezzan: '#2d6a30',
};

export function GameDashboard() {
    const { currentWorld } = useWorldStore();
    const { myOfficer } = useOfficerStore();
    const [frontInfo, setFrontInfo] = useState<FrontInfoResponse | null>(null);
    const lastRecordIdRef = useRef<number | undefined>(undefined);
    const lastHistoryIdRef = useRef<number | undefined>(undefined);
    const [loading, setLoading] = useState(true);
    const loadFrontInfoRef = useRef<() => Promise<void>>(async () => {});

    const [showVersionModal, setShowVersionModal] = useState(false);
    const [mobileTab, setMobileTab] = useState<'map' | 'commands' | 'status' | 'world'>('map');

    const mobileTabs = [
        { key: 'map', label: 'Galaxy', icon: MapIcon },
        { key: 'commands', label: 'Commands', icon: Swords },
        { key: 'status', label: 'Status', icon: User },
        { key: 'world', label: 'Intel', icon: ScrollText },
    ] as const;

    const isTabActive = (tab: string) => mobileTab === tab;

    const updateWorldTime = useWorldStore((s) => s.updateWorldTime);
    const loadAll = useGameStore((s) => s.loadAll);

    useEffect(() => {
        if (currentWorld) loadAll(currentWorld.id);
    }, [currentWorld, loadAll]);

    const loadFrontInfo = useCallback(async () => {
        if (!currentWorld) return;
        try {
            const { data } = await frontApi.getInfo(currentWorld.id, lastRecordIdRef.current, lastHistoryIdRef.current);
            setFrontInfo(data);

            if (data.global) {
                updateWorldTime(data.global.year, data.global.month);
            }

            if (data.global?.lastVote) {
                try {
                    const prevVoteState = localStorage.getItem('openlogh:lastVoteState');
                    const curVoteId = String(data.global.lastVote.id ?? '');
                    if (prevVoteState !== curVoteId && curVoteId) {
                        toast.info('A new survey is in progress!', { duration: 5000 });
                    }
                    if (curVoteId) localStorage.setItem('openlogh:lastVoteState', curVoteId);
                } catch {
                    /* ignore */
                }
            }

            const lastRecord = data.recentRecord.general[0]?.id;
            const lastHistory = data.recentRecord.history[0]?.id;
            if (lastRecord) lastRecordIdRef.current = lastRecord;
            if (lastHistory) lastHistoryIdRef.current = lastHistory;
        } catch {
            /* ignore */
        } finally {
            setLoading(false);
        }
    }, [currentWorld, updateWorldTime]);

    const handleRefresh = useCallback(async () => {
        lastRecordIdRef.current = undefined;
        lastHistoryIdRef.current = undefined;
        await loadFrontInfo();
    }, [loadFrontInfo]);

    useEffect(() => {
        loadFrontInfoRef.current = loadFrontInfo;
    }, [loadFrontInfo]);

    useEffect(() => {
        loadFrontInfo();
    }, [loadFrontInfo]);

    const debouncedReload = useDebouncedCallback(
        useCallback(() => {
            loadFrontInfoRef.current().catch(() => {});
        }, []),
        500
    );

    useEffect(() => {
        if (!currentWorld) return;

        const unsubTurn = subscribeWebSocket(`/topic/world/${currentWorld.id}/turn`, () => {
            debouncedReload();
        });
        const unsubMessage = subscribeWebSocket(`/topic/world/${currentWorld.id}/message`, () => {
            debouncedReload();
        });
        const unsubUpdate = subscribeWebSocket(`/topic/world/${currentWorld.id}/update`, () => {
            debouncedReload();
        });
        const unsubCommand = subscribeWebSocket(`/topic/world/${currentWorld.id}/command`, () => {
            debouncedReload();
        });

        return () => {
            unsubTurn();
            unsubMessage();
            unsubUpdate();
            unsubCommand();
        };
    }, [currentWorld, debouncedReload]);

    const genCounts = useMemo(() => {
        if (!frontInfo?.global.genCount) return { user: 0, npc: 0 };
        let user = 0;
        let npc = 0;
        for (const [npcType, cnt] of frontInfo.global.genCount) {
            if (npcType < 2) user += cnt;
            else npc += cnt;
        }
        return { user, npc };
    }, [frontInfo?.global.genCount]);

    if (!currentWorld) return <LoadingState message="Loading session..." />;
    if (loading) return <LoadingState />;

    const global = frontInfo?.global;
    const mapCode = (currentWorld.config as Record<string, string>)?.mapCode ?? 'che';

    return (
        <div id="container" className="bg-[#0a0e1a] text-gray-200">
            {/* ===== Session Info Header ===== */}
            {global && (
                <>
                    {/* Mobile compact summary */}
                    <div className="lg:hidden text-center text-xs py-1.5 bg-[#0f1429]/80 border-y border-[#1a2040]">
                        <span className="text-[#00d4ff]">{global.scenarioText}</span> | UC {global.year}.{global.month} | Online{' '}
                        {(global.onlineUserCnt ?? 0).toLocaleString()}
                    </div>
                    {/* Desktop full header */}
                    <div className="hidden lg:block">
                        <h3 className="text-center font-bold py-1.5 text-sm bg-[#0f1429] border-b border-[#1a2040]">
                            <span className="text-[#00d4ff] tracking-wider">{global.scenarioText}</span>
                            {global.serverCnt > 0 && <span className="text-gray-500 ml-2">[{global.serverCnt} sessions]</span>}
                        </h3>
                        <div className="grid grid-cols-12 text-center text-[11px] border-b border-[#1a2040] bg-[#0a0e1a]">
                            <div className="col-span-4 border-r border-[#1a2040] py-1.5 text-[#00d4ff]">
                                UC {global.year}.{global.month}
                            </div>
                            <div className="col-span-2 border-r border-[#1a2040] py-1.5">
                                Online: {(global.onlineUserCnt ?? 0).toLocaleString()}
                            </div>
                            <div className="col-span-3 border-r border-[#1a2040] py-1.5">
                                Officers: {genCounts.user.toLocaleString()} + <span className="text-[#00d4ff]">NPC {genCounts.npc.toLocaleString()}</span>
                            </div>
                            <div className="col-span-3 py-1.5 text-gray-400">
                                Last tick: {global.lastExecuted?.substring(5) ?? '-'}
                            </div>
                        </div>
                    </div>
                </>
            )}

            {/* ===== Active Factions Bar ===== */}
            {global && (
                <div className="border-b border-[#1a2040] px-2 py-1.5 text-xs overflow-x-auto whitespace-nowrap lg:whitespace-normal lg:overflow-visible scrollbar-hide bg-[#0f1429]/50">
                    <span className="text-gray-400 mr-2">Active Factions:</span>
                    {global.onlineNations.map((n) => (
                        <span key={n.id} className="mr-3">
                            <span
                                className="inline-block size-2 rounded-full mr-1"
                                style={{ backgroundColor: n.color, boxShadow: `0 0 4px ${n.color}` }}
                            />
                            {n.name}({n.genCount})
                        </span>
                    ))}
                </div>
            )}

            {/* ===== Online Officers ===== */}
            {frontInfo?.nation && (
                <div className="border-b border-[#1a2040] px-2 py-1 text-xs bg-[#0a0e1a]">
                    <span className="text-[#00d4ff]">Online:</span> {frontInfo.nation.onlineGen}
                </div>
            )}

            {/* ===== Faction Notice ===== */}
            <div className="border-b border-[#1a2040] py-1 bg-[#0f1429]/30">
                <div className="px-2 text-xs font-bold text-[#c9a84c]">Faction Directive</div>
                {frontInfo?.nation?.notice && (
                    <div
                        className="px-2 text-xs break-all text-gray-300"
                        dangerouslySetInnerHTML={{ __html: frontInfo.nation.notice.msg }}
                    />
                )}
            </div>

            {/* ===== Desktop Refresh ===== */}
            <div className="hidden lg:flex justify-end border-b border-[#1a2040] px-2 py-1.5">
                <Button onClick={handleRefresh} variant="outline" size="sm" className="gap-1 border-[#1a2040] text-[#00d4ff] hover:bg-[#1a2040]">
                    <RefreshCw className="h-3.5 w-3.5" />
                    Refresh
                </Button>
            </div>

            {/* ===== Mobile Tabs ===== */}
            <div
                className="lg:hidden flex gap-1 p-1 border-b border-[#1a2040] bg-[#0f1429]/50"
                data-tutorial="mobile-tabs"
            >
                {mobileTabs.map((tab) => {
                    const Icon = tab.icon;
                    return (
                        <button
                            key={tab.key}
                            type="button"
                            className={`flex-1 py-2 text-xs font-bold text-center rounded transition-all duration-150 flex items-center justify-center gap-1 ${
                                mobileTab === tab.key
                                    ? 'bg-[#1a2040] text-[#00d4ff] border border-[#00d4ff]/30'
                                    : 'bg-[#0a0e1a] border border-[#1a2040] text-gray-400'
                            }`}
                            onClick={() => setMobileTab(tab.key)}
                        >
                            <Icon className="h-3.5 w-3.5" />
                            {tab.label}
                        </button>
                    );
                })}
            </div>

            <div className="flex flex-col gap-3 pb-4">
                <div className="flex flex-col lg:flex-row gap-2">
                    <div
                        className="w-full lg:w-[700px] shrink-0"
                        style={{ aspectRatio: '700 / 500' }}
                        data-tutorial="map-viewer"
                    >
                        <MapViewer worldId={currentWorld.id} mapCode={mapCode} />
                    </div>
                    <div className="flex-1 overflow-y-auto lg:max-h-[500px]">
                        {myOfficer && (
                            <CommandPanel generalId={myOfficer.id} realtimeMode={currentWorld.realtimeMode} />
                        )}
                    </div>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-2">
                    <div className="space-y-2">
                        <CityBasicCard city={frontInfo?.city ?? null} region={frontInfo?.city?.region} />
                        <NationBasicCard nation={frontInfo?.nation ?? null} global={global} />
                    </div>
                    <div>
                        <GeneralBasicCard
                            general={frontInfo?.general ?? null}
                            nation={frontInfo?.nation ?? null}
                            turnTerm={global?.turnTerm}
                            lastExecuted={global?.lastExecuted}
                        />
                    </div>
                </div>
            </div>

            {/* ===== Record Zone ===== */}
            {frontInfo && (
                <div className={`grid grid-cols-1 lg:grid-cols-2 ${isTabActive('world') ? '' : 'max-lg:hidden'}`}>
                    <div>
                        <div className="text-center border-t border-b border-[#1a2040] text-xs font-semibold py-1 bg-[#1a2040]/50 text-[#00d4ff] tracking-wide">
                            Officer Activity
                        </div>
                        {frontInfo.recentRecord.global.length === 0 ? (
                            <div className="px-2 py-1 text-xs text-gray-500">No records</div>
                        ) : (
                            frontInfo.recentRecord.global.slice(0, 15).map((r) => (
                                <div key={r.id} className="border-b border-[#1a2040]/50 px-2 py-0.5 text-xs">
                                    <span className="text-gray-500">[{r.date}]</span> {formatLog(r.message)}
                                </div>
                            ))
                        )}
                    </div>
                    <div>
                        <div className="text-center border-t border-b border-[#1a2040] text-xs font-semibold py-1 bg-[#1a2040]/50 text-[#00d4ff] tracking-wide">
                            Personal Log
                        </div>
                        {frontInfo.recentRecord.general.length === 0 ? (
                            <div className="px-2 py-1 text-xs text-gray-500">No records</div>
                        ) : (
                            frontInfo.recentRecord.general.slice(0, 15).map((r) => (
                                <div key={r.id} className="border-b border-[#1a2040]/50 px-2 py-0.5 text-xs">
                                    <span className="text-gray-500">[{r.date}]</span> {formatLog(r.message)}
                                </div>
                            ))
                        )}
                    </div>
                    <div className="col-span-1 lg:col-span-2">
                        <div className="text-center border-t border-b border-[#1a2040] text-xs font-semibold py-1 bg-[#c9a84c]/10 text-[#c9a84c] tracking-wide">
                            Galaxy News
                        </div>
                        {frontInfo.recentRecord.history.length === 0 ? (
                            <div className="px-2 py-1 text-xs text-gray-500">No records</div>
                        ) : (
                            frontInfo.recentRecord.history.slice(0, 15).map((r) => (
                                <div key={r.id} className="border-b border-[#1a2040]/50 px-2 py-0.5 text-xs">
                                    {r.date && <span className="text-gray-500">[{r.date}]</span>} {formatLog(r.message)}
                                </div>
                            ))
                        )}
                    </div>
                </div>
            )}

            {/* ===== Faction Power Summary ===== */}
            {global && global.onlineNations.length > 0 && (
                <div className={`${isTabActive('world') ? '' : 'max-lg:hidden'}`}>
                    <div className="text-center border-t border-b border-[#1a2040] text-xs font-semibold py-1 bg-[#c9a84c]/10 text-[#c9a84c] tracking-wide">
                        Faction Power
                    </div>
                    <div className="grid grid-cols-2 lg:grid-cols-4 gap-1 p-1">
                        {global.onlineNations
                            .sort((a, b) => b.genCount - a.genCount)
                            .map((n) => (
                                <div
                                    key={n.id}
                                    className="rounded px-2.5 py-2 flex items-center gap-2 transition-colors hover:bg-[#1a2040]/50"
                                    style={{ borderLeft: `3px solid ${n.color}`, boxShadow: `inset 2px 0 8px -4px ${n.color}` }}
                                >
                                    <div className="min-w-0 flex-1">
                                        <p className="text-xs font-medium truncate">{n.name}</p>
                                        <p className="text-[10px] text-gray-500">Officers: {n.genCount}</p>
                                    </div>
                                </div>
                            ))}
                    </div>
                </div>
            )}

            {/* ===== Officer Status Summary ===== */}
            {frontInfo?.general && (
                <div className={`${isTabActive('status') ? '' : 'max-lg:hidden'}`}>
                    <div className="text-center border-t border-b border-[#1a2040] text-xs font-semibold py-1 bg-[#00d4ff]/10 text-[#00d4ff] tracking-wide">
                        My Officer Summary
                    </div>
                    <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-7 text-center text-xs border-b border-[#1a2040]">
                        <div className="border-r border-[#1a2040]/50 py-1">
                            <span className="text-gray-500">Battles</span>{' '}
                            <span className="text-[#00d4ff]">
                                {frontInfo.general.warnum}W {frontInfo.general.killnum}V {frontInfo.general.deathnum}D
                            </span>
                        </div>
                        <div className="border-r border-[#1a2040]/50 py-1">
                            <span className="text-gray-500">Kills</span>{' '}
                            <span className="text-orange-400">{frontInfo.general.killcrew.toLocaleString()}</span>
                        </div>
                        <div className="border-r border-[#1a2040]/50 py-1">
                            <span className="text-gray-500">Losses</span>{' '}
                            <span className="text-red-400">{frontInfo.general.deathcrew.toLocaleString()}</span>
                        </div>
                        <div className="border-r border-[#1a2040]/50 py-1">
                            <span className="text-gray-500">Intel Ops</span>{' '}
                            <span className="text-purple-400">{frontInfo.general.firenum}</span>
                        </div>
                        <div className="border-r border-[#1a2040]/50 py-1">
                            <span className="text-gray-500">Injury</span>{' '}
                            <span className={frontInfo.general.injury > 0 ? 'text-red-400' : 'text-green-400'}>
                                {frontInfo.general.injury}%
                            </span>
                        </div>
                        <div className="border-r border-[#1a2040]/50 py-1">
                            <span className="text-gray-500">Honor</span>{' '}
                            <span className="text-yellow-400">{frontInfo.general.honorText}</span>
                        </div>
                        <div className="py-1">
                            <span className="text-gray-500">Salary</span>{' '}
                            <span className="text-[#c9a84c]">{frontInfo.general.bill}</span>
                        </div>
                    </div>
                </div>
            )}

            {/* ===== Version Info ===== */}
            {global && (
                <div className={`text-center py-1 ${isTabActive('world') ? '' : 'max-lg:hidden'}`}>
                    <button
                        type="button"
                        className="text-[10px] text-gray-600 hover:text-gray-400"
                        onClick={() => setShowVersionModal(true)}
                    >
                        Session Info
                    </button>
                </div>
            )}
            {showVersionModal && (
                <div
                    className="fixed inset-0 z-50 flex items-center justify-center bg-black/70"
                    onClick={() => setShowVersionModal(false)}
                >
                    <div
                        className="bg-[#0f1429] border border-[#1a2040] rounded p-4 max-w-sm w-full mx-4 space-y-2"
                        onClick={(e) => e.stopPropagation()}
                    >
                        <h3 className="text-sm font-bold text-center text-[#00d4ff]">Session Info</h3>
                        <div className="text-xs space-y-1 text-gray-300">
                            <p><span className="text-gray-500">Scenario:</span> {global?.scenarioText}</p>
                            <p><span className="text-gray-500">Server:</span> {currentWorld?.name}</p>
                            <p><span className="text-gray-500">Game Time:</span> UC {global?.year}.{global?.month}</p>
                            <p><span className="text-gray-500">Last Tick:</span> {global?.lastExecuted ?? '-'}</p>
                            <p><span className="text-gray-500">Extended Officers:</span> {global?.extendedGeneral ? 'ON' : 'OFF'}</p>
                            <p><span className="text-gray-500">NPC Mode:</span> {['Disabled', 'Enabled', 'Create'][global?.npcMode ?? 0]}</p>
                            <p><span className="text-gray-500">Officer Limit:</span> {global?.generalCntLimit?.toLocaleString() ?? 'Unlimited'}</p>
                        </div>
                        <div className="flex justify-center pt-2">
                            <Button size="sm" variant="outline" onClick={() => setShowVersionModal(false)} className="border-[#1a2040] text-[#00d4ff]">
                                Close
                            </Button>
                        </div>
                    </div>
                </div>
            )}

            <style jsx>{`
                #container {
                    width: 100%;
                    max-width: 100%;
                    margin: 0 auto;
                }
                @media (min-width: 1024px) {
                    #container {
                        max-width: 1000px;
                    }
                }
            `}</style>
        </div>
    );
}
