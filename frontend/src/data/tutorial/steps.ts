export type TutorialPhase = 'intro' | 'create' | 'internal' | 'battle' | 'diplomacy' | 'nation' | 'complete';

export interface TutorialStep {
    id: number;
    phase: TutorialPhase;
    route: string;
    title: string;
    description: string;
    targetSelector?: string;
    tooltipPosition?: 'top' | 'bottom' | 'left' | 'right';
    action?: 'click' | 'select' | 'confirm';
}

export const TUTORIAL_STEPS: TutorialStep[] = [
    // Phase 1: 인트로
    {
        id: 0,
        phase: 'intro',
        route: '/tutorial',
        title: '오픈삼국에 오신 것을 환영합니다!',
        description:
            '이 튜토리얼에서는 게임의 전체 흐름을 체험합니다. 장수 생성부터 전투, 외교까지 단계별로 안내해 드립니다.',
    },

    // Phase 2: 장수 생성
    {
        id: 1,
        phase: 'create',
        route: '/tutorial/create',
        title: '장수 생성 -- 스탯 배분',
        description:
            '통솔/무력/지력/정치/매력 5가지 능력치를 배분합니다. 통솔은 전투 지휘, 무력은 전투력, 지력은 계략에 영향을 줍니다.',
        targetSelector: '[data-tutorial="stat-form"]',
        action: 'select',
    },
    {
        id: 2,
        phase: 'create',
        route: '/tutorial/create',
        title: '장수 생성 -- 병종 선택',
        description: '창병, 궁병, 기병 등 병종에 따라 전투 스타일이 달라집니다.',
        targetSelector: '[data-tutorial="crew-select"]',
        action: 'select',
    },
    {
        id: 3,
        phase: 'create',
        route: '/tutorial/create',
        title: '장수 생성 완료!',
        description: '축하합니다! 유비로 플레이합니다. 이제 게임 메인 화면으로 이동합니다.',
        action: 'confirm',
    },

    // Phase 3: 내정
    {
        id: 4,
        phase: 'internal',
        route: '/tutorial/main',
        title: '메인 화면',
        description: '왼쪽 사이드바에서 각종 메뉴를 이용할 수 있습니다. 상단에는 현재 턴과 국가 정보가 표시됩니다.',
        targetSelector: '[data-tutorial="sidebar"]',
    },
    {
        id: 5,
        phase: 'internal',
        route: '/tutorial/command',
        title: '커맨드 -- 개간 선택',
        description:
            '커맨드 페이지에서 이번 턴에 수행할 행동을 선택합니다. 먼저 "개간"을 선택해 도시 농업 수치를 높여봅시다.',
        targetSelector: '[data-tutorial="command-panel"]',
        action: 'click',
    },
    {
        id: 6,
        phase: 'internal',
        route: '/tutorial/command',
        title: '개간 결과',
        description: '성도의 농업 수치가 올랐습니다! 턴이 진행되면 설정한 커맨드가 자동으로 실행됩니다.',
    },
    {
        id: 7,
        phase: 'internal',
        route: '/tutorial/city',
        title: '도시 정보',
        description:
            '도시의 농업/상업/인구/치안 등 다양한 수치를 확인할 수 있습니다. 내정 커맨드로 이 수치들을 높여 국력을 키웁니다.',
        targetSelector: '[data-tutorial="city-stats"]',
    },

    // Phase 4: 전투
    {
        id: 8,
        phase: 'battle',
        route: '/tutorial/command',
        title: '징병',
        description: '전투를 위해 병사를 모집합니다. 징병 커맨드를 선택하면 도시 인구에서 병사가 충원됩니다.',
        targetSelector: '[data-tutorial="command-panel"]',
        action: 'click',
    },
    {
        id: 9,
        phase: 'battle',
        route: '/tutorial/command',
        title: '훈련',
        description: '징병한 병사를 훈련시켜 전투력을 높입니다. 훈련도가 높을수록 전투에서 유리합니다.',
        targetSelector: '[data-tutorial="command-panel"]',
        action: 'click',
    },
    {
        id: 10,
        phase: 'battle',
        route: '/tutorial/command',
        title: '출진!',
        description: '준비가 되었습니다. 중립 도시 한중으로 출진합니다. 출진 커맨드에서 목표 도시를 선택합니다.',
        targetSelector: '[data-tutorial="command-panel"]',
        action: 'click',
    },
    {
        id: 11,
        phase: 'battle',
        route: '/tutorial/battle',
        title: '전투 결과',
        description:
            '전투 로그에서 아군과 적군의 피해를 확인할 수 있습니다. 장수의 능력치와 병종 상성이 결과에 영향을 줍니다.',
        targetSelector: '[data-tutorial="battle-log"]',
    },
    {
        id: 12,
        phase: 'battle',
        route: '/tutorial/battle',
        title: '도시 점령!',
        description: '한중을 점령했습니다! 점령한 도시는 우리 국가 소속이 됩니다. 영토를 넓혀 국력을 키워보세요.',
    },

    // Phase 5: 외교
    {
        id: 13,
        phase: 'diplomacy',
        route: '/tutorial/diplomacy',
        title: '외교 관계',
        description:
            '외교부에서 다른 국가와의 관계를 확인할 수 있습니다. 동맹, 불가침, 전쟁 등 다양한 외교 상태가 있습니다.',
        targetSelector: '[data-tutorial="diplomacy-table"]',
    },
    {
        id: 14,
        phase: 'diplomacy',
        route: '/tutorial/diplomacy',
        title: '중원 정보',
        description: '중원정보 페이지에서 전체 국가/세력의 현황을 한눈에 파악할 수 있습니다.',
    },
    {
        id: 15,
        phase: 'diplomacy',
        route: '/tutorial/diplomacy',
        title: '외교 서신',
        description: '다른 국가에 외교 서신을 보내 동맹을 제안하거나 선전포고를 할 수 있습니다.',
        targetSelector: '[data-tutorial="send-letter"]',
        action: 'click',
    },

    // Phase 6: 국가 운영
    {
        id: 16,
        phase: 'nation',
        route: '/tutorial/nation',
        title: '세력 정보',
        description: '국가의 자원, 기술, 장수 현황을 확인합니다. 군주나 고위 관직자는 국가 정책을 결정할 수 있습니다.',
        targetSelector: '[data-tutorial="nation-info"]',
    },
    {
        id: 17,
        phase: 'nation',
        route: '/tutorial/nation',
        title: '인사부',
        description: '장수에게 관직을 임명하여 능력 보너스를 부여할 수 있습니다. 적재적소에 인재를 배치하세요.',
    },
    {
        id: 18,
        phase: 'nation',
        route: '/tutorial/nation',
        title: '내무부',
        description: '세율 조정, 수도 이전 등 국가 운영 전반의 정책을 설정합니다.',
    },

    // Complete
    {
        id: 19,
        phase: 'complete',
        route: '/tutorial/complete',
        title: '튜토리얼 완료!',
        description:
            '축하합니다! 오픈삼국의 기본 플로우를 모두 체험했습니다. 이제 실제 서버에 참여하여 천하를 통일해 보세요!',
    },
];
