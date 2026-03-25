package com.opensam.engine.map

import com.opensam.entity.General
import org.springframework.stereotype.Service
import kotlin.math.sqrt

@Service
class MovementService(private val mapDataService: MapDataService) {

    companion object {
        const val BASE_SPEED = 15f
        const val ARRIVAL_THRESHOLD = 1.5f
    }

    /**
     * Calculates the next position for a general moving toward their destination.
     * Returns Triple(newX, newY, arrived).
     * If the general has no destination, returns current position with arrived=false.
     */
    fun calculateNextPosition(general: General, mapCode: String): Triple<Float, Float, Boolean> {
        val destX = general.destX ?: return Triple(general.posX, general.posY, false)
        val destY = general.destY ?: return Triple(general.posX, general.posY, false)

        val dx = destX - general.posX
        val dy = destY - general.posY
        val dist = sqrt(dx * dx + dy * dy)

        if (dist < ARRIVAL_THRESHOLD) {
            return Triple(destX, destY, true)
        }

        val speedMultiplier = mapDataService.getMovementSpeed(mapCode, general.posX, general.posY)
        val step = BASE_SPEED * speedMultiplier

        if (step >= dist) {
            return Triple(destX, destY, true)
        }

        val ratio = step / dist
        val newX = general.posX + dx * ratio
        val newY = general.posY + dy * ratio
        return Triple(newX, newY, false)
    }
}
