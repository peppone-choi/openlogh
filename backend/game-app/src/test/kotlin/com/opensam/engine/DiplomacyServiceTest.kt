package com.opensam.engine

import com.opensam.entity.Diplomacy
import com.opensam.entity.Message
import com.opensam.entity.WorldState
import com.opensam.repository.DiplomacyRepository
import com.opensam.repository.MessageRepository
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Optional

class DiplomacyServiceTest {

    private lateinit var service: DiplomacyService
    private lateinit var diplomacyRepository: DiplomacyRepository
    private lateinit var messageRepository: MessageRepository

    private val diplomacies = linkedMapOf<Long, Diplomacy>()
    private val messages = linkedMapOf<Long, Message>()
    private var nextDiplomacyId = 1_000L
    private var nextMessageId = 10_000L

    @BeforeEach
    fun setUp() {
        diplomacyRepository = mock(DiplomacyRepository::class.java)
        messageRepository = mock(MessageRepository::class.java)
        wireDiplomacyRepo()
        wireMessageRepo()
        service = DiplomacyService(diplomacyRepository, messageRepository)
    }

    private fun wireDiplomacyRepo() {
        `when`(diplomacyRepository.save(ArgumentMatchers.any(Diplomacy::class.java))).thenAnswer { inv ->
            val d = inv.arguments[0] as Diplomacy
            if (d.id == 0L) {
                d.id = nextDiplomacyId++
            }
            diplomacies[d.id] = cloneDiplomacy(d)
            d
        }

        `when`(diplomacyRepository.saveAll(ArgumentMatchers.anyList<Diplomacy>())).thenAnswer { inv ->
            val list = inv.arguments[0] as List<Diplomacy>
            list.forEach { d ->
                if (d.id == 0L) {
                    d.id = nextDiplomacyId++
                }
                diplomacies[d.id] = cloneDiplomacy(d)
            }
            list
        }

        `when`(diplomacyRepository.findById(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            Optional.ofNullable(diplomacies[inv.arguments[0] as Long]?.let { cloneDiplomacy(it) })
        }

        `when`(diplomacyRepository.findByWorldId(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val worldId = inv.arguments[0] as Long
            diplomacies.values.filter { it.worldId == worldId }.map { cloneDiplomacy(it) }
        }

        `when`(diplomacyRepository.findByWorldIdAndIsDeadFalse(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            val worldId = inv.arguments[0] as Long
            diplomacies.values.filter { it.worldId == worldId && !it.isDead }.map { cloneDiplomacy(it) }
        }

        `when`(
            diplomacyRepository.findByWorldIdAndSrcNationIdOrDestNationId(
                ArgumentMatchers.anyLong(),
                ArgumentMatchers.anyLong(),
                ArgumentMatchers.anyLong(),
            )
        ).thenAnswer { inv ->
            val worldId = inv.arguments[0] as Long
            val src = inv.arguments[1] as Long
            val dest = inv.arguments[2] as Long
            diplomacies.values
                .filter { it.worldId == worldId && (it.srcNationId == src || it.destNationId == dest) }
                .map { cloneDiplomacy(it) }
        }
    }

    private fun wireMessageRepo() {
        `when`(messageRepository.save(ArgumentMatchers.any(Message::class.java))).thenAnswer { inv ->
            val m = inv.arguments[0] as Message
            if (m.id == null || m.id == 0L) {
                m.id = nextMessageId++
            }
            messages[m.id!!] = cloneMessage(m)
            m
        }

        `when`(messageRepository.findById(ArgumentMatchers.anyLong())).thenAnswer { inv ->
            Optional.ofNullable(messages[inv.arguments[0] as Long]?.let { cloneMessage(it) })
        }
    }

    private fun createWorld(year: Short = 200, month: Short = 3): WorldState =
        WorldState(id = 1, scenarioCode = "test", currentYear = year, currentMonth = month, tickSeconds = 300)

    private fun createDiplomacy(
        id: Long,
        srcNationId: Long = 1,
        destNationId: Long = 2,
        stateCode: String,
        term: Short,
        isDead: Boolean = false,
    ): Diplomacy = Diplomacy(
        id = id,
        worldId = 1,
        srcNationId = srcNationId,
        destNationId = destNationId,
        stateCode = stateCode,
        term = term,
        isDead = isDead,
    )

    private fun seed(vararg data: Diplomacy) {
        data.forEach { diplomacies[it.id] = cloneDiplomacy(it) }
    }

    private fun cloneDiplomacy(d: Diplomacy): Diplomacy {
        return Diplomacy(
            id = d.id,
            worldId = d.worldId,
            srcNationId = d.srcNationId,
            destNationId = d.destNationId,
            stateCode = d.stateCode,
            term = d.term,
            isDead = d.isDead,
            isShowing = d.isShowing,
            meta = d.meta.toMutableMap(),
            createdAt = d.createdAt,
        )
    }

    private fun cloneMessage(m: Message): Message {
        return Message(
            id = m.id,
            worldId = m.worldId,
            mailboxCode = m.mailboxCode,
            messageType = m.messageType,
            srcId = m.srcId,
            destId = m.destId,
            payload = m.payload.toMutableMap(),
            meta = m.meta.toMutableMap(),
        )
    }

    private fun load(id: Long): Diplomacy = diplomacies[id] ?: error("missing diplomacy $id")

    @Test
    fun `processDiplomacyTurn decrements term by 1`() {
        val world = createWorld()
        seed(createDiplomacy(id = 1, stateCode = "불가침", term = 5))

        service.processDiplomacyTurn(world)

        assertEquals(4, load(1).term.toInt())
    }

    @Test
    fun `processDiplomacyTurn marks expiring relations dead correctly`() {
        val world = createWorld()
        seed(
            createDiplomacy(id = 1, stateCode = "불가침", term = 1),
            createDiplomacy(id = 2, stateCode = "종전제의", term = 1),
            createDiplomacy(id = 3, stateCode = "불가침제의", term = 1),
            createDiplomacy(id = 4, stateCode = "불가침파기제의", term = 1),
            createDiplomacy(id = 5, stateCode = "전쟁", term = 1),
        )

        service.processDiplomacyTurn(world)

        assertTrue(load(1).isDead)
        assertTrue(load(2).isDead)
        assertTrue(load(3).isDead)
        assertTrue(load(4).isDead)
        assertFalse(load(5).isDead)
        assertEquals(0, load(5).term.toInt())
    }

    @Test
    fun `processDiplomacyTurn transitions 선전포고 to 전쟁 at term 0`() {
        val world = createWorld()
        seed(createDiplomacy(id = 1, stateCode = "선전포고", term = 1))

        service.processDiplomacyTurn(world)

        // Legacy parity: state=1 AND term=0 -> state=0 (전쟁), term=6 (WAR_INITIAL_TERM)
        assertEquals(6, load(1).term.toInt())
        assertEquals("전쟁", load(1).stateCode)
        assertFalse(load(1).isDead)
    }

    @Test
    fun `processDiplomacyTurn handles empty world gracefully`() {
        assertDoesNotThrow { service.processDiplomacyTurn(createWorld()) }
        assertTrue(diplomacies.isEmpty())
    }

    @Test
    fun `declareWar creates 선전포고 and kills pending proposals`() {
        seed(
            createDiplomacy(id = 1, srcNationId = 1, destNationId = 2, stateCode = "불가침제의", term = 5),
            createDiplomacy(id = 2, srcNationId = 1, destNationId = 2, stateCode = "종전제의", term = 5),
        )

        val result = service.declareWar(1L, 1L, 2L)

        assertEquals("선전포고", result.stateCode)
        assertTrue(load(1).isDead)
        assertTrue(load(2).isDead)
        assertNotNull(diplomacies.values.firstOrNull { it.stateCode == "선전포고" && !it.isDead })
    }

    @Test
    fun `declareWar throws when non aggression exists or already at war`() {
        seed(createDiplomacy(id = 1, stateCode = "불가침", term = 10))
        assertThrows(IllegalStateException::class.java) { service.declareWar(1L, 1L, 2L) }

        diplomacies.clear()
        seed(createDiplomacy(id = 2, stateCode = "전쟁", term = 10))
        assertThrows(IllegalStateException::class.java) { service.declareWar(1L, 1L, 2L) }
    }

    @Test
    fun `proposeNonAggression creates proposal and message`() {
        val result = service.proposeNonAggression(1L, 1L, 2L)
        assertEquals("불가침제의", result.stateCode)
        assertEquals(1, messages.size)
    }

    @Test
    fun `proposeNonAggression blocks war pact and duplicate proposal cases`() {
        seed(createDiplomacy(id = 1, stateCode = "선전포고", term = 10))
        assertThrows(IllegalStateException::class.java) { service.proposeNonAggression(1L, 1L, 2L) }

        diplomacies.clear()
        seed(createDiplomacy(id = 2, stateCode = "불가침", term = 10))
        assertThrows(IllegalStateException::class.java) { service.proposeNonAggression(1L, 1L, 2L) }

        diplomacies.clear()
        seed(createDiplomacy(id = 3, stateCode = "불가침제의", term = 10))
        assertThrows(IllegalStateException::class.java) { service.proposeNonAggression(1L, 1L, 2L) }
    }

    @Test
    fun `acceptNonAggression transitions proposal to pact`() {
        seed(createDiplomacy(id = 10, stateCode = "불가침제의", term = 5))

        val result = service.acceptNonAggression(1L, 1L, 2L)

        assertTrue(load(10).isDead)
        assertEquals("불가침", result.stateCode)
        assertNotNull(diplomacies.values.firstOrNull { it.stateCode == "불가침" && !it.isDead })
    }

    @Test
    fun `acceptNonAggression throws when no proposal`() {
        assertThrows(IllegalStateException::class.java) { service.acceptNonAggression(1L, 1L, 2L) }
    }

    @Test
    fun `proposeBreakNonAggression creates break proposal`() {
        seed(createDiplomacy(id = 1, stateCode = "불가침", term = 10))

        val result = service.proposeBreakNonAggression(1L, 1L, 2L)

        assertEquals("불가침파기제의", result.stateCode)
        assertEquals(1, messages.size)
    }

    @Test
    fun `proposeBreakNonAggression throws when no pact`() {
        assertThrows(IllegalStateException::class.java) { service.proposeBreakNonAggression(1L, 1L, 2L) }
    }

    @Test
    fun `acceptBreakNonAggression kills proposal and pact`() {
        seed(
            createDiplomacy(id = 1, stateCode = "불가침", term = 10),
            createDiplomacy(id = 2, stateCode = "불가침파기제의", term = 10),
        )

        service.acceptBreakNonAggression(1L, 1L, 2L)

        assertTrue(load(1).isDead)
        assertTrue(load(2).isDead)
    }

    @Test
    fun `acceptBreakNonAggression throws when no pending proposal`() {
        assertThrows(IllegalStateException::class.java) { service.acceptBreakNonAggression(1L, 1L, 2L) }
    }

    @Test
    fun `proposeCeasefire creates proposal when at war`() {
        seed(createDiplomacy(id = 1, stateCode = "선전포고", term = 10))

        val result = service.proposeCeasefire(1L, 1L, 2L)

        assertEquals("종전제의", result.stateCode)
        assertEquals(1, messages.size)
    }

    @Test
    fun `proposeCeasefire throws when not at war or proposal exists`() {
        assertThrows(IllegalStateException::class.java) { service.proposeCeasefire(1L, 1L, 2L) }

        seed(
            createDiplomacy(id = 1, stateCode = "전쟁", term = 10),
            createDiplomacy(id = 2, stateCode = "종전제의", term = 5),
        )
        assertThrows(IllegalStateException::class.java) { service.proposeCeasefire(1L, 1L, 2L) }
    }

    @Test
    fun `acceptCeasefire kills proposal and war relations`() {
        seed(
            createDiplomacy(id = 1, stateCode = "종전제의", term = 5),
            createDiplomacy(id = 2, stateCode = "선전포고", term = 5),
            createDiplomacy(id = 3, stateCode = "전쟁", term = 5),
        )

        service.acceptCeasefire(1L, 1L, 2L)

        assertTrue(load(1).isDead)
        assertTrue(load(2).isDead)
        assertTrue(load(3).isDead)
    }

    @Test
    fun `acceptCeasefire throws when no pending ceasefire`() {
        assertThrows(IllegalStateException::class.java) { service.acceptCeasefire(1L, 1L, 2L) }
    }

    @Test
    fun `acceptDiplomaticMessage routes by messageType and marks responded`() {
        seed(createDiplomacy(id = 1, stateCode = "불가침제의", term = 5))
        val message = Message(
            id = 100,
            worldId = 1,
            mailboxCode = "diplomacy",
            messageType = DiplomacyService.MSG_NON_AGGRESSION_PROPOSAL,
            srcId = 1,
            destId = 2,
            payload = mutableMapOf("srcNationId" to 1L, "destNationId" to 2L),
        )
        messages[100] = message

        service.acceptDiplomaticMessage(1L, 100)

        assertTrue(load(1).isDead)
        val saved = messages[100]!!
        assertEquals(true, saved.meta["responded"])
        assertEquals(true, saved.meta["accepted"])
    }

    @Test
    fun `rejectDiplomaticMessage marks rejected`() {
        messages[100] = Message(
            id = 100,
            worldId = 1,
            mailboxCode = "diplomacy",
            messageType = DiplomacyService.MSG_NON_AGGRESSION_PROPOSAL,
            payload = mutableMapOf("srcNationId" to 1L, "destNationId" to 2L),
        )

        service.rejectDiplomaticMessage(1L, 100)

        val saved = messages[100]!!
        assertEquals(true, saved.meta["responded"])
        assertEquals(false, saved.meta["accepted"])
    }

    @Test
    fun `killAllRelationsForNation kills active only`() {
        seed(
            createDiplomacy(id = 1, srcNationId = 5, destNationId = 2, stateCode = "불가침", term = 10),
            createDiplomacy(id = 2, srcNationId = 3, destNationId = 5, stateCode = "선전포고", term = 10),
            createDiplomacy(id = 3, srcNationId = 5, destNationId = 4, stateCode = "불가침", term = 10, isDead = true),
        )

        service.killAllRelationsForNation(1L, 5L)

        assertTrue(load(1).isDead)
        assertTrue(load(2).isDead)
        assertTrue(load(3).isDead)
    }

    @Test
    fun `getRelations returns world relations and createRelation persists`() {
        seed(
            createDiplomacy(id = 1, srcNationId = 1, destNationId = 2, stateCode = "불가침", term = 10),
            createDiplomacy(id = 2, srcNationId = 2, destNationId = 3, stateCode = "선전포고", term = 10),
        )

        val all = service.getRelations(1L)
        assertEquals(2, all.size)

        val created = service.createRelation(1L, 1, 2, "불가침", 12)
        assertEquals("불가침", created.stateCode)
        assertNotNull(diplomacies[created.id])
        verify(diplomacyRepository, times(1)).save(ArgumentMatchers.any(Diplomacy::class.java))
    }
}
