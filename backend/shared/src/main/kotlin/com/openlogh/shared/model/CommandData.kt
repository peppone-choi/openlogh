package com.openlogh.shared.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

/**
 * Strategic command definitions from gin7 manual appendix.
 * Loaded from commands.json at runtime.
 */

@JsonIgnoreProperties(ignoreUnknown = true)
data class CommandsRoot(
    val commands: Map<String, CommandCategory> = emptyMap(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CommandCategory(
    val nameKo: String = "",
    val nameEn: String = "",
    val cpType: String = "",
    val commands: List<CommandDefinition> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CommandDefinition(
    val id: String = "",
    val nameKo: String = "",
    val nameEn: String = "",
    val cpCost: Int = 0,
    val cpCostNote: String? = null,
    val waitTime: Int = 0,
    val waitTimeNote: String? = null,
    val duration: Int = 0,
    val durationNote: String? = null,
    val restriction: String? = null,
    val description: String = "",
)

/**
 * Registry for looking up command definitions by ID or category.
 *
 * Usage:
 * ```
 * val registry = CommandRegistry.load()
 * val cmd = registry.lookupById("warp_navigation")
 * val opsCmds = registry.getCategory("operations")
 * ```
 */
class CommandRegistry private constructor(
    private val root: CommandsRoot,
) {
    /** Flat index: commandId -> CommandDefinition */
    private val byId: Map<String, CommandDefinition> =
        root.commands.values.flatMap { it.commands }.associateBy { it.id }

    /** commandId -> category key */
    private val categoryByCommandId: Map<String, String> =
        root.commands.flatMap { (catKey, cat) ->
            cat.commands.map { cmd -> cmd.id to catKey }
        }.toMap()

    /**
     * Look up a command definition by its ID.
     */
    fun lookupById(commandId: String): CommandDefinition? = byId[commandId]

    /**
     * Get a full command category by key (e.g. "operations", "personal", "intelligence").
     */
    fun getCategory(categoryKey: String): CommandCategory? = root.commands[categoryKey]

    /**
     * Get all category keys.
     */
    fun getCategoryKeys(): Set<String> = root.commands.keys

    /**
     * Get all commands in a category.
     */
    fun getCommandsByCategory(categoryKey: String): List<CommandDefinition> =
        root.commands[categoryKey]?.commands ?: emptyList()

    /**
     * Get the category key for a command ID.
     */
    fun getCategoryForCommand(commandId: String): String? = categoryByCommandId[commandId]

    /**
     * Get the CP type (PCP or MCP) for a command.
     */
    fun getCpType(commandId: String): String? {
        val catKey = categoryByCommandId[commandId] ?: return null
        return root.commands[catKey]?.cpType
    }

    /**
     * Get all command definitions.
     */
    fun getAllCommands(): List<CommandDefinition> = byId.values.toList()

    companion object {
        private val mapper = ObjectMapper().registerKotlinModule()

        /**
         * Load command definitions from classpath JSON resource.
         */
        fun load(): CommandRegistry {
            val resource = CommandRegistry::class.java
                .getResourceAsStream("/data/commands.json")
                ?: throw IllegalStateException("commands.json not found on classpath")
            val root: CommandsRoot = mapper.readValue(resource)
            return CommandRegistry(root)
        }
    }
}
