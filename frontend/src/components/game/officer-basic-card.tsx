'use client';

import type { OfficerFrontInfo, FactionFrontInfo } from '@/types';
import { LoghBar } from '@/components/game/logh-bar';
import {
    isBrightColor,
    calcInjury,
    formatInjury,
    formatGeneralTypeCall,
    formatRefreshScore,
    nextExpLevelRemain,
    ageColor,
    isValidObjKey,
    getShipClassName,
    parseShipClassCode,
    formatDexLevel,
} from '@/lib/game-utils';
import { getPortraitUrl, getShipClassIconUrl } from '@/lib/image';

interface OfficerBasicCardProps {
    general: OfficerFrontInfo | null;
    nation: FactionFrontInfo | null;
    turnTerm?: number;
    lastExecuted?: string | null;
}

export function OfficerBasicCard({ general, nation, turnTerm, lastExecuted }: OfficerBasicCardProps) {
    if (!general) return null;

    const injuryInfo = formatInjury(general.injury);
    const typeCall = formatGeneralTypeCall(general.leadership, general.command, general.intelligence);
    const nationColor = nation?.color ?? '#333';
    const nationTextColor = isBrightColor(nationColor) ? '#000' : '#fff';

    const leadershipEff = calcInjury(general.leadership, general.injury);
    const commandEff = calcInjury(general.command, general.injury);
    const intelligenceEff = calcInjury(general.intelligence, general.injury);
    const politicsEff = calcInjury(general.politics, general.injury);
    const administrationEff = calcInjury(general.administration, general.injury);

    const [expCur, expMax] = nextExpLevelRemain(general.experience, general.explevel);
    const expPercent = expMax > 0 ? (expCur / expMax) * 100 : 0;

    const dedicationLevel = general.dedLevelText || general.dedication.toLocaleString();
    const winRate = general.warnum > 0 ? (general.killnum / general.warnum) * 100 : 0;
    const totalCrewCasualties = general.killcrew + general.deathcrew;
    const killCrewRate = totalCrewCasualties > 0 ? (general.killcrew / totalCrewCasualties) * 100 : 0;

    const statUpThreshold = 100;

    const dexRows: Array<{ label: string; value: number }> = [
        { label: '보병', value: general.dex1 },
        { label: '궁병', value: general.dex2 },
        { label: '기병', value: general.dex3 },
        { label: '귀병', value: general.dex4 },
        { label: '차병', value: general.dex5 },
    ];

    const reservedCommands = Array.isArray(general.reservedCommand)
        ? general.reservedCommand
              .slice(0, 5)
              .map((cmd) => {
                  const brief = typeof cmd?.brief === 'string' ? cmd.brief : null;
                  return brief;
              })
              .filter((brief): brief is string => brief !== null)
        : [];

    let nextExecText = '-';
    if (lastExecuted && turnTerm) {
        const turnTime = new Date(general.turntime).getTime();
        const lastExecTime = new Date(lastExecuted).getTime();
        let effective = turnTime;
        if (effective < lastExecTime) {
            effective = effective + turnTerm * 60000;
        }
        const minutes = Math.max(0, Math.min(999, Math.floor((effective - lastExecTime) / 60000)));
        nextExecText = `${minutes}분`;
    }

    return (
        <div className="bg-card border border-border rounded-lg overflow-hidden text-sm" style={{ maxWidth: 500 }}>
            <div className="flex items-stretch">
                <div
                    className="shrink-0"
                    style={{
                        width: 64,
                        height: 64,
                        backgroundImage: `url('${getPortraitUrl(general.picture)}')`,
                        backgroundSize: 'contain',
                        backgroundRepeat: 'no-repeat',
                    }}
                />
                <div
                    className="flex-1 min-w-0 flex flex-col justify-center px-2.5 py-1"
                    style={{ backgroundColor: nationColor }}
                >
                    <div className="font-bold truncate" style={{ color: nationTextColor }}>
                        {general.npc === 10 && (
                            <img
                                src="/icons/emperor.png"
                                alt="원수"
                                width={14}
                                height={14}
                                className="inline-block align-middle mr-0.5"
                            />
                        )}
                        {general.name}
                    </div>
                    <div className="text-[11px] truncate" style={{ color: nationTextColor, opacity: 0.8 }}>
                        {general.officerCity > 0 &&
                            general.officerLevel >= 2 &&
                            general.officerLevel <= 4 &&
                            `${general.officerCity} `}
                        {general.officerLevelText} · {typeCall} ·{' '}
                        <span style={{ color: injuryInfo.color }}>{injuryInfo.text}</span> ·{' '}
                        {general.turntime.substring(11, 19)}
                    </div>
                </div>
            </div>

            <div className="grid grid-cols-3 gap-px bg-border/50 border-t border-border">
                <StatCell
                    label="통솔"
                    value={leadershipEff}
                    bonus={general.lbonus}
                    injuryColor={injuryInfo.color}
                    exp={(general.leadershipExp / statUpThreshold) * 100}
                />
                <StatCell
                    label="지휘"
                    value={commandEff}
                    injuryColor={injuryInfo.color}
                    exp={(general.commandExp / statUpThreshold) * 100}
                />
                <StatCell
                    label="정보"
                    value={intelligenceEff}
                    injuryColor={injuryInfo.color}
                    exp={(general.intelligenceExp / statUpThreshold) * 100}
                />
                <StatCell
                    label="정치"
                    value={politicsEff}
                    injuryColor={injuryInfo.color}
                    exp={(general.politicsExp / statUpThreshold) * 100}
                />
                <StatCell
                    label="운영"
                    value={administrationEff}
                    injuryColor={injuryInfo.color}
                    exp={(general.administrationExp / statUpThreshold) * 100}
                />
                <div className="bg-card p-1.5">
                    <div className="text-[10px] text-muted-foreground">특수장비</div>
                    <div className="font-medium">{isValidObjKey(general.equipment) ? general.equipment : '-'}</div>
                </div>
            </div>

            <div className="grid grid-cols-4 gap-px bg-border/50 border-t border-border">
                <KV label="기관" value={isValidObjKey(general.engine) ? general.engine : '-'} />
                <KV label="기함" value={isValidObjKey(general.flagship) ? general.flagship : '-'} />
                <KV label="부속품" value={isValidObjKey(general.accessory) ? general.accessory : '-'} />
                <KV label="자금" value={general.funds.toLocaleString()} valueColor="var(--empire-gold)" />
            </div>

            <div className="flex border-t border-border">
                <div
                    className="shrink-0"
                    style={{
                        width: 52,
                        height: 52,
                        backgroundImage: `url('${getShipClassIconUrl(parseShipClassCode(general.shipClass))}')`,
                        backgroundSize: 'contain',
                        backgroundRepeat: 'no-repeat',
                        backgroundPosition: 'center',
                    }}
                />
                <div className="flex-1 grid grid-cols-3 gap-px bg-border/50">
                    <KV label="물자" value={general.supplies.toLocaleString()} />
                    <KV label="함종" value={getShipClassName(general.shipClass)} />
                    <KV label="함선" value={general.ships.toLocaleString()} />
                    <KV label="훈련" value={String(general.training)} />
                    <KV label="사기" value={String(general.morale)} />
                    <KV label="성격" value={isValidObjKey(general.personal) ? general.personal : '-'} />
                </div>
            </div>

            <div className="grid grid-cols-4 gap-px bg-border/50 border-t border-border">
                <div className="bg-card p-1.5 col-span-2">
                    <div className="flex items-center gap-1.5">
                        <span className="text-[10px] text-muted-foreground">Lv.{general.explevel}</span>
                        <div className="flex-1 min-w-0">
                            <LoghBar height={7} percent={expPercent} />
                        </div>
                        <span className="text-[10px]" style={{ color: ageColor(general.age) }}>
                            {general.age}세
                        </span>
                    </div>
                </div>
                <KV
                    label="특기"
                    value={
                        (isValidObjKey(general.specialDomestic)
                            ? general.specialDomestic
                            : `${Math.max(general.age + 1, general.specage)}세`) +
                        ' / ' +
                        (isValidObjKey(general.specialWar)
                            ? general.specialWar
                            : `${Math.max(general.age + 1, general.specage2)}세`)
                    }
                />
                <KV label="함대" value={general.fleetInfo ? general.fleetInfo.name : '-'} />
            </div>

            <div className="grid grid-cols-3 gap-px bg-border/50 border-t border-border">
                <div className="bg-card px-2 py-1">
                    <span className="text-[10px] text-muted-foreground mr-1">수비</span>
                    {general.defenceTrain === 999 ? (
                        <span className="text-red-400 text-xs">안함</span>
                    ) : (
                        <span className="text-emerald-400 text-xs">함({general.defenceTrain})</span>
                    )}
                </div>
                <KV label="삭턴" value={`${general.killturn ?? '-'} 턴`} />
                <div className="bg-card px-2 py-1">
                    <span className="text-[10px] text-muted-foreground mr-1">실행</span>
                    <span className="text-xs">{nextExecText}</span>
                </div>
            </div>

            <div className="border-t border-border px-2.5 py-2 space-y-2 text-xs">
                <div className="grid grid-cols-2 gap-x-3 gap-y-0.5 sm:grid-cols-3">
                    <MetaRow label="명성" value={general.experience.toLocaleString()} />
                    <MetaRow label="계급" value={`${dedicationLevel} (${general.dedication.toLocaleString()})`} />
                    <MetaRow label="봉급" value={general.salary.toLocaleString()} />
                    <MetaRow label="전투" value={general.warnum.toLocaleString()} />
                    <MetaRow label="계략" value={general.firenum.toLocaleString()} />
                    <MetaRow label="사관" value={`${general.belong}년차`} />
                    <MetaRow label="승률" value={`${winRate.toFixed(1)}%`} />
                    <MetaRow
                        label="승/패"
                        value={`${general.killnum.toLocaleString()} / ${general.deathnum.toLocaleString()}`}
                    />
                    <MetaRow label="살상률" value={`${killCrewRate.toFixed(1)}%`} />
                    <MetaRow
                        label="사살/피살"
                        value={`${general.killcrew.toLocaleString()} / ${general.deathcrew.toLocaleString()}`}
                    />
                    <MetaRow
                        label="벌점"
                        value={`${formatRefreshScore(general.refreshScoreTotal ?? 0)} ${(general.refreshScoreTotal ?? 0).toLocaleString()}점(${general.refreshScore ?? 0})`}
                    />
                </div>

                <div>
                    <SectionLabel>숙련도</SectionLabel>
                    <div className="space-y-px mt-0.5">
                        {dexRows.map((dex) => {
                            const info = formatDexLevel(dex.value);
                            return (
                                <div
                                    key={dex.label}
                                    className="grid items-center gap-1"
                                    style={{ gridTemplateColumns: '32px 24px 44px 1fr' }}
                                >
                                    <span className="text-muted-foreground text-center">{dex.label}</span>
                                    <span className="text-center font-medium" style={{ color: info.color }}>
                                        {info.name}
                                    </span>
                                    <span className="text-right tabular-nums text-muted-foreground">
                                        {(dex.value / 1000).toFixed(1)}K
                                    </span>
                                    <LoghBar height={7} percent={(dex.value / 1_000_000) * 100} />
                                </div>
                            );
                        })}
                    </div>
                </div>

                <div>
                    <SectionLabel>예약턴</SectionLabel>
                    {reservedCommands.length > 0 ? (
                        <div className="space-y-0.5 mt-0.5">
                            {reservedCommands.map((brief) => (
                                <div key={brief} className="truncate text-muted-foreground">
                                    {brief}
                                </div>
                            ))}
                        </div>
                    ) : (
                        <div className="mt-0.5 text-muted-foreground">없음</div>
                    )}
                </div>
            </div>
        </div>
    );
}

/** @deprecated Use OfficerBasicCard */
export { OfficerBasicCard as GeneralBasicCard };

function StatCell({
    label,
    value,
    bonus,
    injuryColor,
    exp,
}: {
    label: string;
    value: number;
    bonus?: number;
    injuryColor: string;
    exp: number;
}) {
    return (
        <div className="bg-card p-1.5">
            <div className="flex items-center justify-between">
                <span className="text-[10px] text-muted-foreground">{label}</span>
                <span className="font-bold tabular-nums" style={{ color: injuryColor }}>
                    {value}
                    {bonus != null && bonus > 0 && (
                        <span className="text-cyan-400 font-normal text-[10px]">+{bonus}</span>
                    )}
                </span>
            </div>
            <div className="mt-0.5">
                <LoghBar height={7} percent={exp} />
            </div>
        </div>
    );
}

function KV({ label, value, valueColor }: { label: string; value: string; valueColor?: string }) {
    return (
        <div className="bg-card px-2 py-1">
            <div className="text-[10px] text-muted-foreground">{label}</div>
            <div className="font-medium truncate" style={valueColor ? { color: valueColor } : undefined}>
                {value}
            </div>
        </div>
    );
}

function MetaRow({ label, value }: { label: string; value: string }) {
    return (
        <div className="flex items-center gap-1.5">
            <span className="text-muted-foreground shrink-0 min-w-[3.2rem]">{label}</span>
            <span className="truncate">{value}</span>
        </div>
    );
}

function SectionLabel({ children }: { children: React.ReactNode }) {
    return <div className="text-[10px] font-semibold text-secondary tracking-wider uppercase">{children}</div>;
}
