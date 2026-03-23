package com.openlogh.engine

import kotlin.math.max
import kotlin.random.Random

object TournamentBattle {

    const val TOURNAMENT_STRENGTH = 1
    const val TOURNAMENT_TOTAL = 2

    data class TournamentStats(
        val attack: Double,
        val defense: Double,
        val agility: Double,
    )

    data class TournamentParticipant(
        val id: Long,
        val name: String,
        val stats: TournamentStats,
        val level: Int,
    )

    data class TournamentBattleContext(
        val openYear: Int,
        val openMonth: Int,
        val stage: Int,
        val phase: Int,
        val matchIndex: Int,
    )

    data class TournamentBattleInput(
        val type: Int,
        val battleType: Int,
        val attacker: TournamentParticipant,
        val defender: TournamentParticipant,
        val context: TournamentBattleContext,
        val baseSeed: String,
    )

    data class TotalDamage(val attacker: Int, val defender: Int)

    data class TournamentLogEntry(
        val round: Int,
        val attackerDamage: Int,
        val defenderDamage: Int,
    )

    data class TournamentBattleResult(
        val winnerId: Long?,
        val loserId: Long?,
        val rounds: Int,
        val totalDamage: TotalDamage,
        val draw: Boolean,
        val log: String,
        val logEntries: List<TournamentLogEntry>,
    )

    fun resolveTournamentBattle(input: TournamentBattleInput): TournamentBattleResult {
        val seed = "${input.baseSeed}-${input.context.stage}-${input.context.phase}-${input.context.matchIndex}"
        val rng = Random(seed.hashCode().toLong())

        val atkPower = getPower(input.attacker, input.type)
        val defPower = getPower(input.defender, input.type)

        var attackerHp = input.attacker.level * 100
        var defenderHp = input.defender.level * 100

        var totalAtkDamage = 0
        var totalDefDamage = 0
        val entries = mutableListOf<TournamentLogEntry>()
        val logBuilder = StringBuilder()

        val maxRounds = 50
        while (entries.size < maxRounds && attackerHp > 0 && defenderHp > 0) {
            val round = entries.size + 1
            val atkRoll = atkPower * (0.8 + rng.nextDouble() * 0.4)
            val defRoll = defPower * (0.8 + rng.nextDouble() * 0.4)

            val aDmg = max(1, (atkRoll - defPower * 0.3).toInt())
            val dDmg = max(1, (defRoll - atkPower * 0.3).toInt())

            defenderHp -= aDmg
            attackerHp -= dDmg
            totalAtkDamage += aDmg
            totalDefDamage += dDmg

            entries.add(TournamentLogEntry(round, aDmg, dDmg))
            logBuilder.append("Round $round: ATK=$aDmg DEF=$dDmg\n")
        }

        val attackerAlive = attackerHp > 0
        val defenderAlive = defenderHp > 0

        val draw: Boolean
        val winnerId: Long?
        val loserId: Long?

        when {
            attackerAlive && !defenderAlive -> {
                draw = false
                winnerId = input.attacker.id
                loserId = input.defender.id
            }
            !attackerAlive && defenderAlive -> {
                draw = false
                winnerId = input.defender.id
                loserId = input.attacker.id
            }
            attackerHp != defenderHp -> {
                draw = false
                if (attackerHp > defenderHp) {
                    winnerId = input.attacker.id
                    loserId = input.defender.id
                } else {
                    winnerId = input.defender.id
                    loserId = input.attacker.id
                }
            }
            else -> {
                draw = true
                winnerId = null
                loserId = null
            }
        }

        return TournamentBattleResult(
            winnerId = winnerId,
            loserId = loserId,
            rounds = entries.size,
            totalDamage = TotalDamage(totalAtkDamage, totalDefDamage),
            draw = draw,
            log = logBuilder.toString(),
            logEntries = entries,
        )
    }

    private fun getPower(participant: TournamentParticipant, type: Int): Double {
        val stats = participant.stats
        val baseStat = when (type) {
            TOURNAMENT_TOTAL -> (stats.attack + stats.defense + stats.agility) / 3.0
            else -> stats.attack
        }
        return baseStat * (1.0 + participant.level * 0.05)
    }
}
