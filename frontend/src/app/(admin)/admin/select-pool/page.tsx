'use client';

import { useEffect, useState } from 'react';
import { adminApi } from '@/lib/gameApi';
import { useAdminWorld } from '@/contexts/AdminWorldContext';
import { toast } from 'sonner';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/8bit/card';
import { Button } from '@/components/ui/8bit/button';
import { Input } from '@/components/ui/8bit/input';
import { Badge } from '@/components/ui/8bit/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/8bit/table';
import { Trash2, Plus, Loader2 } from 'lucide-react';
import type { SelectPoolEntry } from '@/types';

export default function AdminSelectPoolPage() {
    const { worldId } = useAdminWorld();
    const [pools, setPools] = useState<SelectPoolEntry[]>([]);
    const [loading, setLoading] = useState(true);

    const [uniqueName, setUniqueName] = useState('');
    const [generalName, setGeneralName] = useState('');
    const [leadership, setLeadership] = useState(50);
    const [strength, setStrength] = useState(50);
    const [intel, setIntel] = useState(50);
    const [politics, setPolitics] = useState(50);
    const [charm, setCharm] = useState(50);
    const [creating, setCreating] = useState(false);

    const load = () => {
        if (worldId == null) return;
        setLoading(true);
        adminApi
            .listSelectPools(worldId)
            .then((res) => setPools(res.data))
            .catch(() => toast.error('풀 목록을 불러올 수 없습니다.'))
            .finally(() => setLoading(false));
    };

    useEffect(() => {
        load();
    }, [worldId]);

    const handleCreate = async () => {
        if (!uniqueName.trim() || !generalName.trim()) {
            toast.error('고유명과 장수명을 입력하세요.');
            return;
        }
        setCreating(true);
        try {
            await adminApi.createSelectPool(
                {
                    uniqueName: uniqueName.trim(),
                    info: { generalName: generalName.trim(), leadership, strength, intel, politics, charm },
                } as Partial<SelectPoolEntry>,
                worldId ?? undefined
            );
            toast.success('풀 항목 생성 완료');
            setUniqueName('');
            setGeneralName('');
            setLeadership(50);
            setStrength(50);
            setIntel(50);
            setPolitics(50);
            setCharm(50);
            load();
        } catch {
            toast.error('생성 실패');
        } finally {
            setCreating(false);
        }
    };

    const handleDelete = async (id: number) => {
        if (!confirm('삭제하시겠습니까?')) return;
        try {
            await adminApi.deleteSelectPool(id, worldId ?? undefined);
            toast.success('삭제 완료');
            load();
        } catch {
            toast.error('삭제 실패');
        }
    };

    const getStatus = (p: SelectPoolEntry) => {
        if (p.generalId) return <Badge variant="outline">선택됨</Badge>;
        if (p.ownerId) return <Badge variant="secondary">예약중</Badge>;
        return <Badge>대기</Badge>;
    };

    const infoVal = (p: SelectPoolEntry, key: string) => {
        const v = p.info?.[key];
        return v != null ? String(v) : '-';
    };

    return (
        <div className="space-y-6">
            <Card>
                <CardHeader>
                    <CardTitle>장수 풀 관리</CardTitle>
                </CardHeader>
                <CardContent>
                    <div className="grid grid-cols-7 gap-2 items-end mb-4">
                        <div>
                            <label className="text-xs text-muted-foreground">고유명</label>
                            <Input
                                value={uniqueName}
                                onChange={(e) => setUniqueName(e.target.value)}
                                placeholder="pool-1"
                            />
                        </div>
                        <div>
                            <label className="text-xs text-muted-foreground">장수명</label>
                            <Input
                                value={generalName}
                                onChange={(e) => setGeneralName(e.target.value)}
                                placeholder="조조"
                            />
                        </div>
                        <div>
                            <label className="text-xs text-muted-foreground">통솔</label>
                            <Input type="number" value={leadership} onChange={(e) => setLeadership(+e.target.value)} />
                        </div>
                        <div>
                            <label className="text-xs text-muted-foreground">무력</label>
                            <Input type="number" value={strength} onChange={(e) => setStrength(+e.target.value)} />
                        </div>
                        <div>
                            <label className="text-xs text-muted-foreground">지력</label>
                            <Input type="number" value={intel} onChange={(e) => setIntel(+e.target.value)} />
                        </div>
                        <div>
                            <label className="text-xs text-muted-foreground">정치</label>
                            <Input type="number" value={politics} onChange={(e) => setPolitics(+e.target.value)} />
                        </div>
                        <div className="flex gap-1">
                            <div className="flex-1">
                                <label className="text-xs text-muted-foreground">매력</label>
                                <Input type="number" value={charm} onChange={(e) => setCharm(+e.target.value)} />
                            </div>
                            <Button onClick={handleCreate} disabled={creating} size="icon" className="mt-4 shrink-0">
                                {creating ? <Loader2 className="size-4 animate-spin" /> : <Plus className="size-4" />}
                            </Button>
                        </div>
                    </div>

                    {loading ? (
                        <div className="flex justify-center py-8">
                            <Loader2 className="size-6 animate-spin text-muted-foreground" />
                        </div>
                    ) : pools.length === 0 ? (
                        <p className="text-center text-sm text-muted-foreground py-8">풀이 비어 있습니다.</p>
                    ) : (
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>고유명</TableHead>
                                    <TableHead>장수명</TableHead>
                                    <TableHead className="text-center">통</TableHead>
                                    <TableHead className="text-center">무</TableHead>
                                    <TableHead className="text-center">지</TableHead>
                                    <TableHead className="text-center">정</TableHead>
                                    <TableHead className="text-center">매</TableHead>
                                    <TableHead className="text-center">상태</TableHead>
                                    <TableHead />
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {pools.map((p) => (
                                    <TableRow key={p.id}>
                                        <TableCell className="text-xs">{p.uniqueName}</TableCell>
                                        <TableCell>{infoVal(p, 'generalName')}</TableCell>
                                        <TableCell className="text-center text-xs">
                                            {infoVal(p, 'leadership')}
                                        </TableCell>
                                        <TableCell className="text-center text-xs">{infoVal(p, 'strength')}</TableCell>
                                        <TableCell className="text-center text-xs">{infoVal(p, 'intel')}</TableCell>
                                        <TableCell className="text-center text-xs">{infoVal(p, 'politics')}</TableCell>
                                        <TableCell className="text-center text-xs">{infoVal(p, 'charm')}</TableCell>
                                        <TableCell className="text-center">{getStatus(p)}</TableCell>
                                        <TableCell>
                                            <Button
                                                variant="ghost"
                                                size="icon"
                                                onClick={() => handleDelete(p.id)}
                                                className="size-7 text-destructive"
                                            >
                                                <Trash2 className="size-3.5" />
                                            </Button>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    )}
                </CardContent>
            </Card>
        </div>
    );
}
