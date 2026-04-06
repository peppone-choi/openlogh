'use client';

import type { FactionPoliticsOverview, NobilityEntry, IntelligenceOffer } from '@/types/politics';
import { EmpirePanel } from './EmpirePanel';
import { AlliancePanel } from './AlliancePanel';
import { FezzanPanel } from './FezzanPanel';

interface PoliticsOverviewProps {
  overview: FactionPoliticsOverview;
  nobilityList: NobilityEntry[];
  intelOffers: IntelligenceOffer[];
  myOfficerId: number;
  myOfficerRank: number;
  onInitiateCoup: () => void;
  onJoinCoup: () => void;
  onAbortCoup: () => void;
  onStartElection: () => void;
  onCastVote: (candidateId: number) => void;
  onTakeLoan: (amount: number) => void;
  onRepayLoan: (loanId: number, amount: number) => void;
  onBuyIntel: (type: string) => void;
}

export function PoliticsOverview({
  overview,
  nobilityList,
  intelOffers,
  myOfficerId,
  myOfficerRank,
  onInitiateCoup,
  onJoinCoup,
  onAbortCoup,
  onStartElection,
  onCastVote,
  onTakeLoan,
  onRepayLoan,
  onBuyIntel,
}: PoliticsOverviewProps) {
  const isEmpire = overview.factionType === 'empire';
  const isAlliance = overview.factionType === 'alliance';
  const isCoupLeader = overview.activeCoup?.leaderId === myOfficerId;
  const isCouncilChair = overview.councilStatus?.seats.some(
    (s) => s.seatCode === 'COUNCIL_CHAIRMAN' && s.officerId === myOfficerId,
  ) ?? false;

  return (
    <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
      {/* Left: Faction-specific panel */}
      <div>
        {isEmpire && (
          <EmpirePanel
            leaderName={overview.leaderName}
            coupStatus={overview.activeCoup ?? null}
            nobilityList={nobilityList}
            myOfficerRank={myOfficerRank}
            onInitiateCoup={onInitiateCoup}
            onJoinCoup={onJoinCoup}
            onAbortCoup={onAbortCoup}
            isLeader={isCoupLeader}
          />
        )}
        {isAlliance && (
          <AlliancePanel
            leaderName={overview.leaderName}
            councilStatus={overview.councilStatus ?? null}
            activeElection={overview.activeElection ?? null}
            onStartElection={onStartElection}
            onCastVote={onCastVote}
            isCouncilChair={isCouncilChair}
            myOfficerId={myOfficerId}
          />
        )}
      </div>

      {/* Right: Fezzan panel (available to both factions) */}
      <div>
        <FezzanPanel
          loans={overview.loans}
          intelOffers={intelOffers}
          fezzanOperational={overview.fezzanOperational}
          onTakeLoan={onTakeLoan}
          onRepayLoan={onRepayLoan}
          onBuyIntel={onBuyIntel}
        />
      </div>
    </div>
  );
}
