'use client';

import type { NationFrontInfo, GlobalInfo } from '@/types';
import { isBrightColor, getNPCColor, formatOfficerLevelText, convTechLevel, isTechLimited } from '@/lib/game-utils';

interface NationBasicCardProps {
    nation: NationFrontInfo | null;
    global?: GlobalInfo | null;
}

export function NationBasicCard({ nation, global }: NationBasicCardProps) {
    if (!nation) return null;

    const textColor = isBrightColor(nation.color) ? 'black' : 'white';
    const nationLevel = nation.level;

    // Tech level calculation
    const startYear = global?.startyear ?? global?.year ?? 0;
    const year = global?.year ?? 0;
    const maxTechLevel = 10;
    const initialAllowed = 1;
    const techIncYear = 5;
    const currentTechLevel = convTechLevel(nation.tech, maxTechLevel);
    const onTechLimit = isTechLimited(startYear, year, nation.tech, maxTechLevel, initialAllowed, techIncYear);

    const noNation = !nation.id;
    const impossibleStrategicCommandText =
        !noNation && nation.impossibleStrategicCommand.length > 0
            ? `불가 전략: ${nation.impossibleStrategicCommand.join(', ')}`
            : '';

    return (
        <div
            className="bg-card border border-border rounded-lg overflow-hidden text-sm"
            style={{
                maxWidth: 500,
                display: 'grid',
                gridTemplateColumns: '7fr 18fr 7fr 18fr',
                gridTemplateRows: `repeat(10, calc(192px / 10))`,
            }}
        >
            {/* Name header */}
            <div
                className="text-center font-bold"
                style={{
                    gridColumn: '1 / span 4',
                    backgroundColor: nation.color,
                    color: textColor,
                    lineHeight: 'calc(193px / 10)',
                }}
            >
                {nation.name}
            </div>

            {/* 성향 */}
            <Head>성향</Head>
            <Body wide={3}>
                {nation.type.name} (<span style={{ color: 'cyan' }}>{nation.type.pros}</span>
                <span style={{ color: 'magenta' }}>{nation.type.cons}</span>)
            </Body>

            {/* 군주/참모 */}
            <Head>{formatOfficerLevelText(20, nationLevel, false, nation.typeCode)}</Head>
            <Body style={{ color: getNPCColor(nation.topChiefs[20]?.npc ?? 1) }}>
                {nation.topChiefs[20]?.name ?? '-'}
            </Body>
            <Head>{formatOfficerLevelText(19, nationLevel, false, nation.typeCode)}</Head>
            <Body style={{ color: getNPCColor(nation.topChiefs[19]?.npc ?? 1) }}>
                {nation.topChiefs[19]?.name ?? '-'}
            </Body>

            {/* 총 주민 */}
            <Head>총 주민</Head>
            <Body>
                {noNation
                    ? '해당 없음'
                    : `${nation.population.now.toLocaleString()} / ${nation.population.max.toLocaleString()}`}
            </Body>

            {/* 총 병사 */}
            <Head>총 병사</Head>
            <Body>
                {noNation ? '해당 없음' : `${nation.crew.now.toLocaleString()} / ${nation.crew.max.toLocaleString()}`}
            </Body>

            {/* 국고/병량 */}
            <Head>국고</Head>
            <Body>{noNation ? '해당 없음' : nation.gold.toLocaleString()}</Body>
            <Head>병량</Head>
            <Body>{noNation ? '해당 없음' : nation.rice.toLocaleString()}</Body>

            {/* 지급률/세율 */}
            <Head>지급률</Head>
            <Body>{noNation ? '해당 없음' : `${nation.bill}%`}</Body>
            <Head>세율</Head>
            <Body>{noNation ? '해당 없음' : `${nation.taxRate}%`}</Body>

            {/* 속령/장수 */}
            <Head>속령</Head>
            <Body>{noNation ? '해당 없음' : nation.population.cityCnt.toLocaleString()}</Body>
            <Head>장수</Head>
            <Body>{noNation ? '해당 없음' : nation.crew.generalCnt.toLocaleString()}</Body>

            {/* 국력/기술력 */}
            <Head>국력</Head>
            <Body>{noNation ? '해당 없음' : nation.power.toLocaleString()}</Body>
            <Head>기술력</Head>
            <Body>
                {noNation ? (
                    '해당 없음'
                ) : (
                    <>
                        {currentTechLevel}등급 /{' '}
                        <span style={{ color: onTechLimit ? 'magenta' : 'limegreen' }}>
                            {Math.floor(nation.tech).toLocaleString()}
                        </span>
                    </>
                )}
            </Body>

            {/* 전략/외교 */}
            <Head>전략</Head>
            <Body
                style={
                    impossibleStrategicCommandText
                        ? {
                              textDecoration: 'underline dashed red',
                              textUnderlineOffset: '2px',
                          }
                        : undefined
                }
            >
                {noNation ? (
                    '해당 없음'
                ) : nation.strategicCmdLimit ? (
                    <span style={{ color: 'red' }} title={impossibleStrategicCommandText || undefined}>
                        {nation.strategicCmdLimit.toLocaleString()}턴
                    </span>
                ) : (
                    <span style={{ color: 'limegreen' }} title={impossibleStrategicCommandText || undefined}>
                        가능
                    </span>
                )}
            </Body>
            <Head>외교</Head>
            <Body>
                {noNation ? (
                    '해당 없음'
                ) : nation.diplomaticLimit ? (
                    <span style={{ color: 'red' }}>{nation.diplomaticLimit.toLocaleString()}턴</span>
                ) : (
                    <span style={{ color: 'limegreen' }}>가능</span>
                )}
            </Body>

            {/* 임관/전쟁 */}
            <Head>임관</Head>
            <Body>
                {noNation ? (
                    '해당 없음'
                ) : nation.prohibitScout ? (
                    <span style={{ color: 'red' }}>금지</span>
                ) : (
                    <span style={{ color: 'limegreen' }}>허가</span>
                )}
            </Body>
            <Head>전쟁</Head>
            <Body>
                {noNation ? (
                    '해당 없음'
                ) : nation.prohibitWar ? (
                    <span style={{ color: 'red' }}>금지</span>
                ) : (
                    <span style={{ color: 'limegreen' }}>허가</span>
                )}
            </Body>
        </div>
    );
}

function Head({ children }: { children: React.ReactNode }) {
    return (
        <div
            className="bg-muted flex items-center justify-center border-t border-border text-center text-muted-foreground text-xs"
            style={{ lineHeight: 'calc(193px / 10)' }}
        >
            {children}
        </div>
    );
}

function Body({ wide, style, children }: { wide?: number; style?: React.CSSProperties; children: React.ReactNode }) {
    return (
        <div
            className="border-t border-border text-center flex items-center justify-center"
            style={{
                lineHeight: 'calc(193px / 10)',
                ...(wide ? { gridColumn: `span ${wide}` } : {}),
                ...style,
            }}
        >
            {children}
        </div>
    );
}
