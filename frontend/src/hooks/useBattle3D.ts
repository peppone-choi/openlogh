'use client';

import { useMemo } from 'react';
import type { BattleSimResponse, BattleSimUnit, BattleSimCity, Nation } from '@/types';
import type { AnimationSequence } from '@/types/battle3d';
import { parseSimulateResult } from '@/components/battle-3d/BattleDataParser';
import { isWebGLSupported } from '@/lib/battle3d-utils';
import { useBattle3DStore } from '@/stores/battle3dStore';

interface UseBattle3DConfig {
    attackerUnit: BattleSimUnit;
    defenderUnit: BattleSimUnit;
    defenderCity: BattleSimCity;
    nations: Nation[];
    terrain?: string;
    weather?: string;
}

interface UseBattle3DResult {
    sequence: AnimationSequence | null;
    isWebGLSupported: boolean;
    isLoading: boolean;
    loadAndPlay: () => void;
}

export function useBattle3D(result: BattleSimResponse | null, config: UseBattle3DConfig): UseBattle3DResult {
    const { loadSequence, play, playState } = useBattle3DStore();
    const webglSupported = useMemo(() => isWebGLSupported(), []);

    const sequence = useMemo<AnimationSequence | null>(() => {
        if (!result) return null;
        return parseSimulateResult(
            result,
            config.attackerUnit,
            config.defenderUnit,
            config.defenderCity,
            config.nations,
            { terrain: config.terrain, weather: config.weather }
        );
    }, [
        result,
        config.attackerUnit,
        config.defenderUnit,
        config.defenderCity,
        config.nations,
        config.terrain,
        config.weather,
    ]);

    const isLoading = playState === 'loading';

    const loadAndPlay = () => {
        if (!sequence) return;
        loadSequence(sequence);
        play();
    };

    return { sequence, isWebGLSupported: webglSupported, isLoading, loadAndPlay };
}
