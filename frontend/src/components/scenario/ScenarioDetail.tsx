'use client';

import { useEffect, useState } from 'react';
import { useScenarioStore } from '@/stores/scenarioStore';
import { CharacterCreator } from './CharacterCreator';
import type { Scenario, CreateCharacterRequest, ScenarioCharacterInfo } from '@/types';

interface ScenarioDetailProps {
    scenario: Scenario;
    onCreateCharacter: (request: CreateCharacterRequest) => void;
    onSelectOriginal: (character: ScenarioCharacterInfo) => void;
    submitting?: boolean;
}

type TabMode = 'custom' | 'original';

export function ScenarioDetail({ scenario, onCreateCharacter, onSelectOriginal, submitting }: ScenarioDetailProps) {
    const { selectedScenario, loading, fetchScenarioDetail } = useScenarioStore();
    const [tab, setTab] = useState<TabMode>('custom');

    useEffect(() => {
        fetchScenarioDetail(scenario.code);
    }, [scenario.code, fetchScenarioDetail]);

    if (loading || !selectedScenario) {
        return <div className="text-center py-8 text-gray-400">Loading scenario details...</div>;
    }

    const { factions, originalCharacters } = selectedScenario;

    return (
        <div className="space-y-4">
            {/* Scenario Info Header */}
            <div className="p-4 rounded border border-gray-700 bg-gray-900/50">
                <h2 className="text-lg font-bold text-yellow-400">{scenario.title}</h2>
                <div className="text-sm text-gray-400 mt-1">UC {scenario.startYear}</div>
                {scenario.battleLocation && (
                    <div className="text-sm text-red-400 mt-1">
                        Active battle: {scenario.battleLocation}
                    </div>
                )}

                {/* Faction Balance */}
                <div className="mt-3 flex gap-2">
                    {factions.map((faction, idx) => (
                        <div
                            key={idx}
                            className="flex-1 p-2 rounded border border-gray-700 text-center text-xs"
                            style={{ borderTopColor: faction.color, borderTopWidth: '3px' }}
                        >
                            <div className="font-semibold text-gray-300">{faction.name}</div>
                            <div className="text-gray-500">{faction.systemCount} systems</div>
                        </div>
                    ))}
                </div>

                {/* Fleet Info */}
                {scenario.formableFleets && (
                    <div className="mt-3 text-xs text-gray-500">
                        <span className="text-gray-400">Formable fleets: </span>
                        {Object.entries(scenario.formableFleets).map(([faction, fleets]) => (
                            <span key={faction} className="mr-3">
                                {faction}: {(fleets as number[]).join(', ')}
                            </span>
                        ))}
                    </div>
                )}
            </div>

            {/* Tab Selection */}
            <div className="flex gap-2">
                <button
                    onClick={() => setTab('custom')}
                    className={`flex-1 py-2 rounded text-sm font-semibold transition-colors ${
                        tab === 'custom'
                            ? 'bg-yellow-500/20 text-yellow-400 border border-yellow-500'
                            : 'bg-gray-900/50 text-gray-400 border border-gray-700 hover:border-gray-500'
                    }`}
                >
                    Custom Character
                </button>
                <button
                    onClick={() => setTab('original')}
                    className={`flex-1 py-2 rounded text-sm font-semibold transition-colors ${
                        tab === 'original'
                            ? 'bg-blue-500/20 text-blue-400 border border-blue-500'
                            : 'bg-gray-900/50 text-gray-400 border border-gray-700 hover:border-gray-500'
                    }`}
                >
                    Original Characters ({originalCharacters.length})
                </button>
            </div>

            {/* Tab Content */}
            {tab === 'custom' ? (
                <CharacterCreator
                    factions={factions}
                    onSubmit={onCreateCharacter}
                    submitting={submitting}
                />
            ) : (
                <OriginalCharacterList
                    characters={originalCharacters}
                    onSelect={onSelectOriginal}
                />
            )}
        </div>
    );
}

function OriginalCharacterList({
    characters,
    onSelect,
}: {
    characters: ScenarioCharacterInfo[];
    onSelect: (character: ScenarioCharacterInfo) => void;
}) {
    // Group by faction
    const grouped = characters.reduce<Record<string, ScenarioCharacterInfo[]>>((acc, ch) => {
        if (!acc[ch.factionName]) acc[ch.factionName] = [];
        acc[ch.factionName].push(ch);
        return acc;
    }, {});

    return (
        <div className="space-y-4">
            <h2 className="text-lg font-bold text-blue-400">Original Characters</h2>
            <p className="text-xs text-gray-500">
                Select an original character from the scenario. Their stats and starting position will be inherited.
            </p>
            {Object.entries(grouped).map(([factionName, chars]) => (
                <div key={factionName}>
                    <h3 className="text-sm font-semibold text-gray-400 mb-2">{factionName}</h3>
                    <div className="grid grid-cols-2 gap-2">
                        {chars.map((ch, idx) => (
                            <button
                                key={idx}
                                onClick={() => onSelect(ch)}
                                className="p-2 rounded border border-gray-700 bg-gray-900/50 text-left text-sm text-gray-300 hover:border-yellow-500 hover:bg-yellow-500/5 transition-colors"
                            >
                                {ch.name}
                            </button>
                        ))}
                    </div>
                </div>
            ))}
        </div>
    );
}
