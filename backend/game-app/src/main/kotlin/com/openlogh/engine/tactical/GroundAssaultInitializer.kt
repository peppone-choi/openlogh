package com.openlogh.engine.tactical

import com.openlogh.entity.Planet
import com.openlogh.repository.PlanetRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 지상전 초기화 서비스.
 *
 * Planet 엔티티에서 궤도 방어력과 수비대 전력을 조회하여
 * GroundAssaultState를 생성. TacticalBattleEngine과 Planet 간
 * 느슨한 결합을 유지.
 *
 * Design Ref: §2.2 — Ground Assault Initialization Flow
 * Design Ref: §4.1 — GroundAssaultInitializer API
 * Plan SC: SC-03 — 하드코딩 100.0 값 0건
 */
@Service
class GroundAssaultInitializer(
    private val planetRepository: PlanetRepository,
) {
    private val log = LoggerFactory.getLogger(GroundAssaultInitializer::class.java)

    /**
     * Planet 엔티티에서 방어력 조회 후 GroundAssaultState 생성.
     */
    fun initiate(planetId: Long): GroundAssaultState {
        val planet = planetRepository.findById(planetId).orElse(null)
        if (planet == null) {
            log.warn("Planet {} not found for ground assault. Using default defense values", planetId)
            return GroundAssaultEngine.initiate(
                orbitalDefense = DEFAULT_ORBITAL_DEFENSE,
                garrisonStrength = DEFAULT_GARRISON_STRENGTH,
            )
        }

        val orbitalDefense = planet.orbitalDefense.toDouble()
        val garrisonStrength = calculateGarrisonStrength(planet)

        log.info("Ground assault initiated: planet={}, orbitalDefense={}, garrisonStrength={}",
            planetId, orbitalDefense, garrisonStrength)

        return GroundAssaultEngine.initiate(
            orbitalDefense = orbitalDefense,
            garrisonStrength = garrisonStrength,
        )
    }

    /**
     * 수비대 전력 계산: fortress 기반.
     * fortress 값이 0이면 기본값 사용.
     */
    fun calculateGarrisonStrength(planet: Planet): Double {
        val fortress = planet.fortress
        if (fortress <= 0) return DEFAULT_GARRISON_STRENGTH
        return fortress * GARRISON_PER_FORTRESS
    }

    companion object {
        private const val DEFAULT_ORBITAL_DEFENSE = 100.0
        private const val DEFAULT_GARRISON_STRENGTH = 100.0
        private const val GARRISON_PER_FORTRESS = 10.0
    }
}
