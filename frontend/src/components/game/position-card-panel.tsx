'use client';

// CommandGroup matches the gin7 7-group classification
type CommandGroup =
    | 'OPERATION'
    | 'PERSONAL'
    | 'COMMAND'
    | 'LOGISTICS'
    | 'PERSONNEL'
    | 'POLITICS'
    | 'INTELLIGENCE';

const GROUP_LABELS: Record<CommandGroup, string> = {
    OPERATION:    '작전',
    PERSONAL:     '개인',
    COMMAND:      '지휘',
    LOGISTICS:    '병참',
    PERSONNEL:    '인사',
    POLITICS:     '정략',
    INTELLIGENCE: '첩보',
};

// Card code prefix → CommandGroup mapping (gin7 convention)
const CARD_PREFIX_MAP: Array<[string, CommandGroup]> = [
    ['OP_',      'OPERATION'],
    ['PERS_',    'PERSONAL'],
    ['CMD_',     'COMMAND'],
    ['LOG_',     'LOGISTICS'],
    ['HR_',      'PERSONNEL'],
    ['POL_',     'POLITICS'],
    ['INTEL_',   'INTELLIGENCE'],
    // fallback prefixes used in game data
    ['SORTIE',   'OPERATION'],
    ['RECRUIT',  'LOGISTICS'],
    ['MOVE',     'OPERATION'],
    ['SCOUT',    'INTELLIGENCE'],
    ['SUPPLY',   'LOGISTICS'],
    ['APPOINT',  'PERSONNEL'],
    ['DISMISS',  'PERSONNEL'],
    ['POLITICS', 'POLITICS'],
    ['PROPOSE',  'POLITICS'],
];

function cardToGroup(code: string): CommandGroup | null {
    const upper = code.toUpperCase();
    for (const [prefix, group] of CARD_PREFIX_MAP) {
        if (upper.startsWith(prefix)) return group;
    }
    return null;
}

function getAvailableGroups(positionCards: string[]): Map<CommandGroup, number> {
    const counts = new Map<CommandGroup, number>();
    for (const card of positionCards) {
        const group = cardToGroup(card);
        if (group) {
            counts.set(group, (counts.get(group) ?? 0) + 1);
        }
    }
    return counts;
}

interface PositionCardPanelProps {
    positionCards: string[];
    selectedGroup: CommandGroup | null;
    onSelectGroup: (group: CommandGroup | null) => void;
}

export function PositionCardPanel({ positionCards, selectedGroup, onSelectGroup }: PositionCardPanelProps) {
    const groupCounts = getAvailableGroups(positionCards);
    const totalCards = positionCards.length;

    // Order tabs: 전체 first, then available groups in definition order
    const orderedGroups = (Object.keys(GROUP_LABELS) as CommandGroup[]).filter((g) =>
        groupCounts.has(g)
    );

    return (
        <div className="flex flex-wrap gap-1 p-2 bg-slate-900 border border-slate-700 rounded">
            {/* 전체 tab */}
            <button
                type="button"
                onClick={() => onSelectGroup(null)}
                className={`px-2 py-1 text-xs rounded transition-colors ${
                    selectedGroup === null
                        ? 'bg-blue-700 text-white'
                        : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
                }`}
            >
                전체
                <span className="ml-1 text-[10px] opacity-75">({totalCards})</span>
            </button>

            {orderedGroups.map((group) => {
                const count = groupCounts.get(group) ?? 0;
                const isActive = selectedGroup === group;
                return (
                    <button
                        key={group}
                        type="button"
                        onClick={() => onSelectGroup(group)}
                        className={`px-2 py-1 text-xs rounded transition-colors ${
                            isActive
                                ? 'bg-blue-700 text-white'
                                : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
                        }`}
                    >
                        {GROUP_LABELS[group]}
                        <span className="ml-1 text-[10px] opacity-75">({count})</span>
                    </button>
                );
            })}
        </div>
    );
}
