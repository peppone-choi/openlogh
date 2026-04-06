package com.openlogh.command

import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

/**
 * gin7 81종 커맨드 레지스트리. Phase 2에서 실제 구현체로 교체된다.
 * @Primary로 CommandExecutor에 주입되어 기존 CommandRegistry를 대체한다.
 *
 * 커맨드 그룹:
 * - 작전커맨드 (Operations, MCP): 16종
 * - 개인커맨드 (Personal, PCP): 15종
 * - 지휘커맨드 (Command, MCP): 8종
 * - 병참커맨드 (Logistics, MCP): 6종
 * - 인사커맨드 (Personnel, PCP): 10종
 * - 정치커맨드 (Politics, PCP): 12종
 * - 첩보커맨드 (Intelligence, MCP): 14종
 */
@Primary
@Component
class Gin7CommandRegistry : CommandRegistry() {

    init {
        // === 작전커맨드 (Operations, MCP, 16종) ===
        registerMcpStub("워프항행")        // warp_navigation
        registerMcpStub("연료보급")        // fuel_resupply
        registerMcpStub("성계내항행")      // intra_system_navigation
        registerMcpStub("군기유지")        // maintain_discipline
        registerMcpStub("항공훈련")        // flight_training
        registerMcpStub("육전훈련")        // ground_combat_training
        registerMcpStub("공전훈련")        // space_combat_training
        registerMcpStub("육전전술훈련")    // ground_tactics_training
        registerMcpStub("공전전술훈련")    // space_tactics_training
        registerMcpStub("경계출동")        // alert_sortie
        registerMcpStub("무력진압")        // armed_suppression
        registerMcpStub("분열행진")        // split_march
        registerMcpStub("징발")            // requisition
        registerMcpStub("특별경비")        // special_security
        registerMcpStub("육전대출격")      // ground_force_deploy
        registerMcpStub("육전대철수")      // ground_force_withdraw

        // === 개인커맨드 (Personal, PCP, 15종) ===
        registerPcpStub("원거리이동")      // long_range_move
        registerPcpStub("근거리이동")      // short_range_move
        registerPcpStub("퇴역")            // retirement
        registerPcpStub("지원")            // enlist
        registerPcpStub("망명")            // defection
        registerPcpStub("회견")            // audience
        registerPcpStub("수강")            // attend_lecture
        registerPcpStub("병기연습")        // weapons_drill
        registerPcpStub("반의")            // objection
        registerPcpStub("모의")            // conspiracy
        registerPcpStub("설득")            // persuasion
        registerPcpStub("반란")            // rebellion
        registerPcpStub("참가")            // participate
        registerPcpStub("자금투입")        // fund_injection
        registerPcpStub("기함구매")        // flagship_purchase

        // === 지휘커맨드 (Command, MCP, 8종) ===
        registerMcpStub("작전계획")        // operation_plan
        registerMcpStub("작전철회")        // operation_cancel
        registerMcpStub("발령")            // assignment
        registerMcpStub("부대결성")        // form_fleet
        registerMcpStub("부대해산")        // disband_fleet
        registerMcpStub("강의")            // give_lecture
        registerMcpStub("수송계획")        // transport_plan
        registerMcpStub("수송중지")        // transport_cancel

        // === 병참커맨드 (Logistics, MCP, 6종) ===
        registerMcpStub("완전수리")        // full_repair
        registerMcpStub("완전보급")        // full_resupply
        registerMcpStub("재편성")          // reorganize
        registerMcpStub("보충")            // reinforce
        registerMcpStub("반출입")          // transfer_goods
        registerMcpStub("할당")            // allocate

        // === 인사커맨드 (Personnel, PCP, 10종) ===
        registerPcpStub("승진")            // promote
        registerPcpStub("발탁")            // field_promote
        registerPcpStub("강등")            // demote
        registerPcpStub("서작")            // grant_title
        registerPcpStub("서훈")            // award_decoration
        registerPcpStub("임명")            // appoint
        registerPcpStub("파면")            // dismiss
        registerPcpStub("사임")            // resign
        registerPcpStub("봉토수여")        // grant_fief
        registerPcpStub("봉토직할")        // reclaim_fief

        // === 정치커맨드 (Politics, PCP, 12종) ===
        registerPcpStub("야회")            // banquet
        registerPcpStub("수렵")            // hunt
        registerPcpStub("회담")            // conference
        registerPcpStub("담화")            // address
        registerPcpStub("연설")            // speech
        registerPcpStub("국가목표")        // national_goal
        registerPcpStub("납입률변경")      // tax_rate_change
        registerPcpStub("관세율변경")      // tariff_rate_change
        registerPcpStub("분배")            // distribution
        registerPcpStub("처단")            // execution
        registerPcpStub("외교")            // diplomacy
        registerPcpStub("통치목표")        // governance_goal

        // === 첩보커맨드 (Intelligence, MCP, 14종) ===
        registerMcpStub("일제수색")        // general_search
        registerMcpStub("체포허가")        // arrest_authorization
        registerMcpStub("집행명령")        // execution_order
        registerMcpStub("체포명령")        // arrest_order
        registerMcpStub("사열")            // inspection
        registerMcpStub("습격")            // raid
        registerMcpStub("감시")            // surveillance
        registerMcpStub("잠입공작")        // infiltration_op
        registerMcpStub("탈출공작")        // escape_op
        registerMcpStub("정보공작")        // intelligence_op
        registerMcpStub("파괴공작")        // sabotage_op
        registerMcpStub("선동공작")        // agitation_op
        registerMcpStub("침입공작")        // incursion_op
        registerMcpStub("귀환공작")        // return_op

        // === 대기 커맨드 (ALWAYS_ALLOWED 기본 커맨드, PCP) ===
        registerPcpStub("대기")            // standby - always allowed
    }

    /**
     * MCP 커맨드 stub을 officerCommands에 등록한다.
     * getCommandPoolType()이 StatCategory.MCP를 반환한다.
     * Phase 2에서 실제 구현체로 교체된다.
     */
    private fun registerMcpStub(nameKo: String) {
        registerOfficerCommand(nameKo) { general, env, arg ->
            Gin7StubCommand(nameKo, StatCategory.MCP, general, env, arg)
        }
    }

    /**
     * PCP 커맨드 stub을 officerCommands에 등록한다.
     * getCommandPoolType()이 StatCategory.PCP를 반환한다 (기본값).
     * Phase 2에서 실제 구현체로 교체된다.
     */
    private fun registerPcpStub(nameKo: String) {
        registerOfficerCommand(nameKo) { general, env, arg ->
            Gin7StubCommand(nameKo, StatCategory.PCP, general, env, arg)
        }
    }

    /**
     * gin7 stub 커맨드 구현체.
     * cpType 파라미터로 MCP/PCP 풀을 구분한다.
     * Phase 2 구현 전까지 모든 커맨드는 이 stub을 반환한다.
     */
    private inner class Gin7StubCommand(
        private val commandName: String,
        private val cpType: StatCategory,
        general: Officer,
        env: CommandEnv,
        arg: Map<String, Any>?,
    ) : OfficerCommand(general, env, arg) {

        override val actionName: String = commandName

        override fun getCost(): CommandCost = CommandCost()

        override fun getCommandPoolType(): StatCategory = cpType

        override fun getPreReqTurn(): Int = 0

        override fun getPostReqTurn(): Int = 0

        override suspend fun run(rng: Random): CommandResult {
            return CommandResult.fail("[$commandName] Phase 2에서 구현 예정 (stub)")
        }
    }
}
