package com.openlogh.engine.turn.cqrs.port

interface IdAllocator {
    fun nextGeneralId(): Long
    fun nextCityId(): Long
    fun nextNationId(): Long
    fun nextTroopId(): Long
    fun nextDiplomacyId(): Long
}
