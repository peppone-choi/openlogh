package com.openlogh.engine.modifier

data class ItemMeta(
    val code: String,
    val rawName: String,
    val category: String,
    var grade: Int = 0,
    val cost: Int = 0,
    val buyable: Boolean = true,
    val rarity: Int = 0,
    val consumable: Boolean = false,
    val info: String = "",
)
