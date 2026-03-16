'use client';

import { useRouter } from 'next/navigation';
import { CITY_LEVEL_NAMES } from '@/lib/game-utils';

// --- CompactTooltip ---
// Used by MapViewer (home, lobby, city picker, command forms, history)

interface CompactTooltipProps {
    cityText: string;
    nationAbbr: string | null;
    nationColor: string | null;
    nationText: string | null;
    isEmperorCity: boolean;
    stateText: string | null;
    stateCode: number;
    position: { x: number; y: number };
    /** text color for the nation abbreviation badge */
    abbrTextColor: string;
    /** bounds for clamping tooltip position */
    bounds: { width: number; height: number };
}

export function CompactTooltip({
    cityText,
    nationAbbr,
    nationColor,
    nationText,
    isEmperorCity,
    stateText,
    stateCode,
    position,
    abbrTextColor,
    bounds,
}: CompactTooltipProps) {
    const abbr = nationAbbr || (nationText ? nationText.slice(0, 1) : '');
    const flagSize = 16;

    return (
        <div
            className="absolute z-[16] pointer-events-none whitespace-nowrap text-[13px] rounded overflow-hidden shadow-lg"
            style={{
                top: Math.min(position.y + 30, bounds.height - 50),
                left: Math.min(position.x + 10, bounds.width - 140),
                border: '1px solid rgba(255,255,255,0.15)',
                minWidth: 120,
            }}
        >
            <div
                className="px-1.5 font-medium text-white"
                style={{
                    backgroundColor: 'rgb(30, 140, 230)',
                    lineHeight: '18px',
                    height: 18,
                }}
            >
                {cityText}
                {isEmperorCity && (
                    <span
                        className="ml-1 inline-flex items-center rounded-sm px-0.5"
                        style={{ backgroundColor: '#f0c040' }}
                    >
                        <img src="/icons/emperor.png" alt="황제" width={14} height={14} />
                    </span>
                )}
            </div>
            {nationText && (
                <div
                    className="px-1.5 flex items-center gap-1.5 text-white font-bold"
                    style={{
                        backgroundColor: 'rgba(20, 20, 30, 0.92)',
                        lineHeight: '20px',
                        height: 20,
                        borderTop: '1px solid rgba(255,255,255,0.08)',
                    }}
                >
                    <span
                        className="inline-flex items-center justify-center font-bold shrink-0"
                        style={{
                            width: flagSize,
                            height: flagSize,
                            backgroundColor: nationColor ?? '#666',
                            color: abbrTextColor,
                            fontSize: abbr.length > 1 ? 8 : 10,
                            letterSpacing: abbr.length > 1 ? '-1px' : undefined,
                            lineHeight: 1,
                            borderRadius: 2,
                            textShadow: abbrTextColor === 'black' ? 'none' : '0 1px 2px rgba(0,0,0,0.5)',
                        }}
                    >
                        {abbr}
                    </span>
                    <span style={{ textShadow: '0 0 4px rgba(0,0,0,0.8)' }}>{nationText}</span>
                </div>
            )}
            {stateText && (
                <div
                    className="px-1.5 text-right text-white"
                    style={{
                        backgroundColor: stateCode > 0 && stateCode <= 2 ? 'rgb(46, 143, 70)' : 'rgb(180, 40, 40)',
                        lineHeight: '17px',
                        height: 17,
                        borderTop: '1px solid rgba(255,255,255,0.08)',
                    }}
                >
                    {stateText}
                </div>
            )}
        </div>
    );
}

// --- DetailTooltip ---
// Used by the full map page (/map)

interface DetailTooltipGeneralInfo {
    name: string;
    nationColor: string;
    crew: number;
    crewType: string;
    isForeign: boolean;
}

interface DetailTooltipProps {
    cityId: number;
    cityName: string;
    nationName: string;
    nationColor: string;
    isVisible: boolean;
    level: number;
    pop: number;
    agri: string;
    comm: string;
    secu: string;
    def: string;
    wall: string;
    trust: number;
    generals: DetailTooltipGeneralInfo[];
    position: { x: number; y: number };
}

export function DetailTooltip({
    cityId,
    cityName,
    nationName,
    nationColor,
    isVisible,
    level,
    pop,
    agri,
    comm,
    secu,
    def,
    wall,
    trust,
    generals,
    position,
}: DetailTooltipProps) {
    const router = useRouter();

    return (
        <div
            className="fixed z-50 bg-gray-800 border border-gray-700 rounded-lg p-3 shadow-lg text-sm space-y-1 max-w-xs"
            style={{
                left: position.x + 12,
                top: position.y - 10,
            }}
        >
            <div className="font-semibold flex items-center gap-2">
                <span className="w-3 h-3 rounded-full" style={{ backgroundColor: nationColor }} />
                {cityName}
            </div>
            <div className="text-gray-400">소속: {nationName}</div>
            <div className="text-gray-400">레벨: {CITY_LEVEL_NAMES[level] ?? level}</div>
            <div className="text-gray-400">인구: {isVisible ? pop.toLocaleString() : '?'}</div>
            <div className="text-gray-400">농업: {agri}</div>
            <div className="text-gray-400">상업: {comm}</div>
            <div className="text-gray-400">치안: {secu}</div>
            <div className="text-gray-400">수비: {def}</div>
            <div className="text-gray-400">성벽: {wall}</div>
            <div className="text-gray-400">민심: {isVisible ? trust : '?'}</div>

            <button
                type="button"
                className="w-full text-center text-xs text-cyan-400 hover:text-cyan-300 border border-gray-600 rounded px-2 py-1 mt-1"
                onClick={(e) => {
                    e.stopPropagation();
                    router.push(`/city?id=${cityId}`);
                }}
            >
                도시 상세 보기
            </button>

            {isVisible ? (
                generals.length > 0 && (
                    <div className="border-t border-gray-700 pt-1 mt-1">
                        <div className="text-gray-300 font-medium text-xs mb-0.5">주둔 장수 ({generals.length}명)</div>
                        <div className="max-h-32 overflow-y-auto space-y-0.5">
                            {generals.map((g) => (
                                <div
                                    key={`${g.name}:${g.crewType}:${g.crew}`}
                                    className="flex items-center gap-1.5 text-xs"
                                >
                                    <span
                                        className="w-2 h-2 rounded-full shrink-0"
                                        style={{ backgroundColor: g.nationColor }}
                                    />
                                    <span className={g.isForeign ? 'text-red-400 font-bold' : 'text-gray-300'}>
                                        {g.name}
                                    </span>
                                    <span className="text-muted-foreground ml-auto">
                                        {g.crewType} {g.crew.toLocaleString()}
                                    </span>
                                </div>
                            ))}
                        </div>
                    </div>
                )
            ) : (
                <div className="border-t border-gray-700 pt-1 mt-1 text-xs text-yellow-300">
                    첩보 부족: 자국 도시 또는 첩보 확보 도시만 상세 정보를 볼 수 있습니다.
                </div>
            )}
        </div>
    );
}
