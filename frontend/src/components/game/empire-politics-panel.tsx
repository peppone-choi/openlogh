'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import axios from 'axios';
import { Crown, ChevronDown, ChevronRight, Swords } from 'lucide-react';
import { LoadingState } from '@/components/game/loading-state';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { Button } from '@/components/ui/8bit/button';

// ── Types ──────────────────────────────────────────────────────────────────

type CoupPhase = 'STABLE' | 'BREWING' | 'ESCALATED' | 'CIVIL_WAR';
type NobilityRank = 'BARON' | 'VISCOUNT' | 'COUNT' | 'MARQUIS' | 'DUKE' | 'ARCHDUKE';

interface Fief {
  planetId: number;
  planetName: string;
  revenue: number;
}

interface NobilityEntry {
  officerId: number;
  officerName: string;
  rank: NobilityRank;
  fiefs: Fief[];
}

interface EmpirePoliticsData {
  coupPhase: CoupPhase;
  nobilityRanks: NobilityEntry[];
  myNobilityRank?: NobilityRank;
}

// ── Constants ──────────────────────────────────────────────────────────────

const COUP_PHASE_CONFIG: Record<CoupPhase, { label: string; badgeClass: string; description: string }> = {
  STABLE: {
    label: '안정',
    badgeClass: 'bg-green-900/60 text-green-400 border border-green-700',
    description: '제국은 현재 안정적입니다.',
  },
  BREWING: {
    label: '긴장',
    badgeClass: 'bg-yellow-900/60 text-yellow-400 border border-yellow-700',
    description: '쿠데타 세력이 결집하고 있습니다.',
  },
  ESCALATED: {
    label: '격화',
    badgeClass: 'bg-orange-900/60 text-orange-400 border border-orange-700',
    description: '정치적 대립이 격화되고 있습니다.',
  },
  CIVIL_WAR: {
    label: '내전',
    badgeClass: 'bg-red-900/60 text-red-400 border border-red-700',
    description: '내전이 발발하였습니다!',
  },
};

const NOBILITY_LABELS: Record<NobilityRank, string> = {
  BARON: '남작',
  VISCOUNT: '자작',
  COUNT: '백작',
  MARQUIS: '후작',
  DUKE: '공작',
  ARCHDUKE: '대공',
};

// ── Component ──────────────────────────────────────────────────────────────

interface EmpirePoliticsPanelProps {
  sessionId: number;
  officerId: number;
}

export function EmpirePoliticsPanel({ sessionId, officerId }: EmpirePoliticsPanelProps) {
  const router = useRouter();
  const [data, setData] = useState<EmpirePoliticsData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [expandedRows, setExpandedRows] = useState<Set<number>>(new Set());

  useEffect(() => {
    setLoading(true);
    axios
      .get<EmpirePoliticsData>(`/api/${sessionId}/politics/empire`)
      .then(({ data: d }) => setData(d))
      .catch((e) => setError(e?.response?.data?.message ?? e.message))
      .finally(() => setLoading(false));
  }, [sessionId]);

  if (loading) return <LoadingState message="제국 정치 정보 로딩 중..." />;

  if (error) {
    return (
      <div className="rounded border border-red-700 bg-red-900/30 p-4 text-sm text-red-400">
        오류: {error}
      </div>
    );
  }

  if (!data) return null;

  const coupCfg = COUP_PHASE_CONFIG[data.coupPhase] ?? COUP_PHASE_CONFIG.STABLE;

  const toggleRow = (officerId: number) => {
    setExpandedRows((prev) => {
      const next = new Set(prev);
      if (next.has(officerId)) next.delete(officerId);
      else next.add(officerId);
      return next;
    });
  };

  return (
    <div className="space-y-4">
      {/* My nobility rank */}
      {data.myNobilityRank && (
        <div className="flex items-center gap-3 rounded border border-yellow-700 bg-yellow-900/20 px-4 py-3">
          <Crown className="size-5 text-yellow-400" />
          <div>
            <p className="text-xs text-yellow-500">내 작위</p>
            <p className="font-bold text-yellow-300">{NOBILITY_LABELS[data.myNobilityRank]}</p>
          </div>
        </div>
      )}

      {/* Coup phase indicator */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm">
            <Swords className="size-4 text-red-400" />
            쿠데타 단계
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          <span className={`inline-block rounded px-3 py-1 text-sm font-bold ${coupCfg.badgeClass}`}>
            {coupCfg.label}
          </span>
          <p className="text-xs text-muted-foreground">{coupCfg.description}</p>
        </CardContent>
      </Card>

      {/* Nobility ranks */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm">
            <Crown className="size-4 text-yellow-400" />
            귀족 작위
          </CardTitle>
        </CardHeader>
        <CardContent>
          {data.nobilityRanks.length === 0 ? (
            <p className="text-xs text-muted-foreground">작위를 가진 장교가 없습니다.</p>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border text-xs text-muted-foreground">
                  <th className="pb-2 text-left font-medium">장교명</th>
                  <th className="pb-2 text-left font-medium">작위</th>
                  <th className="pb-2 text-right font-medium">봉토 수</th>
                </tr>
              </thead>
              <tbody>
                {data.nobilityRanks.map((entry) => (
                  <>
                    <tr
                      key={entry.officerId}
                      className="cursor-pointer border-b border-border/40 hover:bg-muted/20"
                      onClick={() => toggleRow(entry.officerId)}
                    >
                      <td className="py-1.5">
                        <span className="flex items-center gap-1">
                          {expandedRows.has(entry.officerId) ? (
                            <ChevronDown className="size-3 text-muted-foreground" />
                          ) : (
                            <ChevronRight className="size-3 text-muted-foreground" />
                          )}
                          {entry.officerId === officerId ? (
                            <span className="font-bold text-yellow-300">{entry.officerName}</span>
                          ) : (
                            entry.officerName
                          )}
                        </span>
                      </td>
                      <td className="py-1.5 text-yellow-400">
                        {NOBILITY_LABELS[entry.rank] ?? entry.rank}
                      </td>
                      <td className="py-1.5 text-right">{entry.fiefs.length}</td>
                    </tr>
                    {expandedRows.has(entry.officerId) && entry.fiefs.length > 0 && (
                      <tr key={`${entry.officerId}-fiefs`}>
                        <td colSpan={3} className="px-4 pb-2">
                          <div className="rounded bg-muted/10 px-3 py-2 text-xs space-y-1">
                            {entry.fiefs.map((fief) => (
                              <div key={fief.planetId} className="flex justify-between text-muted-foreground">
                                <span>{fief.planetName}</span>
                                <span className="text-yellow-500">+{fief.revenue.toLocaleString()} 자금</span>
                              </div>
                            ))}
                          </div>
                        </td>
                      </tr>
                    )}
                  </>
                ))}
              </tbody>
            </table>
          )}
        </CardContent>
      </Card>

      {/* Politics command shortcut */}
      <div className="flex justify-end">
        <Button
          size="sm"
          variant="outline"
          onClick={() => router.push('/commands?group=POLITICS')}
        >
          정치 커맨드 실행
        </Button>
      </div>
    </div>
  );
}
