package com.openlogh.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.openlogh.entity.Planet
import com.openlogh.model.CityConst
import jakarta.annotation.PostConstruct
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.util.LinkedList

@Service
class MapService {

    private val maps = mutableMapOf<String, List<CityConst>>()
    private val adjacencyIndex = mutableMapOf<String, Map<Int, List<Int>>>()
    private val regionNames = mutableMapOf<String, Map<Int, String>>()

    @PostConstruct
    fun init() {
        loadMap("che")
    }

    private fun loadMap(mapName: String) {
        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        val resource = ClassPathResource("data/maps/$mapName.json")
        val data: Map<String, Any> = mapper.readValue(resource.inputStream, object : TypeReference<Map<String, Any>>() {})

        val rawCities = readListOfStringAnyMap(data["cities"])
        val rawRegions = data["regions"] as? Map<*, *> ?: emptyMap<Any, Any>()

        val cities = rawCities.map { raw ->
            CityConst(
                id = (raw["id"] as Number).toInt(),
                name = raw["name"] as String,
                level = (raw["level"] as Number).toInt(),
                region = (raw["region"] as Number).toInt(),
                population = (raw["population"] as Number).toInt(),
                agriculture = (raw["agriculture"] as Number).toInt(),
                commerce = (raw["commerce"] as Number).toInt(),
                security = (raw["security"] as Number).toInt(),
                defence = (raw["defence"] as Number).toInt(),
                wall = (raw["wall"] as Number).toInt(),
                x = (raw["x"] as Number).toInt(),
                y = (raw["y"] as Number).toInt(),
                connections = readNumberList(raw["connections"]).map { it.toInt() }
            )
        }

        maps[mapName] = cities
        adjacencyIndex[mapName] = cities.associate { it.id to it.connections }
        regionNames[mapName] = rawRegions.entries.mapNotNull { entry ->
            val regionId = entry.key?.toString()?.toIntOrNull() ?: return@mapNotNull null
            entry.value?.toString()?.let { regionId to it }
        }.toMap()
    }

    fun getCities(mapName: String): List<CityConst> {
        if (!maps.containsKey(mapName)) {
            loadMap(mapName)
        }
        return maps[mapName] ?: throw IllegalArgumentException("Unknown map: $mapName")
    }

    fun getPlanet(mapName: String, cityId: Int): CityConst? {
        return getCities(mapName).find { it.id == cityId }
    }

    fun getAdjacentCities(mapName: String, cityId: Int): List<Int> {
        if (!adjacencyIndex.containsKey(mapName)) {
            loadMap(mapName)
        }
        val adj = adjacencyIndex[mapName] ?: throw IllegalArgumentException("Unknown map: $mapName")
        return adj[cityId] ?: emptyList()
    }

    fun getRegions(mapName: String): Map<Int, String> {
        if (!regionNames.containsKey(mapName)) {
            loadMap(mapName)
        }
        return regionNames[mapName] ?: throw IllegalArgumentException("Unknown map: $mapName")
    }

    fun getRegionName(mapName: String, regionId: Int): String? = getRegions(mapName)[regionId]

    fun getMapJson(mapName: String): JsonNode? {
        val resource = ClassPathResource("data/maps/$mapName.json")
        if (!resource.exists()) return null
        val mapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        return mapper.readTree(resource.inputStream)
    }

    fun getDistance(mapName: String, fromCityId: Int, toCityId: Int): Int {
        if (fromCityId == toCityId) return 0

        if (!adjacencyIndex.containsKey(mapName)) {
            loadMap(mapName)
        }
        val adj = adjacencyIndex[mapName] ?: throw IllegalArgumentException("Unknown map: $mapName")

        val visited = mutableSetOf(fromCityId)
        val queue = LinkedList<Pair<Int, Int>>()
        queue.add(fromCityId to 0)

        while (queue.isNotEmpty()) {
            val (current, dist) = queue.poll()
            for (neighbor in adj[current] ?: emptyList()) {
                if (neighbor == toCityId) return dist + 1
                if (visited.add(neighbor)) {
                    queue.add(neighbor to dist + 1)
                }
            }
        }

        return -1
    }

    fun calcAllPairsDistance(cityIds: List<Int>, mapName: String = "che"): Map<Int, Map<Int, Int>> {
        if (cityIds.isEmpty()) return emptyMap()

        val uniqueCityIds = cityIds.distinct()
        val citySet = uniqueCityIds.toSet()
        val adj = adjacencyIndex[mapName] ?: run {
            loadMap(mapName)
            adjacencyIndex[mapName]
        } ?: return emptyMap()

        val dist = mutableMapOf<Int, MutableMap<Int, Int>>()
        for (cityId in uniqueCityIds) {
            val nearList = mutableMapOf(cityId to 0)
            for (neighbor in adj[cityId].orEmpty()) {
                if (neighbor in citySet) {
                    nearList[neighbor] = 1
                }
            }
            dist[cityId] = nearList
        }

        for (stop in uniqueCityIds) {
            for (from in uniqueCityIds) {
                val fromDist = dist[from] ?: continue
                val stopDist = fromDist[stop] ?: continue
                for (to in uniqueCityIds) {
                    val stopToDist = dist[stop]?.get(to) ?: continue
                    val newDist = stopDist + stopToDist
                    val currentDist = fromDist[to]
                    if (currentDist == null || newDist < currentDist) {
                        fromDist[to] = newDist
                    }
                }
            }
        }

        return dist
    }

    fun calcAllPairsDistanceByNation(
        nationIds: List<Long>,
        allCities: List<Planet>,
        mapName: String = "che",
    ): Map<Int, Map<Int, Int>> {
        val nationIdSet = nationIds.toSet()
        val cityIds = allCities.asSequence()
            .filter { it.factionId in nationIdSet }
            .map { it.mapCityId }
            .toList()
        return calcAllPairsDistance(cityIds, mapName)
    }

    fun calcAllPairsDistanceByNations(
        nationIds: List<Long>,
        allCities: List<Planet>,
        mapName: String = "che",
    ): Map<Int, Map<Int, Int>> = calcAllPairsDistanceByNation(nationIds, allCities, mapName)

    private fun readListOfStringAnyMap(raw: Any?): List<Map<String, Any>> {
        if (raw !is Iterable<*>) return emptyList()
        return raw.mapNotNull { entry ->
            if (entry !is Map<*, *>) return@mapNotNull null
            val typed = mutableMapOf<String, Any>()
            entry.forEach { (key, value) ->
                if (key is String && value != null) {
                    typed[key] = value
                }
            }
            typed
        }
    }

    private fun readNumberList(raw: Any?): List<Number> {
        if (raw !is Iterable<*>) return emptyList()
        return raw.mapNotNull { it as? Number }
    }
}
