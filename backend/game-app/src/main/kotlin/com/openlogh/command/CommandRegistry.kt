package com.openlogh.command

import com.openlogh.command.general.*
import com.openlogh.command.nation.*
import com.openlogh.entity.Officer
import org.springframework.stereotype.Component

typealias OfficerCommandFactory = (Officer, CommandEnv, Map<String, Any>?) -> OfficerCommand
typealias FactionCommandFactory = (Officer, CommandEnv, Map<String, Any>?) -> FactionCommand

@Component
class CommandRegistry {
    private val officerCommands = mutableMapOf<String, OfficerCommandFactory>()
    private val factionCommands = mutableMapOf<String, FactionCommandFactory>()
    private val officerSchemas = mutableMapOf<String, ArgSchema>()
    private val factionSchemas = mutableMapOf<String, ArgSchema>()

    init {
        // === General Commands (55) ===

        // Default
        registerOfficerCommand("휴식") { g, e, a -> 휴식(g, e, a) }

        // Civil/Domestic (18)
        registerOfficerCommand("농지개간") { g, e, a -> che_농지개간(g, e, a) }
        registerOfficerCommand("상업투자") { g, e, a -> che_상업투자(g, e, a) }
        registerOfficerCommand("치안강화") { g, e, a -> che_치안강화(g, e, a) }
        registerOfficerCommand("수비강화") { g, e, a -> che_수비강화(g, e, a) }
        registerOfficerCommand("성벽보수") { g, e, a -> che_성벽보수(g, e, a) }
        registerOfficerCommand("정착장려") { g, e, a -> che_정착장려(g, e, a) }
        registerOfficerCommand("주민선정") { g, e, a -> che_주민선정(g, e, a) }
        registerOfficerCommand("기술연구") { g, e, a -> che_기술연구(g, e, a) }
        registerOfficerCommand("모병") { g, e, a -> che_모병(g, e, a) }
        registerOfficerCommand("징병") { g, e, a -> che_징병(g, e, a) }
        registerOfficerCommand("훈련") { g, e, a -> che_훈련(g, e, a) }
        registerOfficerCommand("사기진작") { g, e, a -> che_사기진작(g, e, a) }
        registerOfficerCommand("소집해제") { g, e, a -> che_소집해제(g, e, a) }
        registerOfficerCommand("숙련전환") { g, e, a -> che_숙련전환(g, e, a) }
        registerOfficerCommand("물자조달") { g, e, a -> che_물자조달(g, e, a) }
        registerOfficerCommand("군량매매") { g, e, a -> che_군량매매(g, e, a) }
        registerOfficerCommand("헌납") { g, e, a -> che_헌납(g, e, a) }
        registerOfficerCommand("단련") { g, e, a -> che_단련(g, e, a) }

        // Military (15)
        registerOfficerCommand("출병") { g, e, a -> 출병(g, e, a) }
        registerOfficerCommand("이동") { g, e, a -> 이동(g, e, a) }
        registerOfficerCommand("집합") { g, e, a -> 집합(g, e, a) }
        registerOfficerCommand("귀환") { g, e, a -> 귀환(g, e, a) }
        registerOfficerCommand("접경귀환") { g, e, a -> 접경귀환(g, e, a) }
        registerOfficerCommand("강행") { g, e, a -> 강행(g, e, a) }
        registerOfficerCommand("거병") { g, e, a -> 거병(g, e, a) }
        registerOfficerCommand("전투태세") { g, e, a -> 전투태세(g, e, a) }
        registerOfficerCommand("화계") { g, e, a -> 화계(g, e, a) }
        registerOfficerCommand("첩보") { g, e, a -> 첩보(g, e, a) }
        registerOfficerCommand("선동") { g, e, a -> 선동(g, e, a) }
        registerOfficerCommand("탈취") { g, e, a -> 탈취(g, e, a) }
        registerOfficerCommand("파괴") { g, e, a -> 파괴(g, e, a) }
        registerOfficerCommand("요양") { g, e, a -> 요양(g, e, a) }
        registerOfficerCommand("방랑") { g, e, a -> 방랑(g, e, a) }
        registerOfficerCommand("요격") { g, e, a -> 요격(g, e, a) }
        registerOfficerCommand("순찰") { g, e, a -> 순찰(g, e, a) }
        registerOfficerCommand("좌표이동") { g, e, a -> 좌표이동(g, e, a) }

        // Political (19)
        registerOfficerCommand("등용") { g, e, a -> 등용(g, e, a) }
        registerOfficerCommand("등용수락") { g, e, a -> 등용수락(g, e, a) }
        registerOfficerCommand("임관") { g, e, a -> 임관(g, e, a) }
        registerOfficerCommand("랜덤임관") { g, e, a -> 랜덤임관(g, e, a) }
        registerOfficerCommand("장수대상임관") { g, e, a -> 장수대상임관(g, e, a) }
        registerOfficerCommand("하야") { g, e, a -> 하야(g, e, a) }
        registerOfficerCommand("은퇴") { g, e, a -> 은퇴(g, e, a) }
        registerOfficerCommand("건국") { g, e, a -> 건국(g, e, a) }
        registerOfficerCommand("무작위건국") { g, e, a -> 무작위건국(g, e, a) }
        registerOfficerCommand("모반시도") { g, e, a -> 모반시도(g, e, a) }
        registerOfficerCommand("선양") { g, e, a -> 선양(g, e, a) }
        registerOfficerCommand("해산") { g, e, a -> 해산(g, e, a) }
        registerOfficerCommand("견문") { g, e, a -> 견문(g, e, a) }
        registerOfficerCommand("인재탐색") { g, e, a -> 인재탐색(g, e, a) }
        registerOfficerCommand("증여") { g, e, a -> 증여(g, e, a) }
        registerOfficerCommand("장비매매") { g, e, a -> 장비매매(g, e, a) }
        registerOfficerCommand("내정특기초기화") { g, e, a -> 내정특기초기화(g, e, a) }
        registerOfficerCommand("전투특기초기화") { g, e, a -> 전투특기초기화(g, e, a) }

        // Strategic Operations (3)
        registerOfficerCommand("작전수립") { g, e, a -> 작전수립(g, e, a) }
        registerOfficerCommand("워프항행") { g, e, a -> 워프항행(g, e, a) }
        registerOfficerCommand("장거리워프") { g, e, a -> 장거리워프(g, e, a) }

        // NPC/CR Special (3)
        registerOfficerCommand("NPC능동") { g, e, a -> NPC능동(g, e, a) }
        registerOfficerCommand("CR건국") { g, e, a -> CR건국(g, e, a) }
        registerOfficerCommand("CR맹훈련") { g, e, a -> CR맹훈련(g, e, a) }

        // === Nation Commands (38) ===

        // Default
        registerFactionCommand("Nation휴식") { g, e, a -> Nation휴식(g, e, a) }

        // Resource/Management (10)
        registerFactionCommand("포상") { g, e, a -> che_포상(g, e, a) }
        registerFactionCommand("몰수") { g, e, a -> che_몰수(g, e, a) }
        registerFactionCommand("감축") { g, e, a -> che_감축(g, e, a) }
        registerFactionCommand("증축") { g, e, a -> che_증축(g, e, a) }
        registerFactionCommand("발령") { g, e, a -> che_발령(g, e, a) }
        registerFactionCommand("천도") { g, e, a -> che_천도(g, e, a) }
        registerFactionCommand("백성동원") { g, e, a -> che_백성동원(g, e, a) }
        registerFactionCommand("물자원조") { g, e, a -> che_물자원조(g, e, a) }
        registerFactionCommand("국기변경") { g, e, a -> che_국기변경(g, e, a) }
        registerFactionCommand("국호변경") { g, e, a -> che_국호변경(g, e, a) }

        // Diplomacy (7)
        registerFactionCommand("선전포고") { g, e, a -> che_선전포고(g, e, a) }
        registerFactionCommand("종전제의") { g, e, a -> che_종전제의(g, e, a) }
        registerFactionCommand("종전수락") { g, e, a -> che_종전수락(g, e, a) }
        registerFactionCommand("불가침제의") { g, e, a -> che_불가침제의(g, e, a) }
        registerFactionCommand("불가침수락") { g, e, a -> che_불가침수락(g, e, a) }
        registerFactionCommand("불가침파기제의") { g, e, a -> che_불가침파기제의(g, e, a) }
        registerFactionCommand("불가침파기수락") { g, e, a -> che_불가침파기수락(g, e, a) }

        // Strategic (8)
        registerFactionCommand("급습") { g, e, a -> che_급습(g, e, a) }
        registerFactionCommand("수몰") { g, e, a -> che_수몰(g, e, a) }
        registerFactionCommand("허보") { g, e, a -> che_허보(g, e, a) }
        registerFactionCommand("초토화") { g, e, a -> che_초토화(g, e, a) }
        registerFactionCommand("필사즉생") { g, e, a -> che_필사즉생(g, e, a) }
        registerFactionCommand("이호경식") { g, e, a -> che_이호경식(g, e, a) }
        registerFactionCommand("피장파장") { g, e, a -> che_피장파장(g, e, a) }
        registerFactionCommand("의병모집") { g, e, a -> che_의병모집(g, e, a) }

        // Research (9)
        registerFactionCommand("극병연구") { g, e, a -> event_극병연구(g, e, a) }
        registerFactionCommand("대검병연구") { g, e, a -> event_대검병연구(g, e, a) }
        registerFactionCommand("무희연구") { g, e, a -> event_무희연구(g, e, a) }
        registerFactionCommand("산저병연구") { g, e, a -> event_산저병연구(g, e, a) }
        registerFactionCommand("상병연구") { g, e, a -> event_상병연구(g, e, a) }
        registerFactionCommand("원융노병연구") { g, e, a -> event_원융노병연구(g, e, a) }
        registerFactionCommand("음귀병연구") { g, e, a -> event_음귀병연구(g, e, a) }
        registerFactionCommand("화륜차연구") { g, e, a -> event_화륜차연구(g, e, a) }
        registerFactionCommand("화시병연구") { g, e, a -> event_화시병연구(g, e, a) }

        // Emperor/Vassal (5)
        registerFactionCommand("칭제") { g, e, a -> che_칭제(g, e, a) }
        registerFactionCommand("천자맞이") { g, e, a -> che_천자맞이(g, e, a) }
        registerFactionCommand("선양요구") { g, e, a -> che_선양요구(g, e, a) }
        registerFactionCommand("신속") { g, e, a -> che_신속(g, e, a) }
        registerFactionCommand("독립선언") { g, e, a -> che_독립선언(g, e, a) }

        // Strategic Operations (1)
        registerFactionCommand("작전지시") { g, e, a -> 작전지시(g, e, a) }

        // Special
        registerFactionCommand("무작위수도이전") { g, e, a -> che_무작위수도이전(g, e, a) }
        registerFactionCommand("부대탈퇴지시") { g, e, a -> che_부대탈퇴지시(g, e, a) }
        registerFactionCommand("인구이동") { g, e, a -> cr_인구이동(g, e, a) }
    }

    fun registerOfficerCommand(key: String, factory: OfficerCommandFactory) {
        officerCommands[key] = factory
        officerSchemas[key] = COMMAND_SCHEMAS[key] ?: ArgSchema.NONE
    }

    fun registerFactionCommand(key: String, factory: FactionCommandFactory) {
        factionCommands[key] = factory
        factionSchemas[key] = COMMAND_SCHEMAS[key] ?: ArgSchema.NONE
    }

    fun createOfficerCommand(actionCode: String, general: Officer, env: CommandEnv, arg: Map<String, Any>? = null): OfficerCommand {
        val factory = officerCommands[actionCode] ?: officerCommands["휴식"]!!
        return factory(general, env, arg)
    }

    fun createFactionCommand(actionCode: String, general: Officer, env: CommandEnv, arg: Map<String, Any>? = null): FactionCommand? {
        val factory = factionCommands[actionCode] ?: return null
        return factory(general, env, arg)
    }

    fun hasGeneralCommand(actionCode: String): Boolean = actionCode in officerCommands
    fun hasNationCommand(actionCode: String): Boolean = actionCode in factionCommands
    fun getOfficerSchema(actionCode: String): ArgSchema = officerSchemas[actionCode] ?: ArgSchema.NONE
    fun getFactionSchema(actionCode: String): ArgSchema = factionSchemas[actionCode] ?: ArgSchema.NONE
    fun getSchema(actionCode: String): ArgSchema =
        officerSchemas[actionCode] ?: factionSchemas[actionCode] ?: ArgSchema.NONE
    fun getGeneralCommandNames(): Set<String> = officerCommands.keys
    fun getNationCommandNames(): Set<String> = factionCommands.keys
}
