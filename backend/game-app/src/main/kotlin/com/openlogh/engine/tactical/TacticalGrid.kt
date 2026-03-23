package com.openlogh.engine.tactical

import kotlin.math.sqrt

/** Position in the 1000x1000x500 continuous 3D coordinate space */
data class Position(val x: Double, val y: Double, val z: Double = 0.0) {
    fun distanceTo(other: Position): Double {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /** 2D 수평 거리 (x, y만) */
    fun horizontalDistanceTo(other: Position): Double {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }

    fun isInBounds(fieldSize: Double = FIELD_SIZE, heightSize: Double = HEIGHT_SIZE): Boolean =
        x in 0.0..fieldSize && y in 0.0..fieldSize && z in -heightSize..heightSize

    companion object {
        const val FIELD_SIZE = 1000.0
        /** z축 범위: -250 ~ +250 */
        const val HEIGHT_SIZE = 250.0
    }
}

/** Spherical obstacle region (asteroid belt, nebula, etc.) */
data class Obstacle(
    val center: Position,
    val radius: Double,
    val type: ObstacleType,
)

enum class ObstacleType(val blocksMovement: Boolean, val blocksLineOfFire: Boolean) {
    ASTEROID(true, true),
    NEBULA(false, true),
    DEBRIS(false, false),
}

/**
 * Free-coordinate tactical battlefield (1000x1000 logical space).
 * Units move freely within movement range; obstacles are circular regions.
 */
class TacticalGrid(val fieldSize: Double = Position.FIELD_SIZE) {

    val obstacles: MutableList<Obstacle> = mutableListOf()

    /** Unit positions: unitId -> Position */
    private val unitPositions = mutableMapOf<Int, Position>()

    fun addObstacle(obstacle: Obstacle) {
        obstacles.add(obstacle)
    }

    fun inBounds(pos: Position): Boolean = pos.isInBounds(fieldSize)

    fun inBounds(x: Double, y: Double, z: Double = 0.0): Boolean = Position(x, y, z).isInBounds(fieldSize)

    /** Check if a position is inside any movement-blocking obstacle */
    fun isBlocked(pos: Position): Boolean =
        obstacles.any { it.type.blocksMovement && pos.distanceTo(it.center) < it.radius }

    fun placeUnit(unitId: Int, pos: Position) {
        unitPositions[unitId] = pos
    }

    fun placeUnit(unitId: Int, x: Double, y: Double) {
        unitPositions[unitId] = Position(x, y)
    }

    fun removeUnit(unitId: Int) {
        unitPositions.remove(unitId)
    }

    fun moveUnit(unitId: Int, pos: Position) {
        unitPositions[unitId] = pos
    }

    fun getUnitPosition(unitId: Int): Position? = unitPositions[unitId]

    fun getDistance(a: Position, b: Position): Double = a.distanceTo(b)

    /**
     * Check line of fire between two positions.
     * Returns false if the line segment intersects any LoF-blocking obstacle.
     */
    fun hasLineOfFire(from: Position, to: Position): Boolean {
        for (obs in obstacles) {
            if (!obs.type.blocksLineOfFire) continue
            if (lineIntersectsSphere(from, to, obs.center, obs.radius)) return false
        }
        return true
    }

    /**
     * Compute reachable position: move from [from] toward [target], up to [maxDist],
     * stopping before entering a movement-blocking obstacle.
     * Returns the final position (which may be closer than target if blocked or out of range).
     */
    fun computeMoveTo(from: Position, target: Position, maxDist: Double): Position {
        val totalDist = from.distanceTo(target)
        if (totalDist <= 0.001) return from

        val effectiveDist = totalDist.coerceAtMost(maxDist)
        val ratio = effectiveDist / totalDist
        val candidateX = from.x + (target.x - from.x) * ratio
        val candidateY = from.y + (target.y - from.y) * ratio
        val candidateZ = from.z + (target.z - from.z) * ratio
        val candidate = Position(
            candidateX.coerceIn(0.0, fieldSize),
            candidateY.coerceIn(0.0, fieldSize),
            candidateZ.coerceIn(-Position.HEIGHT_SIZE, Position.HEIGHT_SIZE),
        )

        // Check if path goes through a movement-blocking obstacle
        for (obs in obstacles) {
            if (!obs.type.blocksMovement) continue
            if (lineIntersectsSphere(from, candidate, obs.center, obs.radius)) {
                // Stop just before the obstacle
                val closestDist = distanceToLineSegment3D(obs.center, from, candidate)
                if (closestDist < obs.radius) {
                    val safeRatio = findSafeRatio(from, candidate, obs)
                    val safeX = from.x + (candidate.x - from.x) * safeRatio
                    val safeY = from.y + (candidate.y - from.y) * safeRatio
                    val safeZ = from.z + (candidate.z - from.z) * safeRatio
                    return Position(
                        safeX.coerceIn(0.0, fieldSize),
                        safeY.coerceIn(0.0, fieldSize),
                        safeZ.coerceIn(-Position.HEIGHT_SIZE, Position.HEIGHT_SIZE),
                    )
                }
            }
        }

        return candidate
    }

    /** Line-segment vs sphere intersection test (3D) */
    private fun lineIntersectsSphere(p1: Position, p2: Position, center: Position, radius: Double): Boolean {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val dz = p2.z - p1.z
        val fx = p1.x - center.x
        val fy = p1.y - center.y
        val fz = p1.z - center.z

        val a = dx * dx + dy * dy + dz * dz
        val b = 2.0 * (fx * dx + fy * dy + fz * dz)
        val c = fx * fx + fy * fy + fz * fz - radius * radius

        var discriminant = b * b - 4.0 * a * c
        if (discriminant < 0) return false

        discriminant = sqrt(discriminant)
        val t1 = (-b - discriminant) / (2.0 * a)
        val t2 = (-b + discriminant) / (2.0 * a)

        return (t1 in 0.0..1.0) || (t2 in 0.0..1.0) || (t1 < 0.0 && t2 > 1.0)
    }

    /** Distance from a point to the closest point on a line segment (3D) */
    private fun distanceToLineSegment3D(point: Position, segA: Position, segB: Position): Double {
        val dx = segB.x - segA.x
        val dy = segB.y - segA.y
        val dz = segB.z - segA.z
        val lenSq = dx * dx + dy * dy + dz * dz
        if (lenSq < 0.0001) return point.distanceTo(segA)

        val t = ((point.x - segA.x) * dx + (point.y - segA.y) * dy + (point.z - segA.z) * dz) / lenSq
        val tc = t.coerceIn(0.0, 1.0)
        val proj = Position(segA.x + tc * dx, segA.y + tc * dy, segA.z + tc * dz)
        return point.distanceTo(proj)
    }

    /** Binary search for the safe travel ratio before hitting an obstacle (3D) */
    private fun findSafeRatio(from: Position, to: Position, obs: Obstacle): Double {
        var lo = 0.0
        var hi = 1.0
        repeat(20) {
            val mid = (lo + hi) / 2.0
            val px = from.x + (to.x - from.x) * mid
            val py = from.y + (to.y - from.y) * mid
            val pz = from.z + (to.z - from.z) * mid
            val pos = Position(px, py, pz)
            if (pos.distanceTo(obs.center) < obs.radius) {
                hi = mid
            } else {
                lo = mid
            }
        }
        return lo
    }
}
