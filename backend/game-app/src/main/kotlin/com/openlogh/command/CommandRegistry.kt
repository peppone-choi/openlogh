package com.openlogh.command

import com.openlogh.command.general.*
import com.openlogh.command.nation.*
import com.openlogh.entity.General
import org.springframework.stereotype.Service

typealias GeneralCommandFactory = (General, CommandEnv, Map<String, Any>?) -> BaseCommand
typealias NationCommandFactory = (General, CommandEnv, Map<String, Any>?) -> NationCommand

@Service
class CommandRegistry {

    private val generalCommands: Map<String, GeneralCommandFactory> = mapOf(
        // Default (1)
        "휴식" to { g, e, a -> 휴식(g, e, a) },

        // Civil / Domestic (18)
        "농지개간" to { g, e, a -> che_농지개간(g, e, a) },
        "상업투자" to { g, e, a -> che_상업투자(g, e, a) },
        "치안강화" to { g, e, a -> che_치안강화(g, e, a) },
        "수비강화" to { g, e, a -> che_수비강화(g, e, a) },
        "성벽보수" to { g, e, a -> che_성벽보수(g, e, a) },
        "정착장려" to { g, e, a -> che_정착장려(g, e, a) },
        "주민선정" to { g, e, a -> che_주민선정(g, e, a) },
        "기술연구" to { g, e, a -> che_기술연구(g, e, a) },
        "모병" to { g, e, a -> che_모병(g, e, a) },
        "징병" to { g, e, a -> che_징병(g, e, a) },
        "훈련" to { g, e, a -> che_훈련(g, e, a) },
        "사기진작" to { g, e, a -> che_사기진작(g, e, a) },
        "소집해제" to { g, e, a -> che_소집해제(g, e, a) },
        "숙련전환" to { g, e, a -> che_숙련전환(g, e, a) },
        "물자조달" to { g, e, a -> che_물자조달(g, e, a) },
        "군량매매" to { g, e, a -> che_군량매매(g, e, a) },
        "헌납" to { g, e, a -> che_헌납(g, e, a) },
        "단련" to { g, e, a -> che_단련(g, e, a) },

        // Military (15)
        "출병" to { g, e, a -> 출병(g, e, a) },
        "이동" to { g, e, a -> 이동(g, e, a) },
        "집합" to { g, e, a -> 집합(g, e, a) },
        "귀환" to { g, e, a -> 귀환(g, e, a) },
        "접경귀환" to { g, e, a -> 접경귀환(g, e, a) },
        "강행" to { g, e, a -> 강행(g, e, a) },
        "거병" to { g, e, a -> 거병(g, e, a) },
        "전투태세" to { g, e, a -> 전투태세(g, e, a) },
        "화계" to { g, e, a -> 화계(g, e, a) },
        "첩보" to { g, e, a -> 첩보(g, e, a) },
        "선동" to { g, e, a -> 선동(g, e, a) },
        "탈취" to { g, e, a -> 탈취(g, e, a) },
        "파괴" to { g, e, a -> 파괴(g, e, a) },
        "요양" to { g, e, a -> 요양(g, e, a) },
        "방랑" to { g, e, a -> 방랑(g, e, a) },

        // Political (18)
        "견문" to { g, e, a -> 견문(g, e, a) },
        "등용" to { g, e, a -> 등용(g, e, a) },
        "등용수락" to { g, e, a -> 등용수락(g, e, a) },
        "임관" to { g, e, a -> 임관(g, e, a) },
        "랜덤임관" to { g, e, a -> 랜덤임관(g, e, a) },
        "장수대상임관" to { g, e, a -> 장수대상임관(g, e, a) },
        "하야" to { g, e, a -> 하야(g, e, a) },
        "은퇴" to { g, e, a -> 은퇴(g, e, a) },
        "건국" to { g, e, a -> 건국(g, e, a) },
        "무작위건국" to { g, e, a -> 무작위건국(g, e, a) },
        "모반시도" to { g, e, a -> 모반시도(g, e, a) },
        "선양" to { g, e, a -> 선양(g, e, a) },
        "해산" to { g, e, a -> 해산(g, e, a) },
        "인재탐색" to { g, e, a -> 인재탐색(g, e, a) },
        "증여" to { g, e, a -> 증여(g, e, a) },
        "장비매매" to { g, e, a -> 장비매매(g, e, a) },
        "내정특기초기화" to { g, e, a -> 내정특기초기화(g, e, a) },
        "전투특기초기화" to { g, e, a -> 전투특기초기화(g, e, a) },

        // Special (3)
        "NPC능동" to { g, e, a -> NPC능동(g, e, a) },
        "CR건국" to { g, e, a -> CR건국(g, e, a) },
        "CR맹훈련" to { g, e, a -> CR맹훈련(g, e, a) },

        // Operations / Military (13) — Phase 2
        "연료보급" to { g, e, a -> che_연료보급(g, e, a) },
        "기본훈련" to { g, e, a -> che_기본훈련(g, e, a) },
        "특수훈련" to { g, e, a -> che_특수훈련(g, e, a) },
        "맹훈련" to { g, e, a -> che_맹훈련(g, e, a) },
        "정비" to { g, e, a -> che_정비(g, e, a) },
        "지상작전개시" to { g, e, a -> che_지상작전개시(g, e, a) },
        "지상전투개시" to { g, e, a -> che_지상전투개시(g, e, a) },
        "점령" to { g, e, a -> che_점령(g, e, a) },
        "철수" to { g, e, a -> che_철수(g, e, a) },
        "후퇴" to { g, e, a -> che_후퇴(g, e, a) },
        "육전대출격" to { g, e, a -> che_육전대출격(g, e, a) },
        "육전대철수" to { g, e, a -> che_육전대철수(g, e, a) },
        "정찰" to { g, e, a -> che_정찰(g, e, a) },

        // Personal (11) — Phase 2
        "퇴역" to { g, e, a -> 퇴역(g, e, a) },
        "지원전환" to { g, e, a -> 지원전환(g, e, a) },
        "망명" to { g, e, a -> 망명(g, e, a) },
        "회견" to { g, e, a -> 회견(g, e, a) },
        "수강" to { g, e, a -> 수강(g, e, a) },
        "반의" to { g, e, a -> 반의(g, e, a) },
        "모의" to { g, e, a -> 모의(g, e, a) },
        "설득" to { g, e, a -> 설득(g, e, a) },
        "반란참가" to { g, e, a -> 반란참가(g, e, a) },
        "자금투입" to { g, e, a -> 자금투입(g, e, a) },
        "기함구매" to { g, e, a -> 기함구매(g, e, a) },

        // Command/Leadership (6) — Phase 2
        "작전계획" to { g, e, a -> 작전계획(g, e, a) },
        "작전철회" to { g, e, a -> 작전철회(g, e, a) },
        "장수발령" to { g, e, a -> 발령(g, e, a) },
        "강의" to { g, e, a -> 강의(g, e, a) },
        "수송계획" to { g, e, a -> 수송계획(g, e, a) },
        "수송중지" to { g, e, a -> 수송중지(g, e, a) },

        // Logistics (4) — Phase 2
        "완전수리" to { g, e, a -> 완전수리(g, e, a) },
        "완전보급" to { g, e, a -> 완전보급(g, e, a) },
        "재편성" to { g, e, a -> 재편성(g, e, a) },
        "반출입" to { g, e, a -> 반출입(g, e, a) },

        // Influence / Social (5) — Phase 2
        "야회" to { g, e, a -> 야회(g, e, a) },
        "수렵" to { g, e, a -> 수렵(g, e, a) },
        "회담" to { g, e, a -> 회담(g, e, a) },
        "담화" to { g, e, a -> 담화(g, e, a) },
        "연설" to { g, e, a -> 연설(g, e, a) },

        // P0: Operations (2) — Warp & Inter-system
        "워프항행" to { g, e, a -> che_워프항행(g, e, a) },
        "성계내항행" to { g, e, a -> che_성계내항행(g, e, a) },

        // P0: Command Group (2) — Fleet formation/dissolution
        "부대결성" to { g, e, a -> 부대결성(g, e, a) },
        "부대해산" to { g, e, a -> 부대해산(g, e, a) },

        // P0: Logistics (1) — Replenishment
        "보충" to { g, e, a -> 보충(g, e, a) },

        // P1: Command Group (1) — Assignment
        "할당" to { g, e, a -> 할당(g, e, a) },

        // P1: Personal (7) — Proposal, Order, Return, Movement, Rebellion
        "제안" to { g, e, a -> 제안(g, e, a) },
        "명령" to { g, e, a -> 명령(g, e, a) },
        "귀환설정" to { g, e, a -> 귀환설정(g, e, a) },
        "원거리이동" to { g, e, a -> 원거리이동(g, e, a) },
        "근거리이동" to { g, e, a -> 근거리이동(g, e, a) },
        "반란" to { g, e, a -> 반란(g, e, a) },

        // P1: Operations (9) — Discipline, security, training, etc.
        "군기유지" to { g, e, a -> che_군기유지(g, e, a) },
        "경계출동" to { g, e, a -> che_경계출동(g, e, a) },
        "무력진압" to { g, e, a -> che_무력진압(g, e, a) },
        "분열행진" to { g, e, a -> che_분열행진(g, e, a) },
        "징발" to { g, e, a -> che_징발(g, e, a) },
        "육전훈련" to { g, e, a -> che_육전훈련(g, e, a) },
        "공전훈련" to { g, e, a -> che_공전훈련(g, e, a) },

        // P2: Operations (6) — Special guard, comms, decoy, war game, tactics
        "특별경비" to { g, e, a -> che_특별경비(g, e, a) },
        "통신방해" to { g, e, a -> che_통신방해(g, e, a) },
        "위장함대" to { g, e, a -> che_위장함대(g, e, a) },
        "병기연습" to { g, e, a -> che_병기연습(g, e, a) },
        "육전전술훈련" to { g, e, a -> che_육전전술훈련(g, e, a) },
        "공전전술훈련" to { g, e, a -> che_공전전술훈련(g, e, a) },

        // Espionage (13) — All espionage commands
        "일제수색" to { g, e, a -> che_일제수색(g, e, a) },
        "체포허가" to { g, e, a -> che_체포허가(g, e, a) },
        "집행명령" to { g, e, a -> che_집행명령(g, e, a) },
        "체포명령" to { g, e, a -> che_체포명령(g, e, a) },
        "사열" to { g, e, a -> che_사열(g, e, a) },
        "습격" to { g, e, a -> che_습격(g, e, a) },
        "감시" to { g, e, a -> che_감시(g, e, a) },
        "잠입공작" to { g, e, a -> che_잠입공작(g, e, a) },
        "탈출공작" to { g, e, a -> che_탈출공작(g, e, a) },
        "정보공작" to { g, e, a -> che_정보공작(g, e, a) },
        "파괴공작" to { g, e, a -> che_파괴공작(g, e, a) },
        "선동공작" to { g, e, a -> che_선동공작(g, e, a) },
        "귀환공작" to { g, e, a -> che_귀환공작(g, e, a) },
    )

    private val nationCommands: Map<String, NationCommandFactory> = mapOf(
        // Default (1)
        "Nation휴식" to { g, e, a -> Nation휴식(g, e, a) },

        // Resource management (10)
        "포상" to { g, e, a -> che_포상(g, e, a) },
        "몰수" to { g, e, a -> che_몰수(g, e, a) },
        "감축" to { g, e, a -> che_감축(g, e, a) },
        "증축" to { g, e, a -> che_증축(g, e, a) },
        "발령" to { g, e, a -> che_발령(g, e, a) },
        "천도" to { g, e, a -> che_천도(g, e, a) },
        "백성동원" to { g, e, a -> che_백성동원(g, e, a) },
        "물자원조" to { g, e, a -> che_물자원조(g, e, a) },
        "국기변경" to { g, e, a -> che_국기변경(g, e, a) },
        "국호변경" to { g, e, a -> che_국호변경(g, e, a) },

        // Diplomacy (7)
        "선전포고" to { g, e, a -> che_선전포고(g, e, a) },
        "종전제의" to { g, e, a -> che_종전제의(g, e, a) },
        "종전수락" to { g, e, a -> che_종전수락(g, e, a) },
        "불가침제의" to { g, e, a -> che_불가침제의(g, e, a) },
        "불가침수락" to { g, e, a -> che_불가침수락(g, e, a) },
        "불가침파기제의" to { g, e, a -> che_불가침파기제의(g, e, a) },
        "불가침파기수락" to { g, e, a -> che_불가침파기수락(g, e, a) },

        // Strategic (8)
        "급습" to { g, e, a -> che_급습(g, e, a) },
        "수몰" to { g, e, a -> che_수몰(g, e, a) },
        "허보" to { g, e, a -> che_허보(g, e, a) },
        "초토화" to { g, e, a -> che_초토화(g, e, a) },
        "필사즉생" to { g, e, a -> che_필사즉생(g, e, a) },
        "이호경식" to { g, e, a -> che_이호경식(g, e, a) },
        "피장파장" to { g, e, a -> che_피장파장(g, e, a) },
        "의병모집" to { g, e, a -> che_의병모집(g, e, a) },

        // Research (9)
        "극병연구" to { g, e, a -> event_극병연구(g, e, a) },
        "대검병연구" to { g, e, a -> event_대검병연구(g, e, a) },
        "무희연구" to { g, e, a -> event_무희연구(g, e, a) },
        "산저병연구" to { g, e, a -> event_산저병연구(g, e, a) },
        "상병연구" to { g, e, a -> event_상병연구(g, e, a) },
        "원융노병연구" to { g, e, a -> event_원융노병연구(g, e, a) },
        "음귀병연구" to { g, e, a -> event_음귀병연구(g, e, a) },
        "화륜차연구" to { g, e, a -> event_화륜차연구(g, e, a) },
        "화시병연구" to { g, e, a -> event_화시병연구(g, e, a) },

        // Special (3)
        "무작위수도이전" to { g, e, a -> che_무작위수도이전(g, e, a) },
        "부대탈퇴지시" to { g, e, a -> che_부대탈퇴지시(g, e, a) },
        "인구이동" to { g, e, a -> cr_인구이동(g, e, a) },

        // Additional (5)
        "세율변경" to { g, e, a -> che_세율변경(g, e, a) },
        "징병률변경" to { g, e, a -> che_징병률변경(g, e, a) },
        "국가해산" to { g, e, a -> che_국가해산(g, e, a) },
        "항복" to { g, e, a -> che_항복(g, e, a) },
        "외교초기화" to { g, e, a -> che_외교초기화(g, e, a) },

        // Personnel (7) — Phase 2
        "발탁" to { g, e, a -> 발탁(g, e, a) },
        "강등" to { g, e, a -> 강등(g, e, a) },
        "서작" to { g, e, a -> 서작(g, e, a) },
        "서훈" to { g, e, a -> 서훈(g, e, a) },
        "사임" to { g, e, a -> 사임(g, e, a) },
        "봉토수여" to { g, e, a -> 봉토수여(g, e, a) },
        "봉토직할" to { g, e, a -> 봉토직할(g, e, a) },

        // National Politics (7) — Phase 2
        "국가목표설정" to { g, e, a -> 국가목표설정(g, e, a) },
        "납입률변경" to { g, e, a -> 납입률변경(g, e, a) },
        "관세율변경" to { g, e, a -> 관세율변경(g, e, a) },
        "분배" to { g, e, a -> 분배(g, e, a) },
        "처단" to { g, e, a -> 처단(g, e, a) },
        "외교" to { g, e, a -> 외교(g, e, a) },
        "통치목표" to { g, e, a -> 통치목표(g, e, a) },

        // P0: Personnel (3) — Promotion, Appointment, Dismissal
        "승진" to { g, e, a -> 승진(g, e, a) },
        "임명" to { g, e, a -> 임명(g, e, a) },
        "파면" to { g, e, a -> 파면(g, e, a) },

        // P1: National Politics (2) — Budget, Forced Proposal
        "예산편성" to { g, e, a -> 예산편성(g, e, a) },
        "제안공작" to { g, e, a -> 제안공작(g, e, a) },
    )

    fun getGeneralCommandNames(): Set<String> = generalCommands.keys

    fun getNationCommandNames(): Set<String> = nationCommands.keys

    fun hasGeneralCommand(actionCode: String): Boolean = actionCode in generalCommands

    fun hasNationCommand(actionCode: String): Boolean = actionCode in nationCommands

    fun createGeneralCommand(
        actionCode: String,
        general: General,
        env: CommandEnv,
        arg: Map<String, Any>? = null,
    ): BaseCommand {
        val factory = generalCommands[actionCode] ?: generalCommands["휴식"]!!
        return factory(general, env, arg)
    }

    fun createNationCommand(
        actionCode: String,
        general: General,
        env: CommandEnv,
        arg: Map<String, Any>? = null,
    ): NationCommand? {
        val factory = nationCommands[actionCode] ?: return null
        return factory(general, env, arg)
    }
}
