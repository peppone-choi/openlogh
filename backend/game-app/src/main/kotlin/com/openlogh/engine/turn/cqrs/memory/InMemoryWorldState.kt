package com.openlogh.engine.turn.cqrs.memory

data class InMemoryWorldState(
    val worldId: Long,
    val officers: MutableList<Any> = mutableListOf(),
    val planets: MutableList<Any> = mutableListOf(),
    val factions: MutableList<Any> = mutableListOf(),
)
