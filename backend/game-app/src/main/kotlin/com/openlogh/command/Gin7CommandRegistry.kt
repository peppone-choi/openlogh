package com.openlogh.command

import com.openlogh.entity.Officer
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
        registerStub("워프항행")        // warp_navigation
        registerStub("연료보급")        // fuel_resupply
        registerStub("성계내항행")      // intra_system_navigation
        registerStub("군기유지")        // maintain_discipline
        registerStub("항공훈련")        // flight_training
        registerStub("육전훈련")        // ground_combat_training
        registerStub("공전훈련")        // space_combat_training
        registerStub("육전전술훈련")    // ground_tactics_training
        registerStub("공전전술훈련")    // space_tactics_training
        registerStub("경계출동")        // alert_sortie
        registerStub("무력진압")        // armed_suppression
        registerStub("분열행진")        // split_march
        registerStub("징발")            // requisition
        registerStub("특별경비")        // special_security
        registerStub("육전대출격")      // ground_force_deploy
        registerStub("육전대철수")      // ground_force_withdraw

        // === 개인커맨드 (Personal, PCP, 15종) ===
        registerStub("원거리이동")      // long_range_move
        registerStub("근거리이동")      // short_range_move
        registerStub("퇴역")            // retirement
        registerStub("지원")            // enlist
        registerStub("망명")            // defection
        registerStub("회견")            // audience
        registerStub("수강")            // attend_lecture
        registerStub("병기연습")        // weapons_drill
        registerStub("반의")            // objection
        registerStub("모의")            // conspiracy
        registerStub("설득")            // persuasion
        registerStub("반란")            // rebellion
        registerStub("참가")            // participate
        registerStub("자금투입")        // fund_injection
        registerStub("기함구매")        // flagship_purchase

        // === 지휘커맨드 (Command, MCP, 8종) ===
        registerStub("작전계획")        // operation_plan
        registerStub("작전철회")        // operation_cancel
        registerStub("발령")            // assignment
        registerStub("부대결성")        // form_fleet
        registerStub("부대해산")        // disband_fleet
        registerStub("강의")            // give_lecture
        registerStub("수송계획")        // transport_plan
        registerStub("수송중지")        // transport_cancel

        // === 병참커맨드 (Logistics, MCP, 6종) ===
        registerStub("완전수리")        // full_repair
        registerStub("완전보급")        // full_resupply
        registerStub("재편성")          // reorganize
        registerStub("보충")            // reinforce
        registerStub("반출입")          // transfer_goods
        registerStub("할당")            // allocate

        // === 인사커맨드 (Personnel, PCP, 10종) ===
        registerStub("승진")            // promote
        registerStub("발탁")            // field_promote
        registerStub("강등")            // demote
        registerStub("서작")            // grant_title
        registerStub("서훈")            // award_decoration
        registerStub("임명")            // appoint
        registerStub("파면")            // dismiss
        registerStub("사임")            // resign
        registerStub("봉토수여")        // grant_fief
        registerStub("봉토직할")        // reclaim_fief

        // === 정치커맨드 (Politics, PCP, 12종) ===
        registerStub("야회")            // banquet
        registerStub("수렵")            // hunt
        registerStub("회담")            // conference
        registerStub("담화")            // address
        registerStub("연설")            // speech
        registerStub("국가목표")        // national_goal
        registerStub("납입률변경")      // tax_rate_change
        registerStub("관세율변경")      // tariff_rate_change
        registerStub("분배")            // distribution
        registerStub("처단")            // execution
        registerStub("외교")            // diplomacy
        registerStub("통치목표")        // governance_goal

        // === 첩보커맨드 (Intelligence, MCP, 14종) ===
        registerStub("일제수색")        // general_search
        registerStub("체포허가")        // arrest_authorization
        registerStub("집행명령")        // execution_order
        registerStub("체포명령")        // arrest_order
        registerStub("사열")            // inspection
        registerStub("습격")            // raid
        registerStub("감시")            // surveillance
        registerStub("잠입공작")        // infiltration_op
        registerStub("탈출공작")        // escape_op
        registerStub("정보공작")        // intelligence_op
        registerStub("파괴공작")        // sabotage_op
        registerStub("선동공작")        // agitation_op
        registerStub("침입공작")        // incursion_op
        registerStub("귀환공작")        // return_op

        // === 대기 커맨드 (ALWAYS_ALLOWED 기본 커맨드) ===
        registerStub("대기")            // standby - always allowed
    }

    /**
     * gin7 stub 커맨드를 officerCommands에 등록한다.
     * Phase 2에서 실제 구현체로 교체된다.
     */
    private fun registerStub(nameKo: String) {
        registerOfficerCommand(nameKo) { general, env, arg ->
            Gin7StubCommand(nameKo, general, env, arg)
        }
    }

    /**
     * gin7 stub 커맨드 구현체.
     * Phase 2 구현 전까지 모든 커맨드는 이 stub을 반환한다.
     */
    private inner class Gin7StubCommand(
        private val commandName: String,
        general: Officer,
        env: CommandEnv,
        arg: Map<String, Any>?,
    ) : OfficerCommand(general, env, arg) {

        override val actionName: String = commandName

        override fun getCost(): CommandCost = CommandCost()

        override fun getPreReqTurn(): Int = 0

        override fun getPostReqTurn(): Int = 0

        override suspend fun run(rng: Random): CommandResult {
            return CommandResult.fail("[$commandName] Phase 2에서 구현 예정 (stub)")
        }
    }
}
