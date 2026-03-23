package com.openlogh.gateway.orchestrator

import com.openlogh.gateway.dto.AttachWorldProcessRequest
import com.openlogh.gateway.dto.GameInstanceStatus

interface GameOrchestrator {

    fun attachWorld(worldId: Long, request: AttachWorldProcessRequest): GameInstanceStatus

    fun ensureVersion(request: AttachWorldProcessRequest): GameInstanceStatus

    fun detachWorld(worldId: Long): Boolean

    fun stopVersion(gameVersion: String): Boolean

    fun statuses(): List<GameInstanceStatus>

    fun shutdownAll()

    fun resolveImageVersion(gameVersion: String): String?

    fun listAvailableVersions(): List<String>
}
