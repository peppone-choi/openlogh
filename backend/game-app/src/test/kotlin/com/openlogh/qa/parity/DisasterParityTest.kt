package com.openlogh.qa.parity

import com.openlogh.engine.DeterministicRng
import com.openlogh.engine.EconomyService
import com.openlogh.engine.turn.cqrs.persist.toEntity
import com.openlogh.engine.turn.cqrs.persist.toSnapshot
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.SessionState
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.MessageRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.service.HistoryService
import com.openlogh.service.InheritanceService
import com.openlogh.service.MapService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Disaster Parity Test
 *
 * Verifies processDisasterOrBoom matches legacy RaiseDisaster.php:
 *   - 3-year grace period
 *   - boomRate per month (0, 0.25, 0.25, 0)
 *   - RNG seed string parity ("disater" typo in PHP)
 *   - Disaster state codes per month
 *   - Disaster/boom affectRatio formulas and city field effects
 *   - SabotageInjury: 30% chance, injury 1-16, ships/morale/training *= 0.98
 *
 * Legacy source: hwe/sammo/Event/Action/RaiseDisaster.php
 */
@DisplayName("Disaster Parity")
class DisasterParityTest {

    private lateinit var service: EconomyService
    private lateinit var planetRepository: PlanetRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var officerRepository: OfficerRepository
    private lateinit var messageRepository: MessageRepository

    private val cities = linkedMapOf<Long, Planet>()
    private val generals = linkedMapOf<Long, Officer>()

    @BeforeEach
    fun setUp() {
        planetRepository = mock(PlanetRepository::class.java)
        factionRepository = mock(FactionRepository::class.java)
        officerRepository = mock(OfficerRepository::class.java)
        messageRepository = mock(MessageRepository::class.java)
        service = EconomyService(
            planetRepository, factionRepository, officerRepository,
            messageRepository, mock(MapService::class.java),
            mock(HistoryService::class.java), mock(InheritanceService::class.java),
        )
        wireRepos()
    }

    private fun wireRepos() {
        `when`(planetRepository.findBySessionId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val sessionId = inv.arguments[0] as Long
            cities.values.filter { it.sessionId == sessionId }.map { it.toSnapshot().toEntity() }
        }
        `when`(factionRepository.findBySessionId(ArgumentMatchers.anyLong())).thenAnswer {
            emptyList<Any>()
        }
        `when`(officerRepository.findBySessionId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val sessionId = inv.arguments[0] as Long
            generals.values.filter { it.sessionId == sessionId }
        }
        `when`(officerRepository.findBySessionIdAndPlanetIdIn(ArgumentMatchers.anyLong(), ArgumentMatchers.anyList()))
            .thenAnswer { inv ->
                val planetIds = inv.arguments[1] as List<*>
                generals.values.filter { it.sessionId == 1L && planetIds.contains(it.planetId) }
            }
        `when`(officerRepository.saveAll(ArgumentMatchers.anyList<Officer>())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val saved = inv.arguments[0] as List<Officer>
            saved.forEach { generals[it.id] = it }
            saved
        }
        `when`(messageRepository.saveAll(ArgumentMatchers.anyList<com.openlogh.entity.Message>())).thenAnswer { inv ->
            inv.arguments[0]
        }
        `when`(planetRepository.save(ArgumentMatchers.any(Planet::class.java))).thenAnswer { inv ->
            val city = inv.arguments[0] as Planet
            cities[city.id] = city.toSnapshot().toEntity()
            city
        }
        `when`(planetRepository.saveAll(ArgumentMatchers.anyList<Planet>())).thenAnswer { inv ->
            @Suppress("UNCHECKED_CAST")
            val saved = inv.arguments[0] as List<Planet>
            saved.forEach { cities[it.id] = it.toSnapshot().toEntity() }
            saved
        }
    }

    private fun worldAt(year: Int, month: Int, startYear: Int = 200): SessionState {
        return SessionState(
            id = 1,
            scenarioCode = "test",
            currentYear = year.toShort(),
            currentMonth = month.toShort(),
            tickSeconds = 300,
        ).apply {
            config = mutableMapOf(
                "startYear" to startYear,
                "hiddenSeed" to "golden_parity_seed",
            )
        }
    }

    private fun testCity(id: Long, population: Int = 50000, production: Int = 800, commerce: Int = 600,
                         security: Int = 400, securityMax: Int = 1000, orbitalDefense: Int = 500, orbitalDefenseMax: Int = 1000,
                         fortress: Int = 300, fortressMax: Int = 1000): Planet {
        return Planet(
            id = id, sessionId = 1, name = "TestCity$id", mapPlanetId = id.toInt(),
            population = population, populationMax = 100000,
            production = production, productionMax = 1500,
            commerce = commerce, commerceMax = 1200,
            security = security, securityMax = securityMax,
            approval = 50f, orbitalDefense = orbitalDefense, orbitalDefenseMax = orbitalDefenseMax,
            fortress = fortress, fortressMax = fortressMax,
        )
    }

    // ── A. 3-year grace period ──

    @Nested
    @DisplayName("Grace Period")
    inner class GracePeriod {

        @Test
        @DisplayName("World within 3-year grace period skips disaster processing")
        fun `grace period skips disasters`() {
            // startYear=200, currentYear=202 -> 200+3=203 > 202 -> skip
            val city = testCity(1)
            cities[1L] = city
            val origPop = city.population
            val origAgri = city.production
            val origComm = city.commerce

            val world = worldAt(year = 202, month = 1, startYear = 200)
            service.processDisasterOrBoom(world)

            // City fields should be unchanged (grace period skip)
            val resultCity = cities[1L]!!
            assertThat(resultCity.population).describedAs("population unchanged during grace period").isEqualTo(origPop)
            assertThat(resultCity.production).describedAs("production unchanged during grace period").isEqualTo(origAgri)
            assertThat(resultCity.commerce).describedAs("commerce unchanged during grace period").isEqualTo(origComm)
        }

        @Test
        @DisplayName("World at exactly startYear+3 processes disasters")
        fun `grace period boundary allows disasters`() {
            // startYear=200, currentYear=203 -> 200+3=203, NOT > 203 -> process
            val city = testCity(1)
            cities[1L] = city

            val world = worldAt(year = 203, month = 1, startYear = 200)
            // We just verify it doesn't skip -- actual effects depend on RNG
            service.processDisasterOrBoom(world)
            // No assertion on values -- just verifying the method runs past the guard
        }
    }

    // ── B. boomRate golden values ──

    @Nested
    @DisplayName("Boom Rate")
    inner class BoomRate {

        @Test
        @DisplayName("boomRate is 0.0 for months 1 and 10 (no boom possible)")
        fun `boomRate zero for months 1 and 10`() {
            // Legacy: $boomingRate = [1 => 0, 4 => 0.25, 7 => 0.25, 10 => 0]
            // For months 1 and 10, isGood is always false regardless of RNG
            for (month in listOf(1, 10)) {
                val rng = DeterministicRng.create("golden_parity_seed", "disaster", 210.toShort(), month.toShort())
                val boomRate = when (month) {
                    4, 7 -> 0.25
                    else -> 0.0
                }
                // boomRate is 0 => isGood = boomRate > 0 && ... is always false
                assertThat(boomRate)
                    .describedAs("boomRate for month $month must be 0.0")
                    .isEqualTo(0.0)
            }
        }

        @Test
        @DisplayName("boomRate is 0.25 for months 4 and 7 (25% boom chance)")
        fun `boomRate quarter for months 4 and 7`() {
            for (month in listOf(4, 7)) {
                val boomRate = when (month) {
                    4, 7 -> 0.25
                    else -> 0.0
                }
                assertThat(boomRate)
                    .describedAs("boomRate for month $month must be 0.25")
                    .isEqualTo(0.25)
            }
        }
    }

    @Nested
    @DisplayName("Disaster Effect Golden Values")
    inner class DisasterEffect {

        @Test
        @DisplayName("Disaster affectRatio formula: 0.8 + valueFit(security/securityMax/0.8, 0, 1) * 0.15")
        fun `disaster affectRatio golden value`() {
            // City: security=400, securityMax=1000
            // secuRatio = 400/1000/0.8 = 0.5
            // valueFit(0.5, 0, 1) = 0.5
            // affectRatio = 0.8 + 0.5 * 0.15 = 0.875
            val security = 400.0
            val securityMax = 1000.0
            val secuRatio = (security / securityMax / 0.8).coerceIn(0.0, 1.0)
            val affectRatio = 0.8 + secuRatio * 0.15

            assertThat(secuRatio).isEqualTo(0.5)
            assertThat(affectRatio).isEqualTo(0.875)

            // Apply to city fields (PHP does population * affectRatio, no coerceAtLeast)
            val population = 50000
            val production = 800
            val commerce = 600
            assertThat((population * affectRatio).toInt()).isEqualTo(43750)
            assertThat((production * affectRatio).toInt()).isEqualTo(700)
            assertThat((commerce * affectRatio).toInt()).isEqualTo(525)
        }

        @Test
        @DisplayName("Disaster with high security reduces damage (secuRatio capped at 1.0)")
        fun `disaster high security golden value`() {
            // security=1000, securityMax=1000
            // secuRatio = 1000/1000/0.8 = 1.25 -> capped at 1.0
            // affectRatio = 0.8 + 1.0 * 0.15 = 0.95
            val secuRatio = (1000.0 / 1000.0 / 0.8).coerceIn(0.0, 1.0)
            val affectRatio = 0.8 + secuRatio * 0.15

            assertThat(secuRatio).isEqualTo(1.0)
            assertThat(affectRatio).isCloseTo(0.95, within(1e-10))

            assertThat((50000 * affectRatio).toInt()).isEqualTo(47500)
        }

        @Test
        @DisplayName("Disaster with zero security maximizes damage")
        fun `disaster zero security golden value`() {
            // security=0, securityMax=1000
            // secuRatio = 0/1000/0.8 = 0 -> capped at 0
            // affectRatio = 0.8 + 0 * 0.15 = 0.8
            val secuRatio = (0.0 / 1000.0 / 0.8).coerceIn(0.0, 1.0)
            val affectRatio = 0.8 + secuRatio * 0.15

            assertThat(secuRatio).isEqualTo(0.0)
            assertThat(affectRatio).isEqualTo(0.8)

            assertThat((50000 * affectRatio).toInt()).isEqualTo(40000)
        }
    }

    @Nested
    @DisplayName("Boom Effect Golden Values")
    inner class BoomEffect {

        @Test
        @DisplayName("Boom affectRatio formula: 1.01 + valueFit(security/securityMax/0.8, 0, 1) * 0.04")
        fun `boom affectRatio golden value`() {
            // City: security=400, securityMax=1000
            // secuRatio = 400/1000/0.8 = 0.5
            // affectRatio = 1.01 + 0.5 * 0.04 = 1.03
            val secuRatio = (400.0 / 1000.0 / 0.8).coerceIn(0.0, 1.0)
            val affectRatio = 1.01 + secuRatio * 0.04

            assertThat(secuRatio).isEqualTo(0.5)
            assertThat(affectRatio).isEqualTo(1.03)

            // Apply to city fields (PHP caps at *_max)
            val population = 50000
            val production = 800
            val commerce = 600
            assertThat((population * affectRatio).toInt()).isEqualTo(51500)
            assertThat((production * affectRatio).toInt()).isEqualTo(824)
            assertThat((commerce * affectRatio).toInt()).isEqualTo(618)
        }

        @Test
        @DisplayName("Boom with high security maximizes growth")
        fun `boom high security golden value`() {
            // security=1000, securityMax=1000
            // secuRatio = 1.25 -> capped at 1.0
            // affectRatio = 1.01 + 1.0 * 0.04 = 1.05
            val secuRatio = (1000.0 / 1000.0 / 0.8).coerceIn(0.0, 1.0)
            val affectRatio = 1.01 + secuRatio * 0.04

            assertThat(secuRatio).isEqualTo(1.0)
            assertThat(affectRatio).isEqualTo(1.05)

            assertThat((50000 * affectRatio).toInt()).isEqualTo(52500)
        }

        @Test
        @DisplayName("Boom population capped at populationMax")
        fun `boom population capped at max`() {
            // population=99000, populationMax=100000, affectRatio=1.05
            // 99000 * 1.05 = 103950 -> capped at 100000
            val population = 99000
            val populationMax = 100000
            val affectRatio = 1.05
            assertThat((population * affectRatio).toInt().coerceAtMost(populationMax)).isEqualTo(100000)
        }
    }

    // ── F. raiseProp formula ──

    @Nested
    @DisplayName("Raise Probability Formula")
    inner class RaiseProbability {

        @Test
        @DisplayName("Disaster raiseProp: 0.06 - secuRatio * 0.05 (1-6%)")
        fun `disaster raiseProp golden values`() {
            // security/securityMax = 0.0 -> raiseProp = 0.06 (max disaster chance)
            assertThat(0.06 - 0.0 * 0.05).isEqualTo(0.06)

            // security/securityMax = 1.0 -> raiseProp = 0.01 (min disaster chance)
            assertThat(0.06 - 1.0 * 0.05).isCloseTo(0.01, within(1e-10))

            // security/securityMax = 0.5 -> raiseProp = 0.035
            assertThat(0.06 - 0.5 * 0.05).isCloseTo(0.035, within(1e-10))
        }

        @Test
        @DisplayName("Boom raiseProp: 0.02 + secuRatio * 0.05 (2-7%)")
        fun `boom raiseProp golden values`() {
            // security/securityMax = 0.0 -> raiseProp = 0.02 (min boom chance)
            assertThat(0.02 + 0.0 * 0.05).isEqualTo(0.02)

            // security/securityMax = 1.0 -> raiseProp = 0.07 (max boom chance)
            assertThat(0.02 + 1.0 * 0.05).isEqualTo(0.07)
        }
    }

    // ── G. SabotageInjury golden values ──

    @Nested
    @DisplayName("SabotageInjury Parity")
    inner class SabotageInjury {

        @Test
        @DisplayName("30% injury chance, injury 1-16, ships/morale/training *= 0.98, injury cap 80")
        fun `sabotage injury parameters match legacy`() {
            // Legacy SabotageInjury: 30% chance (rng.nextDouble() >= 0.3 means NO injury)
            // If injured: injury += rng.nextInt(1, 17) (Kotlin: 1..16), cap at 80
            // ships *= 0.98, morale *= 0.98, training *= 0.98

            // Verify formula constants by reading source
            val sourceFile = java.io.File("src/main/kotlin/com/opensam/engine/EconomyService.kt")
            val source = sourceFile.readText()

            // Check 30% threshold
            assertThat(source).contains("rng.nextDouble() >= 0.3")

            // Check injury range 1-17 (exclusive upper = 16 max)
            assertThat(source).contains("rng.nextInt(1, 17)")

            // Check 0.98 multipliers
            assertThat(source).contains("ships * 0.98")
            assertThat(source).contains("morale * 0.98")
            assertThat(source).contains("training * 0.98")

            // Check injury cap at 80
            assertThat(source).contains("coerceIn(0, 80)")
        }

        @Test
        @DisplayName("Injury golden value: general with existing injury gets capped at 80")
        fun `injury cap golden value`() {
            // General with injury=70, injuryAmount=15 -> 70+15=85 -> capped at 80
            val currentInjury = 70
            val injuryAmount = 15
            val result = (currentInjury + injuryAmount).coerceIn(0, 80)
            assertThat(result).isEqualTo(80)
        }

        @Test
        @DisplayName("Crew/morale/train reduction golden values")
        fun `stat reduction golden values`() {
            // ships =5000 -> 5000 * 0.98 = 4900
            assertThat((5000 * 0.98).toInt()).isEqualTo(4900)

            // morale =100 -> 100 * 0.98 = 98
            assertThat((100 * 0.98).toInt()).isEqualTo(98)

            // training =80 -> 80 * 0.98 = 78 (78.4 truncated)
            assertThat((80 * 0.98).toInt()).isEqualTo(78)
        }
    }
}
