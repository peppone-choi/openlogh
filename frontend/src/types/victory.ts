export type VictoryCondition = 'CAPITAL_CAPTURE' | 'SYSTEM_THRESHOLD' | 'TIME_LIMIT';
export type VictoryTier = 'DECISIVE' | 'LIMITED' | 'LOCAL' | 'DEFEAT';

export const VICTORY_CONDITION_LABELS: Record<VictoryCondition, string> = {
    CAPITAL_CAPTURE: '수도 함락',
    SYSTEM_THRESHOLD: '성계 열세',
    TIME_LIMIT: '시간 제한',
};

export const VICTORY_TIER_LABELS: Record<VictoryTier, string> = {
    DECISIVE: '결정적 승리',
    LIMITED: '한정적 승리',
    LOCAL: '국지적 승리',
    DEFEAT: '패배',
};

export interface VictoryResult {
    condition: VictoryCondition;
    conditionKorean: string;
    tier: VictoryTier;
    tierKorean: string;
    winnerFactionId: number;
    loserFactionId: number;
    winnerName: string;
    loserName: string;
    stats: Record<string, unknown>;
}

export interface SessionRanking {
    rank: number;
    officerId: number;
    officerName: string;
    factionId: number;
    factionName: string;
    score: number;
    meritPoints: number;
    rankLevel: number;
    kills: number;
    territoryCaptured: number;
    isPlayer: boolean;
}
