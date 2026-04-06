package com.openlogh.model

/**
 * 7 command group categories based on gin7's position card system.
 * Each position card grants access to one or more command groups,
 * and each command action code belongs to exactly one group.
 */
enum class CommandGroup {
    OPERATIONS,   // 作戦 - flagship/fleet unit operations (출병, 집합, 전투태세, 요격, 순찰)
    PERSONAL,     // 個人 - individual movement, personal actions (휴식, 이동, 은퇴, etc.)
    COMMAND,      // 指揮 - military operation planning, fleet composition (급습, 수몰, 모반시도, etc.)
    LOGISTICS,    // 兵站 - supply allocation, unit reorganization (농지개간, 모병, 훈련, etc.)
    PERSONNEL,    // 人事 - promotion, demotion, appointment (등용, 발령, 포상, etc.)
    POLITICS,     // 政治 - budget, national goals, faction governance (선전포고, 칭제, 천도, etc.)
    INTELLIGENCE, // 諜報 - espionage, investigation, arrests (첩보, 선동, 탈취, 파괴, 화계)
}
