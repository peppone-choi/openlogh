import type { Diplomacy } from '@/types';

export const MOCK_DIPLOMACY: Diplomacy[] = [
    {
        id: -1,
        worldId: -1,
        srcNationId: -1,
        destNationId: -2,
        stateCode: 'neutral',
        term: 0,
        isDead: false,
        isShowing: true,
    },
    {
        id: -2,
        worldId: -1,
        srcNationId: -2,
        destNationId: -1,
        stateCode: 'neutral',
        term: 0,
        isDead: false,
        isShowing: true,
    },
];

/** 외교 서신 발송 후 추가되는 mock diplomacy (동맹 제안) */
export const MOCK_DIPLOMACY_AFTER_LETTER: Diplomacy[] = [
    ...MOCK_DIPLOMACY,
    {
        id: -3,
        worldId: -1,
        srcNationId: -1,
        destNationId: -2,
        stateCode: 'alliance_proposed',
        term: 12,
        isDead: false,
        isShowing: true,
    },
];
