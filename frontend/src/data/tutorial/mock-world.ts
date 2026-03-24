import type { WorldState } from '@/types';

export const MOCK_WORLD: WorldState = {
    id: -1,
    name: '튜토리얼',
    scenarioCode: 'tutorial',
    commitSha: 'tutorial',
    gameVersion: 'tutorial',
    currentYear: 200,
    currentMonth: 1,
    tickSeconds: 0,
    realtimeMode: false,
    commandPointRegenRate: 0,
    config: {},
    meta: { playerCount: 5, generalCntLimit: 50, nationCount: 2 },
    updatedAt: new Date().toISOString(),
};
