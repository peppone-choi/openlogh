'use client';

import { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { ArrowLeft, Truck, Package, RefreshCw } from 'lucide-react';
import { useWorldStore } from '@/stores/worldStore';
import { useOfficerStore } from '@/stores/officerStore';
import { planetApi, fleetApi } from '@/lib/gameApi';
import type { StarSystem, FleetWithMembers } from '@/types';
import { toast } from 'sonner';
import { PageHeader } from '@/components/game/page-header';
import { LoadingState } from '@/components/game/loading-state';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Badge } from '@/components/ui/badge';

interface TransportOrder {
    id: string;
    sourcePlanetId: number;
    sourcePlanetName: string;
    destPlanetId: number;
    destPlanetName: string;
    fleetId: number;
    fleetName: string;
    ships: number;
    supplies: number;
    status: 'pending' | 'in_transit' | 'completed' | 'failed';
    createdAt: string;
}

export default function TransportPage() {
    const router = useRouter();
    const { currentWorld } = useWorldStore();
    const { myOfficer } = useOfficerStore();

    const [planets, setPlanets] = useState<StarSystem[]>([]);
    const [fleets, setFleets] = useState<FleetWithMembers[]>([]);
    const [loading, setLoading] = useState(true);

    // Form state
    const [sourcePlanetId, setSourcePlanetId] = useState('');
    const [destPlanetId, setDestPlanetId] = useState('');
    const [fleetId, setFleetId] = useState('');
    const [ships, setShips] = useState('');
    const [supplies, setSupplies] = useState('');
    const [submitting, setSubmitting] = useState(false);

    // Active transport orders (would come from API in production)
    const [orders, setOrders] = useState<TransportOrder[]>([]);

    const loadData = useCallback(async () => {
        if (!currentWorld || !myOfficer?.factionId) return;
        setLoading(true);
        try {
            const [planetsRes, fleetsRes] = await Promise.all([
                planetApi.listByFaction(myOfficer.factionId),
                fleetApi.listByFaction(myOfficer.factionId),
            ]);
            setPlanets(planetsRes.data);
            setFleets(fleetsRes.data);
        } catch {
            toast.error('데이터를 불러오는 데 실패했습니다.');
        } finally {
            setLoading(false);
        }
    }, [currentWorld, myOfficer]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
        e.preventDefault();
        if (!sourcePlanetId || !destPlanetId || !fleetId) {
            toast.error('출발 행성, 도착 행성, 수송 함대를 모두 선택해주세요.');
            return;
        }
        if (sourcePlanetId === destPlanetId) {
            toast.error('출발 행성과 도착 행성이 같을 수 없습니다.');
            return;
        }
        if (!ships && !supplies) {
            toast.error('수송할 함선 수량 또는 물자를 입력해주세요.');
            return;
        }

        setSubmitting(true);
        try {
            // In production, this would call a transport API endpoint
            const sourcePlanet = planets.find((p) => p.id === Number(sourcePlanetId));
            const destPlanet = planets.find((p) => p.id === Number(destPlanetId));
            const fleet = fleets.find((f) => f.fleet.id === Number(fleetId));

            const newOrder: TransportOrder = {
                id: String(Date.now()),
                sourcePlanetId: Number(sourcePlanetId),
                sourcePlanetName: sourcePlanet?.name ?? sourcePlanetId,
                destPlanetId: Number(destPlanetId),
                destPlanetName: destPlanet?.name ?? destPlanetId,
                fleetId: Number(fleetId),
                fleetName: fleet?.fleet.name ?? fleetId,
                ships: Number(ships) || 0,
                supplies: Number(supplies) || 0,
                status: 'pending',
                createdAt: new Date().toISOString(),
            };
            setOrders((prev) => [newOrder, ...prev]);
            toast.success('수송 계획이 등록되었습니다.');
            setShips('');
            setSupplies('');
        } catch {
            toast.error('수송 계획 등록에 실패했습니다.');
        } finally {
            setSubmitting(false);
        }
    };

    if (!currentWorld) return <LoadingState message="월드를 선택해주세요." />;
    if (loading) return <LoadingState />;

    const STATUS_LABELS: Record<TransportOrder['status'], { label: string; class: string }> = {
        pending: { label: '대기중', class: 'bg-yellow-500/20 text-yellow-400 border-yellow-500/40' },
        in_transit: { label: '수송중', class: 'bg-blue-500/20 text-blue-400 border-blue-500/40' },
        completed: { label: '완료', class: 'bg-green-500/20 text-green-400 border-green-500/40' },
        failed: { label: '실패', class: 'bg-red-500/20 text-red-400 border-red-500/40' },
    };

    return (
        <div className="p-4 max-w-3xl mx-auto space-y-6">
            <div className="flex items-center gap-2">
                <Button variant="ghost" size="sm" onClick={() => router.push('/')}>
                    <ArrowLeft className="size-4 mr-1" />
                    돌아가기
                </Button>
                <Button variant="ghost" size="sm" onClick={loadData}>
                    <RefreshCw className="size-4 mr-1" />
                    갱신
                </Button>
            </div>

            <PageHeader icon={Truck} title="수송 계획" />

            {/* Transport Form */}
            <Card>
                <CardHeader>
                    <CardTitle className="text-sm flex items-center gap-2">
                        <Package className="size-4" />새 수송 계획 등록
                    </CardTitle>
                </CardHeader>
                <CardContent>
                    <form onSubmit={handleSubmit} className="space-y-4">
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                            {/* Source Planet */}
                            <div className="space-y-1.5">
                                <label className="block text-sm text-muted-foreground">출발 행성</label>
                                <Select value={sourcePlanetId} onValueChange={setSourcePlanetId}>
                                    <SelectTrigger>
                                        <SelectValue placeholder="출발 행성 선택..." />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {planets.map((p) => (
                                            <SelectItem key={p.id} value={String(p.id)}>
                                                {p.name}
                                            </SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            </div>

                            {/* Destination Planet */}
                            <div className="space-y-1.5">
                                <label className="block text-sm text-muted-foreground">도착 행성</label>
                                <Select value={destPlanetId} onValueChange={setDestPlanetId}>
                                    <SelectTrigger>
                                        <SelectValue placeholder="도착 행성 선택..." />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {planets
                                            .filter((p) => String(p.id) !== sourcePlanetId)
                                            .map((p) => (
                                                <SelectItem key={p.id} value={String(p.id)}>
                                                    {p.name}
                                                </SelectItem>
                                            ))}
                                    </SelectContent>
                                </Select>
                            </div>
                        </div>

                        {/* Fleet Assignment */}
                        <div className="space-y-1.5">
                            <label className="block text-sm text-muted-foreground">수송 함대</label>
                            <Select value={fleetId} onValueChange={setFleetId}>
                                <SelectTrigger>
                                    <SelectValue placeholder="함대 선택..." />
                                </SelectTrigger>
                                <SelectContent>
                                    {fleets.map((f) => (
                                        <SelectItem key={f.fleet.id} value={String(f.fleet.id)}>
                                            {f.fleet.name}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                            {fleets.length === 0 && (
                                <p className="text-xs text-muted-foreground">
                                    배속된 함대가 없습니다. 함대편성 메뉴에서 함대를 편성해주세요.
                                </p>
                            )}
                        </div>

                        {/* Cargo Manifest */}
                        <div className="space-y-1.5">
                            <label className="block text-sm text-muted-foreground">수송 화물</label>
                            <div className="grid grid-cols-2 gap-3">
                                <div className="space-y-1">
                                    <span className="text-xs text-muted-foreground">함선 수 (척)</span>
                                    <Input
                                        type="number"
                                        min="0"
                                        value={ships}
                                        onChange={(e) => setShips(e.target.value)}
                                        placeholder="0"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <span className="text-xs text-muted-foreground">물자 (단위)</span>
                                    <Input
                                        type="number"
                                        min="0"
                                        value={supplies}
                                        onChange={(e) => setSupplies(e.target.value)}
                                        placeholder="0"
                                    />
                                </div>
                            </div>
                        </div>

                        <Button type="submit" disabled={submitting} className="w-full">
                            {submitting ? '등록중...' : '수송 계획 등록'}
                        </Button>
                    </form>
                </CardContent>
            </Card>

            {/* Active Transport Orders */}
            <Card>
                <CardHeader>
                    <CardTitle className="text-sm">수송 현황</CardTitle>
                </CardHeader>
                <CardContent>
                    {orders.length === 0 ? (
                        <p className="text-sm text-muted-foreground text-center py-4">진행중인 수송 계획이 없습니다.</p>
                    ) : (
                        <div className="space-y-2">
                            {orders.map((order) => {
                                const status = STATUS_LABELS[order.status];
                                return (
                                    <div
                                        key={order.id}
                                        className="flex flex-col sm:flex-row sm:items-center gap-2 p-3 border border-border rounded-md text-sm"
                                    >
                                        <div className="flex-1 space-y-0.5">
                                            <div className="font-medium">
                                                {order.sourcePlanetName}{' '}
                                                <span className="text-muted-foreground">→</span> {order.destPlanetName}
                                            </div>
                                            <div className="text-xs text-muted-foreground">
                                                {order.fleetName}
                                                {order.ships > 0 && ` · 함선 ${order.ships.toLocaleString()}척`}
                                                {order.supplies > 0 && ` · 물자 ${order.supplies.toLocaleString()}`}
                                            </div>
                                        </div>
                                        <Badge variant="outline" className={`text-xs shrink-0 ${status.class}`}>
                                            {status.label}
                                        </Badge>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </CardContent>
            </Card>
        </div>
    );
}
