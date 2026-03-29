import { z } from 'zod';

const STAT_TOTAL = 400;
const STAT_MIN = 10;
const STAT_MAX = 100;

const statSchema = z.number().int().min(STAT_MIN).max(STAT_MAX);

export const characterCreationSchema = z.object({
    name: z.string().min(2, '이름은 2자 이상이어야 합니다.').max(20, '이름은 20자 이하여야 합니다.'),
    originType: z.enum(['noble', 'knight', 'commoner', 'citizen']),
    stats: z.object({
        leadership: statSchema,
        command: statSchema,
        intelligence: statSchema,
        politics: statSchema,
        administration: statSchema,
        mobility: statSchema,
        attack: statSchema,
        defense: statSchema,
    }).refine(
        (stats) => Object.values(stats).reduce((a, b) => a + b, 0) === STAT_TOTAL,
        { message: `능력치 합계는 ${STAT_TOTAL}이어야 합니다.` },
    ),
});

export type CharacterCreationInput = z.infer<typeof characterCreationSchema>;
export { STAT_TOTAL, STAT_MIN, STAT_MAX };
