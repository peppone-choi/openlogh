package com.openlogh.gateway.service

import com.openlogh.gateway.entity.WorldState
import com.openlogh.gateway.repository.WorldStateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class WorldService(
    private val worldStateRepository: WorldStateRepository,
) {
    @Transactional(readOnly = true)
    fun listWorlds(): List<WorldState> = worldStateRepository.findAll()

    @Transactional(readOnly = true)
    fun getWorld(id: Short): WorldState? = worldStateRepository.findById(id).orElse(null)

    fun save(world: WorldState): WorldState = worldStateRepository.save(world)

    fun updateVersionAndActivation(
        world: WorldState,
        commitSha: String,
        gameVersion: String,
        active: Boolean,
    ): WorldState {
        world.commitSha = commitSha
        world.gameVersion = gameVersion
        world.meta["gatewayActive"] = active
        return worldStateRepository.save(world)
    }

    fun markActivation(world: WorldState, active: Boolean): WorldState {
        world.meta["gatewayActive"] = active
        return worldStateRepository.save(world)
    }

    fun deleteWorld(id: Short) {
        worldStateRepository.deleteById(id)
    }
}
