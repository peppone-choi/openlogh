'use client';

import type { GeneralFrontInfo, NationFrontInfo } from '@/types';
import { SammoBar } from '@/components/game/sammo-bar';
import {
    isBrightColor,
    calcInjury,
    formatInjury,
    formatDexLevel,
    formatGeneralTypeCall,
    formatRefreshScore,
    nextExpLevelRemain,
    ageColor,
    isValidObjKey,
    getCrewTypeName,
    parseCrewTypeCode,
} from '@/lib/game-utils';
import { getPortraitUrl, getCrewTypeIconUrl } from '@/lib/image';

interface GeneralBasicCardProps {
    general: GeneralFrontInfo | null;
    nation: NationFrontInfo | null;
    turnTerm?: number;
    lastExecuted?: string | null;
}

export function GeneralBasicCard({ general, nation, turnTerm, lastExecuted }: GeneralBasicCardProps) {
    if (!general) return null;

    const injuryInfo = formatInjury(general.injury);
    const typeCall = formatGeneralTypeCall(general.leadership, general.strength, general.intel);
    const nationColor = nation?.color ?? '#000000';
    const nationTextColor = isBrightColor(nationColor) ? '#000' : '#fff';

    const leadershipEff = calcInjury(general.leadership, general.injury);
    const strengthEff = calcInjury(general.strength, general.injury);
    const intelEff = calcInjury(general.intel, general.injury);
    const politicsEff = calcInjury(general.politics, general.injury);
    const charmEff = calcInjury(general.charm, general.injury);

    const [expCur, expMax] = nextExpLevelRemain(general.experience, general.explevel);
    const expPercent = expMax > 0 ? (expCur / expMax) * 100 : 0;

    const dedicationLevel = general.dedLevelText || general.dedication.toLocaleString();
    const winRate = general.warnum > 0 ? (general.killnum / general.warnum) * 100 : 0;
    const totalCrewCasualties = general.killcrew + general.deathcrew;
    const killCrewRate = totalCrewCasualties > 0 ? (general.killcrew / totalCrewCasualties) * 100 : 0;
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

    // Stat exp bar (upgradeLimit default 100)
    const statUpThreshold = 100;

    // Next execute time
    let nextExecText = '-';
    if (lastExecuted && turnTerm) {
        const turnTime = new Date(general.turntime).getTime();
        const lastExecTime = new Date(lastExecuted).getTime();
        let effective = turnTime;
        if (effective < lastExecTime) {
            effective = effective + turnTerm * 60000;
        }
        const minutes = Math.max(0, Math.min(999, Math.floor((effective - lastExecTime) / 60000)));
        nextExecText = `${minutes}분 남음`;
    }

    return (
        <div className="border border-gray-600 text-sm" style={{ width: 500, maxWidth: '100%' }}>
            <div
                style={{
                    display: 'grid',
                    gridTemplateColumns: '64px repeat(3, 2fr 5fr)',
                    gridTemplateRows: 'repeat(11, calc(64px / 3))',
                    textAlign: 'center',
                }}
            >
                {/* Portrait - spans 3 rows */}
                <div
                    className="border-l border-t border-gray-600"
                    style={{
                        gridRow: '1 / 4',
                        width: 64,
                        height: 64,
                        backgroundImage: `url('${getPortraitUrl(general.picture)}')`,
                        backgroundSize: 'contain',
                        backgroundRepeat: 'no-repeat',
                    }}
                />

                {/* Name bar - spans columns 2-7 */}
                <div
                    className="border-t border-gray-600 font-bold truncate px-1"
                    style={{
                        gridRow: '1 / 2',
                        gridColumn: '2 / 8',
                        color: nationTextColor,
                        backgroundColor: nationColor,
                        lineHeight: 'calc(64px / 3)',
                    }}
                >
                    {general.npc === 10 && (
                        <img
                            src="/icons/emperor.png"
                            alt="황제"
                            width={14}
                            height={14}
                            className="inline-block align-middle mr-0.5"
                        />
                    )}
                    {general.name} 【
                    {general.officerCity > 0 && general.officerLevel >= 2 && general.officerLevel <= 4 && (
                        <>{general.officerCity} </>
                    )}
                    {general.officerLevelText} | {typeCall} |{' '}
                    <span style={{ color: injuryInfo.color }}>{injuryInfo.text}</span>】{' '}
                    {general.turntime.substring(11, 19)}
                </div>

                {/* Row 2: 통솔 */}
                <Cell head>통솔</Cell>
                <Cell>
                    <div className="flex items-center gap-1">
                        <span style={{ color: injuryInfo.color }}>{leadershipEff}</span>
                        {general.lbonus > 0 && <span style={{ color: 'cyan' }}>+{general.lbonus}</span>}
                        <div className="flex-1">
                            <SammoBar height={10} percent={(general.leadershipExp / statUpThreshold) * 100} />
                        </div>
                    </div>
                </Cell>

                {/* 무력 */}
                <Cell head>무력</Cell>
                <Cell>
                    <div className="flex items-center gap-1">
                        <span style={{ color: injuryInfo.color }}>{strengthEff}</span>
                        <div className="flex-1">
                            <SammoBar height={10} percent={(general.strengthExp / statUpThreshold) * 100} />
                        </div>
                    </div>
                </Cell>

                {/* 지력 */}
                <Cell head>지력</Cell>
                <Cell>
                    <div className="flex items-center gap-1">
                        <span style={{ color: injuryInfo.color }}>{intelEff}</span>
                        <div className="flex-1">
                            <SammoBar height={10} percent={(general.intelExp / statUpThreshold) * 100} />
                        </div>
                    </div>
                </Cell>

                {/* Row 3: 정치/매력 */}
                <Cell head>정치</Cell>
                <Cell>
                    <div className="flex items-center gap-1">
                        <span style={{ color: injuryInfo.color }}>{politicsEff}</span>
                        <div className="flex-1">
                            <SammoBar height={10} percent={(general.politicsExp / statUpThreshold) * 100} />
                        </div>
                    </div>
                </Cell>
                <Cell head>매력</Cell>
                <Cell>
                    <div className="flex items-center gap-1">
                        <span style={{ color: injuryInfo.color }}>{charmEff}</span>
                        <div className="flex-1">
                            <SammoBar height={10} percent={(general.charmExp / statUpThreshold) * 100} />
                        </div>
                    </div>
                </Cell>
                <Cell head>서적</Cell>
                <Cell>{isValidObjKey(general.book) ? general.book : '-'}</Cell>

                {/* Row 4: 명마/무기/서적→도구 merged */}
                <Cell head>명마</Cell>
                <Cell>{isValidObjKey(general.horse) ? general.horse : '-'}</Cell>
                <Cell head>무기</Cell>
                <Cell>{isValidObjKey(general.weapon) ? general.weapon : '-'}</Cell>
                <Cell head>도구</Cell>
                <Cell>{isValidObjKey(general.item) ? general.item : '-'}</Cell>

                <Cell head>자금</Cell>
                <Cell>{general.gold.toLocaleString()}</Cell>
                <Cell head>군량</Cell>
                <Cell>{general.rice.toLocaleString()}</Cell>
                {/* Spacers to fill row 5 cols 6-7 */}
                <Cell />
                <Cell />

                {/* Crew type icon - spans 4 rows to block col 1 through row 7 */}
                <div
                    className="border-l border-t border-gray-600"
                    style={{
                        gridRow: '4 / 8',
                        width: 64,
                        height: 64,
                        backgroundImage: `url('${getCrewTypeIconUrl(parseCrewTypeCode(general.crewtype))}')`,
                        backgroundSize: 'contain',
                        backgroundRepeat: 'no-repeat',
                    }}
                />

                {/* Row 5: 병종/병사/성격 */}
                <Cell head>병종</Cell>
                <Cell>{getCrewTypeName(general.crewtype)}</Cell>
                <Cell head>병사</Cell>
                <Cell>{general.crew.toLocaleString()}</Cell>
                <Cell head>성격</Cell>
                <Cell>{isValidObjKey(general.personal) ? general.personal : '-'}</Cell>

                {/* Row 6: 훈련/사기/특기 */}
                <Cell head>훈련</Cell>
                <Cell>{general.train}</Cell>
                <Cell head>사기</Cell>
                <Cell>{general.atmos}</Cell>
                <Cell head>특기</Cell>
                <Cell>
                    {isValidObjKey(general.specialDomestic)
                        ? general.specialDomestic
                        : `${Math.max(general.age + 1, general.specage)}세`}
                    {' / '}
                    {isValidObjKey(general.specialWar)
                        ? general.specialWar
                        : `${Math.max(general.age + 1, general.specage2)}세`}
                </Cell>

                {/* Row 7: Lv + exp bar + 연령 — explicit row to avoid overflow */}
                <div
                    className="border-t border-gray-600 legacy-bg1 text-center"
                    style={{ gridRow: 8, gridColumn: '1 / 3' }}
                >
                    Lv.{general.explevel}
                </div>
                <div
                    className="border-t border-gray-600 flex items-center px-1 min-w-0"
                    style={{ gridRow: 8, gridColumn: '3 / 6' }}
                >
                    <SammoBar height={10} percent={expPercent} />
                </div>
                <div
                    className="border-t border-gray-600 border-l legacy-bg1 text-center"
                    style={{ gridRow: 8, gridColumn: 6 }}
                >
                    연령
                </div>
                <div className="border-t border-gray-600 text-center" style={{ gridRow: 8, gridColumn: 7 }}>
                    <span style={{ color: ageColor(general.age) }}>{general.age}세</span>
                </div>

                {/* Row 9: 수비 + 삭턴 + 실행 */}
                <div className="border-t border-gray-600 border-l legacy-bg1 text-center" style={{ gridRow: 9 }}>
                    수비
                </div>
                <div className="border-t border-gray-600" style={{ gridRow: 9, gridColumn: 'span 2' }}>
                    {general.defenceTrain === 999 ? (
                        <span style={{ color: 'red' }}>수비 안함</span>
                    ) : (
                        <span style={{ color: 'limegreen' }}>수비 함(훈사{general.defenceTrain})</span>
                    )}
                </div>
                <div className="border-t border-gray-600 border-l legacy-bg1 text-center" style={{ gridRow: 9 }}>
                    삭턴
                </div>
                <div className="border-t border-gray-600 text-center" style={{ gridRow: 9 }}>
                    {general.killturn ?? '-'} 턴
                </div>
                <div className="border-t border-gray-600 border-l legacy-bg1 text-center" style={{ gridRow: 9 }}>
                    실행
                </div>
                <div className="border-t border-gray-600 text-center" style={{ gridRow: 9 }}>
                    {nextExecText}
                </div>

                {/* Row 10: 부대 + 벌점 */}
                <div className="border-t border-gray-600 border-l legacy-bg1 text-center" style={{ gridRow: 10 }}>
                    부대
                </div>
                <div className="border-t border-gray-600" style={{ gridRow: 10, gridColumn: 'span 2' }}>
                    {general.troopInfo ? general.troopInfo.name : '-'}
                </div>
                <div className="border-t border-gray-600 border-l legacy-bg1 text-center" style={{ gridRow: 10 }}>
                    벌점
                </div>
                <div className="border-t border-gray-600" style={{ gridRow: 10, gridColumn: 'span 3' }}>
                    {formatRefreshScore(general.refreshScoreTotal ?? 0)}{' '}
                    {(general.refreshScoreTotal ?? 0).toLocaleString()}점({general.refreshScore ?? 0})
                </div>
            </div>

            <div className="border-t border-gray-600 px-1 py-1.5 text-xs">
                <div className="grid grid-cols-2 gap-x-2 gap-y-0.5 sm:grid-cols-3">
                    <SupplementRow label="명성" value={general.experience.toLocaleString()} />
                    <SupplementRow label="계급" value={`${dedicationLevel} (${general.dedication.toLocaleString()})`} />
                    <SupplementRow label="봉급" value={general.bill.toLocaleString()} />
                    <SupplementRow label="전투" value={general.warnum.toLocaleString()} />
                    <SupplementRow label="계략" value={general.firenum.toLocaleString()} />
                    <SupplementRow label="사관" value={`${general.belong}년차`} />
                    <SupplementRow label="승률" value={`${winRate.toFixed(2)}%`} />
                    <SupplementRow
                        label="승리/패배"
                        value={`${general.killnum.toLocaleString()} / ${general.deathnum.toLocaleString()}`}
                    />
                    <SupplementRow label="살상률" value={`${killCrewRate.toFixed(2)}%`} />
                    <SupplementRow
                        label="사살/피살"
                        value={`${general.killcrew.toLocaleString()} / ${general.deathcrew.toLocaleString()}`}
                    />
                </div>

                <div className="mt-1.5 space-y-0.5">
                    <div className="legacy-bg1 border border-gray-600 px-1 font-semibold">숙련도</div>
                    {dexRows.map((dex) => {
                        const info = formatDexLevel(dex.value);
                        return (
                            <div
                                key={dex.label}
                                className="grid items-center gap-1"
                                style={{ gridTemplateColumns: '36px 28px 54px 1fr' }}
                            >
                                <div className="legacy-bg1 border border-gray-600 px-1 text-center">{dex.label}</div>
                                <div className="text-center" style={{ color: info.color }}>
                                    {info.name}
                                </div>
                                <div className="text-right">{(dex.value / 1000).toFixed(1)}K</div>
                                <SammoBar height={7} percent={(dex.value / 1_000_000) * 100} />
                            </div>
                        );
                    })}
                </div>

                <div className="mt-1.5">
                    <div className="legacy-bg1 border border-gray-600 px-1 font-semibold">예약턴</div>
                    {reservedCommands.length > 0 ? (
                        <div className="space-y-0.5 border border-t-0 border-gray-600 px-1 py-1">
                            {reservedCommands.map((brief) => (
                                <div key={brief} className="truncate">
                                    {brief}
                                </div>
                            ))}
                        </div>
                    ) : (
                        <div className="border border-t-0 border-gray-600 px-1 py-1 text-gray-400">없음</div>
                    )}
                </div>
            </div>
        </div>
    );
}

function SupplementRow({ label, value }: { label: string; value: string }) {
    return (
        <div className="flex items-center gap-1">
            <span className="legacy-bg1 inline-block min-w-11 border border-gray-600 px-1 text-center">{label}</span>
            <span className="truncate">{value}</span>
        </div>
    );
}

function Cell({ head, wide, children }: { head?: boolean; wide?: number; children?: React.ReactNode }) {
    return (
        <div
            className={`border-t border-gray-600 ${head ? 'border-l legacy-bg1' : ''}`}
            style={wide ? { gridColumn: `span ${wide}` } : undefined}
        >
            {children}
        </div>
    );
}
