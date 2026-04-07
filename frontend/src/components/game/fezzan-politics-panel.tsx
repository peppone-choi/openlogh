'use client';

import { useEffect, useState } from 'react';
import axios from 'axios';
import { Banknote, Eye, AlertTriangle } from 'lucide-react';
import { toast } from 'sonner';
import { LoadingState } from '@/components/game/loading-state';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { Button } from '@/components/ui/8bit/button';

// ── Types ──────────────────────────────────────────────────────────────────

interface LoanEntry {
  factionId: number;
  factionName: string;
  amount: number;
  interestRate: number;
  dueDate: string;
  repaid: boolean;
  overdue: boolean;
}

interface IntelItem {
  code: string;
  description: string;
  price: number;
}

interface FezzanPoliticsData {
  loans: LoanEntry[];
  intelligenceItems: IntelItem[];
}

// ── Component ──────────────────────────────────────────────────────────────

interface FezzanPoliticsPanelProps {
  sessionId: number;
}

export function FezzanPoliticsPanel({ sessionId }: FezzanPoliticsPanelProps) {
  const [data, setData] = useState<FezzanPoliticsData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    axios
      .get<FezzanPoliticsData>(`/api/${sessionId}/politics/fezzan`)
      .then(({ data: d }) => setData(d))
      .catch((e) => setError(e?.response?.data?.message ?? e.message))
      .finally(() => setLoading(false));
  }, [sessionId]);

  if (loading) return <LoadingState message="페잔 정보 로딩 중..." />;

  if (error) {
    return (
      <div className="rounded border border-red-700 bg-red-900/30 p-4 text-sm text-red-400">
        오류: {error}
      </div>
    );
  }

  const hasOverdue = data?.loans.some((l) => l.overdue) ?? false;

  return (
    <div className="space-y-4">
      {/* Header note */}
      <div className="rounded border border-yellow-800 bg-yellow-900/10 px-4 py-2 text-xs text-yellow-400">
        페잔 자치령은 NPC 전용 진영입니다. 차관 및 정보 거래만 가능합니다.
      </div>

      {/* Fezzan ending warning */}
      {hasOverdue && (
        <div className="flex items-center gap-2 rounded border border-red-700 bg-red-900/20 px-4 py-3 text-sm text-red-400">
          <AlertTriangle className="size-4 shrink-0" />
          <span>연체된 차관이 있습니다. 3건 연체 시 페잔 엔딩이 발동됩니다!</span>
        </div>
      )}

      {/* Loan status */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm">
            <Banknote className="size-4 text-yellow-400" />
            페잔 차관 현황
          </CardTitle>
        </CardHeader>
        <CardContent>
          {!data || data.loans.length === 0 ? (
            <p className="text-xs text-muted-foreground">차관 기록이 없습니다.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-border text-xs text-muted-foreground">
                    <th className="pb-2 text-left font-medium">진영명</th>
                    <th className="pb-2 text-right font-medium">차관액</th>
                    <th className="pb-2 text-right font-medium">이자율</th>
                    <th className="pb-2 text-right font-medium">상환기한</th>
                    <th className="pb-2 text-right font-medium">상환여부</th>
                  </tr>
                </thead>
                <tbody>
                  {data.loans.map((loan) => (
                    <tr
                      key={loan.factionId}
                      className={`border-b border-border/40 ${loan.overdue ? 'bg-red-900/10' : ''}`}
                    >
                      <td className="py-1.5">{loan.factionName}</td>
                      <td className="py-1.5 text-right text-yellow-400">
                        {loan.amount.toLocaleString()} 자금
                      </td>
                      <td className="py-1.5 text-right">
                        {(loan.interestRate * 100).toFixed(1)}%
                      </td>
                      <td className="py-1.5 text-right text-xs">
                        {new Date(loan.dueDate).toLocaleDateString('ko-KR')}
                      </td>
                      <td className="py-1.5 text-right">
                        {loan.repaid ? (
                          <span className="text-green-400">상환완료</span>
                        ) : loan.overdue ? (
                          <span className="font-bold text-red-400">연체</span>
                        ) : (
                          <span className="text-muted-foreground">상환중</span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Intelligence market */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm">
            <Eye className="size-4 text-purple-400" />
            정보 거래
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {!data || data.intelligenceItems.length === 0 ? (
            <p className="text-xs text-muted-foreground">현재 거래 가능한 정보가 없습니다.</p>
          ) : (
            data.intelligenceItems.map((item) => (
              <div
                key={item.code}
                className="flex items-center justify-between gap-4 rounded border border-border bg-muted/10 px-3 py-2"
              >
                <div className="min-w-0">
                  <p className="text-sm font-medium">{item.description}</p>
                  <p className="text-xs text-muted-foreground">{item.code}</p>
                </div>
                <Button
                  size="sm"
                  variant="outline"
                  className="shrink-0"
                  onClick={() => toast.info('페잔 정보 구매 준비 중입니다.')}
                >
                  {item.price.toLocaleString()} 자금
                </Button>
              </div>
            ))
          )}
        </CardContent>
      </Card>
    </div>
  );
}
