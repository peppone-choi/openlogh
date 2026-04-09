package com.openlogh.command.gin7.commander

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * Phase 12: 작전철회 커맨드 — delegates to
 * [com.openlogh.service.OperationPlanService.cancelOperation].
 *
 * Args:
 *   - operationId: Long — the OperationPlan to cancel
 */
class OperationCancelCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "작전철회"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val services = this.services ?: return CommandResult.fail("CommandServices unavailable")
        val opService = services.operationPlanService
            ?: return CommandResult.fail("OperationPlanService unavailable")

        val operationId = (arg?.get("operationId") as? Number)?.toLong()
            ?: return CommandResult.fail("operationId 미지정")

        val cancelled = try {
            opService.cancelOperation(factionId = general.factionId, operationId = operationId)
        } catch (e: NoSuchElementException) {
            return CommandResult.fail(e.message ?: "작전을 찾을 수 없습니다")
        } catch (e: IllegalStateException) {
            return CommandResult.fail(e.message ?: "작전 철회 실패")
        }

        // Phase 12 D-13: Sync channel — propagate CANCELLED so active battles
        // drop bonus eligibility. Nullable-safe: unit tests that don't load the
        // full Spring context skip silently.
        services.tacticalBattleService?.syncOperationToActiveBattles(cancelled)

        pushLog("${general.name}이(가) '${cancelled.name}' 작전을 철회했다.")
        pushNationalHistoryLog("작전 철회: ${cancelled.name}")
        return CommandResult.success(logs)
    }
}
