'use client';

import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Factory, GraduationCap, Home, Hotel, Users, Wine, Wrench } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';

export type FacilityStatus = 'available' | 'damaged' | 'under_construction' | 'unavailable';

export interface Facility {
    code: string;
    name: string;
    icon: LucideIcon;
    status: FacilityStatus;
    level?: number;
}

const STATUS_CONFIG: Record<FacilityStatus, { label: string; className: string }> = {
    available: { label: '운영 중', className: 'bg-green-500/20 text-green-400 border-green-500/40' },
    damaged: { label: '파손', className: 'bg-red-500/20 text-red-400 border-red-500/40' },
    under_construction: { label: '건설 중', className: 'bg-yellow-500/20 text-yellow-400 border-yellow-500/40' },
    unavailable: { label: '없음', className: 'bg-muted text-muted-foreground' },
};

const FACILITY_DEFS: Omit<Facility, 'status' | 'level'>[] = [
    { code: 'arsenal', name: '조병공창', icon: Factory },
    { code: 'academy', name: '사관학교', icon: GraduationCap },
    { code: 'residential', name: '주거구', icon: Home },
    { code: 'hotel', name: '호텔', icon: Hotel },
    { code: 'conference', name: '회의실', icon: Users },
    { code: 'bar', name: '주점', icon: Wine },
    { code: 'maintenance', name: '정비창', icon: Wrench },
];

/**
 * Derives facility list from a planet's meta field.
 * The meta.facilities map: facilityCode → { status, level }
 */
export function deriveFacilities(meta: Record<string, unknown> | undefined): Facility[] {
    const facilitiesRaw = meta?.facilities as Record<string, { status?: string; level?: number }> | undefined;

    return FACILITY_DEFS.map((def) => {
        const raw = facilitiesRaw?.[def.code];
        const statusStr = raw?.status ?? 'unavailable';
        const status: FacilityStatus =
            statusStr === 'available' || statusStr === 'damaged' || statusStr === 'under_construction'
                ? statusStr
                : 'unavailable';
        return {
            ...def,
            status,
            level: raw?.level,
        };
    });
}

interface PlanetFacilitiesProps {
    meta: Record<string, unknown> | undefined;
    /** Override facility list directly (optional) */
    facilities?: Facility[];
}

export function PlanetFacilities({ meta, facilities: facilitiesOverride }: PlanetFacilitiesProps) {
    const facilities = facilitiesOverride ?? deriveFacilities(meta);
    const active = facilities.filter((f) => f.status !== 'unavailable');

    return (
        <Card>
            <CardHeader className="pb-2">
                <CardTitle className="text-sm flex items-center gap-2">
                    <Factory className="size-4 text-orange-400" />
                    행성 시설
                </CardTitle>
            </CardHeader>
            <CardContent>
                {active.length === 0 ? (
                    <div className="text-xs text-muted-foreground">시설 없음</div>
                ) : (
                    <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
                        {facilities.map((f) => {
                            const Icon = f.icon;
                            const statusConf = STATUS_CONFIG[f.status];
                            return (
                                <div
                                    key={f.code}
                                    className={`flex items-center gap-2 rounded border px-2 py-1.5 text-xs ${
                                        f.status === 'unavailable' ? 'opacity-30' : ''
                                    }`}
                                >
                                    <Icon className="size-3.5 shrink-0 text-muted-foreground" />
                                    <span className="font-medium flex-1 truncate">{f.name}</span>
                                    {f.level != null && f.status !== 'unavailable' && (
                                        <span className="text-muted-foreground">Lv{f.level}</span>
                                    )}
                                    <Badge
                                        variant="outline"
                                        className={`text-[10px] px-1 py-0 h-4 ${statusConf.className}`}
                                    >
                                        {statusConf.label}
                                    </Badge>
                                </div>
                            );
                        })}
                    </div>
                )}
            </CardContent>
        </Card>
    );
}
