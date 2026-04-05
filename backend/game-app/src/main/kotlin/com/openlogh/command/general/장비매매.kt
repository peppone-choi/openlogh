package com.openlogh.command.general

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.command.constraint.*
import com.openlogh.entity.Officer
import com.openlogh.engine.modifier.ItemModifiers
import kotlin.random.Random

private val ITEM_TYPE_NAMES = mapOf(
    "horse" to "명마",
    "weapon" to "무기",
    "book" to "서적",
    "item" to "도구",
)

class 장비매매(general: Officer, env: CommandEnv, arg: Map<String, Any>? = null)
    : OfficerCommand(general, env, arg) {

    override val actionName = "장비매매"

    private val itemType: String? get() = arg?.get("itemType") as? String
    private val itemCode: String? get() = arg?.get("itemCode") as? String

    override val minConditionConstraints: List<Constraint>
        get() = if (itemType == null && itemCode == null) emptyList() else listOf(ReqCityTrader())

    override val fullConditionConstraints: List<Constraint>
        get() {
            val cost = getCost()
            val constraints = mutableListOf<Constraint>()
            val code = itemCode
            if (itemType != null || code != null) {
                constraints.add(ReqCityTrader())
            }
            if (code != null && code != "None") {
                val item = ItemModifiers.getMeta(code)
                if (item != null) {
                    constraints.add(ReqCityCapacity("secu", "치안 수치", item.grade * 10))
                }
                constraints.add(ReqGeneralGold(cost.funds))
            }
            return constraints
        }

    override fun getCost(): CommandCost {
        val code = itemCode ?: return CommandCost()
        if (code == "None") return CommandCost()
        val item = ItemModifiers.getMeta(code) ?: return CommandCost()
        return CommandCost(gold = item.cost)
    }

    override fun getPreReqTurn() = 0
    override fun getPostReqTurn() = 0

    override suspend fun run(rng: Random): CommandResult {
        val date = formatDate()

        val iType = itemType ?: return CommandResult(
            success = false,
            logs = listOf("인자가 없습니다."),
        )
        val iCode = itemCode ?: return CommandResult(
            success = false,
            logs = listOf("인자가 없습니다."),
        )

        val buying = iCode != "None"

        if (buying) {
            return buyItem(iType, iCode, date)
        } else {
            return sellItem(iType, date)
        }
    }

    private fun buyItem(itemType: String, itemCode: String, date: String): CommandResult {
        val item = ItemModifiers.getMeta(itemCode)
            ?: return CommandResult(success = false, logs = listOf("아이템 정보가 없습니다."))

        val itemName = "${item.rawName}(+${item.grade})"
        val itemRawName = item.rawName
        val cost = item.cost

        pushLog("<C>${itemName}</> 구입했습니다. <1>$date</>")
        pushHistoryLog("<C>${itemName}</> 구입했습니다. <1>$date</>")
        pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} <C>${itemName}</> 구입했습니다.")

        val exp = 10

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"gold":${-cost},"experience":$exp},"itemChanges":{"type":"$itemType","code":"$itemCode","action":"buy"}}"""
        )
    }

    private fun sellItem(itemType: String, date: String): CommandResult {
        val currentItemCode = when (itemType) {
            "weapon" -> general.flagshipCode
            "book" -> general.equipCode
            "horse" -> general.engineCode
            "item" -> general.accessoryCode
            else -> null
        }
        if (currentItemCode == null || currentItemCode == "None") {
            return CommandResult(success = false, logs = listOf("판매할 아이템이 없습니다."))
        }

        val item = ItemModifiers.getMeta(currentItemCode)
            ?: return CommandResult(success = false, logs = listOf("아이템 정보가 없습니다."))

        val itemName = "${item.rawName}(+${item.grade})"
        val itemRawName = item.rawName
        val sellPrice = item.cost / 2

        pushLog("<C>${itemName}</> 판매했습니다. <1>$date</>")
        pushHistoryLog("<C>${itemName}</> 판매했습니다. <1>$date</>")
        pushLog("<Y>${general.name}</>${pickJosa(general.name, "이")} <C>${itemName}</> 판매했습니다.")

        // If selling a rare (non-buyable) item, push global logs
        val globalLogs = mutableListOf<String>()
        if (!item.buyable) {
            val generalName = general.name
            globalLogs.add("<Y>${generalName}</> <C>${itemName}</> 판매했습니다!")
            globalLogs.add("<R><b>【판매】</b></> <Y>${generalName}</> <C>${itemName}</> 판매했습니다!")
        }

        val exp = 10

        return CommandResult(
            success = true,
            logs = logs,
            message = """{"statChanges":{"gold":$sellPrice,"experience":$exp},"itemChanges":{"type":"$itemType","code":"None","action":"sell"},"globalLogs":${globalLogs.joinToString(",", "[", "]") { "\"$it\"" }}}"""
        )
    }
}
