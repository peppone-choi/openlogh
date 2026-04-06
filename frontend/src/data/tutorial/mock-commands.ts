import type { CommandTableEntry, CommandResult } from '@/types';

/** 튜토리얼에서 보여줄 커맨드 테이블 */
export const MOCK_COMMAND_TABLE: CommandTableEntry[] = [
    {
        actionCode: 'che_develop_agri',
        name: '개간',
        category: '행성 관리',
        enabled: true,
        durationSeconds: 0,
        commandPointCost: 1,
    },
    {
        actionCode: 'che_develop_comm',
        name: '교역투자',
        category: '행성 관리',
        enabled: true,
        durationSeconds: 0,
        commandPointCost: 1,
    },
    {
        actionCode: 'che_develop_secu',
        name: '치안강화',
        category: '행성 관리',
        enabled: true,
        durationSeconds: 0,
        commandPointCost: 1,
    },
    {
        actionCode: 'che_recruit',
        name: '징병',
        category: '군사',
        enabled: true,
        durationSeconds: 0,
        commandPointCost: 1,
    },
    {
        actionCode: 'che_train',
        name: '훈련',
        category: '군사',
        enabled: true,
        durationSeconds: 0,
        commandPointCost: 1,
    },
    {
        actionCode: 'che_war',
        name: '출진',
        category: '군사',
        enabled: true,
        durationSeconds: 0,
        commandPointCost: 2,
    },
];

/** 개간 결과 */
export const MOCK_RESULT_AGRI: CommandResult = {
    success: true,
    logs: ['[개간] 성도의 생산 수치가 100 상승했습니다. (5000 → 5100)'],
};

/** 징집 결과 */
export const MOCK_RESULT_RECRUIT: CommandResult = {
    success: true,
    logs: ['[징집] 함선 500척을 징집했습니다. (1000 → 1500)'],
};

/** 훈련 결과 */
export const MOCK_RESULT_TRAIN: CommandResult = {
    success: true,
    logs: ['[훈련] 훈련도가 20 상승했습니다. (80 → 100)'],
};

/** 출진 결과 */
export const MOCK_RESULT_WAR: CommandResult = {
    success: true,
    logs: [
        '[출진] 유비 함대가 한중을 향해 출진합니다.',
        '[전투] 유비(촉) vs 수비대(중립)',
        '[전투] 유비의 공격! 수비대에 320 피해',
        '[전투] 수비대의 반격! 유비에 85 피해',
        '[전투] 유비의 강공! 수비대에 410 피해',
        '[결과] 유비군 승리! 한중을 점령했습니다.',
    ],
};

/** URL 패턴별 커맨드 결과 매핑 */
export function getMockCommandResult(actionCode?: string): CommandResult {
    switch (actionCode) {
        case 'che_develop_agri':
            return MOCK_RESULT_AGRI;
        case 'che_recruit':
            return MOCK_RESULT_RECRUIT;
        case 'che_train':
            return MOCK_RESULT_TRAIN;
        case 'che_war':
            return MOCK_RESULT_WAR;
        default:
            return { success: true, logs: ['커맨드가 실행되었습니다.'] };
    }
}
