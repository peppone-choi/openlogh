'use client';

import { useState } from 'react';
import type { Loan, IntelligenceOffer } from '@/types/politics';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { Button } from '@/components/ui/8bit/button';
import { Banknote, ShieldAlert, Eye } from 'lucide-react';

interface FezzanPanelProps {
  loans: Loan[];
  intelOffers: IntelligenceOffer[];
  fezzanOperational: boolean;
  onTakeLoan: (amount: number) => void;
  onRepayLoan: (loanId: number, amount: number) => void;
  onBuyIntel: (type: string) => void;
}

export function FezzanPanel({
  loans,
  intelOffers,
  fezzanOperational,
  onTakeLoan,
  onRepayLoan,
  onBuyIntel,
}: FezzanPanelProps) {
  const [loanAmount, setLoanAmount] = useState(1000);
  const [repayAmounts, setRepayAmounts] = useState<Record<number, number>>({});

  const totalDebt = loans.reduce((sum, l) => sum + l.remainingDebt, 0);
  const defaultCount = loans.filter((l) => l.isDefaulted).length;

  const debtLevel = totalDebt === 0 ? 'safe' : defaultCount >= 2 ? 'critical' : defaultCount >= 1 ? 'warning' : 'safe';
  const debtColor = debtLevel === 'critical' ? 'text-red-400' : debtLevel === 'warning' ? 'text-yellow-400' : 'text-green-400';

  if (!fezzanOperational) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm">
            <Banknote className="size-4 text-yellow-500" />
            페잔 자치령
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">페잔 자치령이 더 이상 존재하지 않습니다.</p>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      {/* Debt Summary */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm">
            <Banknote className="size-4 text-yellow-500" />
            페잔 차관
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="flex items-center justify-between text-sm">
            <span>총 부채</span>
            <span className={debtColor}>{totalDebt.toLocaleString()} 자금</span>
          </div>
          {defaultCount > 0 && (
            <div className="flex items-center gap-1 text-xs text-red-400">
              <ShieldAlert className="size-3" />
              채무 불이행 {defaultCount}건 (3건 시 페잔 엔딩)
            </div>
          )}

          {/* Loan List */}
          {loans.length > 0 && (
            <div className="space-y-2 pt-2">
              {loans.map((loan) => (
                <div key={loan.loanId} className="border rounded p-2 space-y-1">
                  <div className="flex items-center justify-between text-xs">
                    <span>원금 {loan.principal.toLocaleString()}</span>
                    <span className={loan.isDefaulted ? 'text-red-400 font-bold' : 'text-muted-foreground'}>
                      {loan.isDefaulted ? '불이행' : '상환중'}
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-xs text-muted-foreground">
                    <span>잔여 {loan.remainingDebt.toLocaleString()} (이율 {(loan.interestRate * 100).toFixed(0)}%)</span>
                    <span>만기 {new Date(loan.dueAt).toLocaleDateString()}</span>
                  </div>
                  <div className="flex items-center gap-2 pt-1">
                    <input
                      type="number"
                      min={1}
                      max={loan.remainingDebt}
                      value={repayAmounts[loan.loanId] ?? loan.remainingDebt}
                      onChange={(e) => setRepayAmounts({ ...repayAmounts, [loan.loanId]: Number(e.target.value) })}
                      className="w-24 text-xs border rounded px-1 py-0.5 bg-background"
                    />
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => onRepayLoan(loan.loanId, repayAmounts[loan.loanId] ?? loan.remainingDebt)}
                    >
                      상환
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Take Loan */}
          <div className="flex items-center gap-2 pt-2 border-t">
            <input
              type="number"
              min={100}
              step={100}
              value={loanAmount}
              onChange={(e) => setLoanAmount(Number(e.target.value))}
              className="w-28 text-sm border rounded px-2 py-1 bg-background"
              placeholder="금액"
            />
            <Button size="sm" onClick={() => onTakeLoan(loanAmount)}>
              차관 요청
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* Intelligence Market */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-sm">
            <Eye className="size-4 text-purple-500" />
            정보 거래
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-2">
            {intelOffers.map((offer) => (
              <div key={offer.type} className="flex items-center justify-between text-sm">
                <div>
                  <span className="font-medium">{offer.nameKo}</span>
                  <p className="text-xs text-muted-foreground">{offer.description}</p>
                </div>
                <Button size="sm" variant="outline" onClick={() => onBuyIntel(offer.type)}>
                  {offer.cost.toLocaleString()} 자금
                </Button>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
