'use client';

import type { CouncilStatus, Election } from '@/types/politics';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { Button } from '@/components/ui/8bit/button';
import { Vote, Users, Trophy } from 'lucide-react';

interface AlliancePanelProps {
  leaderName?: string;
  councilStatus: CouncilStatus | null;
  activeElection: Election | null;
  onStartElection: () => void;
  onCastVote: (candidateId: number) => void;
  isCouncilChair: boolean;
  myOfficerId: number;
}

export function AlliancePanel({
  leaderName,
  councilStatus,
  activeElection,
  onStartElection,
  onCastVote,
  isCouncilChair,
  myOfficerId,
}: AlliancePanelProps) {
  return (
    <div className="space-y-4">
      {/* Council Chair */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm">
            <Trophy className="size-4 text-blue-500" />
            최고평의회 의장
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-lg font-bold">{leaderName ?? '공석'}</p>
          <p className="text-xs text-muted-foreground">민주공화제 - 자유행성동맹</p>
        </CardContent>
      </Card>

      {/* Supreme Council */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm">
            <Users className="size-4" />
            최고평의회 (11석)
          </CardTitle>
        </CardHeader>
        <CardContent>
          {!councilStatus || councilStatus.seats.length === 0 ? (
            <p className="text-xs text-muted-foreground">평의회가 구성되지 않았습니다.</p>
          ) : (
            <div className="space-y-1">
              {councilStatus.seats.map((seat) => (
                <div key={seat.seatCode} className="flex items-center justify-between text-sm">
                  <span className="text-muted-foreground">{seat.nameKo}</span>
                  <span className={seat.officerName ? 'font-medium' : 'text-muted-foreground'}>
                    {seat.officerName ?? '공석'}
                  </span>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Elections */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm">
            <Vote className="size-4 text-green-500" />
            선거
          </CardTitle>
        </CardHeader>
        <CardContent>
          {activeElection ? (
            <div className="space-y-2">
              <div className="flex items-center justify-between text-sm">
                <span>유형</span>
                <span>
                  {activeElection.type === 'COUNCIL_CHAIR' ? '의장 선거' :
                   activeElection.type === 'SINGLE_SEAT' ? '보궐 선거' :
                   activeElection.type === 'CONFIDENCE_VOTE' ? '신임투표' : activeElection.type}
                </span>
              </div>
              <div className="space-y-1 pt-2">
                <p className="text-xs font-medium">후보 목록</p>
                {activeElection.candidates.length === 0 ? (
                  <p className="text-xs text-muted-foreground">아직 후보가 없습니다.</p>
                ) : (
                  activeElection.candidates.map((c) => (
                    <div key={c.officerId} className="flex items-center justify-between text-sm">
                      <span>{c.officerName}</span>
                      <div className="flex items-center gap-2">
                        <span className="text-xs text-muted-foreground">{c.votes}표</span>
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => onCastVote(c.officerId)}
                          disabled={activeElection.isCompleted}
                        >
                          투표
                        </Button>
                      </div>
                    </div>
                  ))
                )}
              </div>
              {activeElection.isCompleted && activeElection.winnerId && (
                <p className="text-sm text-green-400 pt-2">
                  당선자 결정!
                </p>
              )}
            </div>
          ) : (
            <div className="space-y-2">
              <p className="text-xs text-muted-foreground">진행 중인 선거가 없습니다.</p>
              {isCouncilChair && (
                <Button size="sm" variant="outline" onClick={onStartElection}>
                  선거 실시
                </Button>
              )}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
