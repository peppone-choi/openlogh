package com.openlogh.dto

import com.openlogh.entity.Diplomacy

data class DiplomacyRespondWithActionRequest(
    val messageId: Long,
    val action: String,
    val accept: Boolean,
)

data class DiplomacyDto(
    val id: Long,
    val worldId: Long,
    val srcNationId: Long,
    val destNationId: Long,
    val stateCode: String,
    val term: Int,
    val isDead: Boolean,
    val isShowing: Boolean,
) {
    companion object {
        fun from(d: Diplomacy) = DiplomacyDto(
            id = d.id,
            worldId = d.worldId,
            srcNationId = d.srcNationId,
            destNationId = d.destNationId,
            stateCode = d.stateCode,
            term = d.term.toInt(),
            isDead = d.isDead,
            isShowing = d.isShowing,
        )
    }
}
