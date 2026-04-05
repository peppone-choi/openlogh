package com.openlogh.gateway.service

import com.openlogh.gateway.entity.SessionState
import com.openlogh.gateway.repository.SessionStateRepository
import org.springframework.stereotype.Service

@Service
class WorldService(
    private val sessionStateRepository: SessionStateRepository,
) {
    fun listWorlds(): List<SessionState> = sessionStateRepository.findAll()

    fun getWorld(id: Short): SessionState? = sessionStateRepository.findById(id).orElse(null)

    fun save(world: SessionState): SessionState = sessionStateRepository.save(world)

    fun updateVersionAndActivation(
        world: SessionState,
        commitSha: String,
        gameVersion: String,
        active: Boolean,
    ): SessionState {
        world.commitSha = commitSha
        world.gameVersion = gameVersion
        world.meta["gatewayActive"] = active
        return sessionStateRepository.save(world)
    }

    fun markActivation(world: SessionState, active: Boolean): SessionState {
        world.meta["gatewayActive"] = active
        return sessionStateRepository.save(world)
    }

    fun deleteWorld(id: Short) {
        sessionStateRepository.deleteById(id)
    }
}
