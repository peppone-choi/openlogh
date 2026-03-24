package com.openlogh.dto

import com.openlogh.entity.Diplomacy

data class DiplomacyRespondWithActionRequest(
    val messageId: Long,
    val action: String,
    val accept: Boolean,
)

data class DiplomacyDto(
    val id: Long,
    val sessionId: Long,
    val srcFactionId: Long,
    val destFactionId: Long,
    val stateCode: String,
    val term: Int,
    val isDead: Boolean,
    val isShowing: Boolean,
) {
    companion object {
        fun from(d: Diplomacy) = DiplomacyDto(
            id = d.id,
            sessionId = d.sessionId,
            srcFactionId = d.srcFactionId,
            destFactionId = d.destFactionId,
            stateCode = d.stateCode,
            term = d.term.toInt(),
            isDead = d.isDead,
            isShowing = d.isShowing,
        )
    }
}
