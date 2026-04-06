package com.openlogh.engine.ai

import com.openlogh.entity.Officer
import com.openlogh.model.CommandGroup
import com.openlogh.model.PositionCardRegistry

/**
 * A scored command candidate produced by UtilityScorer.
 */
data class CommandCandidate(val commandName: String, val score: Double)

/**
 * Utility-theory command scorer for gin7 AI.
 *
 * Assigns a utility score to each CommandGroup based on:
 *   1. Which officer stats drive that group
 *   2. PersonalityTrait weight multipliers for those stats
 *
 * CommandGroup → stat drivers:
 *   OPERATIONS  : attack, command, mobility
 *   PERSONAL    : leadership, administration
 *   COMMAND     : command, leadership
 *   LOGISTICS   : administration, defense
 *   PERSONNEL   : leadership, politics
 *   POLITICS    : politics, administration
 *   INTELLIGENCE: intelligence, mobility
 */
object UtilityScorer {

    /**
     * Stat drivers per CommandGroup: each entry is a list of (stat extractor, stat name).
     * Score for a group = average of (stat_value * personality_weight_for_that_stat).
     */
    private data class StatDriver(
        val extract: (Officer) -> Double,
        val weight: (PersonalityWeights) -> Double,
    )

    private val groupDrivers: Map<CommandGroup, List<StatDriver>> = mapOf(
        CommandGroup.OPERATIONS to listOf(
            StatDriver({ it.attack.toDouble() }, { w -> w.attack }),
            StatDriver({ it.command.toDouble() }, { w -> w.command }),
            StatDriver({ it.mobility.toDouble() }, { w -> w.mobility }),
        ),
        CommandGroup.PERSONAL to listOf(
            StatDriver({ it.leadership.toDouble() }, { w -> w.leadership }),
            StatDriver({ it.administration.toDouble() }, { w -> w.administration }),
        ),
        CommandGroup.COMMAND to listOf(
            StatDriver({ it.command.toDouble() }, { w -> w.command }),
            StatDriver({ it.leadership.toDouble() }, { w -> w.leadership }),
        ),
        CommandGroup.LOGISTICS to listOf(
            StatDriver({ it.administration.toDouble() }, { w -> w.administration }),
            StatDriver({ it.defense.toDouble() }, { w -> w.defense }),
        ),
        CommandGroup.PERSONNEL to listOf(
            StatDriver({ it.leadership.toDouble() }, { w -> w.leadership }),
            StatDriver({ it.politics.toDouble() }, { w -> w.politics }),
        ),
        CommandGroup.POLITICS to listOf(
            StatDriver({ it.politics.toDouble() }, { w -> w.politics }),
            StatDriver({ it.administration.toDouble() }, { w -> w.administration }),
        ),
        CommandGroup.INTELLIGENCE to listOf(
            StatDriver({ it.intelligence.toDouble() }, { w -> w.intelligence }),
            StatDriver({ it.mobility.toDouble() }, { w -> w.mobility }),
        ),
    )

    /**
     * Score each CommandGroup for this officer/trait combination.
     * Returns a map of group name → utility score.
     */
    fun scoreGroups(officer: Officer, trait: PersonalityTrait): Map<String, Double> {
        val weights = PersonalityWeights.forTrait(trait)
        return groupDrivers.mapKeys { it.key.name }.mapValues { (_, drivers) ->
            val sum = drivers.sumOf { driver ->
                driver.extract(officer) * driver.weight(weights)
            }
            sum / drivers.size
        }
    }

    /**
     * Score a single command for this officer/trait.
     * Returns 0.0 if the officer does not hold a position card granting access to this command.
     */
    fun scoreCommand(commandName: String, officer: Officer, trait: PersonalityTrait): Double {
        val officerCards = officer.getPositionCardEnums()
        if (!PositionCardRegistry.canExecute(officerCards, commandName)) return 0.0

        val groupName = PositionCardRegistry.getCommandGroup(commandName).name
        return scoreGroups(officer, trait)[groupName] ?: 0.0
    }

    /**
     * Rank available commands by utility score, descending.
     * Only includes commands the officer has position card access to.
     *
     * @param availableCommands Commands registered in the registry for this officer to attempt
     * @param officer The officer making a decision
     * @param trait The personality trait weighting decisions
     * @param commandGroupOf Function that returns the CommandGroup name for a command name
     */
    fun rankCandidates(
        availableCommands: List<String>,
        officer: Officer,
        trait: PersonalityTrait,
        commandGroupOf: (String) -> String,
    ): List<CommandCandidate> {
        val groupScores = scoreGroups(officer, trait)
        val officerCards = officer.getPositionCardEnums()

        return availableCommands
            .filter { cmd -> PositionCardRegistry.canExecute(officerCards, cmd) }
            .map { cmd ->
                val groupName = commandGroupOf(cmd)
                val score = groupScores[groupName] ?: 0.0
                CommandCandidate(commandName = cmd, score = score)
            }
            .sortedByDescending { it.score }
    }
}
