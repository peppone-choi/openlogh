package com.openlogh.engine.modifier

import com.openlogh.command.CommandCost
import com.openlogh.entity.Officer
import org.springframework.stereotype.Service

/**
 * 통합 능력치 보정 서비스.
 *
 * gin7 매뉴얼:
 * - 아이템(기함/장비/기관/부속품)에 의한 능력치 보정
 * - 특성(전투특기/내정특기)에 의한 커맨드 효과 보정
 * - 계급에 의한 간접 보정 (커맨드 레인지, 최대 사기 등)
 *
 * 이 서비스는 커맨드 실행 시 비용/효과/성공률에 대한 보정을 통합 처리.
 */
@Service
class ModifierService {

    // ===== 아이템 보정 테이블 =====

    /** 기함 코드 → 능력치 보정 */
    private val flagshipModifiers: Map<String, StatModifier> = mapOf(
        "flagship_brunhilde" to StatModifier(command = 5, leadership = 3, attack = 3),
        "flagship_hyperion" to StatModifier(command = 3, defense = 5, mobility = 3),
        "flagship_barbarossa" to StatModifier(attack = 5, command = 2),
        "flagship_ulysses" to StatModifier(defense = 3, mobility = 3, intelligence = 2),
        "flagship_patro" to StatModifier(command = 4, attack = 2, defense = 2),
    )

    /** 장비 코드 → 능력치 보정 */
    private val equipModifiers: Map<String, StatModifier> = mapOf(
        "equip_zephyr_particle" to StatModifier(attack = 5),
        "equip_rail_cannon" to StatModifier(attack = 3, defense = -1),
        "equip_advanced_sensor" to StatModifier(intelligence = 5),
        "equip_ecm_suite" to StatModifier(intelligence = 3, defense = 2),
    )

    /** 기관 코드 → 능력치 보정 */
    private val engineModifiers: Map<String, StatModifier> = mapOf(
        "engine_high_output" to StatModifier(mobility = 5),
        "engine_stealth" to StatModifier(mobility = 3, intelligence = 2),
        "engine_balanced" to StatModifier(mobility = 2, defense = 2),
    )

    /** 부속품 코드 → 능력치 보정 */
    private val accessoryModifiers: Map<String, StatModifier> = mapOf(
        "accessory_command_module" to StatModifier(command = 3, leadership = 2),
        "accessory_armor_plate" to StatModifier(defense = 4),
        "accessory_targeting" to StatModifier(attack = 3),
    )

    data class StatModifier(
        val leadership: Int = 0,
        val command: Int = 0,
        val intelligence: Int = 0,
        val politics: Int = 0,
        val administration: Int = 0,
        val mobility: Int = 0,
        val attack: Int = 0,
        val defense: Int = 0,
    )

    // ===== 통합 보정 API =====

    /**
     * 장교의 아이템 기반 실효 능력치 보정값 계산.
     * 원래 능력치를 변경하지 않고 보정값만 반환.
     */
    fun getItemModifiers(officer: Officer): StatModifier {
        val flagship = officer.meta["flagship"] as? String
        val equip = officer.equipCode
        val engine = officer.engineCode
        val accessory = officer.accessoryCode

        val mods = listOfNotNull(
            flagship?.let { flagshipModifiers[it] },
            if (equip != "None") equipModifiers[equip] else null,
            if (engine != "None") engineModifiers[engine] else null,
            if (accessory != "None") accessoryModifiers[accessory] else null,
        )

        return mods.fold(StatModifier()) { acc, m ->
            StatModifier(
                leadership = acc.leadership + m.leadership,
                command = acc.command + m.command,
                intelligence = acc.intelligence + m.intelligence,
                politics = acc.politics + m.politics,
                administration = acc.administration + m.administration,
                mobility = acc.mobility + m.mobility,
                attack = acc.attack + m.attack,
                defense = acc.defense + m.defense,
            )
        }
    }

    /**
     * 보정 적용 후 실효 능력치 계산.
     */
    fun getEffectiveStat(officer: Officer, statName: String): Int {
        val mod = getItemModifiers(officer)
        return when (statName) {
            "leadership" -> officer.leadership.toInt() + mod.leadership
            "command" -> officer.command.toInt() + mod.command
            "intelligence" -> officer.intelligence.toInt() + mod.intelligence
            "politics" -> officer.politics.toInt() + mod.politics
            "administration" -> officer.administration.toInt() + mod.administration
            "mobility" -> officer.mobility.toInt() + mod.mobility
            "attack" -> officer.attack.toInt() + mod.attack
            "defense" -> officer.defense.toInt() + mod.defense
            else -> 0
        }.coerceIn(1, 120) // 아이템 보정 포함 최대 120
    }

    // ===== 세션 레벨 보정 (기존 인터페이스 유지) =====

    fun applyModifiers(sessionId: Long) {}

    fun applyDomesticScoreModifier(officer: Officer, ctx: Map<String, Any>): Int? = null

    fun applyDomesticCostModifier(officer: Officer, ctx: Map<String, Any>): Int? = null

    /**
     * 내정 비용 보정. 특성에 따라 할인 적용.
     */
    fun onCalcDomesticCost(personalCode: String, cost: CommandCost): CommandCost {
        return when {
            personalCode.contains("che_안전") -> cost.copy(funds = (cost.funds * 0.8).toInt())
            personalCode.contains("농업") -> cost.copy(funds = (cost.funds * 0.9).toInt())
            personalCode.contains("상업") -> cost.copy(funds = (cost.funds * 0.85).toInt())
            else -> cost
        }
    }

    /**
     * 내정 효과 보정. 특성에 따라 효과 증감.
     */
    fun onCalcDomesticScore(personalCode: String, score: Int): Int {
        return when {
            personalCode.contains("온후") -> (score * 1.1).toInt()
            personalCode.contains("호전") -> (score * 0.95).toInt()
            personalCode.contains("농업") -> (score * 1.15).toInt()
            personalCode.contains("상업") -> (score * 1.15).toInt()
            personalCode.contains("기술") -> (score * 1.2).toInt()
            personalCode.contains("인사") -> (score * 1.1).toInt()
            else -> score
        }
    }

    /**
     * 내정 성공률 보정.
     */
    fun onCalcDomesticSuccess(personalCode: String, successRatio: Double): Double {
        return when {
            personalCode.contains("신중") -> successRatio * 1.1
            personalCode.contains("인사") -> successRatio * 1.15
            else -> successRatio
        }.coerceAtMost(0.98)
    }

    /**
     * 전투 공격력 보정. 전투특기에 따른 보정.
     */
    fun onCalcBattleAttack(officer: Officer, baseAttack: Double): Double {
        val special1 = officer.specialCode
        val mod = getItemModifiers(officer)
        val itemBonus = mod.attack * 0.5

        val traitBonus = when {
            special1.contains("che_돌격") -> baseAttack * 0.15
            special1.contains("che_연사") -> baseAttack * 0.10
            special1.contains("che_화공") -> baseAttack * 0.12
            special1.contains("che_저격") -> baseAttack * 0.08
            else -> 0.0
        }

        return baseAttack + itemBonus + traitBonus
    }

    /**
     * 전투 방어력 보정.
     */
    fun onCalcBattleDefense(officer: Officer, baseDefense: Double): Double {
        val mod = getItemModifiers(officer)
        val itemBonus = mod.defense * 0.5

        val special1 = officer.specialCode
        val traitBonus = when {
            special1.contains("che_반계") -> baseDefense * 0.12
            else -> 0.0
        }

        return baseDefense + itemBonus + traitBonus
    }
}
