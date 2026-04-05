'use client';

import { useRouter } from 'next/navigation';
import { CITY_LEVEL_NAMES } from '@/lib/game-utils';

const GLASS =
    'backdrop-blur-md bg-black/75 border border-foreground/15 rounded-none shadow-xl text-white animate-in fade-in duration-150';

interface NationBadgeProps {
    abbr: string;
    color: string | null;
    textColor: string;
}

function NationBadge({ abbr, color, textColor }: NationBadgeProps) {
    return (
        <span
            className="inline-flex items-center justify-center font-bold shrink-0 rounded-full"
            style={{
                width: 18,
                height: 18,
                backgroundColor: color ?? '#666',
                color: textColor,
                fontSize: abbr.length > 1 ? 8 : 10,
                letterSpacing: abbr.length > 1 ? '-1px' : undefined,
                lineHeight: 1,
                textShadow: textColor === 'black' ? 'none' : '0 1px 2px rgba(0,0,0,0.5)',
            }}
        >
            {abbr}
        </span>
    );
}

interface CompactTooltipProps {
    cityText: string;
    nationAbbr: string | null;
    nationColor: string | null;
    nationText: string | null;
    isEmperorCity: boolean;
    stateText: string | null;
    stateCode: number;
    position: { x: number; y: number };
    abbrTextColor: string;
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

    return (
        <div
            className={`absolute z-[16] pointer-events-none whitespace-nowrap text-[13px] ${GLASS} p-0 overflow-hidden`}
            style={{
                top: Math.min(position.y + 30, bounds.height - 50),
                left: Math.min(position.x + 10, bounds.width - 140),
                minWidth: 120,
            }}
        >
            <div className="px-2 py-1 font-medium text-[13px] flex items-center gap-1.5">
                {nationText && <NationBadge abbr={abbr} color={nationColor} textColor={abbrTextColor} />}
                <span>{cityText}</span>
                {isEmperorCity && (
                    <span className="ml-0.5 inline-flex items-center rounded-sm px-0.5 bg-yellow-500/80">
                        <img src="/icons/emperor.png" alt="황제" width={12} height={12} />
                    </span>
                )}
            </div>
            {nationText && (
                <div className="px-2 py-0.5 text-[11px] text-white/70 border-t border-white/5">{nationText}</div>
            )}
            {stateText && (
                <div
                    className="px-2 py-0.5 text-[11px] text-right border-t border-white/5"
                    style={{
                        color: stateCode > 0 && stateCode <= 2 ? '#6ee7b7' : '#fca5a5',
                    }}
                >
                    {stateText}
                </div>
            )}
        </div>
    );
}

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
            className={`fixed z-50 ${GLASS} p-3 text-sm space-y-1 max-w-xs`}
            style={{
                left: position.x + 12,
                top: position.y - 10,
            }}
        >
            <div className="font-semibold flex items-center gap-2">
                <span className="w-3 h-3 rounded-full shrink-0" style={{ backgroundColor: nationColor }} />
                {cityName}
            </div>
            <div className="text-white/50 text-xs">소속: {nationName}</div>
            <div className="grid grid-cols-2 gap-x-3 gap-y-0.5 text-xs text-white/60">
                <span>레벨: {CITY_LEVEL_NAMES[level] ?? level}</span>
                <span>인구: {isVisible ? pop.toLocaleString() : '?'}</span>
                <span>농업: {agri}</span>
                <span>상업: {comm}</span>
                <span>치안: {secu}</span>
                <span>수비: {def}</span>
                <span>성벽: {wall}</span>
                <span>민심: {isVisible ? trust : '?'}</span>
            </div>

            <button
                type="button"
                className="w-full text-center text-xs text-cyan-400 hover:text-cyan-300 border border-white/10 rounded px-2 py-1 mt-1 transition-colors"
                onClick={(e) => {
                    e.stopPropagation();
                    router.push(`/city?id=${cityId}`);
                }}
            >
                도시 상세 보기
            </button>

            {isVisible ? (
                generals.length > 0 && (
                    <div className="border-t border-white/10 pt-1 mt-1">
                        <div className="text-white/70 font-medium text-xs mb-0.5">주둔 장수 ({generals.length}명)</div>
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
                                    <span className={g.isForeign ? 'text-red-400 font-bold' : 'text-white/70'}>
                                        {g.name}
                                    </span>
                                    <span className="text-white/40 ml-auto">
                                        {g.crewType} {g.crew.toLocaleString()}
                                    </span>
                                </div>
                            ))}
                        </div>
                    </div>
                )
            ) : (
                <div className="border-t border-white/10 pt-1 mt-1 text-xs text-yellow-300/80">
                    첩보 부족: 자국 도시 또는 첩보 확보 도시만 상세 정보를 볼 수 있습니다.
                </div>
            )}
        </div>
    );
}
