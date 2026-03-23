package com.openlogh.engine

import com.openlogh.engine.modifier.ItemMeta
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class UniqueLotteryService {

    enum class UniqueAcquireType { ITEM, SURVEY, RANDOM_RECRUIT, FOUNDING }

    data class GeneralItemSlots(
        val horse: String?,
        val weapon: String?,
        val book: String?,
        val item: String?,
    )

    data class UniqueLotteryConfig(
        val allItems: Map<String, Map<String, Int>?> = emptyMap(),
        val maxUniqueItemLimit: List<List<Int>> = emptyList(),
        val uniqueTrialCoef: Double = 10.0,
        val maxUniqueTrialProb: Double = 10.0,
        val minMonthToAllowInheritItem: Int = 0,
    )

    data class UniqueLotteryInput(
        val rng: Random,
        val config: UniqueLotteryConfig,
        val itemRegistry: Map<String, ItemMeta>,
        val generalItems: GeneralItemSlots,
        val occupiedUniqueCounts: Map<String, Int>,
        val scenarioId: Int,
        val userCount: Int,
        val currentYear: Int,
        val currentMonth: Int,
        val startYear: Int,
        val initYear: Int,
        val initMonth: Int,
        val acquireType: UniqueAcquireType,
    )

    fun buildVoteUniqueSeed(seed: String, worldId: Long, generalId: Long): String =
        "$seed-$worldId-$generalId"

    fun createDeterministicRng(seed: String): Random = Random(seed.hashCode().toLong())

    fun rollUniqueLottery(input: UniqueLotteryInput): String? {
        val pool = input.config.allItems.values
            .flatMap { it?.keys ?: emptyList() }
            .filter { code ->
                val item = input.itemRegistry[code] ?: return@filter false
                !item.buyable || input.acquireType == UniqueAcquireType.FOUNDING
            }
        if (pool.isEmpty()) {
            if (input.acquireType == UniqueAcquireType.FOUNDING) {
                return input.itemRegistry.values.firstOrNull { !it.buyable }?.code
            }
            return null
        }
        if (input.acquireType == UniqueAcquireType.FOUNDING) return pool.first()
        val prob = minOf(input.config.maxUniqueTrialProb, input.config.uniqueTrialCoef)
        if (input.rng.nextDouble() > prob) return null
        return pool[input.rng.nextInt(pool.size)]
    }

    fun resolveUniqueConfig(
        rawConfig: Map<String, Any?>,
        itemRegistry: Map<String, ItemMeta> = emptyMap(),
    ): UniqueLotteryConfig {
        @Suppress("UNCHECKED_CAST")
        val allItemsRaw = rawConfig["allItems"] as? Map<String, Map<String, Int>?>
        val allItems: Map<String, Map<String, Int>?> = if (allItemsRaw != null) {
            allItemsRaw
        } else {
            // Build default legacy pool from registry
            val weapon = mutableMapOf<String, Int>()
            val item = mutableMapOf<String, Int>()
            for ((code, meta) in itemRegistry) {
                val grade = meta.grade
                if (meta.category == "weapon" && grade >= 6) {
                    weapon[code] = grade - 5
                } else if (meta.category != "weapon" && !meta.buyable) {
                    item[code] = 1
                }
            }
            mapOf("weapon" to weapon, "item" to item)
        }
        @Suppress("UNCHECKED_CAST")
        val maxLimit = rawConfig["maxUniqueItemLimit"] as? List<List<Int>> ?: listOf(listOf(-1, 1))
        val trialCoef = (rawConfig["uniqueTrialCoef"] as? Number)?.toDouble() ?: 10.0
        val maxProb = (rawConfig["maxUniqueTrialProb"] as? Number)?.toDouble() ?: 10.0
        val minMonth = (rawConfig["minMonthToAllowInheritItem"] as? Number)?.toInt() ?: 0
        return UniqueLotteryConfig(allItems, maxLimit, trialCoef, maxProb, minMonth)
    }

    fun countOccupiedUniqueItems(
        generals: List<GeneralItemSlots>,
        itemRegistry: Map<String, ItemMeta>,
        config: UniqueLotteryConfig,
    ): Map<String, Int?> {
        val poolCodes = config.allItems.values
            .flatMap { it?.keys ?: emptyList() }
            .toSet()
        val counts = mutableMapOf<String, Int>()
        for (slots in generals) {
            for (code in listOfNotNull(slots.horse, slots.weapon, slots.book, slots.item)) {
                val item = itemRegistry[code] ?: continue
                if (!item.buyable && code in poolCodes) {
                    counts[code] = (counts[code] ?: 0) + 1
                }
            }
        }
        return counts
    }

    fun processLottery(sessionId: Long) {}
}
