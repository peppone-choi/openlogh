package com.opensam.engine.map

import com.opensam.entity.General
import kotlin.math.sqrt

class ProximityDetector {
    companion object {
        const val CELL_SIZE = 35f
    }

    fun contactRadius(crew: Int): Float = sqrt(crew.toFloat() / 100f) + 2f

    fun findContacts(
        generals: List<General>,
        isHostile: (Long, Long) -> Boolean,
    ): List<Pair<General, General>> {
        val active = generals.filter { it.posX > 0 && it.posY > 0 && it.crew > 0 }
        val grid = HashMap<Long, MutableList<General>>()
        for (g in active) {
            val key = cellKey(g.posX, g.posY)
            grid.getOrPut(key) { mutableListOf() }.add(g)
        }
        val contacts = mutableListOf<Pair<General, General>>()
        val checked = HashSet<Long>()
        for (g in active) {
            val cx = (g.posX / CELL_SIZE).toInt()
            val cy = (g.posY / CELL_SIZE).toInt()
            for (dx in -1..1) {
                for (dy in -1..1) {
                    val key = packKey(cx + dx, cy + dy)
                    for (other in grid[key] ?: continue) {
                        if (other.id == g.id || other.nationId == g.nationId) continue
                        if (!isHostile(g.nationId, other.nationId)) continue
                        val pairKey = minOf(g.id, other.id) * 100000 + maxOf(g.id, other.id)
                        if (!checked.add(pairKey)) continue
                        val dist = distance(g, other)
                        if (dist < contactRadius(g.crew) + contactRadius(other.crew)) {
                            contacts.add(g to other)
                        }
                    }
                }
            }
        }
        return contacts
    }

    private fun cellKey(x: Float, y: Float) = packKey((x / CELL_SIZE).toInt(), (y / CELL_SIZE).toInt())
    private fun packKey(cx: Int, cy: Int) = cx.toLong() * 10000 + cy
    private fun distance(a: General, b: General): Float {
        val dx = a.posX - b.posX; val dy = a.posY - b.posY
        return sqrt(dx * dx + dy * dy)
    }
}
