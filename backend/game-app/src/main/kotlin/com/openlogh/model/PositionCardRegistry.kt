package com.openlogh.model

/**
 * Static registry mapping position cards to command action codes.
 * Resolves: given an action code, find its CommandGroup,
 * then check if any of the officer's cards grants that group.
 */
object PositionCardRegistry {

    /**
     * Maps every gin7 command action code to its CommandGroup.
     * gin7 81종 커맨드: 작전(16) + 개인(15) + 지휘(8) + 병참(6) + 인사(10) + 정치(12) + 첩보(14) = 81종
     * "대기"는 ALWAYS_ALLOWED_COMMANDS에 있으므로 여기서 제외 (매핑 없으면 허용 로직 유지).
     */
    private val commandGroupMap: Map<String, CommandGroup> = buildMap {
        // === OPERATIONS group (작전커맨드, MCP, 16종) ===
        for (code in listOf(
            "워프항행", "연료보급", "성계내항행", "군기유지", "항공훈련", "육전훈련",
            "공전훈련", "육전전술훈련", "공전전술훈련", "경계출동", "무력진압",
            "분열행진", "징발", "특별경비", "육전대출격", "육전대철수",
        )) { put(code, CommandGroup.OPERATIONS) }

        // === PERSONAL group (개인커맨드, PCP, 15종) ===
        for (code in listOf(
            "원거리이동", "근거리이동", "퇴역", "지원", "망명", "회견", "수강",
            "병기연습", "반의", "모의", "설득", "반란", "참가", "자금투입", "기함구매",
        )) { put(code, CommandGroup.PERSONAL) }

        // === COMMAND group (지휘커맨드, MCP, 8종) ===
        for (code in listOf(
            "작전계획", "작전철회", "발령", "부대결성", "부대해산", "강의",
            "수송계획", "수송중지",
        )) { put(code, CommandGroup.COMMAND) }

        // === LOGISTICS group (병참커맨드, MCP, 6종) ===
        for (code in listOf(
            "완전수리", "완전보급", "재편성", "보충", "반출입", "할당",
        )) { put(code, CommandGroup.LOGISTICS) }

        // === PERSONNEL group (인사커맨드, PCP, 10종) ===
        for (code in listOf(
            "승진", "발탁", "강등", "서작", "서훈", "임명", "파면", "사임",
            "봉토수여", "봉토직할",
        )) { put(code, CommandGroup.PERSONNEL) }

        // === POLITICS group (정치커맨드, PCP, 12종) ===
        for (code in listOf(
            "야회", "수렵", "회담", "담화", "연설", "국가목표", "납입률변경",
            "관세율변경", "분배", "처단", "외교", "통치목표",
        )) { put(code, CommandGroup.POLITICS) }

        // === INTELLIGENCE group (첩보커맨드, MCP, 14종) ===
        for (code in listOf(
            "일제수색", "체포허가", "집행명령", "체포명령", "사열", "습격",
            "감시", "잠입공작", "탈출공작", "정보공작", "파괴공작", "선동공작",
            "침입공작", "귀환공작",
        )) { put(code, CommandGroup.INTELLIGENCE) }
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
