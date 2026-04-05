package com.openlogh.service

import com.openlogh.dto.BestGeneralResponse
import com.openlogh.dto.MessageResponse
import com.openlogh.entity.HallOfFame
import com.openlogh.entity.Message
import com.openlogh.repository.AppUserRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.HallOfFameRepository
import com.openlogh.repository.SessionStateRepository
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import kotlin.math.roundToInt

@Service
class RankingService(
    private val officerRepository: OfficerRepository,
    private val hallOfFameRepository: HallOfFameRepository,
    private val sessionStateRepository: SessionStateRepository,
    private val appUserRepository: AppUserRepository,
) {
    fun bestGenerals(worldId: Long, sortBy: String, limit: Int): List<BestGeneralResponse> {
        val generals = officerRepository.findBySessionId(worldId)
        val sorted = when (sortBy) {
            "leadership" -> generals.sortedByDescending { it.leadership }
            "strength" -> generals.sortedByDescending { it.command }
            "intel" -> generals.sortedByDescending { it.intelligence }
            "politics" -> generals.sortedByDescending { it.politics }
            "charm" -> generals.sortedByDescending { it.administration }
            "dedication" -> generals.sortedByDescending { it.dedication }
            "crew" -> generals.sortedByDescending { it.ships }
            else -> generals.sortedByDescending { it.experience }
        }
        return sorted.take(limit).map { BestGeneralResponse.from(it) }
    }

    fun hallOfFame(worldId: Long, season: Int?, scenario: String?): List<MessageResponse> {
        val world = sessionStateRepository.findById(worldId.toShort()).orElse(null) ?: return emptyList()
        val serverId = resolveServerId(world)
        val scenarioFilter = scenario?.toIntOrNull()
        val ownerIds = hallOfFameRepository.findByServerId(serverId)
            .mapNotNull { it.owner?.toLongOrNull() }
            .distinct()
        val ownerNames = appUserRepository.findAllById(ownerIds).associate { it.id!! to (it.displayName.ifBlank { it.loginId }) }

        return hallOfFameRepository.findByServerId(serverId)
            .asSequence()
            .filter { season == null || it.season == season }
            .filter { scenarioFilter == null || it.scenario == scenarioFilter }
            .sortedWith(compareBy<HallOfFame> { it.type }.thenByDescending { it.value })
            .map { toResponse(worldId, it, ownerNames) }
            .toList()
    }

    fun hallOfFameOptions(worldId: Long): Map<String, Any> {
        val world = sessionStateRepository.findById(worldId.toShort()).orElse(null) ?: return mapOf("seasons" to emptyList<Map<String, Any>>())
        val serverId = resolveServerId(world)
        val entries = hallOfFameRepository.findByServerId(serverId)

        val seasons = entries
            .groupBy { it.season }
            .toSortedMap(compareByDescending { it })
            .map { (seasonId, seasonEntries) ->
                val scenarios = seasonEntries
                    .map { it.scenario }
                    .distinct()
                    .sortedDescending()
                    .map { scenarioId ->
                        mapOf(
                            "code" to scenarioId.toString(),
                            "label" to scenarioId.toString(),
                        )
                    }
                mapOf(
                    "id" to seasonId,
                    "label" to "${seasonId}기",
                    "scenarios" to scenarios,
                )
            }

        return mapOf("seasons" to seasons)
    }

    fun uniqueItemOwners(worldId: Long): List<Map<String, Any?>> {
        val generals = officerRepository.findBySessionId(worldId)
        val slots = listOf(
            "weapon" to "무기",
            "book" to "서적",
            "horse" to "명마",
            "item" to "도구",
        )

        return slots.flatMap { (slot, slotLabel) ->
            generals
                .filter {
                    val code = when (slot) {
                        "weapon" -> it.flagshipCode
                        "book" -> it.equipCode
                        "horse" -> it.engineCode
                        "item" -> it.accessoryCode
                        else -> "None"
                    }
                    code != "None" && code.isNotBlank()
                }
                .map { gen ->
                    val code = when (slot) {
                        "weapon" -> gen.flagshipCode
                        "book" -> gen.equipCode
                        "horse" -> gen.engineCode
                        else -> gen.accessoryCode
                    }
                    mapOf(
                        "slot" to slot,
                        "slotLabel" to slotLabel,
                        "generalId" to gen.id,
                        "generalName" to gen.name,
                        "nationId" to gen.factionId,
                        "nationName" to "",
                        "nationColor" to "",
                        "itemName" to code,
                        "itemGrade" to "unique",
                    )
                }
        }
    }

    private fun resolveServerId(world: com.openlogh.entity.SessionState): String {
        return (world.config["serverId"] as? String).orEmpty().ifBlank { world.name }
    }

    private fun toResponse(
        worldId: Long,
        fame: HallOfFame,
        ownerNames: Map<Long, String>,
    ): MessageResponse {
        val ownerName = fame.owner?.toLongOrNull()?.let(ownerNames::get)
        val payload = mutableMapOf<String, Any>(
            "category" to fame.type,
            "type" to fame.type,
            "generalName" to ((fame.aux["name"] as? String) ?: "-"),
            "name" to ((fame.aux["name"] as? String) ?: "-"),
            "nationName" to ((fame.aux["nationName"] as? String) ?: "재야"),
            "nationColor" to ((fame.aux["bgColor"] as? String) ?: "#888888"),
            "bgColor" to ((fame.aux["bgColor"] as? String) ?: "#888888"),
            "value" to fame.value,
            "printValue" to formatHallValue(fame.type, fame.value),
            "scenario" to fame.scenario.toString(),
            "season" to fame.season,
            "serverName" to fame.serverId,
            "generalId" to fame.generalNo,
        )
        (fame.aux["picture"] as? String)?.takeIf { it.isNotBlank() }?.let { payload["picture"] = it }
        ownerName?.let { payload["ownerName"] = it }

        return MessageResponse.from(
            Message(
                id = fame.id,
                sessionId = worldId,
                mailboxCode = "hall_of_fame",
                mailboxType = "PUBLIC",
                messageType = "hall_of_fame",
                srcId = fame.generalNo,
                payload = payload,
                meta = mutableMapOf(
                    "season" to fame.season,
                    "scenario" to fame.scenario,
                ),
                sentAt = OffsetDateTime.now(),
            )
        )
    }

    private fun formatHallValue(type: String, value: Double): String {
        return when (type) {
            "winrate", "killrate", "betrate", "ttrate", "tlrate", "tsrate", "tirate" ->
                "${((value * 10000.0).roundToInt() / 100.0)}%"
            else -> value.roundToInt().toString()
        }
    }
}
