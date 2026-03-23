package com.openlogh.engine.turn.cqrs

data class TurnResult(
    val advancedTurns: Int = 0,
    val events: List<Any> = emptyList(),
)
