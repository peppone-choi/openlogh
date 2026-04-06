package com.openlogh.command

import com.openlogh.command.gin7.personal.AttendLectureCommand
import com.openlogh.command.gin7.personal.AudienceCommand
import com.openlogh.command.gin7.personal.ConspiracyCommand
import com.openlogh.command.gin7.personal.DefectionCommand
import com.openlogh.command.gin7.personal.EnlistCommand
import com.openlogh.command.gin7.personal.FlagshipPurchaseCommand
import com.openlogh.command.gin7.personal.FundInjectionCommand
import com.openlogh.command.gin7.personal.LongRangeMoveCommand
import com.openlogh.command.gin7.personal.ObjectionCommand
import com.openlogh.command.gin7.personal.ParticipateCommand
import com.openlogh.command.gin7.personal.PersuasionCommand
import com.openlogh.command.gin7.personal.RebellionCommand
import com.openlogh.command.gin7.personal.RetirementCommand
import com.openlogh.command.gin7.personal.ShortRangeMoveCommand
import com.openlogh.command.gin7.personal.WeaponsDrillCommand
import com.openlogh.command.gin7.personnel.AppointCommand
import com.openlogh.command.gin7.personnel.AwardDecorationCommand
import com.openlogh.command.gin7.personnel.DemoteCommand
import com.openlogh.command.gin7.personnel.DismissCommand
import com.openlogh.command.gin7.personnel.FieldPromoteCommand
import com.openlogh.command.gin7.personnel.FiefCommands
import com.openlogh.command.gin7.personnel.GrantFiefCommand
import com.openlogh.command.gin7.personnel.GrantTitleCommand
import com.openlogh.command.gin7.personnel.PromoteCommand
import com.openlogh.command.gin7.personnel.ReclaimFiefCommand
import com.openlogh.command.gin7.personnel.ResignCommand
import com.openlogh.command.gin7.intelligence.AgitationOpCommand
import com.openlogh.command.gin7.intelligence.ArrestAuthorizationCommand
import com.openlogh.command.gin7.intelligence.ArrestOrderCommand
import com.openlogh.command.gin7.intelligence.EscapeOpCommand
import com.openlogh.command.gin7.intelligence.ExecutionOrderCommand
import com.openlogh.command.gin7.intelligence.GeneralSearchCommand
import com.openlogh.command.gin7.intelligence.IncursionOpCommand
import com.openlogh.command.gin7.intelligence.InfiltrationOpCommand
import com.openlogh.command.gin7.intelligence.InspectionCommand
import com.openlogh.command.gin7.intelligence.IntelligenceOpCommand
import com.openlogh.command.gin7.intelligence.RaidCommand
import com.openlogh.command.gin7.intelligence.ReturnOpCommand
import com.openlogh.command.gin7.intelligence.SabotageOpCommand
import com.openlogh.command.gin7.intelligence.SurveillanceCommand
import com.openlogh.command.gin7.politics.AddressCommand
import com.openlogh.command.gin7.politics.BanquetCommand
import com.openlogh.command.gin7.politics.ConferenceCommand
import com.openlogh.command.gin7.politics.DiplomacyCommand
import com.openlogh.command.gin7.politics.DistributionCommand
import com.openlogh.command.gin7.politics.ExecutionCommand
import com.openlogh.command.gin7.politics.GovernanceGoalCommand
import com.openlogh.command.gin7.politics.HuntCommand
import com.openlogh.command.gin7.politics.NationalGoalCommand
import com.openlogh.command.gin7.politics.SpeechCommand
import com.openlogh.command.gin7.politics.TariffRateChangeCommand
import com.openlogh.command.gin7.politics.TaxRateChangeCommand
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
        registerOfficerCommand("원거리이동") { g, e, a -> LongRangeMoveCommand(g, e, a) }
        registerOfficerCommand("근거리이동") { g, e, a -> ShortRangeMoveCommand(g, e, a) }
        registerOfficerCommand("퇴역") { g, e, a -> RetirementCommand(g, e, a) }
        registerOfficerCommand("지원") { g, e, a -> EnlistCommand(g, e, a) }
        registerOfficerCommand("망명") { g, e, a -> DefectionCommand(g, e, a) }
        registerOfficerCommand("회견") { g, e, a -> AudienceCommand(g, e, a) }
        registerOfficerCommand("수강") { g, e, a -> AttendLectureCommand(g, e, a) }
        registerOfficerCommand("병기연습") { g, e, a -> WeaponsDrillCommand(g, e, a) }
        registerOfficerCommand("반의") { g, e, a -> ObjectionCommand(g, e, a) }
        registerOfficerCommand("모의") { g, e, a -> ConspiracyCommand(g, e, a) }
        registerOfficerCommand("설득") { g, e, a -> PersuasionCommand(g, e, a) }
        registerOfficerCommand("반란") { g, e, a -> RebellionCommand(g, e, a) }
        registerOfficerCommand("참가") { g, e, a -> ParticipateCommand(g, e, a) }
        registerOfficerCommand("자금투입") { g, e, a -> FundInjectionCommand(g, e, a) }
        registerOfficerCommand("기함구매") { g, e, a -> FlagshipPurchaseCommand(g, e, a) }

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
        registerOfficerCommand("승진") { g, e, a -> PromoteCommand(g, e, a) }
        registerOfficerCommand("발탁") { g, e, a -> FieldPromoteCommand(g, e, a) }
        registerOfficerCommand("강등") { g, e, a -> DemoteCommand(g, e, a) }
        registerOfficerCommand("서작") { g, e, a -> GrantTitleCommand(g, e, a) }
        registerOfficerCommand("서훈") { g, e, a -> AwardDecorationCommand(g, e, a) }
        registerOfficerCommand("임명") { g, e, a -> AppointCommand(g, e, a) }
        registerOfficerCommand("파면") { g, e, a -> DismissCommand(g, e, a) }
        registerOfficerCommand("사임") { g, e, a -> ResignCommand(g, e, a) }
        registerOfficerCommand("봉토수여") { g, e, a -> GrantFiefCommand(g, e, a) }
        registerOfficerCommand("봉토직할") { g, e, a -> ReclaimFiefCommand(g, e, a) }

        // === 정치커맨드 (Politics, PCP, 12종) ===
        registerOfficerCommand("야회") { g, e, a -> BanquetCommand(g, e, a) }
        registerOfficerCommand("수렵") { g, e, a -> HuntCommand(g, e, a) }
        registerOfficerCommand("회담") { g, e, a -> ConferenceCommand(g, e, a) }
        registerOfficerCommand("담화") { g, e, a -> AddressCommand(g, e, a) }
        registerOfficerCommand("연설") { g, e, a -> SpeechCommand(g, e, a) }
        registerOfficerCommand("국가목표") { g, e, a -> NationalGoalCommand(g, e, a) }
        registerOfficerCommand("납입률변경") { g, e, a -> TaxRateChangeCommand(g, e, a) }
        registerOfficerCommand("관세율변경") { g, e, a -> TariffRateChangeCommand(g, e, a) }
        registerOfficerCommand("분배") { g, e, a -> DistributionCommand(g, e, a) }
        registerOfficerCommand("처단") { g, e, a -> ExecutionCommand(g, e, a) }
        registerOfficerCommand("외교") { g, e, a -> DiplomacyCommand(g, e, a) }
        registerOfficerCommand("통치목표") { g, e, a -> GovernanceGoalCommand(g, e, a) }

        // === 첩보커맨드 (Intelligence, MCP, 14종) ===
        registerOfficerCommand("일제수색") { g, e, a -> GeneralSearchCommand(g, e, a) }
        registerOfficerCommand("체포허가") { g, e, a -> ArrestAuthorizationCommand(g, e, a) }
        registerOfficerCommand("집행명령") { g, e, a -> ExecutionOrderCommand(g, e, a) }
        registerOfficerCommand("체포명령") { g, e, a -> ArrestOrderCommand(g, e, a) }
        registerOfficerCommand("사열") { g, e, a -> InspectionCommand(g, e, a) }
        registerOfficerCommand("습격") { g, e, a -> RaidCommand(g, e, a) }
        registerOfficerCommand("감시") { g, e, a -> SurveillanceCommand(g, e, a) }
        registerOfficerCommand("잠입공작") { g, e, a -> InfiltrationOpCommand(g, e, a) }
        registerOfficerCommand("탈출공작") { g, e, a -> EscapeOpCommand(g, e, a) }
        registerOfficerCommand("정보공작") { g, e, a -> IntelligenceOpCommand(g, e, a) }
        registerOfficerCommand("파괴공작") { g, e, a -> SabotageOpCommand(g, e, a) }
        registerOfficerCommand("선동공작") { g, e, a -> AgitationOpCommand(g, e, a) }
        registerOfficerCommand("침입공작") { g, e, a -> IncursionOpCommand(g, e, a) }
        registerOfficerCommand("귀환공작") { g, e, a -> ReturnOpCommand(g, e, a) }

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
