'use client';

import { useCallback, useEffect, useState } from 'react';
import axios from 'axios';
import { toast } from 'sonner';
import { ArrowRight, ArrowLeft, RefreshCw } from 'lucide-react';

type Direction = 'TO_FLEET' | 'TO_PLANET';
type ResourceType = 'funds' | 'supplies';

interface WarehouseState {
    planetSupplies: number;
    planetFunds: number;
    fleetSupplies: number;
}

interface WarehouseTransferPanelProps {
    planetId: number;
    fleetId: number | null;
    sessionId: number;
}

interface TransferRowProps {
    label: string;
    planetValue: number;
    fleetValue: number;
    direction: Direction;
    amount: number;
    onAmountChange: (v: number) => void;
    onToggleDirection: () => void;
    onTransfer: () => void;
    loading: boolean;
}

function TransferRow({
    label,
    planetValue,
    fleetValue,
    direction,
    amount,
    onAmountChange,
    onToggleDirection,
    onTransfer,
    loading,
}: TransferRowProps) {
    return (
        <div className="space-y-1.5">
            <div className="flex items-center gap-2 text-xs">
                <span className="text-gray-400 w-8">{label}</span>
                <span className="text-gray-300 tabular-nums w-20 text-right">
                    행성 {planetValue.toLocaleString()}
                </span>
                <button
                    type="button"
                    onClick={onToggleDirection}
                    className="border border-gray-600 hover:border-gray-400 px-1.5 py-0.5 rounded-none transition-colors"
                    title={direction === 'TO_FLEET' ? '행성 → 부대' : '부대 → 행성'}
                >
                    {direction === 'TO_FLEET' ? (
                        <ArrowRight className="size-3 text-blue-400" />
                    ) : (
                        <ArrowLeft className="size-3 text-yellow-400" />
                    )}
                </button>
                <span className="text-gray-300 tabular-nums w-20">
                    부대 {fleetValue.toLocaleString()}
                </span>
            </div>
            <div className="flex items-center gap-2">
                <input
                    type="number"
                    min={0}
                    value={amount}
                    onChange={(e) => onAmountChange(Math.max(0, Number(e.target.value)))}
                    className="w-28 bg-gray-900 border border-gray-700 text-gray-200 text-xs px-2 py-1 rounded-none outline-none focus:border-blue-600"
                    placeholder="수량"
                />
                <button
                    type="button"
                    onClick={onTransfer}
                    disabled={loading || amount <= 0}
                    className="text-xs border border-blue-700 text-blue-300 hover:bg-blue-900/30 disabled:opacity-40 disabled:cursor-not-allowed px-3 py-1 rounded-none transition-colors"
                >
                    이송
                </button>
            </div>
        </div>
    );
}

export function WarehouseTransferPanel({ planetId, fleetId, sessionId }: WarehouseTransferPanelProps) {
    const [warehouse, setWarehouse] = useState<WarehouseState | null>(null);
    const [loadingWarehouse, setLoadingWarehouse] = useState(true);

    const [fundsDirection, setFundsDirection] = useState<Direction>('TO_FLEET');
    const [suppliesDirection, setSuppliesDirection] = useState<Direction>('TO_FLEET');
    const [fundsAmount, setFundsAmount] = useState(0);
    const [suppliesAmount, setSuppliesAmount] = useState(0);
    const [transferring, setTransferring] = useState(false);

    const fetchWarehouse = useCallback(async () => {
        setLoadingWarehouse(true);
        try {
            const { data } = await axios.get<WarehouseState>(
                `/api/${sessionId}/warehouse/${planetId}`
            );
            setWarehouse(data);
        } catch (err) {
            console.error('창고 상태 조회 실패:', err);
        } finally {
            setLoadingWarehouse(false);
        }
    }, [sessionId, planetId]);

    useEffect(() => {
        if (!fleetId) return;
        void fetchWarehouse();
    }, [fetchWarehouse, fleetId]);

    const handleTransfer = async (resourceType: ResourceType, direction: Direction, amount: number) => {
        if (!fleetId || amount <= 0) return;
        setTransferring(true);
        try {
            await axios.post(`/api/${sessionId}/warehouse/transfer`, {
                fleetId,
                planetId,
                direction,
                resourceType,
                amount,
            });
            const resourceLabel = resourceType === 'funds' ? '자금' : '물자';
            const dirLabel = direction === 'TO_FLEET' ? '부대 창고로' : '행성 창고로';
            toast.success(`${resourceLabel} ${amount.toLocaleString()} ${dirLabel} 이송했습니다.`);
            if (resourceType === 'funds') setFundsAmount(0);
            else setSuppliesAmount(0);
            await fetchWarehouse();
        } catch {
            toast.error('이송에 실패했습니다.');
        } finally {
            setTransferring(false);
        }
    };

    if (!fleetId) return null;

    return (
        <div className="border border-gray-700 bg-gray-950/60 rounded-none">
            {/* Header */}
            <div className="border-b border-gray-700 px-4 py-2 flex items-center justify-between">
                <span className="text-xs font-medium text-gray-300">창고 이송</span>
                <button
                    type="button"
                    onClick={() => void fetchWarehouse()}
                    disabled={loadingWarehouse}
                    className="text-gray-500 hover:text-gray-300 transition-colors"
                >
                    <RefreshCw className={`size-3 ${loadingWarehouse ? 'animate-spin' : ''}`} />
                </button>
            </div>

            <div className="px-4 py-3 space-y-4">
                {loadingWarehouse ? (
                    <p className="text-xs text-gray-500">불러오는 중...</p>
                ) : warehouse ? (
                    <>
                        <TransferRow
                            label="자금"
                            planetValue={warehouse.planetFunds}
                            fleetValue={0}
                            direction={fundsDirection}
                            amount={fundsAmount}
                            onAmountChange={setFundsAmount}
                            onToggleDirection={() =>
                                setFundsDirection((d) => (d === 'TO_FLEET' ? 'TO_PLANET' : 'TO_FLEET'))
                            }
                            onTransfer={() => handleTransfer('funds', fundsDirection, fundsAmount)}
                            loading={transferring}
                        />
                        <TransferRow
                            label="물자"
                            planetValue={warehouse.planetSupplies}
                            fleetValue={warehouse.fleetSupplies}
                            direction={suppliesDirection}
                            amount={suppliesAmount}
                            onAmountChange={setSuppliesAmount}
                            onToggleDirection={() =>
                                setSuppliesDirection((d) => (d === 'TO_FLEET' ? 'TO_PLANET' : 'TO_FLEET'))
                            }
                            onTransfer={() => handleTransfer('supplies', suppliesDirection, suppliesAmount)}
                            loading={transferring}
                        />
                    </>
                ) : (
                    <p className="text-xs text-gray-600">창고 정보를 불러올 수 없습니다.</p>
                )}
            </div>
        </div>
    );
}
