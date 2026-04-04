'use client';

import { isBattleLogHtml, parseBattleLogHtml, getWarTypeColor } from '@/lib/formatBattleLog';
import { formatLog } from '@/lib/formatLog';

interface BattleLogEntryProps {
    message: string;
}

export function BattleLogEntry({ message }: BattleLogEntryProps) {
    if (!isBattleLogHtml(message)) {
        return <>{formatLog(message)}</>;
    }

    const data = parseBattleLogHtml(message);
    if (!data) {
        // Fallback: render as raw HTML for small_war_log that failed to parse
        return <span dangerouslySetInnerHTML={{ __html: message }} />;
    }

    const arrowColor = getWarTypeColor(data.warType);

    return (
        <span style={{ display: 'inline-block' }}>
            {/* Attacker (me) */}
            <span style={{ fontSize: '0.75em' }}>
                <span>{data.attacker.crewType}</span>
                <span style={{ color: 'yellow' }}>
                    【<span>{data.attacker.name}</span>】
                </span>
            </span>
            <span style={{ color: 'orangered', fontSize: '90%' }}>
                {data.attacker.remainCrew}
                <span>({data.attacker.killedCrew})</span>
            </span>

            {/* Arrow */}
            <span style={{ color: arrowColor }}>{data.arrow}</span>

            {/* Defender (you) */}
            <span style={{ color: 'orangered', fontSize: '90%' }}>
                {data.defender.remainCrew}
                <span>({data.defender.killedCrew})</span>
            </span>
            <span style={{ fontSize: '0.75em' }}>
                <span style={{ color: 'yellow' }}>
                    【<span>{data.defender.name}</span>】
                </span>
                <span>{data.defender.crewType}</span>
            </span>
        </span>
    );
}
