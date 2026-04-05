package com.openlogh.qa.parity

import com.openlogh.engine.DiplomacyService
import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.engine.turn.cqrs.persist.toSnapshot
import com.openlogh.entity.Diplomacy
import com.openlogh.entity.SessionState
import com.openlogh.repository.DiplomacyRepository
import com.openlogh.repository.MessageRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Optional

/**
 * Diplomacy Parity Test
 *
 * Verifies DiplomacyService state transitions and timer constants match legacy PHP behavior:
 *   - Timer constants: WAR_DECLARATION_TERM=24, WAR_INITIAL_TERM=6, NON_AGGRESSION_TERM=60,
 *     CEASEFIRE_PROPOSAL_TERM=12, NA_PROPOSAL_TERM=12
 *   - State transitions: declaration->war, non-aggression expiry, proposal expiry, war persistence
 *   - Command actions: declareWar, acceptNonAggression, acceptCeasefire, acceptBreakNonAggression
 *   - State code mapping: 0=war, 1=declared, 2=neutral, 3=NA proposal, 4=NA break, 5=ceasefire, 7=NA
 *
 * Legacy source: hwe/func_gamerule.php lines 337-406, hwe/sammo/Command/Nation/che_*.php
 */
@DisplayName("Diplomacy Parity")
class DiplomacyParityTest {

    private lateinit var service: DiplomacyService
    private lateinit var diplomacyRepository: DiplomacyRepository
    private lateinit var messageRepository: MessageRepository

    private val diplomacies = mutableListOf<Diplomacy>()
    private var nextId = 1L

    companion object {
        private const val WORLD_ID = 1L
        private const val NATION_A = 10L
        private const val NATION_B = 20L
    }

    @BeforeEach
    fun setUp() {
        diplomacyRepository = mock(DiplomacyRepository::class.java)
        messageRepository = mock(MessageRepository::class.java)
        diplomacies.clear()
        nextId = 1L
        wireRepos()

        val factory = JpaWorldPortFactory(diplomacyRepository = diplomacyRepository)
        service = DiplomacyService(factory, diplomacyRepository, messageRepository)
    }

    private fun wireRepos() {
        `when`(diplomacyRepository.findByWorldIdAndIsDeadFalse(anyLong())).thenAnswer {
            diplomacies.filter { !it.isDead && it.sessionId == WORLD_ID }
        }
        `when`(diplomacyRepository.findBySessionId(anyLong())).thenAnswer {
            diplomacies.filter { it.sessionId == WORLD_ID }
        }
        `when`(diplomacyRepository.findByWorldIdAndSrcNationIdOrDestNationId(anyLong(), anyLong(), anyLong()))
            .thenAnswer { inv ->
                val nationId = inv.arguments[1] as Long
                diplomacies.filter {
                    it.sessionId == WORLD_ID && (it.srcFactionId == nationId || it.destFactionId == nationId)
                }
            }
        `when`(diplomacyRepository.findActiveRelation(anyLong(), anyLong(), anyLong(), anyString()))
            .thenAnswer { inv ->
                val nationA = inv.arguments[1] as Long
                val nationB = inv.arguments[2] as Long
                val stateCode = inv.arguments[3] as String
                diplomacies.firstOrNull {
                    !it.isDead && it.sessionId == WORLD_ID && it.stateCode == stateCode &&
                        ((it.srcFactionId == nationA && it.destFactionId == nationB) ||
                            (it.srcFactionId == nationB && it.destFactionId == nationA))
                }
            }
        `when`(diplomacyRepository.findActiveRelationsBetween(anyLong(), anyLong(), anyLong()))
            .thenAnswer { inv ->
                val nationA = inv.arguments[1] as Long
                val nationB = inv.arguments[2] as Long
                diplomacies.filter {
                    !it.isDead && it.sessionId == WORLD_ID &&
                        ((it.srcFactionId == nationA && it.destFactionId == nationB) ||
                            (it.srcFactionId == nationB && it.destFactionId == nationA))
                }
            }
        `when`(diplomacyRepository.save(any(Diplomacy::class.java))).thenAnswer { inv ->
            val d = inv.arguments[0] as Diplomacy
            if (d.id == 0L) {
                d.id = nextId++
                diplomacies.add(d)
            } else {
                val idx = diplomacies.indexOfFirst { it.id == d.id }
                if (idx >= 0) diplomacies[idx] = d
                else diplomacies.add(d)
            }
            d
        }
        `when`(diplomacyRepository.findById(anyLong())).thenAnswer { inv ->
            val id = inv.arguments[0] as Long
            Optional.ofNullable(diplomacies.firstOrNull { it.id == id })
        }
        `when`(messageRepository.save(any())).thenAnswer { inv -> inv.arguments[0] }
    }

    private fun addDiplomacy(
        stateCode: String,
        term: Short,
        srcNationId: Long = NATION_A,
        destNationId: Long = NATION_B,
        isDead: Boolean = false,
    ): Diplomacy {
        val d = Diplomacy(
            id = nextId++,
            sessionId = WORLD_ID,
            srcFactionId = srcNationId,
            destFactionId = destNationId,
            stateCode = stateCode,
            term = term,
            isDead = isDead,
        )
        diplomacies.add(d)
        return d
    }

    private fun testWorld(): SessionState = SessionState(
        id = 1,
        scenarioCode = "test",
        currentYear = 210,
        currentMonth = 1,
        tickSeconds = 300,
    )

    // ========== A. Timer Constants (DIPL-02) ==========

    @Nested
    @DisplayName("Timer Constants")
    inner class TimerConstants {

        @Test
        @DisplayName("WAR_DECLARATION_TERM equals 24 (legacy: che_선전포고.php line 158)")
        fun `war declaration term is 24`() {
            // Legacy: func_gamerule.php, $dip->term set to 24 for 선전포고
            assertThat(DiplomacyService.WAR_DECLARATION_TERM.toInt()).isEqualTo(24)
        }

        @Test
        @DisplayName("WAR_INITIAL_TERM equals 6 (legacy: func_gamerule.php line 406)")
        fun `war initial term is 6`() {
            // Legacy: when 선전포고 term reaches 0, transition to 전쟁 with term=6
            assertThat(DiplomacyService.WAR_INITIAL_TERM.toInt()).isEqualTo(6)
        }

        @Test
        @DisplayName("NON_AGGRESSION_TERM equals 60 (legacy: che_불가침수락.php)")
        fun `non aggression term is 60`() {
            // Legacy: non-aggression pact lasts 60 turns (~5 years)
            assertThat(DiplomacyService.NON_AGGRESSION_TERM.toInt()).isEqualTo(60)
        }

        @Test
        @DisplayName("CEASEFIRE_PROPOSAL_TERM equals 12 (legacy: che_종전수락.php)")
        fun `ceasefire proposal term is 12`() {
            // Legacy: ceasefire proposal expires after 12 turns if not accepted
            assertThat(DiplomacyService.CEASEFIRE_PROPOSAL_TERM.toInt()).isEqualTo(12)
        }

        @Test
        @DisplayName("NA_PROPOSAL_TERM equals 12 (legacy: che_불가침제의.php)")
        fun `na proposal term is 12`() {
            // Legacy: non-aggression proposal expires after 12 turns
            assertThat(DiplomacyService.NA_PROPOSAL_TERM.toInt()).isEqualTo(12)
        }
    }

    // ========== B. State Transitions - Turn Processing (DIPL-01) ==========

    @Nested
    @DisplayName("State Transitions - Turn Processing")
    inner class TurnProcessing {

        @Test
        @DisplayName("선전포고 with term=1 transitions to 전쟁 with term=WAR_INITIAL_TERM(6)")
        fun `declaration transitions to war when term reaches zero`() {
            // Legacy: func_gamerule.php lines 393-406
            // When 선전포고 term decrements to 0, state becomes 전쟁 with term=6
            val d = addDiplomacy("선전포고", term = 1)

            service.processDiplomacyTurn(testWorld())

            val updated = diplomacies.first { it.id == d.id }
            assertThat(updated.stateCode).isEqualTo("전쟁")
            assertThat(updated.term.toInt()).isEqualTo(DiplomacyService.WAR_INITIAL_TERM.toInt())
            assertThat(updated.isDead).isFalse()
        }

        @Test
        @DisplayName("선전포고 with term>1 decrements term but stays 선전포고")
        fun `declaration decrements term each turn`() {
            val d = addDiplomacy("선전포고", term = 10)

            service.processDiplomacyTurn(testWorld())

            val updated = diplomacies.first { it.id == d.id }
            assertThat(updated.stateCode).isEqualTo("선전포고")
            assertThat(updated.term.toInt()).isEqualTo(9)
            assertThat(updated.isDead).isFalse()
        }

        @Test
        @DisplayName("불가침 with term=1 transitions to isDead=true")
        fun `non aggression expires when term reaches zero`() {
            // Legacy: func_gamerule.php - non-aggression pact expires
            val d = addDiplomacy("불가침", term = 1)

            service.processDiplomacyTurn(testWorld())

            val updated = diplomacies.first { it.id == d.id }
            assertThat(updated.isDead).isTrue()
        }

        @Test
        @DisplayName("전쟁 with term=1 does NOT set isDead (war persists until ceasefire)")
        fun `war does not auto expire`() {
            // Legacy: func_gamerule.php - war continues indefinitely until ceasefire
            val d = addDiplomacy("전쟁", term = 1)

            service.processDiplomacyTurn(testWorld())

            val updated = diplomacies.first { it.id == d.id }
            assertThat(updated.isDead).isFalse()
            assertThat(updated.term.toInt()).isEqualTo(0)
        }

        @Test
        @DisplayName("전쟁 with term=0 still does NOT set isDead")
        fun `war at zero term persists`() {
            val d = addDiplomacy("전쟁", term = 0)

            service.processDiplomacyTurn(testWorld())

            val updated = diplomacies.first { it.id == d.id }
            assertThat(updated.isDead).isFalse()
        }

        @Test
        @DisplayName("종전제의 with term=1 transitions to isDead=true")
        fun `ceasefire proposal expires`() {
            val d = addDiplomacy("종전제의", term = 1)

            service.processDiplomacyTurn(testWorld())

            val updated = diplomacies.first { it.id == d.id }
            assertThat(updated.isDead).isTrue()
        }

        @Test
        @DisplayName("불가침제의 with term=1 transitions to isDead=true")
        fun `na proposal expires`() {
            val d = addDiplomacy("불가침제의", term = 1)

            service.processDiplomacyTurn(testWorld())

            val updated = diplomacies.first { it.id == d.id }
            assertThat(updated.isDead).isTrue()
        }

        @Test
        @DisplayName("불가침파기제의 with term=1 transitions to isDead=true")
        fun `na break proposal expires`() {
            val d = addDiplomacy("불가침파기제의", term = 1)

            service.processDiplomacyTurn(testWorld())

            val updated = diplomacies.first { it.id == d.id }
            assertThat(updated.isDead).isTrue()
        }

        @Test
        @DisplayName("Term decrement is exactly 1 per call")
        fun `term decrements by exactly one`() {
            val d = addDiplomacy("불가침", term = 30)

            service.processDiplomacyTurn(testWorld())

            val updated = diplomacies.first { it.id == d.id }
            assertThat(updated.term.toInt()).isEqualTo(29)
        }

        @Test
        @Disabled("Potential parity gap: war term casualty extension not implemented -- legacy func_gamerule.php lines 337-349 increments war term based on city.dead count")
        @DisplayName("War term extends based on city casualties (legacy func_gamerule.php lines 337-349)")
        fun `war term casualty extension`() {
            // Legacy: if city.dead > threshold, war term += increment
            // This behavior is in func_gamerule.php but may not be implemented in Kotlin
        }
    }

    // ========== C. State Transitions - Command Actions (DIPL-01) ==========

    @Nested
    @DisplayName("State Transitions - Command Actions")
    inner class CommandActions {

        @Test
        @DisplayName("declareWar creates 선전포고 with Short.MAX_VALUE term (not direct 전쟁)")
        fun `declare war creates declaration not direct war`() {
            // Legacy: che_선전포고.php creates 선전포고, not direct war
            val result = service.declareWar(WORLD_ID, NATION_A, NATION_B)

            assertThat(result.stateCode).isEqualTo("선전포고")
            assertThat(result.term).isEqualTo(Short.MAX_VALUE)
            assertThat(result.isDead).isFalse()
        }

        @Test
        @DisplayName("declareWar throws when non-aggression pact is active")
        fun `declare war blocked by non aggression pact`() {
            addDiplomacy("불가침", term = 30)

            assertThatThrownBy {
                service.declareWar(WORLD_ID, NATION_A, NATION_B)
            }.isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("non-aggression pact is active")
        }

        @Test
        @DisplayName("declareWar throws when already at war (선전포고)")
        fun `declare war blocked by existing declaration`() {
            addDiplomacy("선전포고", term = 10)

            assertThatThrownBy {
                service.declareWar(WORLD_ID, NATION_A, NATION_B)
            }.isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Already at war")
        }

        @Test
        @DisplayName("declareWar throws when already at war (전쟁)")
        fun `declare war blocked by active war`() {
            addDiplomacy("전쟁", term = 5)

            assertThatThrownBy {
                service.declareWar(WORLD_ID, NATION_A, NATION_B)
            }.isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("Already at war")
        }

        @Test
        @DisplayName("acceptNonAggression transitions proposal to isDead and creates 불가침 with term=60")
        fun `accept non aggression creates pact`() {
            // Legacy: che_불가침수락.php
            addDiplomacy("불가침제의", term = 8)

            val result = service.acceptNonAggression(WORLD_ID, NATION_A, NATION_B)

            // Proposal should be dead
            val proposal = diplomacies.first { it.stateCode == "불가침제의" }
            assertThat(proposal.isDead).isTrue()

            // New pact should exist with correct term
            assertThat(result.stateCode).isEqualTo("불가침")
            assertThat(result.term.toInt()).isEqualTo(DiplomacyService.NON_AGGRESSION_TERM.toInt())
            assertThat(result.term.toInt()).isEqualTo(60)
            assertThat(result.isDead).isFalse()
        }

        @Test
        @DisplayName("acceptCeasefire kills war and proposal, no new ceasefire state created (Pitfall 5)")
        fun `accept ceasefire kills war relation`() {
            // Legacy: che_종전수락.php kills war, does NOT create a "ceasefire" state
            addDiplomacy("전쟁", term = 3)
            addDiplomacy("종전제의", term = 5)

            service.acceptCeasefire(WORLD_ID, NATION_A, NATION_B)

            // Both war and proposal should be dead
            val war = diplomacies.first { it.stateCode == "전쟁" }
            val proposal = diplomacies.first { it.stateCode == "종전제의" }
            assertThat(war.isDead).isTrue()
            assertThat(proposal.isDead).isTrue()

            // No new "ceasefire" state should be created
            val ceasefireStates = diplomacies.filter {
                !it.isDead && it.stateCode !in listOf("전쟁", "종전제의")
            }
            assertThat(ceasefireStates).isEmpty()
        }

        @Test
        @DisplayName("acceptCeasefire also kills 선전포고 if present")
        fun `accept ceasefire kills declaration too`() {
            addDiplomacy("선전포고", term = 10)
            addDiplomacy("종전제의", term = 5)

            service.acceptCeasefire(WORLD_ID, NATION_A, NATION_B)

            val declaration = diplomacies.first { it.stateCode == "선전포고" }
            val proposal = diplomacies.first { it.stateCode == "종전제의" }
            assertThat(declaration.isDead).isTrue()
            assertThat(proposal.isDead).isTrue()
        }

        @Test
        @DisplayName("acceptBreakNonAggression kills both pact and break proposal")
        fun `accept break non aggression kills pact and proposal`() {
            // Legacy: che_불가침파기수락.php
            addDiplomacy("불가침", term = 30)
            addDiplomacy("불가침파기제의", term = 8)

            service.acceptBreakNonAggression(WORLD_ID, NATION_A, NATION_B)

            val pact = diplomacies.first { it.stateCode == "불가침" }
            val proposal = diplomacies.first { it.stateCode == "불가침파기제의" }
            assertThat(pact.isDead).isTrue()
            assertThat(proposal.isDead).isTrue()
        }
    }

    // ========== D. State Code Mapping (DIPL-01) ==========

    @Nested
    @DisplayName("State Code Mapping")
    inner class StateCodeMapping {

        @Test
        @DisplayName("setDiplomacyState(0) creates 전쟁 (legacy integer code 0)")
        fun `state code 0 maps to war`() {
            service.setDiplomacyState(WORLD_ID, NATION_A, NATION_B, 0, 10)

            val created = diplomacies.first { it.stateCode == "전쟁" }
            assertThat(created.stateCode).isEqualTo("전쟁")
            assertThat(created.term.toInt()).isEqualTo(10)
        }

        @Test
        @DisplayName("setDiplomacyState(1) creates 선전포고 (legacy integer code 1)")
        fun `state code 1 maps to declaration`() {
            service.setDiplomacyState(WORLD_ID, NATION_A, NATION_B, 1, 24)

            val created = diplomacies.first { it.stateCode == "선전포고" }
            assertThat(created.stateCode).isEqualTo("선전포고")
            assertThat(created.term.toInt()).isEqualTo(24)
        }

        @Test
        @DisplayName("setDiplomacyState(7) creates 불가침 (legacy integer code 7)")
        fun `state code 7 maps to non aggression`() {
            service.setDiplomacyState(WORLD_ID, NATION_A, NATION_B, 7, 60)

            val created = diplomacies.first { it.stateCode == "불가침" }
            assertThat(created.stateCode).isEqualTo("불가침")
            assertThat(created.term.toInt()).isEqualTo(60)
        }

        @Test
        @DisplayName("setDiplomacyState(2) kills all relations (neutral)")
        fun `state code 2 kills relations`() {
            addDiplomacy("전쟁", term = 5)

            service.setDiplomacyState(WORLD_ID, NATION_A, NATION_B, 2, 0)

            val war = diplomacies.first { it.stateCode == "전쟁" }
            assertThat(war.isDead).isTrue()
        }

        @Test
        @DisplayName("setDiplomacyState(3) creates 불가침제의 (legacy integer code 3)")
        fun `state code 3 maps to na proposal`() {
            service.setDiplomacyState(WORLD_ID, NATION_A, NATION_B, 3, 12)

            val created = diplomacies.first { it.stateCode == "불가침제의" }
            assertThat(created.stateCode).isEqualTo("불가침제의")
        }

        @Test
        @DisplayName("setDiplomacyState(4) creates 불가침파기제의 (legacy integer code 4)")
        fun `state code 4 maps to na break proposal`() {
            service.setDiplomacyState(WORLD_ID, NATION_A, NATION_B, 4, 12)

            val created = diplomacies.first { it.stateCode == "불가침파기제의" }
            assertThat(created.stateCode).isEqualTo("불가침파기제의")
        }

        @Test
        @DisplayName("setDiplomacyState(5) creates 종전제의 (legacy integer code 5)")
        fun `state code 5 maps to ceasefire proposal`() {
            service.setDiplomacyState(WORLD_ID, NATION_A, NATION_B, 5, 12)

            val created = diplomacies.first { it.stateCode == "종전제의" }
            assertThat(created.stateCode).isEqualTo("종전제의")
        }

        @Test
        @DisplayName("getDiplomacyState returns correct integer code roundtrip")
        fun `get diplomacy state roundtrip`() {
            addDiplomacy("전쟁", term = 5)

            val state = service.getDiplomacyState(WORLD_ID, NATION_A, NATION_B)

            assertThat(state).isNotNull
            assertThat(state!!.state).isEqualTo(0)
            assertThat(state.stateCode).isEqualTo("전쟁")
            assertThat(state.term).isEqualTo(5)
        }

        @Test
        @DisplayName("getDiplomacyState returns 7 for 불가침")
        fun `get state returns 7 for non aggression`() {
            addDiplomacy("불가침", term = 40)

            val state = service.getDiplomacyState(WORLD_ID, NATION_A, NATION_B)

            assertThat(state).isNotNull
            assertThat(state!!.state).isEqualTo(7)
        }

        @Test
        @DisplayName("getDiplomacyState returns null when no active relation exists")
        fun `get state returns null for no relation`() {
            val state = service.getDiplomacyState(WORLD_ID, NATION_A, NATION_B)

            assertThat(state).isNull()
        }
    }
}
