package com.openlogh.engine.war

import kotlin.math.min

private val DEX_THRESHOLDS = intArrayOf(
    350, 1375, 3500, 7125, 12650, 20475, 31000, 44625, 61750, 82775,
    108100, 138125, 173250, 213875, 260400, 313225, 372750, 439375,
    513500, 595525, 685850, 784875, 893000, 1010625, 1138150, 1275975,
)

fun getTechLevel(tech: Float): Int = min((tech / 1000).toInt(), 12)

fun getTechAbil(tech: Float): Int = getTechLevel(tech) * 25

fun getTechCost(tech: Float): Double = 1.0 + getTechLevel(tech) * 0.15

fun getDexLevel(dex: Int): Int {
    var level = 0
    for (threshold in DEX_THRESHOLDS) {
        if (dex >= threshold) level++ else break
    }
    return level
}

fun getDexLog(dex1: Int, dex2: Int): Double {
    val diff = getDexLevel(dex1) - getDexLevel(dex2)
    return 1.0 + diff.toDouble() / 55.0
}
