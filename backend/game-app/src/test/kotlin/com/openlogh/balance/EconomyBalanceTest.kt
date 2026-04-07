package com.openlogh.balance

import com.openlogh.engine.Gin7EconomyService
import com.openlogh.entity.Faction
import com.openlogh.entity.Planet
import com.openlogh.entity.SessionState
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.PlanetRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyList
import kotlin.math.abs

/**
 * 경제 밸런스 검증 테스트 (TEST-03)
 *
 * Gin7EconomyService를 Spring Context 없이 순수 단위 테스트로 검증한다.
 * FactionRepository, PlanetRepository는 Mockito mock으로 대체한다.
 * DB 없이 실행 가능하다.
 *
 * 실제 엔티티 필드 타입:
 *  - Faction.funds: Int
 *  - Faction.taxRate: Short
 *  - Planet.supplyState: Short (1=공급, 0=고립)
 *  - Planet.approval: Float (class body var)
 *
 * 세수 공식: planet.commerce * faction.taxRate.toInt() / 100
 *
 * 밸런스 이슈 발견 시 이 블록에 @Disabled + 코멘트로 문서화:
 * (현재 발견된 이슈 없음)
 */
@Suppress("unchecked_cast")
class EconomyBalanceTest {

    private lateinit var factionRepository: FactionRepository
    private lateinit var planetRepository: PlanetRepository
    private lateinit var economyService: Gin7EconomyService

    @BeforeEach
    fun setUp() {
        factionRepository = mock(FactionRepository::class.java)
        planetRepository  = mock(PlanetRepository::class.java)
        economyService    = Gin7EconomyService(factionRepository, planetRepository)
    }

    private fun makeWorld(month: Short = 1): SessionState {
        val world = mock(SessionState::class.java)
        `when`(world.id).thenReturn(1)
        `when`(world.currentMonth).thenReturn(month)
        return world
    }

    /** Faction 실 객체: id 0은 내부 skip 로직 때문에 1 이상으로 설정 */
    private fun makeFaction(id: Long = 1L, taxRate: Int = 30, funds: Int = 0): Faction =
        Faction().apply {
            this.id = id
            this.taxRate = taxRate.toShort()
            this.funds = funds
        }

    /**
     * Planet 실 객체.
     * supplyState: Short (1=공급됨, 0=고립)
     * approval: constructor parameter로 전달
     */
    private fun makePlanet(
        factionId: Long = 1L,
        population: Int = 100_000,
        commerce: Int = 1_000,
        supplyState: Short = 1,
        approval: Float = 50f,
    ): Planet = Planet(
        factionId  = factionId,
        population = population,
        populationMax = population * 2,
        production = 1_000,
        productionMax = 2_000,
        commerce   = commerce,
        commerceMax = commerce * 2,
        supplyState = supplyState,
        approval   = approval,
    )

    @Test
    fun `taxRate=30 행성의 세수가 0보다 크다`() {
        val world   = makeWorld(month = 1)
        val faction = makeFaction(taxRate = 30, funds = 0)
        val planet  = makePlanet(commerce = 1_000, supplyState = 1)

        `when`(factionRepository.findBySessionId(anyLong())).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(anyLong())).thenReturn(listOf(planet))
        `when`(factionRepository.saveAll(anyList())).thenAnswer { it.arguments[0] }
        `when`(planetRepository.saveAll(anyList())).thenAnswer { it.arguments[0] }

        economyService.processMonthly(world)

        // commerce=1000, taxRate=30 → 세수 = 1000 * 30 / 100 = 300
        assertThat(faction.funds)
            .withFailMessage("taxRate=30 행성 세수가 0: funds=${faction.funds}")
            .isGreaterThan(0)
    }

    @Test
    fun `supplyState=0인 행성은 세수가 0이다`() {
        val world   = makeWorld(month = 1)
        val faction = makeFaction(taxRate = 30, funds = 0)
        val planet  = makePlanet(commerce = 1_000, supplyState = 0)  // 고립 행성

        `when`(factionRepository.findBySessionId(anyLong())).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(anyLong())).thenReturn(listOf(planet))
        `when`(factionRepository.saveAll(anyList())).thenAnswer { it.arguments[0] }
        `when`(planetRepository.saveAll(anyList())).thenAnswer { it.arguments[0] }

        economyService.processMonthly(world)

        assertThat(faction.funds)
            .withFailMessage("supplyState=0 행성에서 세수 발생: funds=${faction.funds}")
            .isEqualTo(0)
    }

    @Test
    fun `세수가 commerce times taxRate div 100의 50퍼센트 이내다`() {
        val commerce = 1_000
        val taxRate  = 30
        val world    = makeWorld(month = 1)
        val faction  = makeFaction(taxRate = taxRate, funds = 0)
        val planet   = makePlanet(commerce = commerce, supplyState = 1)

        `when`(factionRepository.findBySessionId(anyLong())).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(anyLong())).thenReturn(listOf(planet))
        `when`(factionRepository.saveAll(anyList())).thenAnswer { it.arguments[0] }
        `when`(planetRepository.saveAll(anyList())).thenAnswer { it.arguments[0] }

        economyService.processMonthly(world)

        val expected  = commerce * taxRate / 100
        val actual    = faction.funds
        val tolerance = (expected * 0.5).coerceAtLeast(1.0)

        assertThat(abs(actual - expected).toDouble())
            .withFailMessage(
                "세수=$actual, 기대=$expected (±50%% 범위 초과). commerce=$commerce, taxRate=$taxRate"
            )
            .isLessThanOrEqualTo(tolerance)
    }

    @Test
    fun `세금 월이 아닌 경우 세수가 없다`() {
        // month=2 는 세금 월이 아님 (1,4,7,10만 세금 월)
        val world   = makeWorld(month = 2)
        val faction = makeFaction(taxRate = 30, funds = 0)
        val planet  = makePlanet(commerce = 1_000, supplyState = 1)

        `when`(factionRepository.findBySessionId(anyLong())).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(anyLong())).thenReturn(listOf(planet))
        `when`(factionRepository.saveAll(anyList())).thenAnswer { it.arguments[0] }
        `when`(planetRepository.saveAll(anyList())).thenAnswer { it.arguments[0] }

        economyService.processMonthly(world)

        assertThat(faction.funds)
            .withFailMessage("세금 월이 아닌데 세수 발생: funds=${faction.funds}")
            .isEqualTo(0)
    }

    @Test
    fun `isTaxMonth은 1 4 7 10월만 true다`() {
        val taxMonths    = listOf<Short>(1, 4, 7, 10)
        val nonTaxMonths = (1..12).map { it.toShort() }.filter { it !in taxMonths }

        taxMonths.forEach { month ->
            assertThat(economyService.isTaxMonth(month))
                .withFailMessage("$month 월은 세금 월이어야 한다")
                .isTrue()
        }
        nonTaxMonths.forEach { month ->
            assertThat(economyService.isTaxMonth(month))
                .withFailMessage("$month 월은 세금 월이 아니어야 한다")
                .isFalse()
        }
    }

    @Test
    fun `taxRate=30 초과 시 행성 approval이 하락한다`() {
        val world   = makeWorld(month = 1)
        val faction = makeFaction(taxRate = 50, funds = 0) // taxRate 50 > 30
        val planet  = makePlanet(commerce = 1_000, supplyState = 1, approval = 50f)

        `when`(factionRepository.findBySessionId(anyLong())).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(anyLong())).thenReturn(listOf(planet))
        `when`(factionRepository.saveAll(anyList())).thenAnswer { it.arguments[0] }
        `when`(planetRepository.saveAll(anyList())).thenAnswer { it.arguments[0] }

        val approvalBefore = planet.approval
        economyService.processMonthly(world)

        assertThat(planet.approval)
            .withFailMessage("taxRate=50 > 30인데 approval 하락 없음: before=$approvalBefore, after=${planet.approval}")
            .isLessThan(approvalBefore)
    }
}
