package com.openlogh.engine.turn.cqrs.memory

class DirtyTracker {
    private val dirtyEntities = mutableSetOf<Any>()

    fun markDirty(entity: Any) {
        dirtyEntities.add(entity)
    }

    fun getDirtyEntities(): Set<Any> = dirtyEntities.toSet()

    fun clear() {
        dirtyEntities.clear()
    }
}
