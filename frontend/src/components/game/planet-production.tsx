'use client';

import type { StarSystem } from '@/types';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Factory } from 'lucide-react';

interface PlanetProductionProps {
    planet: StarSystem;
}

interface ProductionItem {
    label: string;
    value: number | string;
    unit?: string;
}

/**
 * Displays the monthly auto-production schedule for a planet.
 * Production amounts are derived from planet stats following LOGH game rules.
 */
export function PlanetProduction({ planet }: PlanetProductionProps) {
    const meta = planet.meta ?? {};

    // Monthly ship production (함선 생산): based on production stat
    const production = planet.production ?? planet.agri ?? 0;
    const productionMax = planet.productionMax ?? planet.agriMax ?? 1;
    const productionRate = productionMax > 0 ? production / productionMax : 0;

    // Monthly ship units produced (전함 기준 유닛 수)
    const monthlyShips = Math.floor(productionRate * 3);

    // Monthly supplies production (물자 생산): based on commerce
    const commerce = planet.commerce ?? planet.comm ?? 0;
    const commerceMax = planet.commerceMax ?? planet.commMax ?? 1;
    const commerceRate = commerceMax > 0 ? commerce / commerceMax : 0;
    const monthlySupplies = Math.floor(commerceRate * 500);

    // Ground unit production (육전대): from meta or default
    const monthlyGroundUnits = typeof meta.monthlyGroundUnits === 'number' ? meta.monthlyGroundUnits : 0;

    // Orbital defense restoration
    const orbitalDefense = planet.orbital_defense ?? planet.orbitalDefense ?? planet.def ?? 0;
    const orbitalDefenseMax = planet.orbitalDefenseMax ?? planet.defMax ?? 1;
    const monthlyOrbitalRestore = orbitalDefense < orbitalDefenseMax ? Math.floor(orbitalDefenseMax * 0.05) : 0;

    // Fortress repair
    const fortress = planet.fortress ?? planet.wall ?? 0;
    const fortressMax = planet.fortressMax ?? planet.wallMax ?? 1;
    const monthlyFortressRepair = fortress < fortressMax ? Math.floor(fortressMax * 0.03) : 0;

    const items: ProductionItem[] = [
        { label: '함선 생산', value: monthlyShips > 0 ? `+${monthlyShips}` : '-', unit: '유닛/월' },
        { label: '물자 생산', value: monthlySupplies > 0 ? `+${monthlySupplies.toLocaleString()}` : '-', unit: '/월' },
        { label: '육전대 생산', value: monthlyGroundUnits > 0 ? `+${monthlyGroundUnits}` : '-', unit: '유닛/월' },
        {
            label: '궤도방어 복구',
            value: monthlyOrbitalRestore > 0 ? `+${monthlyOrbitalRestore.toLocaleString()}` : '-',
            unit: '/월',
        },
        {
            label: '요새 복구',
            value: monthlyFortressRepair > 0 ? `+${monthlyFortressRepair.toLocaleString()}` : '-',
            unit: '/월',
        },
    ];

    return (
        <Card>
            <CardHeader className="pb-2">
                <CardTitle className="text-sm flex items-center gap-2">
                    <Factory className="size-4" />
                    월간 생산 현황
                </CardTitle>
            </CardHeader>
            <CardContent>
                <div className="grid grid-cols-2 gap-x-4 gap-y-1.5">
                    {items.map((item) => (
                        <div key={item.label} className="flex justify-between items-center text-xs">
                            <span className="text-muted-foreground">{item.label}</span>
                            <span className="font-mono text-right">
                                {item.value}
                                {item.unit && item.value !== '-' && (
                                    <span className="text-muted-foreground ml-0.5">{item.unit}</span>
                                )}
                            </span>
                        </div>
                    ))}
                </div>
                <p className="text-[10px] text-muted-foreground mt-2">
                    * 생산량은 행성 생산력/교역 수치 기준 추정값입니다.
                </p>
            </CardContent>
        </Card>
    );
}
