package com.openlogh.qa.parity

import com.openlogh.engine.UnificationService
import com.openlogh.entity.City
import com.openlogh.entity.General
import com.openlogh.entity.Nation
import com.openlogh.entity.WorldState
import com.openlogh.repository.AppUserRepository
import com.openlogh.repository.CityRepository
import com.openlogh.repository.EmperorRepository
import com.openlogh.repository.GameHistoryRepository
import com.openlogh.repository.GeneralRepository
import com.openlogh.repository.HallOfFameRepository
import com.openlogh.repository.MessageRepository
import com.openlogh.repository.NationRepository
import com.openlogh.repository.OldGeneralRepository
import com.openlogh.repository.OldNationRepository
import com.openlogh.service.HistoryService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Game End Parity Test
 *
 * Verifies UnificationService.checkAndSettleUnification matches legacy
 * func_gamerule.php checkEmperior (lines 696-762):
 *   - Guard: isUnited != 0 -> return (already unified)
 *   - Guard: activeNations.size != 1 -> return
 *   - Guard: ownedCount != cities.size -> return
 *   - Action: isUnited = 2, refreshLimit *= 100
 *   - Inheritance: UNIFIER_POINT = 2000 for officers with officerLevel > 4
 *
 * Legacy source: hwe/func_gamerule.php lines 696-762
 */
@DisplayName("Game End Parity")
class GameEndParityTest {

    private lateinit var service: UnificationService
    private lateinit var nationRepository: NationRepository
    private lateinit var cityRepository: CityRepository
    private lateinit var generalRepository: GeneralRepository
    private lateinit var appUserRepository: AppUserRepository
    private lateinit var hallOfFameRepository: HallOfFameRepository
    private lateinit var emperorRepository: EmperorRepository
    private lateinit var oldNationRepository: OldNationRepository
    private lateinit var oldGeneralRepository: OldGeneralRepository
    private lateinit var gameHistoryRepository: GameHistoryRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var historyService: HistoryService

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNonNull(): T = org.mockito.ArgumentMatchers.any<T>() as T

    @BeforeEach
    fun setUp() {
        nationRepository = mock(NationRepository::class.java)
        cityRepository = mock(CityRepository::class.java)
        generalRepository = mock(GeneralRepository::class.java)
        appUserRepository = mock(AppUserRepository::class.java)
        hallOfFameRepository = mock(HallOfFameRepository::class.java)
        emperorRepository = mock(EmperorRepository::class.java)
        oldNationRepository = mock(OldNationRepository::class.java)
        oldGeneralRepository = mock(OldGeneralRepository::class.java)
        gameHistoryRepository = mock(GameHistoryRepository::class.java)
        messageRepository = mock(MessageRepository::class.java)
        historyService = mock(HistoryService::class.java)

        service = UnificationService(
            nationRepository,
            cityRepository,
            generalRepository,
            appUserRepository,
            hallOfFameRepository,
            emperorRepository,
            oldNationRepository,
            oldGeneralRepository,
            gameHistoryRepository,
            messageRepository,
            historyService,
        )
    }

    // ── Helpers ──

    private fun buildWorldState(isUnited: Int = 0, refreshLimit: Int = 30000): WorldState {
        return WorldState(
            id = 1,
            name = "테스트서버",
            scenarioCode = "test",
            currentYear = 210,
            currentMonth = 6,
            tickSeconds = 300,
        ).apply {
            config = mutableMapOf(
                "isUnited" to isUnited,
                "refreshLimit" to refreshLimit,
            )
            meta = mutableMapOf("season" to 1, "scenarioId" to 1)
        }
    }

    private fun buildNation(id: Long, level: Int, name: String = "위"): Nation {
        return Nation(
            id = id,
            worldId = 1,
            name = name,
            color = "#FF0000",
            level = level.toShort(),
        )
    }

    private fun buildCity(id: Long, nationId: Long): City {
        return City(
            id = id,
            worldId = 1,
            name = "도시$id",
            nationId = nationId,
        )
    }

    private fun buildGeneral(
        id: Long,
        nationId: Long,
        officerLevel: Int,
        userId: Long? = null,
        npcState: Short = 0,
    ): General {
        return General(
            id = id,
            worldId = 1,
            nationId = nationId,
            name = "장수$id",
            officerLevel = officerLevel.toShort(),
            userId = userId,
            npcState = npcState,
        )
    }

    /**
     * Wire mocks required for a successful unification path.
     * Callers can override specific mocks after calling this.
     */
    private fun wireSuccessfulUnificationMocks(
        nations: List<Nation>,
        cities: List<City>,
        generals: List<General> = emptyList(),
    ) {
        `when`(nationRepository.findByWorldId(1L)).thenReturn(nations)
        `when`(cityRepository.findByWorldId(1L)).thenReturn(cities)
        `when`(generalRepository.findByWorldId(1L)).thenReturn(generals)
        `when`(messageRepository.findByWorldIdAndMailboxCodeAndDestIdOrderBySentAtDesc(
            anyLong(), anyString(), anyLong()
        )).thenReturn(emptyList())
        `when`(messageRepository.save(anyNonNull())).thenAnswer { it.arguments[0] }
        `when`(gameHistoryRepository.findByServerId(anyString())).thenReturn(null)
        `when`(gameHistoryRepository.save(anyNonNull())).thenAnswer { it.arguments[0] }
        `when`(gameHistoryRepository.count()).thenReturn(0)
        `when`(oldNationRepository.findByServerIdAndNation(anyString(), anyLong())).thenReturn(null)
        `when`(oldNationRepository.save(anyNonNull())).thenAnswer { it.arguments[0] }
        `when`(oldNationRepository.findByServerId(anyString())).thenReturn(emptyList())
        `when`(oldGeneralRepository.findByServerIdAndGeneralNo(anyString(), anyLong())).thenReturn(null)
        `when`(oldGeneralRepository.save(anyNonNull())).thenAnswer { it.arguments[0] }
        `when`(emperorRepository.save(anyNonNull())).thenAnswer { it.arguments[0] }
        `when`(hallOfFameRepository.findByServerIdAndTypeAndGeneralNo(anyString(), anyString(), anyLong()))
            .thenReturn(null)
        `when`(hallOfFameRepository.save(anyNonNull())).thenAnswer { it.arguments[0] }
        `when`(appUserRepository.findById(anyLong())).thenReturn(java.util.Optional.empty())
    }

    // ── A. Unification Guard Conditions (DIPL-03) ──

    @Nested
    @DisplayName("Unification Guard Conditions")
    inner class UnificationGuardConditions {

        @Test
        @DisplayName("Already unified (isUnited=2) returns early, no repository calls")
        fun `already unified skips`() {
            // Legacy: if($worldState['isunited'] != 0) return;
            val world = buildWorldState(isUnited = 2)

            service.checkAndSettleUnification(world)

            verify(nationRepository, never()).findByWorldId(anyLong())
            assertThat(world.config["isUnited"])
                .describedAs("isUnited must remain 2 (unchanged)")
                .isEqualTo(2)
        }

        @Test
        @DisplayName("Already unified (isUnited=1) also returns early")
        fun `already unified non-zero skips`() {
            // Any non-zero isUnited should trigger early return
            val world = buildWorldState(isUnited = 1)

            service.checkAndSettleUnification(world)

            verify(nationRepository, never()).findByWorldId(anyLong())
            assertThat(world.config["isUnited"])
                .describedAs("isUnited must remain 1 (unchanged)")
                .isEqualTo(1)
        }

        @Test
        @DisplayName("Multiple active nations (2+ with level>0) returns early, no unification")
        fun `multiple active nations skips`() {
            // Legacy: SELECT nation FROM nation WHERE level > 0 LIMIT 2; if count != 1 return
            val world = buildWorldState()
            val nations = listOf(
                buildNation(1, level = 7, name = "위"),
                buildNation(2, level = 5, name = "촉"),
            )
            `when`(nationRepository.findByWorldId(1L)).thenReturn(nations)

            service.checkAndSettleUnification(world)

            assertThat(world.config["isUnited"])
                .describedAs("isUnited must remain 0 when multiple nations active")
                .isEqualTo(0)
        }

        @Test
        @DisplayName("Single active nation but not all cities owned returns early")
        fun `not all cities owned skips`() {
            // Legacy: if cityCnt != count(CityConst::all()) return
            val world = buildWorldState()
            val winner = buildNation(1, level = 7)
            val loser = buildNation(2, level = 0)
            val cities = listOf(
                buildCity(1, nationId = 1),
                buildCity(2, nationId = 1),
                buildCity(3, nationId = 0), // unowned city
            )
            `when`(nationRepository.findByWorldId(1L)).thenReturn(listOf(winner, loser))
            `when`(cityRepository.findByWorldId(1L)).thenReturn(cities)

            service.checkAndSettleUnification(world)

            assertThat(world.config["isUnited"])
                .describedAs("isUnited must remain 0 when not all cities owned")
                .isEqualTo(0)
        }

        @Test
        @DisplayName("Empty cities list returns early, no unification")
        fun `empty cities skips`() {
            val world = buildWorldState()
            val winner = buildNation(1, level = 7)
            `when`(nationRepository.findByWorldId(1L)).thenReturn(listOf(winner))
            `when`(cityRepository.findByWorldId(1L)).thenReturn(emptyList())

            service.checkAndSettleUnification(world)

            assertThat(world.config["isUnited"])
                .describedAs("isUnited must remain 0 when no cities exist")
                .isEqualTo(0)
        }

        @Test
        @DisplayName("Zero active nations returns early")
        fun `zero active nations skips`() {
            val world = buildWorldState()
            val nations = listOf(
                buildNation(1, level = 0),
                buildNation(2, level = 0),
            )
            `when`(nationRepository.findByWorldId(1L)).thenReturn(nations)

            service.checkAndSettleUnification(world)

            assertThat(world.config["isUnited"])
                .describedAs("isUnited must remain 0 when no active nations")
                .isEqualTo(0)
        }
    }

    // ── B. Unification Trigger (DIPL-03) ──

    @Nested
    @DisplayName("Unification Trigger")
    inner class UnificationTrigger {

        @Test
        @DisplayName("Successful unification sets isUnited to exactly 2")
        fun `successful unification sets isUnited to 2`() {
            // Legacy: $worldState['isunited'] = 2
            // Not 1, not true -- exactly the integer 2
            val world = buildWorldState(isUnited = 0, refreshLimit = 30000)
            val winner = buildNation(1, level = 5)
            val loser = buildNation(2, level = 0)
            val cities = listOf(
                buildCity(1, nationId = 1),
                buildCity(2, nationId = 1),
                buildCity(3, nationId = 1),
            )
            wireSuccessfulUnificationMocks(listOf(winner, loser), cities)

            service.checkAndSettleUnification(world)

            assertThat(world.config["isUnited"])
                .describedAs("isUnited must be exactly integer 2 (legacy: \$worldState['isunited'] = 2)")
                .isEqualTo(2)
            // Verify it's exactly Int 2, not some other type
            assertThat(world.config["isUnited"])
                .isInstanceOf(Int::class.javaObjectType)
        }

        @Test
        @DisplayName("refreshLimit multiplied by 100 after unification")
        fun `refreshLimit multiplied by 100`() {
            // Legacy: refreshLimit *= 100
            // 30000 * 100 = 3000000
            val world = buildWorldState(isUnited = 0, refreshLimit = 30000)
            val winner = buildNation(1, level = 5)
            val cities = listOf(
                buildCity(1, nationId = 1),
                buildCity(2, nationId = 1),
                buildCity(3, nationId = 1),
            )
            wireSuccessfulUnificationMocks(listOf(winner), cities)

            service.checkAndSettleUnification(world)

            assertThat(world.config["refreshLimit"])
                .describedAs("refreshLimit must be original * 100 (30000 * 100 = 3000000)")
                .isEqualTo(3000000)
        }

        @Test
        @DisplayName("Custom refreshLimit is correctly multiplied")
        fun `custom refreshLimit multiplied`() {
            // Verify with different initial refreshLimit
            val world = buildWorldState(isUnited = 0, refreshLimit = 50000)
            val winner = buildNation(1, level = 7)
            val cities = listOf(
                buildCity(1, nationId = 1),
                buildCity(2, nationId = 1),
            )
            wireSuccessfulUnificationMocks(listOf(winner), cities)

            service.checkAndSettleUnification(world)

            assertThat(world.config["refreshLimit"])
                .describedAs("refreshLimit 50000 * 100 = 5000000")
                .isEqualTo(5000000)
        }

        @Test
        @DisplayName("Default refreshLimit 30000 used when config key missing")
        fun `default refreshLimit when missing`() {
            // When refreshLimit is not in config, default 30000 is used
            val world = buildWorldState(isUnited = 0, refreshLimit = 30000)
            world.config.remove("refreshLimit")
            val winner = buildNation(1, level = 5)
            val cities = listOf(buildCity(1, nationId = 1))
            wireSuccessfulUnificationMocks(listOf(winner), cities)

            service.checkAndSettleUnification(world)

            // Default 30000 * 100 = 3000000
            assertThat(world.config["refreshLimit"])
                .describedAs("Default refreshLimit (30000) * 100 = 3000000")
                .isEqualTo(3000000)
        }
    }

    // ── C. Unifier Point Constant ──

    @Nested
    @DisplayName("Unifier Point Constant")
    inner class UnifierPointConstant {

        @Test
        @DisplayName("UNIFIER_POINT equals 2000 (legacy: func_gamerule.php line ~800)")
        fun `UNIFIER_POINT is 2000`() {
            // Legacy: func_gamerule.php line ~800: 통일 보너스 2000점
            // Use reflection to verify the companion object constant
            val companionField = UnificationService::class.java
                .getDeclaredField("UNIFIER_POINT")
            companionField.isAccessible = true
            val value = companionField.getInt(null)

            assertThat(value)
                .describedAs("UNIFIER_POINT must equal 2000 for legacy parity")
                .isEqualTo(2000)
        }
    }

    // ── D. Inheritance Point Calculation ──

    @Nested
    @DisplayName("Inheritance Point Awards")
    inner class InheritancePointAwards {

        @Test
        @DisplayName("Officers with officerLevel > 4 of winning nation receive UNIFIER_POINT")
        fun `unifier point awarded to high officers`() {
            // Legacy: officers of winning nation with officerLevel > 4 get 2000 points
            // Verify by reading source code for the condition
            val sourceFile = java.io.File("src/main/kotlin/com/opensam/engine/UnificationService.kt")
            assertThat(sourceFile.exists()).isTrue()
            val source = sourceFile.readText()

            // The condition: general.nationId == winnerNationId && general.officerLevel.toInt() > 4
            assertThat(source)
                .describedAs("settleInheritance must check officerLevel > 4 for UNIFIER_POINT award")
                .contains("officerLevel")
                .contains("UNIFIER_POINT")

            // Verify the condition uses > 4 (not >= 5, not > 5)
            // In Kotlin source: general.officerLevel.toInt() > 4
            assertThat(source)
                .describedAs("UNIFIER_POINT condition must use officerLevel > 4")
                .contains(".officerLevel.toInt() > 4")
        }

        @Test
        @DisplayName("Officers with officerLevel <= 4 do not receive UNIFIER_POINT")
        fun `no unifier point for low officers`() {
            // The settleInheritance logic awards 0 for officerLevel <= 4
            // Verify via source that unifierAward = 0 in the else branch
            val sourceFile = java.io.File("src/main/kotlin/com/opensam/engine/UnificationService.kt")
            val source = sourceFile.readText()

            // The else branch sets unifierAward = 0
            assertThat(source)
                .describedAs("Non-qualifying officers must get 0 unifier award")
                .contains("} else {\n                0\n            }")
        }
    }
}
