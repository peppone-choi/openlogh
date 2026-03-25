'use client';

import { useState, useMemo } from 'react';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { useGameStore } from '@/stores/gameStore';
import { useOfficerStore } from '@/stores/officerStore';
import { useWorldStore } from '@/stores/worldStore';
import type { City, CommandArg, General, Nation } from '@/types';
import { PLANET_LEVEL_NAMES } from '@/lib/game-utils';
import { MapViewer } from './map-viewer';

/** Arg schema for each command that requires user input */
type ArgField =
    | { type: 'city'; key: string; label: string }
    | { type: 'nation'; key: string; label: string }
    | { type: 'general'; key: string; label: string }
    | { type: 'number'; key: string; label: string; placeholder?: string }
    | { type: 'boolean'; key: string; label: string }
    | { type: 'text'; key: string; label: string; placeholder?: string }
    | {
          type: 'select';
          key: string;
          label: string;
          options: { value: string; label: string }[];
      };

const COMMAND_ARGS: Record<string, ArgField[]> = {
    // ===== Default =====
    휴식: [],

    // ===== Operations / MCP =====
    워프항행: [],
    성계내항행: [{ type: 'city', key: 'destCityId', label: '목적지 행성' }],
    연료보급: [],
    정찰: [{ type: 'city', key: 'destCityId', label: '정찰 대상 행성' }],
    군기유지: [],
    기본훈련: [],
    특수훈련: [],
    맹훈련: [],
    육전훈련: [],
    공전훈련: [],
    경계출동: [],
    무력진압: [],
    분열행진: [],
    징발: [],
    특별경비: [],
    정비: [],
    지상작전개시: [{ type: 'city', key: 'destCityId', label: '목표 행성' }],
    지상전투개시: [{ type: 'city', key: 'destCityId', label: '목표 행성' }],
    점령: [],
    철수: [],
    후퇴: [],
    육전대출격: [],
    육전대철수: [],
    육전전술훈련: [],
    공전전술훈련: [],

    // ===== Personal / PCP =====
    퇴역: [],
    지원전환: [],
    망명: [{ type: 'nation', key: 'destNationId', label: '망명 대상 진영' }],
    회견: [{ type: 'general', key: 'destGeneralId', label: '대상 장교' }],
    수강: [],
    기함구매: [],
    자금투입: [],
    귀환설정: [{ type: 'city', key: 'destCityId', label: '귀환 행성' }],
    원거리이동: [{ type: 'city', key: 'destCityId', label: '목적지 행성' }],
    근거리이동: [],
    병기연습: [],
    반의: [],
    모의: [],
    설득: [{ type: 'general', key: 'destGeneralId', label: '설득 대상' }],
    반란참가: [],
    반란: [],

    // ===== Command / Leadership =====
    작전계획: [],
    장수발령: [
        { type: 'general', key: 'destGeneralId', label: '대상 장교' },
        { type: 'city', key: 'destCityId', label: '이동 행성' },
    ],
    작전철회: [],
    부대결성: [],
    부대해산: [],
    강의: [],
    수송계획: [],
    수송중지: [],

    // ===== Logistics =====
    재편성: [],
    완전수리: [],
    완전보급: [],
    반출입: [],
    보충: [],
    할당: [],

    // ===== Influence / Social =====
    야회: [],
    수렵: [],
    회담: [],
    담화: [],
    연설: [],

    // ===== Personal (proposal/order) =====
    제안: [{ type: 'general', key: 'destGeneralId', label: '제안 대상' }],
    명령: [{ type: 'general', key: 'destGeneralId', label: '명령 대상' }],

    // ===== Espionage / Intelligence =====
    일제수색: [],
    체포허가: [{ type: 'general', key: 'destGeneralId', label: '체포 대상' }],
    집행명령: [{ type: 'general', key: 'destGeneralId', label: '집행 대상' }],
    체포명령: [{ type: 'general', key: 'destGeneralId', label: '체포 대상' }],
    사열: [],
    습격: [{ type: 'general', key: 'destGeneralId', label: '습격 대상' }],
    감시: [{ type: 'general', key: 'destGeneralId', label: '감시 대상' }],
    잠입공작: [{ type: 'city', key: 'destCityId', label: '잠입 대상 행성' }],
    탈출공작: [],
    정보공작: [],
    파괴공작: [],
    선동공작: [{ type: 'city', key: 'destCityId', label: '선동 대상 행성' }],
    귀환공작: [],
    통신방해: [],
    위장함대: [],

    // ===== Nation: Default =====
    Nation휴식: [],

    // ===== Nation: Personnel =====
    승진: [{ type: 'general', key: 'destGeneralId', label: '승진 대상' }],
    발탁: [{ type: 'general', key: 'destGeneralId', label: '발탁 대상' }],
    강등: [{ type: 'general', key: 'destGeneralId', label: '강등 대상' }],
    서작: [{ type: 'general', key: 'destGeneralId', label: '서작 대상' }],
    서훈: [{ type: 'general', key: 'destGeneralId', label: '서훈 대상' }],
    임명: [{ type: 'general', key: 'destGeneralId', label: '임명 대상' }],
    파면: [{ type: 'general', key: 'destGeneralId', label: '파면 대상' }],
    사임: [],
    봉토수여: [
        { type: 'general', key: 'destGeneralId', label: '봉토 수여 대상' },
        { type: 'city', key: 'destCityId', label: '봉토 행성' },
    ],
    봉토직할: [{ type: 'city', key: 'destCityId', label: '직할 전환 행성' }],

    // ===== Nation: Political =====
    국가목표설정: [],
    납입률변경: [{ type: 'number', key: 'rate', label: '납입률 (%)', placeholder: '0~100' }],
    관세율변경: [{ type: 'number', key: 'rate', label: '관세율 (%)', placeholder: '0~100' }],
    분배: [],
    처단: [{ type: 'general', key: 'destGeneralId', label: '처단 대상' }],
    외교: [{ type: 'nation', key: 'destNationId', label: '외교 대상 진영' }],
    통치목표: [],
    예산편성: [],
    제안공작: [{ type: 'general', key: 'destGeneralId', label: '제안공작 대상' }],

    // ===== Nation: Diplomacy =====
    선전포고: [{ type: 'nation', key: 'destNationId', label: '대상 진영' }],
    불가침제의: [{ type: 'nation', key: 'destNationId', label: '대상 진영' }],
    불가침수락: [
        { type: 'nation', key: 'destNationId', label: '대상 진영' },
        { type: 'general', key: 'destGeneralId', label: '대상 장교' },
    ],
    불가침파기제의: [{ type: 'nation', key: 'destNationId', label: '대상 진영' }],
    불가침파기수락: [
        { type: 'nation', key: 'destNationId', label: '대상 진영' },
        { type: 'general', key: 'destGeneralId', label: '대상 장교' },
    ],
    종전제의: [{ type: 'nation', key: 'destNationId', label: '대상 진영' }],
    종전수락: [
        { type: 'nation', key: 'destNationId', label: '대상 진영' },
        { type: 'general', key: 'destGeneralId', label: '대상 장교' },
    ],

    // ===== Nation: Resource / Administration =====
    감축: [{ type: 'city', key: 'destCityId', label: '감축 대상 행성' }],
    주민동원: [{ type: 'city', key: 'destCityId', label: '동원 대상 행성' }],
    외교공작: [{ type: 'nation', key: 'destNationId', label: '대상 진영' }],
    세율변경: [{ type: 'number', key: 'rate', label: '세율 (%)', placeholder: '0~100' }],
    징병률변경: [{ type: 'number', key: 'rate', label: '징병률 (%)', placeholder: '0~100' }],
    국가해산: [],
    항복: [{ type: 'nation', key: 'destNationId', label: '항복 대상 진영' }],
};

/** Contextual help text for commands */
const COMMAND_HELP: Record<string, string> = {
    // Operations
    워프항행: '워프 항행으로 지정된 그리드 좌표로 이동합니다. 연료를 소모합니다.',
    성계내항행: '성계 내 다른 행성으로 항행합니다. 대기 시간이 발생합니다.',
    연료보급: '함대에 연료를 보급합니다.',
    정찰: '대상 행성의 정보를 수집합니다.',
    군기유지: '함대의 군기를 유지하여 사기를 높입니다.',
    기본훈련: '함대의 기본 훈련을 실시합니다.',
    특수훈련: '물자를 소모하여 특수 훈련을 실시합니다.',
    맹훈련: '물자를 소모하여 고강도 훈련을 실시합니다. 사기가 감소할 수 있습니다.',
    육전훈련: '육전 능력을 향상시키는 훈련입니다.',
    공전훈련: '공중전 능력을 향상시키는 훈련입니다.',
    경계출동: '아군 행성의 치안을 강화합니다.',
    무력진압: '치안을 크게 강화하지만 지지율이 하락합니다.',
    분열행진: '분열행진을 실시합니다.',
    징발: '행성에서 물자를 징발합니다.',
    특별경비: '특별 경비 태세를 갖춥니다.',
    정비: '함선을 정비하여 훈련도를 회복합니다.',
    지상작전개시: '적 행성에 대한 지상 작전을 개시합니다.',
    지상전투개시: '적 행성에서 지상 전투를 개시합니다.',
    점령: '적 행성을 점령합니다.',
    철수: '현재 위치에서 철수합니다.',
    후퇴: '긴급 후퇴합니다. 사기가 크게 감소합니다.',
    육전대출격: '육전대를 행성으로 출격시킵니다.',
    육전대철수: '육전대를 철수시킵니다.',
    육전전술훈련: '육전 전술 훈련을 실시합니다.',
    공전전술훈련: '공중전 전술 훈련을 실시합니다.',
    // Personal
    퇴역: '현역에서 퇴역합니다.',
    지원전환: '군사/정치 경력을 전환합니다.',
    망명: '다른 진영으로 망명합니다.',
    회견: '같은 행성의 장교와 회견하여 친밀도를 높입니다.',
    수강: '사관학교에서 수강하여 능력치를 향상시킵니다.',
    기함구매: '기함을 구매합니다.',
    자금투입: '개인 자금을 투입합니다.',
    귀환설정: '기함 격침 시 귀환할 행성을 설정합니다.',
    원거리이동: '다른 행성의 시설로 이동합니다.',
    근거리이동: '같은 행성 내 스팟으로 이동합니다.',
    병기연습: '병기 연습을 실시합니다.',
    반의: '반의를 표명합니다 (쿠데타 1단계).',
    모의: '같은 행성의 장교와 모의합니다 (쿠데타 2단계).',
    설득: '대상 장교를 설득합니다.',
    반란참가: '진행 중인 반란에 참가합니다.',
    반란: '반란을 개시합니다 (쿠데타 실행).',
    // Command
    작전계획: '작전 계획을 수립합니다.',
    장수발령: '장교를 지정 행성으로 발령합니다.',
    작전철회: '수립된 작전을 철회합니다.',
    부대결성: '새로운 함대를 결성합니다.',
    부대해산: '소속 함대를 해산합니다.',
    강의: '강의를 실시하여 장교의 능력을 향상시킵니다.',
    수송계획: '물자 수송 계획을 수립합니다.',
    수송중지: '수송을 중지합니다.',
    // Logistics
    재편성: '함대를 재편성하여 훈련도를 향상시킵니다.',
    완전수리: '함대를 완전히 수리합니다.',
    완전보급: '함대에 물자를 보급합니다.',
    반출입: '물자를 국가와 개인 간 이동합니다.',
    보충: '손실 함선을 보충합니다.',
    할당: '행성 창고에서 함대로 유닛을 할당합니다.',
    // Influence
    야회: '야회를 개최하여 영향력을 높입니다.',
    수렵: '봉토 행성에서 수렵을 실시합니다.',
    회담: '다른 장교와 회담하여 영향력을 높입니다.',
    담화: '다른 장교와 담화를 나눕니다.',
    연설: '행성에서 연설하여 영향력과 지지율을 높입니다.',
    // Proposal/Order
    제안: '상급자에게 제안합니다.',
    명령: '하급자에게 명령합니다.',
    // Espionage
    일제수색: '행성에서 적 스파이를 수색합니다.',
    체포허가: '대상 장교에 대한 체포 허가를 발부합니다.',
    집행명령: '체포 대상에 대한 집행 명령을 하달합니다.',
    체포명령: '같은 행성의 장교를 체포합니다.',
    사열: '사열로 반란 징후를 탐지합니다.',
    습격: '같은 행성의 장교를 습격합니다.',
    감시: '대상 장교를 감시합니다.',
    잠입공작: '적 행성에 잠입합니다.',
    탈출공작: '잠입 상태에서 탈출을 시도합니다.',
    정보공작: '잠입 중 정보를 수집합니다.',
    파괴공작: '잠입 중 시설을 파괴합니다.',
    선동공작: '적 행성의 지지율을 떨어뜨립니다.',
    귀환공작: '잠입 완료 후 아군 영토로 귀환합니다.',
    통신방해: '적의 통신을 방해합니다.',
    위장함대: '위장 함대를 운용합니다.',
    // Nation
    Nation휴식: '국가 커맨드를 사용하지 않고 턴을 넘깁니다.',
    승진: '장교를 1계급 승진시킵니다.',
    발탁: '장교를 특별 발탁합니다.',
    강등: '장교를 1계급 강등합니다.',
    서작: '장교에게 작위를 수여합니다 (제국 전용).',
    서훈: '장교에게 훈장을 수여합니다.',
    임명: '장교에게 직무카드를 부여합니다.',
    파면: '장교의 직무카드를 박탈합니다.',
    사임: '현재 직무에서 사임합니다.',
    봉토수여: '장교에게 행성을 봉토로 수여합니다 (제국 전용).',
    봉토직할: '봉토를 직할령으로 전환합니다 (제국 전용).',
    국가목표설정: '국가 전략 목표를 설정합니다.',
    납입률변경: '납입률을 변경합니다.',
    관세율변경: '관세율을 변경합니다.',
    분배: '국가 자원을 장교에게 분배합니다.',
    처단: '체포된 인물을 처형 또는 추방합니다.',
    외교: '외교 행동을 실행합니다 (선전포고/종전/불가침).',
    통치목표: '행성의 통치 목표를 설정합니다.',
    예산편성: '국가 예산을 편성합니다.',
    제안공작: '정치공작을 소모하여 제안을 강제 수락시킵니다.',
    선전포고: '타국에게 선전 포고합니다.',
    종전제의: '전쟁 중인 진영에 종전을 제의합니다.',
    종전수락: '종전 제의를 수락합니다.',
    불가침제의: '타국에 불가침 조약을 제의합니다.',
    불가침수락: '불가침 제의를 수락합니다.',
    불가침파기제의: '불가침 조약 파기를 제의합니다.',
    불가침파기수락: '불가침 파기 제의를 수락합니다.',
    감축: '행성의 시설 등급을 감축합니다.',
    주민동원: '행성 주민을 동원하여 방어 시설을 강화합니다.',
    외교공작: '외교 공작을 실행합니다.',
    세율변경: '세율을 변경합니다.',
    징병률변경: '징병률을 변경합니다.',
    국가해산: '국가를 해산합니다.',
    항복: '대상 진영에 항복합니다.',
};

/** Commands that target cities (shown with map selector) */
const CITY_TARGET_COMMANDS = new Set([
    '성계내항행',
    '정찰',
    '지상작전개시',
    '지상전투개시',
    '잠입공작',
    '선동공작',
    '귀환설정',
    '원거리이동',
    '감축',
    '주민동원',
    '봉토직할',
    '장수발령',
    '봉토수여',
]);

interface CommandArgFormProps {
    actionCode: string;
    onSubmit: (arg: CommandArg) => void;
}

export function CommandArgForm({ actionCode, onSubmit }: CommandArgFormProps) {
    const { cities, nations, generals } = useGameStore();
    const { myOfficer } = useOfficerStore();
    const { currentWorld } = useWorldStore();
    const [valuesByCommand, setValuesByCommand] = useState<Record<string, Record<string, string>>>({});
    const [mapSelectorOpen, setMapSelectorOpen] = useState(false);
    const [mapSelectorField, setMapSelectorField] = useState<string | null>(null);

    const fields = COMMAND_ARGS[actionCode];
    const values = valuesByCommand[actionCode] ?? {};
    const helpText = COMMAND_HELP[actionCode];

    // Sort cities by nation ownership for better UX
    const sortedCities = useMemo(() => {
        if (!myOfficer) return cities;
        const myCities: City[] = [];
        const otherCities: City[] = [];
        for (const c of cities) {
            if (c.nationId === myOfficer.nationId) myCities.push(c);
            else otherCities.push(c);
        }
        return [...myCities, ...otherCities];
    }, [cities, myOfficer]);

    if (!fields) {
        // No args needed - auto-submit
        return null;
    }

    const setValue = (key: string, val: string) => {
        setValuesByCommand((prev) => ({
            ...prev,
            [actionCode]: {
                ...(prev[actionCode] ?? {}),
                [key]: val,
            },
        }));
    };

    const handleSubmit = () => {
        const arg: CommandArg = {};
        for (const field of fields) {
            const raw = values[field.key];
            if (!raw && raw !== '0') continue;
            if (
                field.type === 'number' ||
                field.type === 'city' ||
                field.type === 'nation' ||
                field.type === 'general'
            ) {
                arg[field.key] = Number(raw);
            } else if (field.type === 'boolean') {
                arg[field.key] = raw === 'true';
            } else if (field.type === 'select' && !isNaN(Number(raw))) {
                arg[field.key] = Number(raw);
            } else {
                arg[field.key] = raw;
            }
        }
        onSubmit(arg);
    };

    // Filter cities to own nation for some commands
    const myCities = myOfficer ? cities.filter((c) => c.nationId === myOfficer.nationId) : cities;

    const renderField = (field: ArgField) => {
        switch (field.type) {
            case 'city': {
                const isCityTarget = CITY_TARGET_COMMANDS.has(actionCode);
                const showAllCities = field.key === 'destCityId' || field.key === 'destCityID' || isCityTarget;
                const list: City[] = showAllCities ? sortedCities : myCities;
                const selectedCity = cities.find((c) => c.id === Number(values[field.key]));
                return (
                    <div key={field.key} className="space-y-2">
                        <div className="flex items-center gap-2">
                            <span className="text-xs font-medium">
                                {selectedCity
                                    ? `${selectedCity.name} (${PLANET_LEVEL_NAMES[selectedCity.level] ?? selectedCity.level})`
                                    : `${field.label} 미선택`}
                            </span>
                            <select
                                value={values[field.key] ?? ''}
                                onChange={(e) => setValue(field.key, e.target.value)}
                                className="flex-1 bg-background border border-input rounded-md px-2 py-1 text-xs"
                            >
                                <option value="">직접 선택</option>
                                {list.map((c) => {
                                    const nation = nations.find((n) => n.id === c.nationId);
                                    const nationTag = nation ? ` [${nation.name}]` : c.nationId === 0 ? ' [공백]' : '';
                                    const isMyCity = myOfficer && c.nationId === myOfficer.nationId;
                                    return (
                                        <option
                                            key={c.id}
                                            value={c.id}
                                            style={isMyCity ? { fontWeight: 'bold' } : undefined}
                                        >
                                            {c.name} ({PLANET_LEVEL_NAMES[c.level] ?? c.level}){nationTag}
                                        </option>
                                    );
                                })}
                            </select>
                        </div>
                        <div className="w-full max-w-lg mx-auto overflow-hidden rounded">
                            <MapViewer
                                worldId={currentWorld?.id ?? 0}
                                compact
                                onCitySelect={(cityId: number) => setValue(field.key, cityId.toString())}
                            />
                        </div>
                    </div>
                );
            }
            case 'nation': {
                const list: Nation[] = nations.filter((n) => !myOfficer || n.id !== myOfficer.nationId);
                return (
                    <select
                        key={field.key}
                        value={values[field.key] ?? ''}
                        onChange={(e) => setValue(field.key, e.target.value)}
                        className="w-full bg-background border border-input rounded-md px-2 py-1.5 text-xs"
                    >
                        <option value="">{field.label}...</option>
                        {list.map((n) => (
                            <option key={n.id} value={n.id}>
                                {n.name}
                            </option>
                        ))}
                    </select>
                );
            }
            case 'general': {
                const list: General[] = generals.filter((g) => !myOfficer || g.id !== myOfficer.id);
                return (
                    <select
                        key={field.key}
                        value={values[field.key] ?? ''}
                        onChange={(e) => setValue(field.key, e.target.value)}
                        className="w-full bg-background border border-input rounded-md px-2 py-1.5 text-xs"
                    >
                        <option value="">{field.label}...</option>
                        {list.map((g) => {
                            const nation = nations.find((n) => n.id === g.nationId);
                            const nationTag = nation ? ` [${nation.name}]` : g.nationId === 0 ? ' [재야]' : '';
                            const city = cities.find((c) => c.id === g.cityId);
                            const cityTag = city ? ` ${city.name}` : '';
                            const stats = `${g.leadership ?? '?'}/${g.strength ?? '?'}/${g.intel ?? '?'}`;
                            return (
                                <option key={g.id} value={g.id}>
                                    {g.name}
                                    {nationTag}
                                    {cityTag} ({stats})
                                </option>
                            );
                        })}
                    </select>
                );
            }
            case 'select':
                return (
                    <select
                        key={field.key}
                        value={values[field.key] ?? ''}
                        onChange={(e) => setValue(field.key, e.target.value)}
                        className="w-full bg-background border border-input rounded-md px-2 py-1.5 text-xs"
                    >
                        <option value="">{field.label}...</option>
                        {field.options.map((o) => (
                            <option key={o.value} value={o.value}>
                                {o.label}
                            </option>
                        ))}
                    </select>
                );
            case 'boolean':
                return (
                    <label key={field.key} className="flex items-center gap-2 text-xs text-muted-foreground">
                        <input
                            type="checkbox"
                            checked={values[field.key] === 'true'}
                            onChange={(e) => setValue(field.key, e.target.checked ? 'true' : 'false')}
                            className="accent-amber-400"
                        />
                        {field.label}
                    </label>
                );
            case 'number':
                return (
                    <Input
                        key={field.key}
                        type="number"
                        value={values[field.key] ?? ''}
                        onChange={(e) => setValue(field.key, e.target.value)}
                        placeholder={field.placeholder ?? field.label}
                        className="text-xs h-8"
                    />
                );
            case 'text':
                return (
                    <Input
                        key={field.key}
                        type="text"
                        value={values[field.key] ?? ''}
                        onChange={(e) => setValue(field.key, e.target.value)}
                        placeholder={field.placeholder ?? field.label}
                        className="text-xs h-8"
                    />
                );
        }
    };

    return (
        <div className="space-y-2">
            {helpText && (
                <div className="rounded-md bg-amber-900/20 border border-amber-800/40 px-3 py-2 text-xs text-amber-200/90">
                    {helpText}
                </div>
            )}
            <p className="text-xs text-muted-foreground">명령 인자</p>
            {fields.map((field) => (
                <div key={field.key} className="space-y-0.5">
                    <label htmlFor={`arg-${field.key}`} className="text-[10px] text-muted-foreground font-medium">
                        {field.label}
                    </label>
                    <div id={`arg-${field.key}`}>{renderField(field)}</div>
                </div>
            ))}
            <Button size="sm" onClick={handleSubmit} className="w-full">
                예약
            </Button>
        </div>
    );
}

export { COMMAND_ARGS };
