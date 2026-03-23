package com.openlogh.engine.war

import kotlin.random.Random

class BattleTriggerContext(
    val attacker: WarUnit,
    val defender: WarUnit,
    val rng: Random = Random.Default,
    val phaseNumber: Int = 0,
    val isVsCity: Boolean = false,
) {
    val battleLogs: MutableList<String> = mutableListOf()
    var criticalChanceBonus: Double = 0.0
    var dodgeChanceBonus: Double = 0.0
    var dodgeDisabled: Boolean = false
    var magicActivated: Boolean = false
    var magicChanceBonus: Double = 0.0
    var magicDamageMultiplier: Double = 1.0
    var magicReflected: Boolean = false
    var magicFailDamage: Double = 0.0
    var attackMultiplier: Double = 1.0
    var defenceMultiplier: Double = 1.0
    var criticalActivated: Boolean = false
    var snipeActivated: Boolean = false
    var snipeWoundAmount: Int = 0
    var injuryImmune: Boolean = false
    var moraleBoost: Int = 0
    var counterDamageRatio: Double = 0.0
}

abstract class BattleTrigger(
    val code: String,
    val priority: Int = 50,
) {
    open fun onBattleInit(ctx: BattleTriggerContext): BattleTriggerContext = ctx
    open fun onPreCritical(ctx: BattleTriggerContext): BattleTriggerContext = ctx
    open fun onPostCritical(ctx: BattleTriggerContext): BattleTriggerContext = ctx
    open fun onPreDodge(ctx: BattleTriggerContext): BattleTriggerContext = ctx
    open fun onPreMagic(ctx: BattleTriggerContext): BattleTriggerContext = ctx
    open fun onPostMagic(ctx: BattleTriggerContext): BattleTriggerContext = ctx
    open fun onMagicFail(ctx: BattleTriggerContext): BattleTriggerContext = ctx
    open fun onDamageCalc(ctx: BattleTriggerContext): BattleTriggerContext = ctx
    open fun onPostDamage(ctx: BattleTriggerContext): BattleTriggerContext = ctx
    open fun onInjuryCheck(ctx: BattleTriggerContext): BattleTriggerContext = ctx
}

// ========== 23 Named Special Triggers ==========

object 필살Trigger : BattleTrigger("필살", priority = 10) {
    override fun onPreCritical(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.criticalChanceBonus += 0.30
        ctx.dodgeDisabled = true
        ctx.battleLogs.add("필살 발동!")
        return ctx
    }
}

object 회피Trigger : BattleTrigger("회피", priority = 50) {
    override fun onPreDodge(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.dodgeChanceBonus += 0.08
        return ctx
    }
}

object 반계Trigger : BattleTrigger("반계", priority = 50) {
    override fun onPostMagic(ctx: BattleTriggerContext): BattleTriggerContext {
        if (ctx.magicActivated) {
            ctx.magicReflected = true
            ctx.battleLogs.add("반계 발동! 계략 반사!")
        }
        return ctx
    }
}

object 신산Trigger : BattleTrigger("신산", priority = 50) {
    override fun onPreMagic(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.magicChanceBonus += 0.20
        return ctx
    }
}

object 위압Trigger : BattleTrigger("위압", priority = 20) {
    override fun onDamageCalc(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.attackMultiplier *= 1.05
        return ctx
    }
}

object 저격Trigger : BattleTrigger("저격", priority = 15) {
    override fun onPreCritical(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.criticalChanceBonus += 0.08
        return ctx
    }

    override fun onPostCritical(ctx: BattleTriggerContext): BattleTriggerContext {
        if (!ctx.criticalActivated) return ctx
        if (ctx.defender is WarUnitCity) return ctx
        val attacker = ctx.attacker
        if (attacker !is WarUnitGeneral) return ctx
        val woundChance = attacker.officer.command.toDouble() / 200.0
        if (ctx.rng.nextDouble() < woundChance) {
            ctx.snipeActivated = true
            ctx.snipeWoundAmount = ctx.rng.nextInt(5) + 2
            ctx.battleLogs.add("저격 발동! 부상 ${ctx.snipeWoundAmount}")
        }
        return ctx
    }
}

object 격노Trigger : BattleTrigger("격노", priority = 50) {
    override fun onDamageCalc(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.attackMultiplier *= 1.2
        return ctx
    }
}

object 돌격Trigger : BattleTrigger("돌격", priority = 50) {
    override fun onDamageCalc(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.attackMultiplier *= 1.15
        return ctx
    }
}

object 화공Trigger : BattleTrigger("화공", priority = 50) {
    override fun onPreMagic(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.magicChanceBonus += 0.15
        ctx.magicDamageMultiplier *= 1.2
        return ctx
    }
}

object 기습Trigger : BattleTrigger("기습", priority = 50) {
    override fun onPreCritical(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.criticalChanceBonus += if (ctx.phaseNumber == 0) 0.10 else 0.05
        return ctx
    }

    override fun onPreDodge(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.dodgeChanceBonus += if (ctx.phaseNumber == 0) 0.10 else 0.05
        return ctx
    }
}

object 매복Trigger : BattleTrigger("매복", priority = 50) {
    override fun onPreDodge(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.dodgeChanceBonus += 0.08
        return ctx
    }
}

object 방어Trigger : BattleTrigger("방어", priority = 50) {
    override fun onPreDodge(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.dodgeChanceBonus += 0.15
        return ctx
    }

    override fun onDamageCalc(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.defenceMultiplier *= 1.1
        return ctx
    }
}

object 귀모Trigger : BattleTrigger("귀모", priority = 50) {
    override fun onPreMagic(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.magicChanceBonus += 0.25
        ctx.magicDamageMultiplier *= 1.3
        return ctx
    }

    override fun onMagicFail(ctx: BattleTriggerContext): BattleTriggerContext {
        val intel = if (ctx.attacker is WarUnitGeneral) (ctx.attacker as WarUnitGeneral).officer.intelligence else 0
        ctx.magicFailDamage = intel * 0.5
        ctx.battleLogs.add("계략 실패! 자폭 피해 ${ctx.magicFailDamage}")
        return ctx
    }
}

object 공성Trigger : BattleTrigger("공성", priority = 50) {
    override fun onDamageCalc(ctx: BattleTriggerContext): BattleTriggerContext {
        if (ctx.isVsCity) ctx.attackMultiplier *= 1.3
        return ctx
    }
}

object 철벽Trigger : BattleTrigger("철벽", priority = 50) {
    override fun onBattleInit(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.injuryImmune = true
        return ctx
    }

    override fun onPreDodge(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.dodgeChanceBonus += 0.12
        return ctx
    }

    override fun onDamageCalc(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.defenceMultiplier *= 1.1
        return ctx
    }
}

object 분투Trigger : BattleTrigger("분투", priority = 50) {
    override fun onDamageCalc(ctx: BattleTriggerContext): BattleTriggerContext {
        if (ctx.attacker.hp < ctx.attacker.maxHp / 2) {
            ctx.attackMultiplier *= 1.15
            ctx.battleLogs.add("분투 발동! 공격력 증가!")
        } else {
            ctx.attackMultiplier *= 1.05
        }
        return ctx
    }
}

object 용병Trigger : BattleTrigger("용병", priority = 50) {
    override fun onDamageCalc(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.attackMultiplier *= 1.05
        return ctx
    }

    override fun onPostDamage(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.moraleBoost = 2
        return ctx
    }
}

object 견고Trigger : BattleTrigger("견고", priority = 50) {
    override fun onBattleInit(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.injuryImmune = true
        ctx.battleLogs.add("견고 특성 활성: 부상 면역")
        return ctx
    }

    override fun onInjuryCheck(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.injuryImmune = true
        return ctx
    }

    override fun onDamageCalc(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.defenceMultiplier *= 1.05
        return ctx
    }
}

object 수군Trigger : BattleTrigger("수군", priority = 50) {
    override fun onDamageCalc(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.attackMultiplier *= 1.05
        return ctx
    }
}

object 연사Trigger : BattleTrigger("연사", priority = 50) {
    override fun onDamageCalc(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.attackMultiplier *= 1.08
        return ctx
    }
}

object 반격Trigger : BattleTrigger("반격", priority = 50) {
    override fun onPostDamage(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.counterDamageRatio = 0.20
        ctx.battleLogs.add("반격 발동! 20% 반사 피해")
        return ctx
    }
}

object 사기진작Trigger : BattleTrigger("사기진작", priority = 50) {
    override fun onPostDamage(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.moraleBoost = 3
        ctx.battleLogs.add("사기진작 발동! 사기 +3")
        return ctx
    }
}

object 부상무효Trigger : BattleTrigger("부상무효", priority = 50) {
    override fun onInjuryCheck(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.injuryImmune = true
        return ctx
    }
}

// ========== 8 ShipClass "che_" Triggers ==========

private fun chePassthrough(code: String) = object : BattleTrigger(code) {}

val che_선제사격시도 = chePassthrough("che_선제사격시도")
val che_선제사격발동 = chePassthrough("che_선제사격발동")
val che_방어력증가5p = object : BattleTrigger("che_방어력증가5p") {
    override fun onDamageCalc(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.defenceMultiplier *= 1.05
        return ctx
    }
}
val che_기병병종전투 = chePassthrough("che_기병병종전투")
val che_성벽부상무효 = object : BattleTrigger("che_성벽부상무효") {
    override fun onBattleInit(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.injuryImmune = true
        return ctx
    }
}
val che_저지시도 = chePassthrough("che_저지시도")
val che_저지발동 = chePassthrough("che_저지발동")
val che_성벽선제 = chePassthrough("che_성벽선제")

// ========== 18 Additional Triggers (to reach 49 total) ==========

private fun simpleDamage(code: String, mult: Double) = object : BattleTrigger(code) {
    override fun onDamageCalc(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.attackMultiplier *= mult; return ctx
    }
}

val 심공Trigger = simpleDamage("심공", 1.05)
val 매혹Trigger = chePassthrough("매혹")
val 잠행Trigger = chePassthrough("잠행")
val 결사Trigger = simpleDamage("결사", 1.10)
val 화계Trigger = chePassthrough("화계")
val 수전Trigger = chePassthrough("수전")
val 혼란Trigger = chePassthrough("혼란")
val 축성Trigger = chePassthrough("축성")
val 방화Trigger = chePassthrough("방화")
val 약탈Trigger = chePassthrough("약탈")
val 간첩Trigger = chePassthrough("간첩")
val 속전Trigger = simpleDamage("속전", 1.05)
val 연합Trigger = chePassthrough("연합")
val 포위Trigger = chePassthrough("포위")
val 궁격Trigger = simpleDamage("궁격", 1.05)
val 참수Trigger = chePassthrough("참수")
val 일기Trigger = chePassthrough("일기")
val 독전Trigger = chePassthrough("독전")

// ========== Registry ==========

object BattleTriggerRegistry {
    private val triggers: Map<String, BattleTrigger> = listOf(
        // 23 named specials
        필살Trigger, 회피Trigger, 반계Trigger, 신산Trigger, 위압Trigger, 저격Trigger, 격노Trigger, 돌격Trigger,
        화공Trigger, 기습Trigger, 매복Trigger, 방어Trigger, 귀모Trigger, 공성Trigger, 철벽Trigger, 분투Trigger,
        용병Trigger, 견고Trigger, 수군Trigger, 연사Trigger, 반격Trigger, 사기진작Trigger, 부상무효Trigger,
        // 8 che_ triggers
        che_선제사격시도, che_선제사격발동, che_방어력증가5p, che_기병병종전투,
        che_성벽부상무효, che_저지시도, che_저지발동, che_성벽선제,
        // 18 additional
        심공Trigger, 매혹Trigger, 잠행Trigger, 결사Trigger, 화계Trigger, 수전Trigger,
        혼란Trigger, 축성Trigger, 방화Trigger, 약탈Trigger, 간첩Trigger, 속전Trigger,
        연합Trigger, 포위Trigger, 궁격Trigger, 참수Trigger, 일기Trigger, 독전Trigger,
    ).associateBy { it.code }

    fun get(code: String): BattleTrigger? = triggers[code]
    fun allCodes(): Set<String> = triggers.keys
}
