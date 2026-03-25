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
        // ===== Default (1) =====
        "휴식" to { g, e, a -> 휴식(g, e, a) },

        // ===== Operations / MCP (25) =====
        "워프항행" to { g, e, a -> che_워프항행(g, e, a) },
        "성계내항행" to { g, e, a -> che_성계내항행(g, e, a) },
        "연료보급" to { g, e, a -> che_연료보급(g, e, a) },
        "정찰" to { g, e, a -> che_정찰(g, e, a) },
        "군기유지" to { g, e, a -> che_군기유지(g, e, a) },
        "기본훈련" to { g, e, a -> che_기본훈련(g, e, a) },
        "특수훈련" to { g, e, a -> che_특수훈련(g, e, a) },
        "맹훈련" to { g, e, a -> che_맹훈련(g, e, a) },
        "육전훈련" to { g, e, a -> che_육전훈련(g, e, a) },
        "공전훈련" to { g, e, a -> che_공전훈련(g, e, a) },
        "경계출동" to { g, e, a -> che_경계출동(g, e, a) },
        "무력진압" to { g, e, a -> che_무력진압(g, e, a) },
        "분열행진" to { g, e, a -> che_분열행진(g, e, a) },
        "징발" to { g, e, a -> che_징발(g, e, a) },
        "특별경비" to { g, e, a -> che_특별경비(g, e, a) },
        "정비" to { g, e, a -> che_정비(g, e, a) },
        "지상작전개시" to { g, e, a -> che_지상작전개시(g, e, a) },
        "지상전투개시" to { g, e, a -> che_지상전투개시(g, e, a) },
        "점령" to { g, e, a -> che_점령(g, e, a) },
        "철수" to { g, e, a -> che_철수(g, e, a) },
        "후퇴" to { g, e, a -> che_후퇴(g, e, a) },
        "육전대출격" to { g, e, a -> che_육전대출격(g, e, a) },
        "육전대철수" to { g, e, a -> che_육전대철수(g, e, a) },
        "육전전술훈련" to { g, e, a -> che_육전전술훈련(g, e, a) },
        "공전전술훈련" to { g, e, a -> che_공전전술훈련(g, e, a) },

        // ===== Personal / PCP (16) =====
        "퇴역" to { g, e, a -> 퇴역(g, e, a) },
        "지원전환" to { g, e, a -> 지원전환(g, e, a) },
        "망명" to { g, e, a -> 망명(g, e, a) },
        "회견" to { g, e, a -> 회견(g, e, a) },
        "수강" to { g, e, a -> 수강(g, e, a) },
        "기함구매" to { g, e, a -> 기함구매(g, e, a) },
        "자금투입" to { g, e, a -> 자금투입(g, e, a) },
        "귀환설정" to { g, e, a -> 귀환설정(g, e, a) },
        "원거리이동" to { g, e, a -> 원거리이동(g, e, a) },
        "근거리이동" to { g, e, a -> 근거리이동(g, e, a) },
        "병기연습" to { g, e, a -> che_병기연습(g, e, a) },
        "반의" to { g, e, a -> 반의(g, e, a) },
        "모의" to { g, e, a -> 모의(g, e, a) },
        "설득" to { g, e, a -> 설득(g, e, a) },
        "반란참가" to { g, e, a -> 반란참가(g, e, a) },
        "반란" to { g, e, a -> 반란(g, e, a) },

        // ===== Command / Leadership (8) =====
        "작전계획" to { g, e, a -> 작전계획(g, e, a) },
        "장수발령" to { g, e, a -> 발령(g, e, a) },
        "작전철회" to { g, e, a -> 작전철회(g, e, a) },
        "부대결성" to { g, e, a -> 부대결성(g, e, a) },
        "부대해산" to { g, e, a -> 부대해산(g, e, a) },
        "강의" to { g, e, a -> 강의(g, e, a) },
        "수송계획" to { g, e, a -> 수송계획(g, e, a) },
        "수송중지" to { g, e, a -> 수송중지(g, e, a) },

        // ===== Logistics (6) =====
        "재편성" to { g, e, a -> 재편성(g, e, a) },
        "완전수리" to { g, e, a -> 완전수리(g, e, a) },
        "완전보급" to { g, e, a -> 완전보급(g, e, a) },
        "반출입" to { g, e, a -> 반출입(g, e, a) },
        "보충" to { g, e, a -> 보충(g, e, a) },
        "할당" to { g, e, a -> 할당(g, e, a) },

        // ===== Influence / Social (5) =====
        "야회" to { g, e, a -> 야회(g, e, a) },
        "수렵" to { g, e, a -> 수렵(g, e, a) },
        "회담" to { g, e, a -> 회담(g, e, a) },
        "담화" to { g, e, a -> 담화(g, e, a) },
        "연설" to { g, e, a -> 연설(g, e, a) },

        // ===== Personal (proposal/order) (2) =====
        "제안" to { g, e, a -> 제안(g, e, a) },
        "명령" to { g, e, a -> 명령(g, e, a) },

        // ===== Espionage / Intelligence (15) =====
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
        "통신방해" to { g, e, a -> che_통신방해(g, e, a) },
        "위장함대" to { g, e, a -> che_위장함대(g, e, a) },
    )

    private val nationCommands: Map<String, NationCommandFactory> = mapOf(
        // ===== Default (1) =====
        "Nation휴식" to { g, e, a -> Nation휴식(g, e, a) },

        // ===== Personnel (10) =====
        "승진" to { g, e, a -> 승진(g, e, a) },
        "발탁" to { g, e, a -> 발탁(g, e, a) },
        "강등" to { g, e, a -> 강등(g, e, a) },
        "서작" to { g, e, a -> 서작(g, e, a) },
        "서훈" to { g, e, a -> 서훈(g, e, a) },
        "임명" to { g, e, a -> 임명(g, e, a) },
        "파면" to { g, e, a -> 파면(g, e, a) },
        "사임" to { g, e, a -> 사임(g, e, a) },
        "봉토수여" to { g, e, a -> 봉토수여(g, e, a) },
        "봉토직할" to { g, e, a -> 봉토직할(g, e, a) },

        // ===== Political (9) =====
        "국가목표설정" to { g, e, a -> 국가목표설정(g, e, a) },
        "납입률변경" to { g, e, a -> 납입률변경(g, e, a) },
        "관세율변경" to { g, e, a -> 관세율변경(g, e, a) },
        "분배" to { g, e, a -> 분배(g, e, a) },
        "처단" to { g, e, a -> 처단(g, e, a) },
        "외교" to { g, e, a -> 외교(g, e, a) },
        "통치목표" to { g, e, a -> 통치목표(g, e, a) },
        "예산편성" to { g, e, a -> 예산편성(g, e, a) },
        "제안공작" to { g, e, a -> 제안공작(g, e, a) },

        // ===== Diplomacy (7) =====
        "선전포고" to { g, e, a -> che_선전포고(g, e, a) },
        "불가침제의" to { g, e, a -> che_불가침제의(g, e, a) },
        "불가침수락" to { g, e, a -> che_불가침수락(g, e, a) },
        "불가침파기제의" to { g, e, a -> che_불가침파기제의(g, e, a) },
        "불가침파기수락" to { g, e, a -> che_불가침파기수락(g, e, a) },
        "종전제의" to { g, e, a -> che_종전제의(g, e, a) },
        "종전수락" to { g, e, a -> che_종전수락(g, e, a) },

        // ===== Resource / Administration (7) =====
        "감축" to { g, e, a -> che_감축(g, e, a) },
        "주민동원" to { g, e, a -> che_백성동원(g, e, a) },
        "외교공작" to { g, e, a -> che_이호경식(g, e, a) },
        "세율변경" to { g, e, a -> che_세율변경(g, e, a) },
        "징병률변경" to { g, e, a -> che_징병률변경(g, e, a) },
        "국가해산" to { g, e, a -> che_국가해산(g, e, a) },
        "항복" to { g, e, a -> che_항복(g, e, a) },
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
