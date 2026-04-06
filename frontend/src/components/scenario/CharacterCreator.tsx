'use client';

import { useState, useCallback, useMemo } from 'react';
import type { CreateCharacterRequest, ScenarioFactionInfo } from '@/types';

const STAT_TOTAL = 400;
const STAT_MIN = 20;
const STAT_MAX = 95;

const STATS = [
    { key: 'leadership', label: 'Leadership', labelKo: '통솔', category: 'PCP', desc: '인재 활용, 함대 최대 사기' },
    { key: 'command', label: 'Command', labelKo: '지휘', category: 'MCP', desc: '부대 지휘 능력' },
    { key: 'intelligence', label: 'Intelligence', labelKo: '정보', category: 'PCP', desc: '정보 수집/분석, 첩보' },
    { key: 'politics', label: 'Politics', labelKo: '정치', category: 'PCP', desc: '시민 지지 획득' },
    { key: 'administration', label: 'Administration', labelKo: '운영', category: 'PCP', desc: '행성 통치, 사무 관리' },
    { key: 'mobility', label: 'Mobility', labelKo: '기동', category: 'MCP', desc: '함대 이동/기동 지휘' },
    { key: 'attack', label: 'Attack', labelKo: '공격', category: 'MCP', desc: '공격 지휘 능력' },
    { key: 'defense', label: 'Defense', labelKo: '방어', category: 'MCP', desc: '방어 지휘 능력' },
] as const;

type StatKey = (typeof STATS)[number]['key'];

const EMPIRE_ORIGINS = [
    { value: 'noble', label: '귀족 (Noble)', desc: '제국 귀족 가문 출신' },
    { value: 'knight', label: '제국기사 (Imperial Knight)', desc: '제국기사 가문 출신' },
    { value: 'commoner', label: '평민 (Commoner)', desc: '평민 출신' },
    { value: 'exile', label: '망명자 (Exile)', desc: '동맹에서 망명한 자' },
];

const ALLIANCE_ORIGINS = [
    { value: 'citizen', label: '시민 (Citizen)', desc: '자유행성동맹 시민' },
    { value: 'exile', label: '망명자 (Exile)', desc: '제국에서 망명한 자' },
];

interface CharacterCreatorProps {
    factions: ScenarioFactionInfo[];
    onSubmit: (request: CreateCharacterRequest) => void;
    submitting?: boolean;
}

export function CharacterCreator({ factions, onSubmit, submitting }: CharacterCreatorProps) {
    const [name, setName] = useState('');
    const [factionIndex, setFactionIndex] = useState(0);
    const [origin, setOrigin] = useState<string>('');
    const [stats, setStats] = useState<Record<StatKey, number>>({
        leadership: 50,
        command: 50,
        intelligence: 50,
        politics: 50,
        administration: 50,
        mobility: 50,
        attack: 50,
        defense: 50,
    });

    const totalPoints = useMemo(() => {
        return Object.values(stats).reduce((a, b) => a + b, 0);
    }, [stats]);

    const remaining = STAT_TOTAL - totalPoints;

    const updateStat = useCallback((key: StatKey, value: number) => {
        const clamped = Math.max(STAT_MIN, Math.min(STAT_MAX, value));
        setStats((prev) => ({ ...prev, [key]: clamped }));
    }, []);

    const selectedFaction = factions[factionIndex];
    const factionType = selectedFaction?.name.includes('제국') ? 'empire' : 'alliance';
    const origins = factionType === 'empire' ? EMPIRE_ORIGINS : ALLIANCE_ORIGINS;

    const isValid = name.trim().length >= 2 && remaining === 0 && origin !== '';

    const handleSubmit = () => {
        if (!isValid) return;
        onSubmit({
            name: name.trim(),
            nationId: factionIndex + 1,
            statMode: '8stat',
            ...stats,
            origin,
        });
    };

    return (
        <div className="space-y-6">
            <h2 className="text-lg font-bold text-yellow-400">Character Creation</h2>

            {/* Name */}
            <div>
                <label className="block text-sm text-gray-400 mb-1">Name</label>
                <input
                    type="text"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="Enter officer name"
                    className="w-full px-3 py-2 bg-gray-900 border border-gray-700 rounded text-white placeholder-gray-600 focus:border-yellow-400 focus:outline-none"
                    maxLength={20}
                />
            </div>

            {/* Faction Selection */}
            <div>
                <label className="block text-sm text-gray-400 mb-1">Faction</label>
                <div className="flex gap-2">
                    {factions.filter(f => !f.name.includes('페잔')).map((faction, idx) => (
                        <button
                            key={idx}
                            onClick={() => { setFactionIndex(idx); setOrigin(''); }}
                            className={`flex-1 p-2 rounded border text-sm transition-colors ${
                                factionIndex === idx
                                    ? 'border-yellow-400 bg-yellow-400/10'
                                    : 'border-gray-700 bg-gray-900/50 hover:border-gray-500'
                            }`}
                            style={{ borderLeftColor: faction.color, borderLeftWidth: '3px' }}
                        >
                            {faction.name}
                            <div className="text-xs text-gray-500">{faction.systemCount} systems</div>
                        </button>
                    ))}
                </div>
            </div>

            {/* Origin Selection */}
            <div>
                <label className="block text-sm text-gray-400 mb-1">Origin</label>
                <div className="grid grid-cols-2 gap-2">
                    {origins.map((o) => (
                        <button
                            key={o.value}
                            onClick={() => setOrigin(o.value)}
                            className={`p-2 rounded border text-left text-sm transition-colors ${
                                origin === o.value
                                    ? 'border-yellow-400 bg-yellow-400/10 text-yellow-300'
                                    : 'border-gray-700 bg-gray-900/50 text-gray-400 hover:border-gray-500'
                            }`}
                        >
                            <div className="font-semibold">{o.label}</div>
                            <div className="text-xs text-gray-500">{o.desc}</div>
                        </button>
                    ))}
                </div>
            </div>

            {/* Stat Allocation */}
            <div>
                <div className="flex justify-between items-center mb-2">
                    <label className="text-sm text-gray-400">Stats (8-stat system)</label>
                    <span className={`text-sm font-mono ${remaining === 0 ? 'text-green-400' : remaining < 0 ? 'text-red-400' : 'text-yellow-400'}`}>
                        Remaining: {remaining}
                    </span>
                </div>
                <div className="space-y-2">
                    {STATS.map((stat) => (
                        <div key={stat.key} className="flex items-center gap-2">
                            <div className="w-20 text-right">
                                <span className="text-sm text-gray-300">{stat.labelKo}</span>
                                <span className={`text-xs ml-1 ${stat.category === 'PCP' ? 'text-blue-400' : 'text-red-400'}`}>
                                    {stat.category}
                                </span>
                            </div>
                            <input
                                type="range"
                                min={STAT_MIN}
                                max={STAT_MAX}
                                value={stats[stat.key]}
                                onChange={(e) => updateStat(stat.key, parseInt(e.target.value))}
                                className="flex-1 h-2 bg-gray-700 rounded-lg appearance-none cursor-pointer accent-yellow-400"
                            />
                            <input
                                type="number"
                                min={STAT_MIN}
                                max={STAT_MAX}
                                value={stats[stat.key]}
                                onChange={(e) => updateStat(stat.key, parseInt(e.target.value) || STAT_MIN)}
                                className="w-14 px-1 py-0.5 bg-gray-900 border border-gray-700 rounded text-center text-sm text-white"
                            />
                        </div>
                    ))}
                </div>
                <div className="mt-2 text-xs text-gray-500">
                    Total: {totalPoints}/{STAT_TOTAL} | Each stat: {STAT_MIN}-{STAT_MAX} | Start rank: Sub-lieutenant
                </div>
            </div>

            {/* Submit */}
            <button
                onClick={handleSubmit}
                disabled={!isValid || submitting}
                className={`w-full py-3 rounded font-bold text-sm transition-colors ${
                    isValid && !submitting
                        ? 'bg-yellow-500 hover:bg-yellow-400 text-black'
                        : 'bg-gray-700 text-gray-500 cursor-not-allowed'
                }`}
            >
                {submitting ? 'Creating...' : 'Create Officer'}
            </button>
            {!isValid && (
                <div className="text-xs text-gray-500 text-center">
                    {name.trim().length < 2 && 'Name must be at least 2 characters. '}
                    {remaining !== 0 && `Allocate exactly ${STAT_TOTAL} total points. `}
                    {origin === '' && 'Select an origin. '}
                </div>
            )}
        </div>
    );
}
