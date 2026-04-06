'use client';

import { useCallback, useEffect } from 'react';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { usePoliticsStore } from '@/stores/politicsStore';
import { PoliticsOverview } from '@/components/politics/PoliticsOverview';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { EmptyState } from '@/components/game/empty-state';
import { Landmark } from 'lucide-react';

export default function PoliticsPage() {
  const currentWorld = useWorldStore((s) => s.currentWorld);
  const { myOfficer, fetchMyOfficer } = useOfficerStore();
  const {
    overview,
    nobilityList,
    intelOffers,
    loading,
    error,
    fetchOverview,
    fetchNobility,
    fetchIntelOffers,
    initiateCoup,
    joinCoup,
    abortCoup,
    startElection,
    castVote,
    takeLoan,
    repayLoan,
    buyIntel,
    clearError,
  } = usePoliticsStore();

  useEffect(() => {
    if (currentWorld && myOfficer) {
      fetchOverview(currentWorld.id, myOfficer.nationId);
      fetchIntelOffers(currentWorld.id);
      if (overview?.factionType === 'empire') {
        fetchNobility(currentWorld.id, myOfficer.nationId);
      }
    }
  }, [currentWorld, myOfficer, fetchOverview, fetchIntelOffers, fetchNobility, overview?.factionType]);

  useEffect(() => {
    if (currentWorld && !myOfficer) {
      fetchMyOfficer(currentWorld.id).catch(() => {});
    }
  }, [currentWorld, myOfficer, fetchMyOfficer]);

  const handleInitiateCoup = useCallback(() => {
    if (currentWorld && myOfficer) {
      initiateCoup(currentWorld.id, myOfficer.nationId, myOfficer.id);
    }
  }, [currentWorld, myOfficer, initiateCoup]);

  const handleJoinCoup = useCallback(() => {
    if (currentWorld && myOfficer && overview?.activeCoup) {
      joinCoup(currentWorld.id, overview.activeCoup.coupId, myOfficer.id);
    }
  }, [currentWorld, myOfficer, overview, joinCoup]);

  const handleAbortCoup = useCallback(() => {
    if (currentWorld && myOfficer && overview?.activeCoup) {
      abortCoup(currentWorld.id, overview.activeCoup.coupId, myOfficer.id);
    }
  }, [currentWorld, myOfficer, overview, abortCoup]);

  const handleStartElection = useCallback(() => {
    if (currentWorld && myOfficer) {
      startElection(currentWorld.id, myOfficer.nationId);
    }
  }, [currentWorld, myOfficer, startElection]);

  const handleCastVote = useCallback((candidateId: number) => {
    if (currentWorld && myOfficer && overview?.activeElection) {
      castVote(currentWorld.id, myOfficer.id, overview.activeElection.electionId, candidateId);
    }
  }, [currentWorld, myOfficer, overview, castVote]);

  const handleTakeLoan = useCallback((amount: number) => {
    if (currentWorld && myOfficer) {
      takeLoan(currentWorld.id, myOfficer.nationId, amount);
    }
  }, [currentWorld, myOfficer, takeLoan]);

  const handleRepayLoan = useCallback((loanId: number, amount: number) => {
    if (currentWorld) {
      repayLoan(currentWorld.id, loanId, amount);
    }
  }, [currentWorld, repayLoan]);

  const handleBuyIntel = useCallback((type: string) => {
    if (currentWorld && myOfficer) {
      // For now, buy intel against the "other" faction
      // In a real implementation, user would select target faction
      buyIntel(currentWorld.id, myOfficer.nationId, 0, type);
    }
  }, [currentWorld, myOfficer, buyIntel]);

  if (!currentWorld) return <div className="p-4 text-muted-foreground">월드를 선택해주세요.</div>;
  if (loading) return <LoadingState />;

  return (
    <div className="space-y-4 max-w-5xl mx-auto">
      <PageHeader
        icon={Landmark}
        title="진영 정치"
        description={
          overview?.governanceType === 'autocracy' ? '전제군주제 - 은하제국' :
          overview?.governanceType === 'democracy' ? '민주공화제 - 자유행성동맹' :
          '진영 정치 시스템'
        }
      />

      {error && (
        <div className="bg-destructive/10 border border-destructive rounded p-3 text-sm text-destructive flex items-center justify-between">
          <span>{error}</span>
          <button className="text-xs underline" onClick={clearError}>닫기</button>
        </div>
      )}

      {!overview ? (
        <EmptyState icon={Landmark} title="정치 정보를 불러오는 중..." />
      ) : (
        <PoliticsOverview
          overview={overview}
          nobilityList={nobilityList}
          intelOffers={intelOffers}
          myOfficerId={myOfficer?.id ?? 0}
          myOfficerRank={myOfficer?.officerLevel ?? 0}
          onInitiateCoup={handleInitiateCoup}
          onJoinCoup={handleJoinCoup}
          onAbortCoup={handleAbortCoup}
          onStartElection={handleStartElection}
          onCastVote={handleCastVote}
          onTakeLoan={handleTakeLoan}
          onRepayLoan={handleRepayLoan}
          onBuyIntel={handleBuyIntel}
        />
      )}
    </div>
  );
}
