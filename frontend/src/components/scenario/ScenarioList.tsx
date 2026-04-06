'use client';

import { useEffect } from 'react';
import { useScenarioStore } from '@/stores/scenarioStore';
import type { Scenario } from '@/types';

interface ScenarioListProps {
    onSelect: (scenario: Scenario) => void;
    selectedCode?: string;
}

export function ScenarioList({ onSelect, selectedCode }: ScenarioListProps) {
    const { loghScenarios, loading, fetchLoghScenarios } = useScenarioStore();

    useEffect(() => {
        if (loghScenarios.length === 0) {
            fetchLoghScenarios();
        }
    }, [loghScenarios.length, fetchLoghScenarios]);

    if (loading) {
        return <div className="text-center py-8 text-gray-400">Loading scenarios...</div>;
    }

    return (
        <div className="space-y-2">
            <h2 className="text-lg font-bold text-yellow-400 mb-4">Scenario Selection</h2>
            {loghScenarios.map((scenario) => (
                <button
                    key={scenario.code}
                    onClick={() => onSelect(scenario)}
                    className={`w-full text-left p-3 rounded border transition-colors ${
                        selectedCode === scenario.code
                            ? 'border-yellow-400 bg-yellow-400/10 text-yellow-300'
                            : 'border-gray-700 bg-gray-900/50 text-gray-300 hover:border-gray-500 hover:bg-gray-800/50'
                    }`}
                >
                    <div className="flex justify-between items-center">
                        <span className="font-semibold">{scenario.title}</span>
                        <span className="text-sm text-gray-500">UC {scenario.startYear}</span>
                    </div>
                    {scenario.battleLocation && (
                        <div className="text-xs text-red-400 mt-1">
                            Battle: {scenario.battleLocation}
                        </div>
                    )}
                    {scenario.factionCount && scenario.factionCount > 0 && (
                        <div className="text-xs text-gray-500 mt-1">
                            {scenario.factionCount} factions
                        </div>
                    )}
                </button>
            ))}
        </div>
    );
}
