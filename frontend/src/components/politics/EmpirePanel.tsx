'use client';

import type { CoupStatus, NobilityEntry } from '@/types/politics';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { Button } from '@/components/ui/8bit/button';
import { Crown, Swords, Users } from 'lucide-react';

interface EmpirePanelProps {
  leaderName?: string;
  coupStatus: CoupStatus | null;
  nobilityList: NobilityEntry[];
  myOfficerRank: number;
  onInitiateCoup: () => void;
  onJoinCoup: () => void;
  onAbortCoup: () => void;
  isLeader: boolean;
}

const NOBILITY_LABELS: Record<string, { label: string; color: string }> = {
  DUKE: { label: '공작', color: 'text-yellow-400' },
  MARQUIS: { label: '후작', color: 'text-orange-400' },
  COUNT: { label: '백작', color: 'text-blue-400' },
  BARON: { label: '남작', color: 'text-green-400' },
  COMMONER: { label: '평민', color: 'text-muted-foreground' },
};

export function EmpirePanel({
  leaderName,
  coupStatus,
  nobilityList,
  myOfficerRank,
  onInitiateCoup,
  onJoinCoup,
  onAbortCoup,
  isLeader,
}: EmpirePanelProps) {
  return (
    <div className="space-y-4">
      {/* Sovereign */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm">
            <Crown className="size-4 text-yellow-500" />
            원수
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-lg font-bold">{leaderName ?? '공석'}</p>
          <p className="text-xs text-muted-foreground">전제군주제 - 은하제국</p>
        </CardContent>
      </Card>

      {/* Nobility */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm">
            <Users className="size-4" />
            귀족 서열
          </CardTitle>
        </CardHeader>
        <CardContent>
          {nobilityList.length === 0 ? (
            <p className="text-xs text-muted-foreground">작위를 가진 장교가 없습니다.</p>
          ) : (
            <div className="space-y-1">
              {nobilityList.map((entry) => {
                const info = NOBILITY_LABELS[entry.rank] ?? NOBILITY_LABELS.COMMONER;
                return (
                  <div key={entry.officerId} className="flex items-center justify-between text-sm">
                    <span>{entry.officerName}</span>
                    <span className={info.color}>{info.label} (+{(entry.politicsBonus * 100).toFixed(0)}%)</span>
                  </div>
                );
              })}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Coup */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm">
            <Swords className="size-4 text-red-500" />
            쿠데타
          </CardTitle>
        </CardHeader>
        <CardContent>
          {coupStatus ? (
            <div className="space-y-2">
              <div className="flex items-center justify-between text-sm">
                <span>상태</span>
                <span className={coupStatus.phase === 'ACTIVE' ? 'text-red-400 font-bold' : 'text-yellow-400'}>
                  {coupStatus.phase === 'PLANNING' ? '비밀 모의 중' :
                   coupStatus.phase === 'ACTIVE' ? '쿠데타 진행 중' : coupStatus.phase}
                </span>
              </div>
              <div className="flex items-center justify-between text-sm">
                <span>주동자</span>
                <span>{coupStatus.leaderName}</span>
              </div>
              <div className="flex items-center justify-between text-sm">
                <span>지지자</span>
                <span>{coupStatus.supporterCount}명</span>
              </div>
              <div className="space-y-1">
                <div className="flex items-center justify-between text-xs">
                  <span>정치공작</span>
                  <span>{coupStatus.politicalPower} / {coupStatus.threshold}</span>
                </div>
                <div className="w-full bg-muted rounded-full h-2">
                  <div
                    className="bg-red-500 h-2 rounded-full transition-all"
                    style={{ width: `${Math.min(100, (coupStatus.politicalPower / coupStatus.threshold) * 100)}%` }}
                  />
                </div>
              </div>
              <div className="flex gap-2 pt-2">
                {!isLeader && coupStatus.phase === 'PLANNING' && (
                  <Button size="sm" variant="outline" onClick={onJoinCoup}>참여</Button>
                )}
                {isLeader && coupStatus.phase === 'PLANNING' && (
                  <Button size="sm" variant="destructive" onClick={onAbortCoup}>중단</Button>
                )}
              </div>
            </div>
          ) : (
            <div className="space-y-2">
              <p className="text-xs text-muted-foreground">진행 중인 쿠데타가 없습니다.</p>
              {myOfficerRank >= 5 && (
                <Button size="sm" variant="outline" onClick={onInitiateCoup}>
                  쿠데타 모의 시작
                </Button>
              )}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
