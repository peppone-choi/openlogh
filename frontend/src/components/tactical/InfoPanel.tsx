'use client';

import type { TacticalBattle, TacticalUnit } from '@/types/tactical';

interface InfoPanelProps {
    battle: TacticalBattle;
    units: TacticalUnit[];
    myOfficerId?: number;
    /** UC year for date display */
    ucYear?: number;
    /** UC month for date display */
    ucMonth?: number;
    /** Star system name */
    starSystemName?: string;
    /** Operation name */
    operationName?: string;
    /** Commanding officer name */
    commanderName?: string;
    /** Commanding officer rank */
    commanderRank?: string;
    /** Merit points */
    meritPoints?: number;
}

const LABEL_STYLE: React.CSSProperties = {
    color: '#666',
    fontSize: 9,
    fontFamily: 'monospace',
    marginBottom: 1,
};

const VALUE_STYLE: React.CSSProperties = {
    color: '#cccccc',
    fontSize: 11,
    fontFamily: 'monospace',
    marginBottom: 6,
};

function InfoRow({ label, value }: { label: string; value: string }) {
    return (
        <div>
            <div style={LABEL_STYLE}>{label}</div>
            <div style={VALUE_STYLE}>{value}</div>
        </div>
    );
}

export function InfoPanel({
    battle,
    units,
    myOfficerId,
    ucYear = 800,
    ucMonth = 1,
    starSystemName = '미지정',
    operationName,
    commanderName = '—',
    commanderRank = '',
    meritPoints = 0,
}: InfoPanelProps) {
    const totalSupplies = units.reduce((sum, u) => sum + (u.ships ?? 0), 0);
    const myUnit = myOfficerId ? units.find((u) => u.officerId === myOfficerId) : undefined;
    const mySide = myUnit?.side;
    const factionName = mySide === 'ATTACKER' ? '자유행성동맹' : '은하제국';

    return (
        <div
            style={{
                position: 'absolute',
                bottom: 8,
                right: 8,
                width: 200,
                background: '#0d0d1a',
                border: '1px solid #333',
                padding: 8,
                zIndex: 10,
                fontFamily: 'monospace',
            }}
        >
            <InfoRow label="진영" value={factionName} />
            <InfoRow label="입력 턴" value={`${battle.tickCount}턴`} />
            <InfoRow label="UC/RC 날짜" value={`UC ${ucYear}년 ${ucMonth}월`} />
            <InfoRow label="성계명" value={starSystemName} />
            <InfoRow label="작전명" value={operationName ?? '미지정'} />
            <InfoRow
                label="작전총사령관"
                value={commanderRank ? `${commanderRank} ${commanderName}` : commanderName}
            />
            <InfoRow label="작전공적" value={`${meritPoints.toLocaleString()} pt`} />
            <InfoRow label="총물자량" value={`${totalSupplies.toLocaleString()} 척`} />
        </div>
    );
}
