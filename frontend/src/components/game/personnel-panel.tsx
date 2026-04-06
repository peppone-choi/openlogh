'use client';

import { useEffect } from 'react';
import { usePersonnelStore } from '@/stores/personnelStore';
import { RankBadge, MeritProgressBar } from './rank-badge';
import { getRankHeadcountLimit } from '@/types/personnel';
import type { RankLadderEntry, PersonnelInfo } from '@/types/personnel';

interface PersonnelPanelProps {
    sessionId: number;
    factionId: number;
    /** The current player's officer ID (for authority checks) */
    myOfficerId?: number;
    /** Whether the current player has personnel authority */
    hasPersonnelAuthority?: boolean;
}

export function PersonnelPanel({
    sessionId,
    factionId,
    myOfficerId,
    hasPersonnelAuthority = false,
}: PersonnelPanelProps) {
    const {
        rankLadder,
        personnelInfo,
        isLoading,
        error,
        actionMessage,
        loadLadder,
        loadInfo,
        promote,
        demote,
        clearError,
        clearActionMessage,
    } = usePersonnelStore();

    useEffect(() => {
        loadLadder(sessionId, factionId);
    }, [sessionId, factionId, loadLadder]);

    const handleSelectOfficer = (officerId: number) => {
        loadInfo(sessionId, officerId);
    };

    const handlePromote = async (officerId: number) => {
        if (!myOfficerId) return;
        await promote(sessionId, myOfficerId, officerId);
    };

    const handleDemote = async (officerId: number) => {
        if (!myOfficerId) return;
        await demote(sessionId, myOfficerId, officerId);
    };

    return (
        <div className="flex flex-col gap-4">
            <h3 className="text-lg font-semibold">인사 관리</h3>

            {error && (
                <div
                    className="p-2 text-sm text-red-600 bg-red-50 rounded border border-red-200 cursor-pointer"
                    onClick={clearError}
                >
                    {error}
                </div>
            )}

            {actionMessage && (
                <div
                    className="p-2 text-sm text-green-600 bg-green-50 rounded border border-green-200 cursor-pointer"
                    onClick={clearActionMessage}
                >
                    {actionMessage}
                </div>
            )}

            {/* Rank Ladder Table */}
            <div className="border rounded overflow-hidden">
                <table className="w-full text-sm">
                    <thead className="bg-muted">
                        <tr>
                            <th className="px-3 py-2 text-left">순위</th>
                            <th className="px-3 py-2 text-left">이름</th>
                            <th className="px-3 py-2 text-left">계급</th>
                            <th className="px-3 py-2 text-right">공적</th>
                            <th className="px-3 py-2 text-right">명성</th>
                            <th className="px-3 py-2 text-right">능력합</th>
                        </tr>
                    </thead>
                    <tbody>
                        {isLoading && (
                            <tr>
                                <td
                                    colSpan={6}
                                    className="px-3 py-4 text-center text-muted-foreground"
                                >
                                    로딩 중...
                                </td>
                            </tr>
                        )}
                        {!isLoading && rankLadder.length === 0 && (
                            <tr>
                                <td
                                    colSpan={6}
                                    className="px-3 py-4 text-center text-muted-foreground"
                                >
                                    소속 장교가 없습니다.
                                </td>
                            </tr>
                        )}
                        {rankLadder.map((entry, index) => (
                            <LadderRow
                                key={entry.officerId}
                                entry={entry}
                                rank={index + 1}
                                isSelected={
                                    personnelInfo?.officerId === entry.officerId
                                }
                                onSelect={() =>
                                    handleSelectOfficer(entry.officerId)
                                }
                            />
                        ))}
                    </tbody>
                </table>
            </div>

            {/* Personnel Detail */}
            {personnelInfo && (
                <PersonnelDetail
                    info={personnelInfo}
                    hasAuthority={hasPersonnelAuthority}
                    isMyself={personnelInfo.officerId === myOfficerId}
                    onPromote={() => handlePromote(personnelInfo.officerId)}
                    onDemote={() => handleDemote(personnelInfo.officerId)}
                    isLoading={isLoading}
                />
            )}
        </div>
    );
}

/** Single row in the rank ladder table */
function LadderRow({
    entry,
    rank,
    isSelected,
    onSelect,
}: {
    entry: RankLadderEntry;
    rank: number;
    isSelected: boolean;
    onSelect: () => void;
}) {
    return (
        <tr
            className={`border-t cursor-pointer hover:bg-muted/50 ${
                isSelected ? 'bg-muted/70' : ''
            }`}
            onClick={onSelect}
        >
            <td className="px-3 py-2 text-muted-foreground">{rank}</td>
            <td className="px-3 py-2 font-medium">{entry.name}</td>
            <td className="px-3 py-2">
                <RankBadge
                    rankTier={entry.rankTier}
                    rankTitle={entry.rankTitle}
                    rankTitleKo={entry.rankTitleKo}
                />
            </td>
            <td className="px-3 py-2 text-right font-mono">
                {entry.meritPoints}
            </td>
            <td className="px-3 py-2 text-right font-mono">
                {entry.famePoints}
            </td>
            <td className="px-3 py-2 text-right font-mono">
                {entry.totalStats}
            </td>
        </tr>
    );
}

/** Detail view for a selected officer's personnel info */
function PersonnelDetail({
    info,
    hasAuthority,
    isMyself,
    onPromote,
    onDemote,
    isLoading,
}: {
    info: PersonnelInfo;
    hasAuthority: boolean;
    isMyself: boolean;
    onPromote: () => void;
    onDemote: () => void;
    isLoading: boolean;
}) {
    const headcountLimit = getRankHeadcountLimit(info.rankTier);
    const headcountText =
        headcountLimit === Infinity
            ? '제한 없음'
            : `최대 ${headcountLimit}명`;

    return (
        <div className="border rounded p-4 space-y-3">
            <div className="flex items-center justify-between">
                <div>
                    <h4 className="font-semibold text-base">{info.name}</h4>
                    <RankBadge
                        rankTier={info.rankTier}
                        rankTitle={info.rankTitle}
                        rankTitleKo={info.rankTitleKo}
                        size="md"
                    />
                </div>
                <div className="text-right text-sm text-muted-foreground">
                    <div>정원: {headcountText}</div>
                    {info.nextRankTitleKo && (
                        <div>
                            다음 계급: {info.nextRankTitleKo} ({info.nextRankTitle})
                        </div>
                    )}
                </div>
            </div>

            <MeritProgressBar meritPoints={info.meritPoints} />

            <div className="grid grid-cols-3 gap-2 text-sm">
                <div className="text-center p-2 bg-muted rounded">
                    <div className="text-muted-foreground">공적</div>
                    <div className="font-mono font-semibold">
                        {info.meritPoints}
                    </div>
                </div>
                <div className="text-center p-2 bg-muted rounded">
                    <div className="text-muted-foreground">평가</div>
                    <div className="font-mono font-semibold">
                        {info.evaluationPoints}
                    </div>
                </div>
                <div className="text-center p-2 bg-muted rounded">
                    <div className="text-muted-foreground">명성</div>
                    <div className="font-mono font-semibold">
                        {info.famePoints}
                    </div>
                </div>
            </div>

            {/* Action buttons - only shown for officers with authority, not for self */}
            {hasAuthority && !isMyself && (
                <div className="flex gap-2 pt-2">
                    <button
                        className="flex-1 px-3 py-1.5 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
                        onClick={onPromote}
                        disabled={
                            isLoading ||
                            info.rankTier >= 10 ||
                            !info.promotionEligible
                        }
                    >
                        승진
                    </button>
                    <button
                        className="flex-1 px-3 py-1.5 text-sm bg-red-600 text-white rounded hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed"
                        onClick={onDemote}
                        disabled={isLoading || info.rankTier <= 0}
                    >
                        강등
                    </button>
                </div>
            )}
        </div>
    );
}
