'use client';

import type { StarSystemFrontInfo } from '@/types';
import { LoghBar } from '@/components/game/logh-bar';
import { isBrightColor, getNPCColor, REGION_NAMES, PLANET_LEVEL_NAMES } from '@/lib/game-utils';

interface PlanetBasicCardProps {
    city: StarSystemFrontInfo | null;
    region?: number;
}

export function PlanetBasicCard({ city, region }: PlanetBasicCardProps) {
    if (!city) return null;

    const factionInfo = city.factionInfo ?? city.nationInfo;
    const factionColor = factionInfo.color ?? '#000000';
    const textColor = isBrightColor(factionColor) ? 'black' : 'white';
    const regionText = REGION_NAMES[region ?? 0] ?? '';
    const levelText = PLANET_LEVEL_NAMES[city.level] ?? `Lv.${city.level}`;

    const tradeRouteRaw = city.trade_route ?? city.trade;
    const tradeAltText = tradeRouteRaw ? `${tradeRouteRaw}%` : '항로 없음';
    const tradeBarPercent = tradeRouteRaw ? (tradeRouteRaw - 95) * 10 : 0;

    // Support both new and legacy field names
    const population = city.population ?? city.pop;
    const production = city.production ?? city.agri;
    const commerce = city.commerce ?? city.comm;
    const security = city.security ?? city.secu;
    const orbitalDefense = city.orbital_defense ?? city.def;
    const fortress = city.fortress ?? city.wall;
    const approval = city.approval ?? city.trust ?? 0;

    return (
        <div
            className="bg-card border border-border rounded-lg overflow-hidden text-sm"
            style={{
                display: 'grid',
                gridTemplateColumns: '1fr 1fr 1fr 1fr',
            }}
        >
            {/* Star system name header */}
            <div
                className="font-bold text-center"
                style={{
                    gridColumn: '1 / 5',
                    color: textColor,
                    backgroundColor: factionColor,
                    lineHeight: '1.8em',
                }}
            >
                【{regionText} | {levelText}】 {city.name}
            </div>

            {/* Faction name */}
            <div
                className="border-t border-border/30 font-bold text-center text-[13px]"
                style={{
                    gridColumn: '1 / 5',
                    color: textColor,
                    backgroundColor: factionColor,
                    lineHeight: '1.8em',
                }}
            >
                {factionInfo.id ? `지배 진영 【 ${factionInfo.name} 】` : '공 백 지'}
            </div>

            {/* Row 3: 주민 + 지지도 + 행성총독 */}
            <StatPanel label="주민" colSpan="1 / 3" headRatio="1fr 5fr">
                <LoghBar height={7} percent={(population[0] / population[1]) * 100} />
                <CellText>
                    {population[0].toLocaleString()} / {population[1].toLocaleString()}
                </CellText>
            </StatPanel>
            <StatPanel label="지지도" colSpan="3 / 4">
                <LoghBar height={7} percent={approval} />
                <CellText>{approval.toLocaleString(undefined, { maximumFractionDigits: 1 })}</CellText>
            </StatPanel>
            <OfficerCell label="행성총독" npc={city.officerList[4]?.npc ?? 0} name={city.officerList[4]?.name} />

            {/* Row 4: 생산 + 교역 + 함대사령관 */}
            <StatPanel label="생산">
                <LoghBar height={7} percent={(production[0] / production[1]) * 100} />
                <CellText>
                    {production[0].toLocaleString()} / {production[1].toLocaleString()}
                </CellText>
            </StatPanel>
            <StatPanel label="교역">
                <LoghBar height={7} percent={(commerce[0] / commerce[1]) * 100} />
                <CellText>
                    {commerce[0].toLocaleString()} / {commerce[1].toLocaleString()}
                </CellText>
            </StatPanel>
            <StatPanel label="치안">
                <LoghBar height={7} percent={(security[0] / security[1]) * 100} />
                <CellText>
                    {security[0].toLocaleString()} / {security[1].toLocaleString()}
                </CellText>
            </StatPanel>
            <OfficerCell label="함대사령관" npc={city.officerList[3]?.npc ?? 0} name={city.officerList[3]?.name} />

            {/* Row 5: 궤도방어 + 요새 + 항로 + 행정관 */}
            <StatPanel label="궤도방어">
                <LoghBar height={7} percent={(orbitalDefense[0] / orbitalDefense[1]) * 100} />
                <CellText>
                    {orbitalDefense[0].toLocaleString()} / {orbitalDefense[1].toLocaleString()}
                </CellText>
            </StatPanel>
            <StatPanel label="요새">
                <LoghBar height={7} percent={(fortress[0] / fortress[1]) * 100} />
                <CellText>
                    {fortress[0].toLocaleString()} / {fortress[1].toLocaleString()}
                </CellText>
            </StatPanel>
            <StatPanel label="항로">
                <LoghBar height={7} percent={tradeBarPercent} altText={tradeAltText} />
                <CellText>{tradeAltText}</CellText>
            </StatPanel>
            <OfficerCell label="행정관" npc={city.officerList[2]?.npc ?? 0} name={city.officerList[2]?.name} />
        </div>
    );
}

/** @deprecated Use PlanetBasicCard */
export { PlanetBasicCard as CityBasicCard };

function StatPanel({
    label,
    colSpan,
    headRatio,
    children,
}: {
    label: string;
    colSpan?: string;
    headRatio?: string;
    children: React.ReactNode;
}) {
    return (
        <div
            className="border-t border-border"
            style={{
                display: 'grid',
                gridTemplateColumns: headRatio ?? '1fr 2fr',
                ...(colSpan ? { gridColumn: colSpan } : {}),
            }}
        >
            <div className="bg-muted flex items-center justify-center text-muted-foreground text-xs">{label}</div>
            <div>{children}</div>
        </div>
    );
}

function OfficerCell({ label, npc, name }: { label: string; npc: number; name?: string }) {
    return (
        <div className="border-t border-border" style={{ display: 'grid', gridTemplateColumns: '1fr 2fr' }}>
            <div className="bg-muted flex items-center justify-center text-muted-foreground text-xs">{label}</div>
            <div className="flex items-center justify-center" style={{ color: getNPCColor(npc) }}>
                {name ?? '-'}
            </div>
        </div>
    );
}

function CellText({ children }: { children: React.ReactNode }) {
    return (
        <div className="text-center" style={{ lineHeight: '1.2em' }}>
            {children}
        </div>
    );
}
