package com.opensam.engine.war

import com.opensam.entity.City
import com.opensam.model.CrewType
import kotlin.math.ceil
import kotlin.math.round
import kotlin.random.Random

data class BattleResult(
    val attackerWon: Boolean,
    val attackerLogs: MutableList<String> = mutableListOf(),
    val defenderLogs: MutableList<String> = mutableListOf(),
    val attackerDamageDealt: Int = 0,
    val defenderDamageDealt: Int = 0,
    val cityOccupied: Boolean = false,
)

class BattleEngine {

    companion object {
        /** Legacy GameConst::$armperphase - base war power per phase. */
        const val ARM_PER_PHASE = 500.0
    }

    fun resolveBattle(
        attacker: WarUnitGeneral,
        defenders: List<WarUnit>,
        city: City,
        rng: Random,
        year: Int = 200,
        startYear: Int = 180,
    ): BattleResult {
        val logs = mutableListOf<String>()
        var totalAttackerDamage = 0
        var totalDefenderDamage = 0
        // C7: track damage dealt to accumulate level exp (PHP: damage/50)
        var attackerDamageDealtForExp = 0
        // Map each defender WarUnitGeneral to the damage dealt TO them (for their exp)
        val defenderDamageDealtForExp = mutableMapOf<WarUnitGeneral, Int>()
        var attackerWon = true
        var cityOccupied = false

        // Sort defenders by battle order (highest first)
        val sortedDefenders = defenders.sortedByDescending { it.calcBattleOrder() }.toMutableList()

        // Collect attacker triggers once (used across engagements)
        val attackerTriggers = collectTriggers(attacker)
        val attackerWarTriggers = collectWarUnitTriggers(attacker)

        // Track injury immunity from init triggers
        var attackerInjuryImmune = false
        var attackerRageActivationCount = 0

        val attackerCrewType = CrewType.fromCode(attacker.crewType)
        val maxPhase = attackerCrewType?.speed ?: 7
        var currentPhase = 0
        val cityUnit = WarUnitCity(city, year, startYear)
        var defenderIndex = 0
        var currentDefender: WarUnit? = if (sortedDefenders.isNotEmpty()) sortedDefenders[0] else null
        var inSiege = false
        var defenderInitialized = false

        // Unified phase loop: phase < maxPhase for generals, unlimited for siege
        while (currentPhase < maxPhase) {
            if (currentDefender == null) {
                if (inSiege) break
                currentDefender = cityUnit
                inSiege = true
                defenderInitialized = false
            }

            if (!defenderInitialized) {
                // PHP process_war.php: addTrain(1) for both sides at start of each new engagement
                attacker.train = (attacker.train + 1).coerceAtMost(110)
                if (currentDefender is WarUnitGeneral) {
                    currentDefender.train = (currentDefender.train + 1).coerceAtMost(110)
                }

                val defenderTriggers = collectTriggers(currentDefender)
                val defenderWarTriggers = collectWarUnitTriggers(currentDefender)

                val initCtx = BattleTriggerContext(
                    attacker = attacker, defender = currentDefender, rng = rng,
                    isVsCity = inSiege,
                )
                for (trigger in attackerTriggers) trigger.onBattleInit(initCtx)
                for (trigger in defenderTriggers) trigger.onBattleInit(initCtx)
                // WarUnitTrigger: onEngagementStart (once per new opponent)
                for (trigger in attackerWarTriggers) trigger.onEngagementStart(initCtx)
                for (trigger in defenderWarTriggers) trigger.onEngagementStart(initCtx)
                if (initCtx.injuryImmune) attackerInjuryImmune = true
                logs.addAll(initCtx.battleLogs)
                defenderInitialized = true
            }

            // WarUnitTrigger: onPreAttack (per phase before attack roll)
            val preAttackCtx = BattleTriggerContext(
                attacker = attacker, defender = currentDefender, rng = rng,
                phaseNumber = currentPhase, isVsCity = inSiege,
                rageActivationCount = attackerRageActivationCount,
            )
            for (trigger in attackerWarTriggers) trigger.onPreAttack(preAttackCtx)
            logs.addAll(preAttackCtx.battleLogs)

            // Execute one combat phase
            val phaseResult = executeCombatPhase(
                attacker, currentDefender, rng,
                phaseNumber = currentPhase,
                isVsCity = inSiege,
            )
            totalAttackerDamage += phaseResult.damage.first
            totalDefenderDamage += phaseResult.damage.second
            logs.addAll(phaseResult.logs)
            currentPhase++
            // C7: accumulate level exp: damage/50 per phase
            attackerDamageDealtForExp += phaseResult.damage.second
            if (currentDefender is WarUnitGeneral) {
                defenderDamageDealtForExp[currentDefender] =
                    (defenderDamageDealtForExp[currentDefender] ?: 0) + phaseResult.damage.first
            }

            // WarUnitTrigger: onPostDamage (per phase after damage)
            val postDamageCtx = BattleTriggerContext(
                attacker = attacker, defender = currentDefender, rng = rng,
                phaseNumber = currentPhase - 1, isVsCity = inSiege,
                rageActivationCount = attackerRageActivationCount,
            )
            for (trigger in attackerWarTriggers) trigger.onPostDamage(postDamageCtx)
            logs.addAll(postDamageCtx.battleLogs)
            attackerRageActivationCount = postDamageCtx.rageActivationCount

            // Attacker continuation check
            val attackerContinuation = attacker.continueWar()
            if (!attackerContinuation.canContinue) {
                logs.add(
                    if (attackerContinuation.isRiceShortage) {
                        "<Y>${attacker.name}</>이(가) 쌀 부족으로 퇴각합니다."
                    } else {
                        "<Y>${attacker.name}</>이(가) 병력 소진으로 퇴각합니다."
                    }
                )
                attackerWon = false
                break
            }

            // Defender continuation check
            val defenderCanContinue = if (currentDefender is WarUnitCity) {
                // City: only HP check (no rice). In siege: continues while HP > 0
                if (inSiege) currentDefender.hp > 0 else false
            } else if (currentDefender is WarUnitGeneral) {
                currentDefender.continueWar().canContinue
            } else {
                currentDefender.isAlive
            }

            if (!defenderCanContinue) {
                // WarUnitTrigger: onPostRound (after all phases with one opponent)
                val roundCtx = BattleTriggerContext(
                    attacker = attacker, defender = currentDefender, rng = rng,
                    isVsCity = inSiege,
                )
                for (trigger in attackerWarTriggers) trigger.onPostRound(roundCtx)
                logs.addAll(roundCtx.battleLogs)

                if (currentDefender is WarUnitCity && inSiege) {
                    // City walls defeated — city conquered!
                    cityOccupied = true
                    cityUnit.applyResults()
                    logs.add("<R>${city.name}</> 점령!")
                    break
                } else if (currentDefender is WarUnitGeneral) {
                    val defenderContinuation = currentDefender.continueWar()
                    logs.add(
                        if (defenderContinuation.isRiceShortage) {
                            "<Y>${currentDefender.name}</>이(가) 쌀 부족으로 퇴각합니다."
                        } else {
                            "<Y>${currentDefender.name}</>이(가) 병력 소진으로 퇴각합니다."
                        }
                    )
                }

                // Move to next defender
                defenderIndex++
                if (defenderIndex < sortedDefenders.size) {
                    currentDefender = sortedDefenders[defenderIndex]
                    defenderInitialized = false
                } else {
                    // All generals eliminated — switch to siege
                    currentDefender = null  // Will trigger siege on next iteration
                    defenderInitialized = false
                }
            }
        }

        // Legacy parity: siege continues beyond maxPhase — no phase cap for siege
        if (attackerWon && inSiege && !cityOccupied && attacker.continueWar().canContinue && cityUnit.hp > 0) {
            while (attacker.continueWar().canContinue && cityUnit.hp > 0) {
                val phaseResult = executeCombatPhase(attacker, cityUnit, rng, phaseNumber = currentPhase, isVsCity = true)
                totalAttackerDamage += phaseResult.damage.first
                totalDefenderDamage += phaseResult.damage.second
                logs.addAll(phaseResult.logs)
                currentPhase++
                // C7: accumulate exp (siege: no general defender, only attacker gains)
                attackerDamageDealtForExp += phaseResult.damage.second

                val attackerContinuation = attacker.continueWar()
                if (!attackerContinuation.canContinue) {
                    logs.add(
                        if (attackerContinuation.isRiceShortage) {
                            "<Y>${attacker.name}</>이(가) 쌀 부족으로 퇴각합니다."
                        } else {
                            "<Y>${attacker.name}</>이(가) 병력 소진으로 퇴각합니다."
                        }
                    )
                    attackerWon = false
                    break
                }
                if (cityUnit.hp <= 0) {
                    cityOccupied = true
                    cityUnit.applyResults()
                    logs.add("<R>${city.name}</> 점령!")
                    break
                }
            }
        }

        // If maxPhase reached without entering siege but all defenders down, try siege
        if (attackerWon && !inSiege && !cityOccupied && attacker.continueWar().canContinue) {
            val allDefendersDown = sortedDefenders.all { it is WarUnitGeneral && !it.continueWar().canContinue }
            if (allDefendersDown) {
                inSiege = true
                val siegeInitCtx = BattleTriggerContext(attacker = attacker, defender = cityUnit, rng = rng, isVsCity = true)
                for (trigger in attackerTriggers) trigger.onBattleInit(siegeInitCtx)
                if (siegeInitCtx.injuryImmune) attackerInjuryImmune = true
                logs.addAll(siegeInitCtx.battleLogs)

                while (attacker.continueWar().canContinue && cityUnit.hp > 0) {
                    val phaseResult = executeCombatPhase(attacker, cityUnit, rng, phaseNumber = currentPhase, isVsCity = true)
                    totalAttackerDamage += phaseResult.damage.first
                    totalDefenderDamage += phaseResult.damage.second
                    logs.addAll(phaseResult.logs)
                    currentPhase++
                    // C7: accumulate exp (siege: only attacker gains)
                    attackerDamageDealtForExp += phaseResult.damage.second

                    val attackerContinuation = attacker.continueWar()
                    if (!attackerContinuation.canContinue) {
                        logs.add(
                            if (attackerContinuation.isRiceShortage) {
                                "<Y>${attacker.name}</>이(가) 쌀 부족으로 퇴각합니다."
                            } else {
                                "<Y>${attacker.name}</>이(가) 병력 소진으로 퇴각합니다."
                            }
                        )
                        attackerWon = false
                        break
                    }
                    if (cityUnit.hp <= 0) {
                        cityOccupied = true
                        cityUnit.applyResults()
                        logs.add("<R>${city.name}</> 점령!")
                        break
                    }
                }
            }
        }

        // C7: Apply battle experience (PHP process_war.php parity)
        // Per-phase level exp: damage/50; defenders get 0.8x multiplier
        attacker.pendingLevelExp += attackerDamageDealtForExp / 50
        for ((defUnit, damageReceived) in defenderDamageDealtForExp) {
            defUnit.pendingLevelExp += (damageReceived / 50 * 0.8).toInt()
        }

        // City capture: +1000 exp to attacker
        if (cityOccupied) {
            attacker.pendingLevelExp += 1000
        }

        // Win/lose stat exp (+1 based on armType) and atmos boost
        if (attackerWon && attacker.isAlive) {
            // PHP addWin(): atmos *= 1.1, addStatExp(1)
            attacker.atmos = (attacker.atmos * 1.1).toInt().coerceAtMost(100)
            attacker.pendingStatExp += 1
            // Defenders that survived also get atmos *= 1.05 and stat exp on their side
            for (def in sortedDefenders) {
                if (def is WarUnitGeneral) {
                    def.atmos = (def.atmos * 1.05).toInt().coerceAtMost(100)
                    def.pendingStatExp += 1
                }
            }
        } else {
            // Attacker lost: defenders won — apply win bonuses to defenders
            for (def in sortedDefenders) {
                if (def is WarUnitGeneral) {
                    def.atmos = (def.atmos * 1.1).toInt().coerceAtMost(100)
                    def.pendingStatExp += 1
                }
            }
            attacker.atmos = (attacker.atmos * 1.05).toInt().coerceAtMost(100)
            attacker.pendingStatExp += 1
        }

        // Injury check: fire onInjuryCheck triggers before wound roll
        val injuryCtx = BattleTriggerContext(attacker = attacker, defender = attacker, rng = rng)
        for (trigger in attackerTriggers) trigger.onInjuryCheck(injuryCtx)
        val effectiveInjuryImmune = attackerInjuryImmune || injuryCtx.injuryImmune

        // Legacy parity (PHP WarUnitGeneral::tryWound):
        // Flat 5% chance (nextBool(0.05)), injury random [10, 80], capped at 80
        if (!effectiveInjuryImmune && attacker.isAlive) {
            if (rng.nextDouble() < 0.05) {
                val woundAmount = rng.nextInt(10, 81)  // [10, 80] inclusive
                attacker.injury = (attacker.injury + woundAmount).coerceAtMost(80)
                logs.add("<Y>${attacker.name}</>이(가) 부상을 입었습니다.")
            }
        }

        // C8: PHP process_war.php calls tryWound() for BOTH attacker AND all defenders.
        // Apply injury check to each defender general (same 5% chance, [10,80] range, cap 80).
        for (def in sortedDefenders) {
            if (def is WarUnitGeneral && def.isAlive) {
                if (rng.nextDouble() < 0.05) {
                    val woundAmount = rng.nextInt(10, 81)  // [10, 80] inclusive
                    def.injury = (def.injury + woundAmount).coerceAtMost(80)
                    logs.add("<Y>${def.name}</>이(가) 부상을 입었습니다.")
                }
            }
        }

        attacker.applyResults()
        for (def in sortedDefenders) {
            if (def is WarUnitGeneral) def.applyResults()
        }
        // Always sync city battle damage (wall/def) back to entity,
        // even when city is not conquered — damage must persist across turns.
        // applyResults() is idempotent if already called on conquest.
        cityUnit.applyResults()

        return BattleResult(
            attackerWon = attackerWon && attacker.isAlive,
            attackerLogs = logs,
            defenderLogs = logs.toMutableList(),
            attackerDamageDealt = totalAttackerDamage,
            defenderDamageDealt = totalDefenderDamage,
            cityOccupied = cityOccupied,
        )
    }

    data class BattleResultWithPhases(
        val battleResult: BattleResult,
        val phaseDetails: List<com.opensam.dto.BattlePhaseDetail>,
    )

    fun resolveBattleWithPhases(
        attacker: WarUnitGeneral,
        defenders: List<WarUnit>,
        city: City,
        rng: Random,
        year: Int = 200,
        startYear: Int = 180,
    ): BattleResultWithPhases {
        val phaseDetails = mutableListOf<com.opensam.dto.BattlePhaseDetail>()
        val logs = mutableListOf<String>()
        var totalAttackerDamage = 0
        var totalDefenderDamage = 0
        var attackerDamageDealtForExp = 0
        val defenderDamageDealtForExp = mutableMapOf<WarUnitGeneral, Int>()
        var attackerWon = true
        var cityOccupied = false

        val sortedDefenders = defenders.sortedByDescending { it.calcBattleOrder() }.toMutableList()
        val attackerTriggers = collectTriggers(attacker)
        val attackerWarTriggers = collectWarUnitTriggers(attacker)
        var attackerInjuryImmune = false
        var attackerRageActivationCount = 0

        val attackerCrewType = CrewType.fromCode(attacker.crewType)
        val maxPhase = attackerCrewType?.speed ?: 7
        var currentPhase = 0
        val cityUnit = WarUnitCity(city, year, startYear)
        var defenderIndex = 0
        var currentDefender: WarUnit? = if (sortedDefenders.isNotEmpty()) sortedDefenders[0] else null
        var inSiege = false
        var defenderInitialized = false

        while (currentPhase < maxPhase) {
            if (currentDefender == null) {
                if (inSiege) break
                currentDefender = cityUnit
                inSiege = true
                defenderInitialized = false
            }

            if (!defenderInitialized) {
                attacker.train = (attacker.train + 1).coerceAtMost(110)
                if (currentDefender is WarUnitGeneral) {
                    currentDefender.train = (currentDefender.train + 1).coerceAtMost(110)
                }
                val defenderTriggers = collectTriggers(currentDefender)
                val defenderWarTriggers = collectWarUnitTriggers(currentDefender)
                val initCtx = BattleTriggerContext(attacker = attacker, defender = currentDefender, rng = rng, isVsCity = inSiege)
                for (trigger in attackerTriggers) trigger.onBattleInit(initCtx)
                for (trigger in defenderTriggers) trigger.onBattleInit(initCtx)
                // WarUnitTrigger: onEngagementStart (once per new opponent)
                for (trigger in attackerWarTriggers) trigger.onEngagementStart(initCtx)
                for (trigger in defenderWarTriggers) trigger.onEngagementStart(initCtx)
                if (initCtx.injuryImmune) attackerInjuryImmune = true
                logs.addAll(initCtx.battleLogs)
                defenderInitialized = true
            }

            // WarUnitTrigger: onPreAttack (per phase before attack roll)
            val preAttackCtx = BattleTriggerContext(
                attacker = attacker, defender = currentDefender!!, rng = rng,
                phaseNumber = currentPhase, isVsCity = inSiege,
                rageActivationCount = attackerRageActivationCount,
            )
            for (trigger in attackerWarTriggers) trigger.onPreAttack(preAttackCtx)
            logs.addAll(preAttackCtx.battleLogs)

            val attackerHpBefore = attacker.hp
            val defenderHpBefore = currentDefender.hp

            val phaseResult = executeCombatPhase(attacker, currentDefender, rng, phaseNumber = currentPhase, isVsCity = inSiege)
            totalAttackerDamage += phaseResult.damage.first
            totalDefenderDamage += phaseResult.damage.second
            logs.addAll(phaseResult.logs)

            phaseDetails.add(com.opensam.dto.BattlePhaseDetail(
                phase = currentPhase,
                attackerHp = attackerHpBefore,
                defenderHp = defenderHpBefore,
                attackerDamage = phaseResult.damage.second,
                defenderDamage = phaseResult.damage.first,
                defenderIndex = defenderIndex,
                events = phaseResult.logs.toList(),
            ))

            currentPhase++
            attackerDamageDealtForExp += phaseResult.damage.second
            if (currentDefender is WarUnitGeneral) {
                defenderDamageDealtForExp[currentDefender] =
                    (defenderDamageDealtForExp[currentDefender] ?: 0) + phaseResult.damage.first
            }

            // WarUnitTrigger: onPostDamage (per phase after damage)
            val postDamageCtx = BattleTriggerContext(
                attacker = attacker, defender = currentDefender, rng = rng,
                phaseNumber = currentPhase - 1, isVsCity = inSiege,
                rageActivationCount = attackerRageActivationCount,
            )
            for (trigger in attackerWarTriggers) trigger.onPostDamage(postDamageCtx)
            logs.addAll(postDamageCtx.battleLogs)
            attackerRageActivationCount = postDamageCtx.rageActivationCount

            val attackerContinuation = attacker.continueWar()
            if (!attackerContinuation.canContinue) {
                logs.add(
                    if (attackerContinuation.isRiceShortage) {
                        "<Y>${attacker.name}</>이(가) 쌀 부족으로 퇴각합니다."
                    } else {
                        "<Y>${attacker.name}</>이(가) 병력 소진으로 퇴각합니다."
                    }
                )
                attackerWon = false
                break
            }

            val defenderCanContinue = if (currentDefender is WarUnitCity) {
                if (inSiege) currentDefender.hp > 0 else false
            } else if (currentDefender is WarUnitGeneral) {
                currentDefender.continueWar().canContinue
            } else {
                currentDefender.isAlive
            }

            if (!defenderCanContinue) {
                // WarUnitTrigger: onPostRound (after all phases with one opponent)
                val roundCtx = BattleTriggerContext(
                    attacker = attacker, defender = currentDefender, rng = rng,
                    isVsCity = inSiege,
                )
                for (trigger in attackerWarTriggers) trigger.onPostRound(roundCtx)
                logs.addAll(roundCtx.battleLogs)

                if (currentDefender is WarUnitCity && inSiege) {
                    cityOccupied = true
                    cityUnit.applyResults()
                    logs.add("<R>${city.name}</> 점령!")
                    break
                } else if (currentDefender is WarUnitGeneral) {
                    val defenderContinuation = currentDefender.continueWar()
                    logs.add(
                        if (defenderContinuation.isRiceShortage) {
                            "<Y>${currentDefender.name}</>이(가) 쌀 부족으로 퇴각합니다."
                        } else {
                            "<Y>${currentDefender.name}</>이(가) 병력 소진으로 퇴각합니다."
                        }
                    )
                }
                defenderIndex++
                if (defenderIndex < sortedDefenders.size) {
                    currentDefender = sortedDefenders[defenderIndex]
                    defenderInitialized = false
                } else {
                    currentDefender = null
                    defenderInitialized = false
                }
            }
        }

        if (attackerWon && inSiege && !cityOccupied && attacker.continueWar().canContinue && cityUnit.hp > 0) {
            while (attacker.continueWar().canContinue && cityUnit.hp > 0) {
                val attackerHpBefore = attacker.hp
                val defenderHpBefore = cityUnit.hp
                val phaseResult = executeCombatPhase(attacker, cityUnit, rng, phaseNumber = currentPhase, isVsCity = true)
                totalAttackerDamage += phaseResult.damage.first
                totalDefenderDamage += phaseResult.damage.second
                logs.addAll(phaseResult.logs)
                phaseDetails.add(com.opensam.dto.BattlePhaseDetail(
                    phase = currentPhase,
                    attackerHp = attackerHpBefore,
                    defenderHp = defenderHpBefore,
                    attackerDamage = phaseResult.damage.second,
                    defenderDamage = phaseResult.damage.first,
                    defenderIndex = defenderIndex,
                    events = phaseResult.logs.toList(),
                ))
                currentPhase++
                attackerDamageDealtForExp += phaseResult.damage.second

                val attackerContinuation = attacker.continueWar()
                if (!attackerContinuation.canContinue) {
                    logs.add(
                        if (attackerContinuation.isRiceShortage) {
                            "<Y>${attacker.name}</>이(가) 쌀 부족으로 퇴각합니다."
                        } else {
                            "<Y>${attacker.name}</>이(가) 병력 소진으로 퇴각합니다."
                        }
                    )
                    attackerWon = false
                    break
                }
                if (cityUnit.hp <= 0) {
                    cityOccupied = true
                    cityUnit.applyResults()
                    logs.add("<R>${city.name}</> 점령!")
                    break
                }
            }
        }

        if (attackerWon && !inSiege && !cityOccupied && attacker.continueWar().canContinue) {
            val allDefendersDown = sortedDefenders.all { it is WarUnitGeneral && !it.continueWar().canContinue }
            if (allDefendersDown) {
                inSiege = true
                val siegeInitCtx = BattleTriggerContext(attacker = attacker, defender = cityUnit, rng = rng, isVsCity = true)
                for (trigger in attackerTriggers) trigger.onBattleInit(siegeInitCtx)
                if (siegeInitCtx.injuryImmune) attackerInjuryImmune = true
                logs.addAll(siegeInitCtx.battleLogs)

                while (attacker.continueWar().canContinue && cityUnit.hp > 0) {
                    val attackerHpBefore = attacker.hp
                    val defenderHpBefore = cityUnit.hp
                    val phaseResult = executeCombatPhase(attacker, cityUnit, rng, phaseNumber = currentPhase, isVsCity = true)
                    totalAttackerDamage += phaseResult.damage.first
                    totalDefenderDamage += phaseResult.damage.second
                    logs.addAll(phaseResult.logs)
                    phaseDetails.add(com.opensam.dto.BattlePhaseDetail(
                        phase = currentPhase,
                        attackerHp = attackerHpBefore,
                        defenderHp = defenderHpBefore,
                        attackerDamage = phaseResult.damage.second,
                        defenderDamage = phaseResult.damage.first,
                        defenderIndex = defenderIndex,
                        events = phaseResult.logs.toList(),
                    ))
                    currentPhase++
                    attackerDamageDealtForExp += phaseResult.damage.second

                    val attackerContinuation = attacker.continueWar()
                    if (!attackerContinuation.canContinue) {
                        logs.add(
                            if (attackerContinuation.isRiceShortage) {
                                "<Y>${attacker.name}</>이(가) 쌀 부족으로 퇴각합니다."
                            } else {
                                "<Y>${attacker.name}</>이(가) 병력 소진으로 퇴각합니다."
                            }
                        )
                        attackerWon = false
                        break
                    }
                    if (cityUnit.hp <= 0) {
                        cityOccupied = true
                        cityUnit.applyResults()
                        logs.add("<R>${city.name}</> 점령!")
                        break
                    }
                }
            }
        }

        attacker.pendingLevelExp += attackerDamageDealtForExp / 50
        for ((defUnit, damageReceived) in defenderDamageDealtForExp) {
            defUnit.pendingLevelExp += (damageReceived / 50 * 0.8).toInt()
        }
        if (cityOccupied) attacker.pendingLevelExp += 1000

        if (attackerWon && attacker.isAlive) {
            attacker.atmos = (attacker.atmos * 1.1).toInt().coerceAtMost(100)
            attacker.pendingStatExp += 1
            for (def in sortedDefenders) {
                if (def is WarUnitGeneral) {
                    def.atmos = (def.atmos * 1.05).toInt().coerceAtMost(100)
                    def.pendingStatExp += 1
                }
            }
        } else {
            for (def in sortedDefenders) {
                if (def is WarUnitGeneral) {
                    def.atmos = (def.atmos * 1.1).toInt().coerceAtMost(100)
                    def.pendingStatExp += 1
                }
            }
            attacker.atmos = (attacker.atmos * 1.05).toInt().coerceAtMost(100)
            attacker.pendingStatExp += 1
        }

        val injuryCtx = BattleTriggerContext(attacker = attacker, defender = attacker, rng = rng)
        for (trigger in attackerTriggers) trigger.onInjuryCheck(injuryCtx)
        val effectiveInjuryImmune = attackerInjuryImmune || injuryCtx.injuryImmune

        if (!effectiveInjuryImmune && attacker.isAlive) {
            if (rng.nextDouble() < 0.05) {
                val woundAmount = rng.nextInt(10, 81)
                attacker.injury = (attacker.injury + woundAmount).coerceAtMost(80)
                logs.add("<Y>${attacker.name}</>이(가) 부상을 입었습니다.")
            }
        }
        for (def in sortedDefenders) {
            if (def is WarUnitGeneral && def.isAlive) {
                if (rng.nextDouble() < 0.05) {
                    val woundAmount = rng.nextInt(10, 81)
                    def.injury = (def.injury + woundAmount).coerceAtMost(80)
                    logs.add("<Y>${def.name}</>이(가) 부상을 입었습니다.")
                }
            }
        }

        attacker.applyResults()
        for (def in sortedDefenders) {
            if (def is WarUnitGeneral) def.applyResults()
        }
        cityUnit.applyResults()

        val battleResult = BattleResult(
            attackerWon = attackerWon && attacker.isAlive,
            attackerLogs = logs,
            defenderLogs = logs.toMutableList(),
            attackerDamageDealt = totalAttackerDamage,
            defenderDamageDealt = totalDefenderDamage,
            cityOccupied = cityOccupied,
        )
        return BattleResultWithPhases(battleResult = battleResult, phaseDetails = phaseDetails)
    }

    /**
     * War power result with bidirectional multipliers.
     * Legacy parity: PHP returns both myWarPowerMultiply and opposeWarPowerMultiply
     * from getWarPowerMultiplier(), applying them bidirectionally.
     */
    data class WarPowerResult(
        val warPower: Double,
        val opposeWarPowerMultiply: Double = 1.0,
    )

    /**
     * Compute war power for one side attacking another.
     * Legacy: WarUnit::computeWarPower() + WarUnitGeneral::computeWarPower()
     *
     * Formula: (armperphase + myAttack - opDefence) × atmos/train × expLevel × random
     *
     * Returns WarPowerResult with the computed power and any oppose multiplier
     * from crew type coefficients (bidirectional application per PHP parity).
     */
    private fun computeWarPower(attacker: WarUnit, defender: WarUnit, rng: Random): WarPowerResult {
        val myAttack = attacker.getBaseAttack()
        val opDefence = defender.getBaseDefence()

        var warPower = ARM_PER_PHASE + myAttack - opDefence

        // Floor guarantee: minimum ~50 war power
        if (warPower < 100.0) {
            warPower = maxOf(0.0, warPower)
            warPower = (warPower + 100.0) / 2.0
            warPower = warPower + rng.nextDouble() * (100.0 - warPower)
        }

        warPower *= attacker.atmos.toDouble()
        warPower /= maxOf(1.0, defender.train.toDouble())

        // Legacy: dex is looked up by attacker's crew type arm type for both sides
        val attackerCrewTypeObj = CrewType.fromCode(attacker.crewType)
        val armType = attackerCrewTypeObj?.armType ?: com.opensam.model.ArmType.FOOTMAN
        val attackerDex = attacker.getDexForArmType(armType)
        val defenderDex = defender.getDexForArmType(armType)
        warPower *= getDexLog(attackerDex, defenderDex)

        // Legacy parity (PHP WarUnitGeneral.php):
        // [$specialMyWarPowerMultiply, $specialOpposeWarPowerMultiply] = $this->general->getWarPowerMultiplier($this);
        // $warPower *= $specialMyWarPowerMultiply;
        // $opposeWarPowerMultiply *= $specialOpposeWarPowerMultiply;
        // Both attacker's attack coef AND defender's defence coef are applied to THIS side's power,
        // AND the opponent's coefficients are returned to be applied to the OTHER side's power.
        val defenderCrewType = CrewType.fromCode(defender.crewType)
        val attackTypeCoef = if (attackerCrewTypeObj != null && defenderCrewType != null) {
            attackerCrewTypeObj.getAttackCoef(defenderCrewType)
        } else 1.0
        val defenceTypeCoef = if (defenderCrewType != null && attackerCrewTypeObj != null) {
            defenderCrewType.getDefenceCoef(attackerCrewTypeObj)
        } else 1.0

        // Apply own attack coefficient to own war power
        warPower *= attackTypeCoef

        // The defence coefficient of the defender against this attacker becomes
        // the oppose multiplier — applied to the OTHER side's war power calculation
        val opposeWarPowerMultiply = defenceTypeCoef

        // Experience level scaling (WarUnitGeneral only)
        // Legacy: own warPower boosted AND opposeWarPowerMultiply reduced
        var expOpposeMultiply = 1.0
        if (attacker is WarUnitGeneral) {
            val expLevel = attacker.general.expLevel.toInt()
            if (defender is WarUnitCity) {
                warPower *= 1.0 + expLevel / 600.0
            } else {
                val expFactor = maxOf(0.01, 1.0 - expLevel / 300.0)
                warPower /= expFactor
                expOpposeMultiply = expFactor
            }
        }

        return WarPowerResult(
            warPower = maxOf(1.0, round(warPower)),
            opposeWarPowerMultiply = opposeWarPowerMultiply * expOpposeMultiply,
        )
    }

    internal fun collectTriggers(unit: WarUnit): List<BattleTrigger> {
        if (unit !is WarUnitGeneral) return emptyList()
        return listOfNotNull(
            BattleTriggerRegistry.get(unit.general.specialCode),
            BattleTriggerRegistry.get(unit.general.special2Code),
        ).sortedBy { it.priority }
    }

    internal fun collectWarUnitTriggers(unit: WarUnit): List<WarUnitTrigger> {
        if (unit !is WarUnitGeneral) return emptyList()
        return listOfNotNull(
            WarUnitTriggerRegistry.get(unit.general.specialCode),
            WarUnitTriggerRegistry.get(unit.general.special2Code),
        ).sortedBy { it.priority }
    }

    data class PhaseResult(
        val damage: Pair<Int, Int>,
        val logs: List<String>,
    )

    private fun executeCombatPhase(
        attacker: WarUnit,
        defender: WarUnit,
        rng: Random,
        phaseNumber: Int = 0,
        isVsCity: Boolean = false,
    ): PhaseResult {
        attacker.beginPhase()
        defender.beginPhase()

        val attackerTriggers = collectTriggers(attacker)
        val defenderTriggers = collectTriggers(defender)
        val ctx = BattleTriggerContext(
            attacker = attacker,
            defender = defender,
            rng = rng,
            phaseNumber = phaseNumber,
            isVsCity = isVsCity,
        )

        // Compute war power for each side (legacy: each side independently)
        // PHP parity: bidirectional coefficient application
        // Each side computes its own war power AND an oppose multiplier that affects the other side
        val attackerResult = computeWarPower(attacker, defender, rng)
        val defenderResult = computeWarPower(defender, attacker, rng)

        val attackerVariedWarPower = attackerResult.warPower * (0.9 + rng.nextDouble() * 0.2)
        val defenderVariedWarPower = defenderResult.warPower * (0.9 + rng.nextDouble() * 0.2)

        // Apply oppose multipliers bidirectionally (PHP: $this->oppose->setWarPowerMultiply)
        // attackerResult.opposeWarPowerMultiply = defender.defenceCoef(attacker.armType) → boosts attacker damage
        // defenderResult.opposeWarPowerMultiply = attacker.defenceCoef(defender.armType) → boosts defender damage
        var attackerDamage = (attackerVariedWarPower * attackerResult.opposeWarPowerMultiply).toInt().coerceAtLeast(1)
        var defenderDamage = (defenderVariedWarPower * defenderResult.opposeWarPowerMultiply).toInt().coerceAtLeast(1)

        // PRE triggers: modify chances before rolls (legacy: 시도)
        for (trigger in attackerTriggers) trigger.onPreCritical(ctx)
        for (trigger in defenderTriggers) trigger.onPreDodge(ctx)
        for (trigger in attackerTriggers) trigger.onPreMagic(ctx)
        for (trigger in defenderTriggers) trigger.onPreMagic(ctx)

        // Critical hit roll (legacy: 필살시도/발동)
        // Legacy WarUnit::criticalDamage() returns random in [1.3, 2.0)
        if (rng.nextDouble() < attacker.criticalChance + ctx.criticalChanceBonus) {
            val critMultiplier = 1.3 + rng.nextDouble() * 0.7  // [1.3, 2.0)
            attackerDamage = (attackerDamage * critMultiplier).toInt()
            ctx.criticalActivated = true
            // POST critical (legacy: 필살발동)
            for (trigger in attackerTriggers) trigger.onPostCritical(ctx)
        }

        // Dodge roll (legacy: 회피시도/발동)
        // Legacy: avoidRatio *= 0.75 when opponent is footman
        var effectiveDodgeChance = defender.dodgeChance + ctx.dodgeChanceBonus
        val attackerCrewObj = com.opensam.model.CrewType.fromCode(attacker.crewType)
        if (attackerCrewObj?.armType == com.opensam.model.ArmType.FOOTMAN) {
            effectiveDodgeChance *= 0.75
        }
        if (!ctx.dodgeDisabled && rng.nextDouble() < effectiveDodgeChance) {
            attackerDamage = (attackerDamage * 0.3).toInt()
            ctx.dodgeActivated = true
            // POST dodge (legacy: 회피발동)
            for (trigger in defenderTriggers) trigger.onPostDodge(ctx)
        }

        // Magic/stratagem roll (legacy: 계략시도/발동/실패)
        val totalMagicChance = attacker.magicChance + ctx.magicChanceBonus
        if (totalMagicChance > 0) {
            if (rng.nextDouble() < totalMagicChance) {
                // Stratagem success (legacy: 계략발동)
                val magicDamage = (attacker.intel * 2 * attacker.magicDamageMultiplier * ctx.magicDamageMultiplier).toInt()
                attackerDamage += magicDamage
                ctx.magicActivated = true
                for (trigger in attackerTriggers) trigger.onPostMagic(ctx)
            } else {
                // Stratagem failure (legacy: 계략실패)
                ctx.magicFailed = true
                for (trigger in attackerTriggers) trigger.onMagicFail(ctx)
                if (ctx.magicFailDamage > 0) {
                    defenderDamage += ctx.magicFailDamage.toInt()
                }
            }
        }

        // Defender 반계 check (after magic resolved)
        if (ctx.magicActivated) {
            for (trigger in defenderTriggers) trigger.onPostMagic(ctx)
            if (ctx.magicReflected) {
                val reflectedDamage = (attacker.intel * 2 * attacker.magicDamageMultiplier * 0.3).toInt()
                defenderDamage += reflectedDamage
            }
        }

        // Damage calc triggers
        for (trigger in attackerTriggers) trigger.onDamageCalc(ctx)
        attackerDamage = (attackerDamage * ctx.attackMultiplier).toInt()

        // Defence multiplier from defender triggers
        for (trigger in defenderTriggers) trigger.onDamageCalc(ctx)
        if (ctx.defenceMultiplier != 1.0) {
            attackerDamage = (attackerDamage / ctx.defenceMultiplier).toInt()
        }

        ctx.attackerDamage = attackerDamage
        ctx.defenderDamage = defenderDamage

        val attackerHP = attacker.hp
        val defenderHP = defender.hp
        var deadAttacker = defenderDamage.toDouble()
        var deadDefender = attackerDamage.toDouble()

        if (deadAttacker > attackerHP || deadDefender > defenderHP) {
            val deadAttackerRatio = deadAttacker / maxOf(1, attackerHP)
            val deadDefenderRatio = deadDefender / maxOf(1, defenderHP)

            if (deadDefenderRatio > deadAttackerRatio) {
                deadAttacker /= deadDefenderRatio
                deadDefender = defenderHP.toDouble()
            } else {
                deadDefender /= deadAttackerRatio
                deadAttacker = attackerHP.toDouble()
            }
        }

        defenderDamage = minOf(ceil(deadAttacker).toInt(), attackerHP)
        attackerDamage = minOf(ceil(deadDefender).toInt(), defenderHP)

        ctx.attackerDamage = attackerDamage
        ctx.defenderDamage = defenderDamage

        // Apply damage
        defender.takeDamage(attackerDamage)
        attacker.takeDamage(defenderDamage)

        // Snipe wound application (legacy: 저격발동 applies wound)
        if (ctx.snipeActivated && defender is WarUnitGeneral) {
            defender.injury = (defender.injury + ctx.snipeWoundAmount).coerceAtMost(80)
        }

        // Post damage triggers (counter, morale)
        for (trigger in attackerTriggers) trigger.onPostDamage(ctx)
        for (trigger in defenderTriggers) trigger.onPostDamage(ctx)

        // Apply counter damage (legacy: 반격)
        if (ctx.counterDamageRatio > 0) {
            val counterDamage = (attackerDamage * ctx.counterDamageRatio).toInt()
            attacker.takeDamage(counterDamage)
        }

        // Apply morale boost (legacy: 사기진작)
        if (ctx.moraleBoost > 0 && attacker is WarUnitGeneral) {
            attacker.atmos = (attacker.atmos + ctx.moraleBoost).coerceAtMost(100)
        }

        // Rice consumption (generals only)
        if (attacker is WarUnitGeneral) {
            attacker.consumeRice(
                damageDealt = attackerDamage,
                isAttacker = true,
                vsCity = isVsCity,
            )
        }
        if (defender is WarUnitGeneral) {
            defender.consumeRice(
                damageDealt = defenderDamage,
                isAttacker = false,
                vsCity = isVsCity,
            )
        }

        // Morale loss
        if (defender is WarUnitGeneral) {
            defender.atmos = (defender.atmos - 3).coerceAtLeast(0)
        }
        if (attacker is WarUnitGeneral) {
            attacker.atmos = (attacker.atmos - 1).coerceAtLeast(0)
        }

        return PhaseResult(
            damage = Pair(attackerDamage, defenderDamage),
            logs = ctx.battleLogs.toList(),
        )
    }
}
