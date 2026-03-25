package com.opensam.engine.map

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.awt.image.BufferedImage
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

@Service
class MapDataService {

    private val log = LoggerFactory.getLogger(MapDataService::class.java)

    data class TerrainGrid(
        val terrain: BufferedImage?,
        val road: BufferedImage?,
        val width: Int,
        val height: Int,
    )

    private val cache = ConcurrentHashMap<String, TerrainGrid>()

    private val cdnBase = System.getenv("NEXT_PUBLIC_IMAGE_CDN_BASE")
        ?: "https://cdn.jsdelivr.net/gh/peppone-choi/opensamguk-image@master/"

    private fun load(mapCode: String): TerrainGrid = cache.getOrPut(mapCode) {
        val terrainUrl = "${cdnBase}map/${mapCode}/terrain.png"
        val roadUrl = "${cdnBase}map/${mapCode}/road.png"

        val terrain = tryLoad(terrainUrl)
        val road = tryLoad(roadUrl)

        val width = terrain?.width ?: 1024
        val height = terrain?.height ?: 1024
        TerrainGrid(terrain, road, width, height)
    }

    private fun tryLoad(url: String): BufferedImage? = try {
        ImageIO.read(URL(url).openStream())
    } catch (e: Exception) {
        log.warn("MapDataService: could not load image from $url — falling back to PLAIN terrain")
        null
    }

    private fun clamp(value: Int, max: Int) = value.coerceIn(0, max - 1)

    fun getTerrainType(mapCode: String, x: Float, y: Float): TerrainType {
        val grid = load(mapCode)
        val terrain = grid.terrain ?: return TerrainType.PLAIN

        val px = clamp(x.toInt(), grid.width)
        val py = clamp(y.toInt(), grid.height)
        val rgb = terrain.getRGB(px, py)

        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF

        return classifyColor(r, g, b)
    }

    private fun classifyColor(r: Int, g: Int, b: Int): TerrainType {
        // Water: predominantly blue
        if (b > 150 && b > r + 50 && b > g + 30) return TerrainType.WATER
        // Mountain: dark grey / brown
        if (r in 80..160 && g in 70..140 && b in 60..120 && Math.abs(r - g) < 30) return TerrainType.MOUNTAIN
        // Forest: predominantly green
        if (g > 120 && g > r + 20 && g > b + 20) return TerrainType.FOREST
        // Desert: sandy / yellow
        if (r > 180 && g > 150 && b < 120) return TerrainType.DESERT
        return TerrainType.PLAIN
    }

    fun isOnRoad(mapCode: String, x: Float, y: Float): Boolean {
        val grid = load(mapCode)
        val road = grid.road ?: return false

        val px = clamp(x.toInt(), grid.width)
        val py = clamp(y.toInt(), grid.height)
        val argb = road.getRGB(px, py)
        val alpha = (argb shr 24) and 0xFF

        return alpha > 128
    }

    fun getMovementSpeed(mapCode: String, x: Float, y: Float): Float {
        val terrain = getTerrainType(mapCode, x, y)
        val onRoad = isOnRoad(mapCode, x, y)
        return if (onRoad) terrain.roadSpeed else terrain.offRoadSpeed
    }
}
