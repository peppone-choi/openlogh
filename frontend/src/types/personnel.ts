// Personnel & Rank types matching backend PersonnelDtos

export interface RankLadderEntry {
    officerId: number;
    name: string;
    rankTier: number;
    rankTitle: string;
    rankTitleKo: string;
    meritPoints: number;
    famePoints: number;
    totalStats: number;
}

export interface PersonnelInfo {
    officerId: number;
    name: string;
    rankTier: number;
    rankTitle: string;
    rankTitleKo: string;
    meritPoints: number;
    evaluationPoints: number;
    famePoints: number;
    promotionEligible: boolean;
    nextRankTitle: string | null;
    nextRankTitleKo: string | null;
}

export interface PromoteDemoteRequest {
    officerId: number;
}

export interface PersonnelActionResponse {
    success: boolean;
    message: string;
    updatedOfficer: PersonnelInfo | null;
}

/** Rank tier display info */
export const RANK_TIERS = [
    { tier: 0, empire: 'Sub-Lieutenant', alliance: 'Sub-Lieutenant', ko: '소위' },
    { tier: 1, empire: 'Lieutenant', alliance: 'Lieutenant', ko: '대위' },
    { tier: 2, empire: 'Lieutenant Commander', alliance: 'Lieutenant Commander', ko: '소령' },
    { tier: 3, empire: 'Commander', alliance: 'Commander', ko: '중령' },
    { tier: 4, empire: 'Captain', alliance: 'Captain', ko: '대령' },
    { tier: 5, empire: 'Commodore', alliance: 'Commodore', ko: '준장' },
    { tier: 6, empire: 'Rear Admiral', alliance: 'Rear Admiral', ko: '소장' },
    { tier: 7, empire: 'Vice Admiral', alliance: 'Vice Admiral', ko: '중장' },
    { tier: 8, empire: 'Admiral', alliance: 'Admiral', ko: '대장' },
    { tier: 9, empire: 'Fleet Admiral', alliance: 'Admiral of the Fleet', ko: '상급대장' },
    { tier: 10, empire: 'Reichsmarschall', alliance: 'Fleet Admiral', ko: '원수' },
] as const;

/** Headcount limits matching RankHeadcount.kt */
export const RANK_HEADCOUNT_LIMITS: Record<number, number> = {
    10: 5,
    9: 5,
    8: 10,
    7: 20,
    6: 40,
    5: 80,
};

/** Get the headcount limit for a tier, or Infinity for unlimited tiers */
export function getRankHeadcountLimit(tier: number): number {
    return RANK_HEADCOUNT_LIMITS[tier] ?? Infinity;
}
