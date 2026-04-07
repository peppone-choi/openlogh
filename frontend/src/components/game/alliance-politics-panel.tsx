'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import axios from 'axios';
import { Vote, Users, BarChart2 } from 'lucide-react';
import { toast } from 'sonner';
import { LoadingState } from '@/components/game/loading-state';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { Button } from '@/components/ui/8bit/button';

// ── Types ──────────────────────────────────────────────────────────────────

type CouncilSeatCode =
  | 'DEFENSE'
  | 'FOREIGN'
  | 'INTERNAL'
  | 'FINANCE'
  | 'INTELLIGENCE'
  | 'FLEET_CMD'
  | 'GROUND_CMD';

type ElectionType = 'PARLIAMENTARY' | 'PRESIDENTIAL';

interface CouncilSeat {
  code: CouncilSeatCode;
  holderId?: number;
  holderName?: string;
}

interface ElectionCandidate {
  officerId: number;
  officerName: string;
  votes: number;
}

interface UpcomingElection {
  electionId: number;
  type: ElectionType;
  scheduledDate: string;
  candidates: ElectionCandidate[];
}

interface AlliancePoliticsData {
  seats: CouncilSeat[];
  elections: UpcomingElection[];
  democracyIndex: number;
}

// ── Constants ──────────────────────────────────────────────────────────────

const SEAT_LABELS: Record<CouncilSeatCode, string> = {
  DEFENSE: '국방',
  FOREIGN: '외교',
  INTERNAL: '내무',
  FINANCE: '재무',
  INTELLIGENCE: '정보',
  FLEET_CMD: '우주군사령',
  GROUND_CMD: '지상군사령',
};

const ELECTION_TYPE_LABELS: Record<ElectionType, string> = {
  PARLIAMENTARY: '의회선거',
  PRESIDENTIAL: '최고평의원선거',
};

// ── Helpers ────────────────────────────────────────────────────────────────

function getDemocracyLabel(index: number): string {
  if (index >= 70) return '높음';
  if (index >= 40) return '보통';
  return '낮음';
}

function getDemocracyColor(index: number): string {
  if (index >= 70) return 'bg-blue-500';
  if (index >= 40) return 'bg-blue-400/60';
  return 'bg-blue-300/40';
}

// ── Component ──────────────────────────────────────────────────────────────

interface AlliancePoliticsPanelProps {
  sessionId: number;
  officerId: number;
}

export function AlliancePoliticsPanel({ sessionId, officerId }: AlliancePoliticsPanelProps) {
  const router = useRouter();
  const [data, setData] = useState<AlliancePoliticsData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    axios
      .get<AlliancePoliticsData>(`/api/${sessionId}/politics/alliance`)
      .then(({ data: d }) => setData(d))
      .catch((e) => setError(e?.response?.data?.message ?? e.message))
      .finally(() => setLoading(false));
  }, [sessionId]);

  if (loading) return <LoadingState message="동맹 정치 정보 로딩 중..." />;

  if (error) {
    return (
      <div className="rounded border border-red-700 bg-red-900/30 p-4 text-sm text-red-400">
        오류: {error}
      </div>
    );
  }

  if (!data) return null;

  const democracyLabel = getDemocracyLabel(data.democracyIndex);
  const democracyBarColor = getDemocracyColor(data.democracyIndex);
  const mySeat = data.seats.find((s) => s.holderId === officerId);

  return (
    <div className="space-y-4">
      {/* My seat highlight */}
      {mySeat && (
        <div className="flex items-center gap-3 rounded border border-blue-700 bg-blue-900/20 px-4 py-3">
          <Users className="size-5 text-blue-400" />
          <div>
            <p className="text-xs text-blue-400">내 의석</p>
            <p className="font-bold text-blue-300">{SEAT_LABELS[mySeat.code] ?? mySeat.code}</p>
          </div>
        </div>
      )}

      {/* Democracy index */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm">
            <BarChart2 className="size-4 text-blue-400" />
            민주주의 지수
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-2">
          <div className="flex items-center justify-between text-sm">
            <span className="text-muted-foreground">현재 지수</span>
            <span className="font-bold text-blue-300">
              {data.democracyIndex} / 100{' '}
              <span className="text-xs text-muted-foreground">({democracyLabel})</span>
            </span>
          </div>
          <div className="h-3 w-full overflow-hidden rounded-full bg-muted">
            <div
              className={`h-full rounded-full transition-all ${democracyBarColor}`}
              style={{ width: `${Math.min(100, Math.max(0, data.democracyIndex))}%` }}
            />
          </div>
        </CardContent>
      </Card>

      {/* Council seats */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm">
            <Users className="size-4 text-blue-400" />
            의회 의석
          </CardTitle>
        </CardHeader>
        <CardContent>
          {data.seats.length === 0 ? (
            <p className="text-xs text-muted-foreground">의석 정보가 없습니다.</p>
          ) : (
            <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
              {data.seats.map((seat) => {
                const isMySeats = seat.holderId === officerId;
                return (
                  <div
                    key={seat.code}
                    className={`rounded border p-2 text-xs ${
                      isMySeats
                        ? 'border-blue-600 bg-blue-900/30'
                        : 'border-border bg-muted/10'
                    }`}
                  >
                    <p className={`font-semibold ${isMySeats ? 'text-blue-300' : 'text-foreground'}`}>
                      {SEAT_LABELS[seat.code as CouncilSeatCode] ?? seat.code}
                    </p>
                    <p className={`mt-0.5 ${seat.holderName ? 'text-foreground' : 'text-muted-foreground'}`}>
                      {seat.holderName ?? '공석'}
                    </p>
                  </div>
                );
              })}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Elections */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm">
            <Vote className="size-4 text-green-400" />
            선거 일정
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {data.elections.length === 0 ? (
            <p className="text-xs text-muted-foreground">예정된 선거가 없습니다.</p>
          ) : (
            data.elections.map((election) => (
              <div
                key={election.electionId}
                className="rounded border border-border bg-muted/10 px-3 py-2 space-y-1"
              >
                <div className="flex items-center justify-between text-sm">
                  <span className="font-medium">
                    {ELECTION_TYPE_LABELS[election.type] ?? election.type}
                  </span>
                  <span className="text-xs text-muted-foreground">
                    {new Date(election.scheduledDate).toLocaleDateString('ko-KR')}
                  </span>
                </div>
                <div className="flex items-center justify-between text-xs text-muted-foreground">
                  <span>후보 {election.candidates.length}명</span>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => toast.info('투표 기능 준비 중입니다.')}
                  >
                    투표 참가
                  </Button>
                </div>
              </div>
            ))
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
