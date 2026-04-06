package com.openlogh.model

/**
 * Static registry mapping position cards to command action codes.
 * Resolves: given an action code, find its CommandGroup,
 * then check if any of the officer's cards grants that group.
 */
object PositionCardRegistry {

    /**
     * Maps every command action code to its CommandGroup.
     * All 93 existing commands (55 officer + 38 faction) are covered.
     */
    private val commandGroupMap: Map<String, CommandGroup> = buildMap {
        // === PERSONAL group (22) ===
        // Individual actions always available via PERSONAL card
        for (code in listOf(
            "휴식", "요양", "단련", "숙련전환", "견문", "은퇴",
            "장비매매", "군량매매", "내정특기초기화", "전투특기초기화",
            "이동", "강행", "귀환", "접경귀환", "방랑", "좌표이동",
            "임관", "랜덤임관", "장수대상임관", "등용수락",
            "건국", "무작위건국",
        )) { put(code, CommandGroup.PERSONAL) }

        // NPC/CR special commands - always allowed (PERSONAL)
        for (code in listOf("NPC능동", "CR건국", "CR맹훈련")) {
            put(code, CommandGroup.PERSONAL)
        }

        // Nation rest - always allowed (PERSONAL)
        put("Nation휴식", CommandGroup.PERSONAL)

        // === OPERATIONS group (8) ===
        // Fleet/unit tactical operations via CAPTAIN card
        for (code in listOf("출병", "집합", "전투태세", "요격", "순찰", "작전수립", "워프항행", "장거리워프")) {
            put(code, CommandGroup.OPERATIONS)
        }

        // === COMMAND group (13) ===
        // Military strategy, rebellion, strategic maneuvers
        for (code in listOf(
            "모반시도", "거병", "선양", "해산",
            "급습", "수몰", "허보", "초토화",
            "필사즉생", "이호경식", "피장파장", "의병모집",
            "작전지시",
        )) { put(code, CommandGroup.COMMAND) }

        // === LOGISTICS group (19) ===
        // Infrastructure, resource management, military supply
        for (code in listOf(
            "농지개간", "상업투자", "치안강화", "수비강화", "성벽보수",
            "정착장려", "주민선정", "기술연구",
            "모병", "징병", "훈련", "사기진작", "소집해제",
            "물자조달", "헌납", "물자원조",
            "증축", "감축", "백성동원",
        )) { put(code, CommandGroup.LOGISTICS) }

        // === PERSONNEL group (7) ===
        // Hiring, promotion, personnel management
        for (code in listOf(
            "등용", "인재탐색", "증여", "발령", "포상", "몰수", "부대탈퇴지시",
        )) { put(code, CommandGroup.PERSONNEL) }

        // === POLITICS group (27) ===
        // Diplomacy, governance, faction-level decisions
        for (code in listOf(
            "하야", "선전포고", "종전제의", "종전수락",
            "불가침제의", "불가침수락", "불가침파기제의", "불가침파기수락",
            "칭제", "천자맞이", "선양요구", "신속", "독립선언",
            "천도", "국기변경", "국호변경", "무작위수도이전", "인구이동",
        )) { put(code, CommandGroup.POLITICS) }

        // Research commands -> POLITICS
        for (code in listOf(
            "극병연구", "대검병연구", "무희연구", "산저병연구",
            "상병연구", "원융노병연구", "음귀병연구", "화륜차연구", "화시병연구",
        )) { put(code, CommandGroup.POLITICS) }

        // === INTELLIGENCE group (5) ===
        // Espionage, sabotage, arson
        for (code in listOf("첩보", "선동", "탈취", "파괴", "화계")) {
            put(code, CommandGroup.INTELLIGENCE)
        }
    }

    /** Reverse index: CommandGroup -> set of action codes */
    private val groupToCommands: Map<CommandGroup, Set<String>> by lazy {
        commandGroupMap.entries
            .groupBy({ it.value }, { it.key })
            .mapValues { (_, v) -> v.toSet() }
    }

    /**
     * Returns the CommandGroup for a given action code.
     * Falls back to PERSONAL for unknown commands (safe default).
     */
    fun getCommandGroup(actionCode: String): CommandGroup =
        commandGroupMap[actionCode] ?: CommandGroup.PERSONAL

    /**
     * Returns all action codes that a given card grants access to,
     * based on the card's command groups.
     */
    fun getCommandsForCard(card: PositionCard): Set<String> =
        card.commandGroups.flatMapTo(mutableSetOf()) { group ->
            groupToCommands[group] ?: emptySet()
        }

    /**
     * Returns which position cards can execute a given command.
     * A card can execute a command if the card's commandGroups contains
     * the command's group.
     */
    fun getCardsForCommand(actionCode: String): Set<PositionCard> {
        val group = getCommandGroup(actionCode)
        return PositionCard.entries.filterTo(mutableSetOf()) { group in it.commandGroups }
    }

    /**
     * Checks if any of the held cards grants access to the given command.
     */
    fun canExecute(cards: List<PositionCard>, actionCode: String): Boolean {
        val requiredGroup = getCommandGroup(actionCode)
        return cards.any { requiredGroup in it.commandGroups }
    }

    /**
     * Returns the default cards every officer receives on creation.
     */
    fun getDefaultCards(): List<PositionCard> = PositionCard.defaults()

    /**
     * Returns the total number of mapped command action codes.
     * Useful for verification that all commands are covered.
     */
    fun mappedCommandCount(): Int = commandGroupMap.size
}
