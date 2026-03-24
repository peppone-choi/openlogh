package com.openlogh.engine

import com.openlogh.entity.Diplomacy
import com.openlogh.entity.Message
import com.openlogh.entity.SessionState
import com.openlogh.repository.DiplomacyRepository
import com.openlogh.repository.MessageRepository
import org.springframework.stereotype.Service

@Service
class DiplomacyService(
    private val diplomacyRepository: DiplomacyRepository,
    private val messageRepository: MessageRepository,
) {
    companion object {
        const val WAR_INITIAL_TERM: Short = 6
        const val MSG_NON_AGGRESSION_PROPOSAL = "non_aggression_proposal"
        const val MSG_BREAK_NON_AGGRESSION_PROPOSAL = "break_non_aggression_proposal"
        const val MSG_CEASEFIRE_PROPOSAL = "ceasefire_proposal"
    }

    fun processDiplomacyTurn(world: SessionState) {
        val active = diplomacyRepository.findBySessionIdAndIsDeadFalse(world.id.toLong())
        if (active.isEmpty()) return

        val toSave = mutableListOf<Diplomacy>()
        for (d in active) {
            if (d.term > 0) d.term = (d.term - 1).toShort()
            if (d.term <= 0.toShort()) {
                when (d.stateCode) {
                    "선전포고" -> { d.stateCode = "전쟁"; d.term = WAR_INITIAL_TERM }
                    "전쟁" -> { /* war continues */ }
                    else -> d.isDead = true
                }
            }
            toSave.add(d)
        }
        diplomacyRepository.saveAll(toSave)
    }

    fun declareWar(sessionId: Long, srcFactionId: Long, destFactionId: Long): Diplomacy {
        val existing = findActiveBetween(sessionId, srcFactionId, destFactionId)
        for (d in existing) {
            if (d.stateCode in listOf("불가침", "전쟁", "선전포고")) {
                error("Cannot declare war: active '${d.stateCode}' exists")
            }
        }
        for (d in existing) { if (!d.isDead) { d.isDead = true; diplomacyRepository.save(d) } }
        return diplomacyRepository.save(Diplomacy(
            sessionId = sessionId, srcNationId = srcFactionId, destFactionId = destFactionId,
            stateCode = "선전포고", term = 3,
        ))
    }

    fun proposeNonAggression(sessionId: Long, srcFactionId: Long, destFactionId: Long): Diplomacy {
        val existing = findActiveBetween(sessionId, srcFactionId, destFactionId)
        for (d in existing) {
            when (d.stateCode) {
                "선전포고", "전쟁" -> error("Cannot propose non-aggression during war")
                "불가침" -> error("Non-aggression pact already exists")
                "불가침제의" -> return d
            }
        }
        val proposal = diplomacyRepository.save(Diplomacy(
            sessionId = sessionId, srcNationId = srcFactionId, destFactionId = destFactionId,
            stateCode = "불가침제의", term = 12,
        ))
        val nonAggressionContent = "불가침 제의가 도착했습니다. 외교부에서 확인해주세요."
        messageRepository.save(Message(
            sessionId = sessionId, mailboxCode = "diplomacy",
            messageType = MSG_NON_AGGRESSION_PROPOSAL, srcId = srcFactionId, destId = destFactionId,
            payload = mutableMapOf("content" to nonAggressionContent, "srcNationId" to srcFactionId, "destNationId" to destFactionId),
        ))
        return proposal
    }

    fun acceptNonAggression(sessionId: Long, srcFactionId: Long, destFactionId: Long): Diplomacy {
        val existing = findActiveBetween(sessionId, srcFactionId, destFactionId)
        val proposal = existing.find { it.stateCode == "불가침제의" } ?: error("No proposal found")
        proposal.isDead = true; diplomacyRepository.save(proposal)
        return diplomacyRepository.save(Diplomacy(
            sessionId = sessionId, srcNationId = srcFactionId, destFactionId = destFactionId,
            stateCode = "불가침", term = 24,
        ))
    }

    fun proposeBreakNonAggression(sessionId: Long, srcFactionId: Long, destFactionId: Long): Diplomacy {
        val existing = findActiveBetween(sessionId, srcFactionId, destFactionId)
        existing.find { it.stateCode == "불가침" } ?: error("No pact to break")
        val proposal = diplomacyRepository.save(Diplomacy(
            sessionId = sessionId, srcNationId = srcFactionId, destFactionId = destFactionId,
            stateCode = "불가침파기제의", term = 12,
        ))
        val breakContent = "불가침 파기 제의가 도착했습니다. 외교부에서 확인해주세요."
        messageRepository.save(Message(
            sessionId = sessionId, mailboxCode = "diplomacy",
            messageType = MSG_BREAK_NON_AGGRESSION_PROPOSAL, srcId = srcFactionId, destId = destFactionId,
            payload = mutableMapOf("content" to breakContent, "srcNationId" to srcFactionId, "destNationId" to destFactionId),
        ))
        return proposal
    }

    fun acceptBreakNonAggression(sessionId: Long, srcFactionId: Long, destFactionId: Long) {
        val existing = findActiveBetween(sessionId, srcFactionId, destFactionId)
        existing.find { it.stateCode == "불가침파기제의" } ?: error("No break proposal found")
        for (d in existing) {
            if (d.stateCode in listOf("불가침", "불가침파기제의")) { d.isDead = true; diplomacyRepository.save(d) }
        }
    }

    fun proposeCeasefire(sessionId: Long, srcFactionId: Long, destFactionId: Long): Diplomacy {
        val existing = findActiveBetween(sessionId, srcFactionId, destFactionId)
        if (existing.none { it.stateCode in listOf("전쟁", "선전포고") }) error("Not at war")
        if (existing.any { it.stateCode == "종전제의" }) error("Ceasefire proposal already exists")
        val proposal = diplomacyRepository.save(Diplomacy(
            sessionId = sessionId, srcNationId = srcFactionId, destFactionId = destFactionId,
            stateCode = "종전제의", term = 12,
        ))
        val ceasefireContent = "종전 제의가 도착했습니다. 외교부에서 확인해주세요."
        messageRepository.save(Message(
            sessionId = sessionId, mailboxCode = "diplomacy",
            messageType = MSG_CEASEFIRE_PROPOSAL, srcId = srcFactionId, destId = destFactionId,
            payload = mutableMapOf("content" to ceasefireContent, "srcNationId" to srcFactionId, "destNationId" to destFactionId),
        ))
        return proposal
    }

    fun acceptCeasefire(sessionId: Long, srcFactionId: Long, destFactionId: Long) {
        val existing = findActiveBetween(sessionId, srcFactionId, destFactionId)
        existing.find { it.stateCode == "종전제의" } ?: error("No ceasefire proposal found")
        for (d in existing) { d.isDead = true; diplomacyRepository.save(d) }
    }

    fun acceptDiplomaticMessage(sessionId: Long, messageId: Long) {
        val msg = messageRepository.findById(messageId).orElseThrow()
        val src = (msg.payload["srcNationId"] as Number).toLong()
        val dest = (msg.payload["destFactionId"] as Number).toLong()
        when (msg.messageType) {
            MSG_NON_AGGRESSION_PROPOSAL -> acceptNonAggression(sessionId, src, dest)
            MSG_BREAK_NON_AGGRESSION_PROPOSAL -> acceptBreakNonAggression(sessionId, src, dest)
            MSG_CEASEFIRE_PROPOSAL -> acceptCeasefire(sessionId, src, dest)
        }
        msg.meta["responded"] = true; msg.meta["accepted"] = true; messageRepository.save(msg)
    }

    fun rejectDiplomaticMessage(sessionId: Long, messageId: Long) {
        val msg = messageRepository.findById(messageId).orElseThrow()
        msg.meta["responded"] = true; msg.meta["accepted"] = false; messageRepository.save(msg)
    }

    fun killAllRelationsForNation(sessionId: Long, factionId: Long) {
        val relations = diplomacyRepository.findBySessionIdAndSrcNationIdOrDestFactionId(sessionId, factionId, factionId)
        for (d in relations) { if (!d.isDead) d.isDead = true }
        diplomacyRepository.saveAll(relations)
    }

    fun getRelations(sessionId: Long): List<Diplomacy> = diplomacyRepository.findBySessionId(sessionId)

    fun createRelation(sessionId: Long, srcFactionId: Long, destFactionId: Long, stateCode: String, term: Int): Diplomacy {
        return diplomacyRepository.save(Diplomacy(
            sessionId = sessionId, srcNationId = srcFactionId, destFactionId = destFactionId,
            stateCode = stateCode, term = term.toShort(),
        ))
    }

    fun getRelationsForNation(sessionId: Long, factionId: Long): List<Diplomacy> {
        return diplomacyRepository.findBySessionIdAndSrcNationIdOrDestFactionId(sessionId, factionId, factionId)
            .filter { !it.isDead }
    }

    fun areAtWar(sessionId: Long, factionA: Long, factionB: Long): Boolean {
        return findActiveBetween(sessionId, factionA, factionB)
            .any { it.stateCode == "전쟁" }
    }

    private fun findActiveBetween(sessionId: Long, factionA: Long, factionB: Long): List<Diplomacy> {
        return diplomacyRepository.findBySessionIdAndSrcNationIdOrDestFactionId(sessionId, factionA, factionB)
            .filter { !it.isDead }
    }
}
