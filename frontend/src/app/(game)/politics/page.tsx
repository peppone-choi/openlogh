'use client';

import { useCallback, useEffect, useState } from 'react';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { usePoliticsStore } from '@/stores/politicsStore';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { EmptyState } from '@/components/game/empty-state';
import { EmpirePoliticsPanel } from '@/components/game/empire-politics-panel';
import { AlliancePoliticsPanel } from '@/components/game/alliance-politics-panel';
import { FezzanPoliticsPanel } from '@/components/game/fezzan-politics-panel';
import { Landmark } from 'lucide-react';

type TabKey = 'empire' | 'alliance' | 'fezzan';

const TAB_LABELS: Record<TabKey, string> = {
  empire: '제국 정치',
  alliance: '동맹 정치',
  fezzan: '페잔 현황',
};

const FACTION_SUBTITLES: Record<string, string> = {
  empire: '은하제국',
  alliance: '자유행성동맹',
  fezzan: '페잔 자치령',
  rebel: '반란군',
};

export default function PoliticsPage() {
  const currentWorld = useWorldStore((s) => s.currentWorld);
  const { myOfficer, fetchMyOfficer } = useOfficerStore();
  const { factionType, fetchFactionType, loading } = usePoliticsStore();

  const [activeTab, setActiveTab] = useState<TabKey | null>(null);

  useEffect(() => {
    if (currentWorld && !myOfficer) {
      fetchMyOfficer(currentWorld.id).catch(() => {});
    }
  }, [currentWorld, myOfficer, fetchMyOfficer]);

  useEffect(() => {
    // General uses nationId; Officer type alias uses factionId — support both
    const resolvedFactionId =
      (myOfficer as unknown as { factionId?: number }).factionId ?? myOfficer?.nationId;
    if (currentWorld && resolvedFactionId) {
      fetchFactionType(currentWorld.id, resolvedFactionId);
    }
  }, [currentWorld, myOfficer, fetchFactionType]);

  useEffect(() => {
    if (factionType && activeTab === null) {
      const defaultTab: TabKey =
        factionType === 'empire' ? 'empire'
        : factionType === 'alliance' ? 'alliance'
        : 'fezzan';
      setActiveTab(defaultTab);
    }
  }, [factionType, activeTab]);

  const handleTabChange = useCallback((tab: TabKey) => {
    setActiveTab(tab);
  }, []);

  if (!currentWorld) {
    return <div className="p-4 text-muted-foreground">월드를 선택해주세요.</div>;
  }

  if (loading || !myOfficer) {
    return <LoadingState message="장교 정보 로딩 중..." />;
  }

  if (!factionType || activeTab === null) {
    return <EmptyState icon={Landmark} title="진영 정보를 불러오는 중..." />;
  }

  const factionSubtitle = FACTION_SUBTITLES[factionType] ?? '진영 정치 시스템';

  return (
    <div className="space-y-4 max-w-5xl mx-auto">
      <PageHeader
        icon={Landmark}
        title="진영 정치"
        description={factionSubtitle}
      />

      {/* Tab navigation */}
      <div className="flex border-b border-border">
        {(Object.keys(TAB_LABELS) as TabKey[]).map((tab) => (
          <button
            key={tab}
            onClick={() => handleTabChange(tab)}
            className={`px-4 py-2 text-sm font-medium transition-colors border-b-2 -mb-px ${
              activeTab === tab
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            {TAB_LABELS[tab]}
            {factionType === tab && (
              <span className="ml-1.5 inline-block size-1.5 rounded-full bg-primary align-middle" />
            )}
          </button>
        ))}
      </div>

      {/* Faction panel */}
      <div>
        {activeTab === 'empire' && (
          <EmpirePoliticsPanel
            sessionId={currentWorld.id}
            officerId={myOfficer.id}
          />
        )}
        {activeTab === 'alliance' && (
          <AlliancePoliticsPanel
            sessionId={currentWorld.id}
            officerId={myOfficer.id}
          />
        )}
        {activeTab === 'fezzan' && (
          <FezzanPoliticsPanel sessionId={currentWorld.id} />
        )}
      </div>

      {factionType === 'rebel' && activeTab === null && (
        <div className="rounded border border-border bg-muted/10 p-6 text-center text-sm text-muted-foreground">
          반란군은 독립 정치 체계가 없습니다. 커맨드를 통해 활동하세요.
        </div>
      )}
    </div>
  );
}
