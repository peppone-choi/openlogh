package com.openlogh.service

import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.RecordRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.SessionStateRepository
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

/**
 * Implements the map-recent endpoint logic from legacy j_map_recent.php.
 *
 * Returns a cached snapshot of the current world map state including:
 * - All planet ownership/faction data
 * - Recent global history entries (last 10)
 * - Map theme name
 *
 * The result is cached for 10 minutes with ETag support for HTTP 304 responses.
 */
@Service
class MapRecentService(
    private val sessionStateRepository: SessionStateRepository,
    private val factionRepository: FactionRepository,
    private val planetRepository: PlanetRepository,
    private val recordRepository: RecordRepository,
    private val mapService: MapService,
    private val planetService: PlanetService,
) {
    data class MapRecentCacheEntry(
        val etag: String,
        val timestamp: Long,
        val data: Map<String, Any>,
    )

    @Volatile
    private var cacheByWorld: MutableMap<Long, MapRecentCacheEntry> = mutableMapOf()

    fun evictCache(worldId: Long) {
        cacheByWorld.remove(worldId)
    }

    /**
     * Get the recent map snapshot for a world. Returns cached data if fresh (< 10 min).
     *
     * @param worldId the world to query
     * @param clientEtag optional ETag from client for conditional response
     * @return a pair of (data map, isNotModified). If isNotModified is true, caller should return 304.
     */
    fun getMapRecent(worldId: Long, clientEtag: String?): Pair<MapRecentCacheEntry, Boolean> {
        val now = Instant.now().epochSecond
        val cached = cacheByWorld[worldId]

        if (cached != null && (now - cached.timestamp) < 600) {
            // Cache is fresh
            if (clientEtag != null && clientEtag == cached.etag) {
                return cached to true // 304 Not Modified
            }
            return cached to false
        }

        // Build fresh map data
        val entry = buildMapData(worldId, now)
        cacheByWorld[worldId] = entry

        if (clientEtag != null && clientEtag == entry.etag) {
            return entry to true
        }
        return entry to false
    }

    private fun buildMapData(worldId: Long, nowEpoch: Long): MapRecentCacheEntry {
        val world = sessionStateRepository.findById(worldId.toShort()).orElse(null)
            ?: return MapRecentCacheEntry(
                etag = "",
                timestamp = nowEpoch,
                data = mapOf("result" to false, "reason" to "서버 초기화되지 않음"),
            )

        val mapCode = (world.config["mapCode"] as? String) ?: "che"
        val mapCities = try {
            mapService.getCities(mapCode)
        } catch (_: Exception) {
            mapService.getCities("che")
        }
        val mapCityByName = mapCities.associateBy { it.name }

        val nations = factionRepository.findBySessionId(worldId)
        val nationById = nations.associateBy { it.id }

        val cities = planetRepository.findBySessionId(worldId)
        val cityDataList = cities.mapNotNull { planet ->
            val mapCity = mapCityByName[planet.name] ?: return@mapNotNull null
            val faction = nationById[planet.factionId]
            mapOf(
                "id" to planet.id,
                "name" to planet.name,
                "x" to mapCity.x,
                "y" to mapCity.y,
                "level" to planet.level.toInt(),
                "region" to planet.region.toInt(),
                "nationId" to planet.factionId,
                "factionName" to (faction?.name ?: ""),
                "nationColor" to (faction?.color ?: "#4b5563"),
                "pop" to planet.population,
                "popMax" to planet.populationMax,
                "agri" to planet.production,
                "comm" to planet.commerce,
                "secu" to planet.security,
                "def" to planet.orbitalDefense,
                "wall" to planet.fortress,
                "trust" to planet.approval,
                "state" to planet.state.toInt(),
                "supply" to planet.supplyState.toInt(),
            )
        }

        // Get recent history (last 10 entries)
        val historyRecords = recordRepository.findBySessionIdAndRecordTypeOrderByCreatedAtDesc(worldId, "world_history")
            .take(10)
        val history = historyRecords.map { record ->
            mapOf(
                "id" to record.id,
                "message" to (record.payload["message"]?.toString() ?: ""),
                "year" to record.year.toInt(),
                "month" to record.month.toInt(),
                "sentAt" to record.createdAt.toString(),
            )
        }

        val rawMap = mapOf(
            "result" to true,
            "worldId" to worldId,
            "year" to world.currentYear.toInt(),
            "month" to world.currentMonth.toInt(),
            "cities" to cityDataList,
            "nations" to nations.map { n ->
                mapOf(
                    "id" to n.id,
                    "name" to n.name,
                    "color" to n.color,
                    "level" to n.factionRank.toInt(),
                    "gold" to n.funds,
                    "rice" to n.supplies,
                )
            },
            "history" to history,
            "theme" to mapCode,
        )

        // Generate ETag from world ID + timestamp
        val digest = MessageDigest.getInstance("SHA-256")
        val etagInput = "${world.id}$nowEpoch"
        val etagBytes = digest.digest(etagInput.toByteArray())
        val etag = etagBytes.joinToString("") { "%02x".format(it) }

        return MapRecentCacheEntry(
            etag = etag,
            timestamp = nowEpoch,
            data = rawMap,
        )
    }
}
