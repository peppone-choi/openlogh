// ========== Enums / Literal Types ==========

export type NobilityRank = 'COMMONER' | 'BARON' | 'COUNT' | 'MARQUIS' | 'DUKE';
export type CoupPhase = 'PLANNING' | 'ACTIVE' | 'SUCCESS' | 'FAILED' | 'ABORTED';
export type ElectionTypeCode = 'COUNCIL_CHAIR' | 'SINGLE_SEAT' | 'CONFIDENCE_VOTE';
export type GovernanceType = 'autocracy' | 'democracy' | 'npc' | 'unknown';
export type IntelligenceTypeCode =
  | 'FLEET_POSITIONS'
  | 'PLANET_RESOURCES'
  | 'OFFICER_INFO'
  | 'MILITARY_POWER'
  | 'COUP_INTEL';

// ========== Empire ==========

export interface CoupStatus {
  coupId: number;
  phase: CoupPhase;
  leaderId: number;
  leaderName: string;
  supporterCount: number;
  politicalPower: number;
  threshold: number;
  startedAt: string;
}

export interface NobilityEntry {
  officerId: number;
  officerName: string;
  rank: NobilityRank;
  nameKo: string;
  politicsBonus: number;
}

// ========== Alliance ==========

export interface CouncilSeat {
  seatCode: string;
  nameKo: string;
  officerId?: number;
  officerName?: string;
  electedAt?: string;
  termEndAt?: string;
}

export interface CouncilStatus {
  seats: CouncilSeat[];
}

export interface Candidate {
  officerId: number;
  officerName: string;
  votes: number;
}

export interface Election {
  electionId: number;
  type: ElectionTypeCode;
  startedAt: string;
  candidates: Candidate[];
  isCompleted: boolean;
  winnerId?: number;
}

// ========== Fezzan ==========

export interface Loan {
  loanId: number;
  principal: number;
  interestRate: number;
  remainingDebt: number;
  issuedAt: string;
  dueAt: string;
  isDefaulted: boolean;
}

export interface IntelligenceOffer {
  type: IntelligenceTypeCode;
  nameKo: string;
  cost: number;
  description: string;
}

// ========== Overview ==========

export interface FactionPoliticsOverview {
  factionId: number;
  factionType: string;
  governanceType: GovernanceType;
  leaderName?: string;
  leaderId?: number;
  councilStatus?: CouncilStatus;
  activeCoup?: CoupStatus;
  activeElection?: Election;
  loans: Loan[];
  fezzanOperational: boolean;
}
