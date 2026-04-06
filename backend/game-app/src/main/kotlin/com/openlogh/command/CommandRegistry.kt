package com.openlogh.command

import com.openlogh.entity.Officer
import org.springframework.stereotype.Component

typealias OfficerCommandFactory = (Officer, CommandEnv, Map<String, Any>?) -> OfficerCommand
typealias FactionCommandFactory = (Officer, CommandEnv, Map<String, Any>?) -> FactionCommand

/**
 * Legacy CommandRegistry — 삼국지 커맨드 제거됨 (Phase 1).
 * gin7 커맨드는 @Primary Gin7CommandRegistry에서 처리된다.
 * 이 클래스는 CommandExecutor 타입 참조 유지를 위해 보존된다.
 */
@Component
open class CommandRegistry {
    private val officerCommands = mutableMapOf<String, OfficerCommandFactory>()
    private val factionCommands = mutableMapOf<String, FactionCommandFactory>()
    private val officerSchemas = mutableMapOf<String, ArgSchema>()
    private val factionSchemas = mutableMapOf<String, ArgSchema>()

    // init{} 비어 있음 — 삼국지 커맨드 93종 전량 제거 (Phase 1)

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
