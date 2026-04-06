package com.openlogh.command

import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.command.nation.Nation휴식
import com.openlogh.command.nation.che_감축
import com.openlogh.command.nation.che_포상
import com.openlogh.command.nation.che_국기변경
import com.openlogh.command.nation.che_국호변경
import com.openlogh.command.nation.che_몰수
import com.openlogh.command.nation.che_물자원조
import com.openlogh.command.nation.che_발령
import com.openlogh.command.nation.che_백성동원
import com.openlogh.command.nation.che_증축
import com.openlogh.command.nation.che_천도
import com.openlogh.engine.DiplomacyService
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.FactionRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.time.OffsetDateTime
import kotlin.random.Random

class NationResourceCommandTest {

    private val fixedRng = Random(42)

    private fun createGeneral(
        id: Long = 1,
        factionId: Long = 1,
        planetId: Long = 1,
        officerLevel: Short = 20,
        funds: Int = 1000,
        supplies: Int = 1000,
    ): Officer = Officer(
        id = id,
        sessionId = 1,
        name = "테스트장수$id",
        factionId = factionId,
        planetId = planetId,
        officerLevel = officerLevel,
        funds = funds,
        supplies = supplies,
        leadership = 50,
        command = 50,
        intelligence = 50,
        politics = 50,
        administration = 50,
        turnTime = OffsetDateTime.now(),
    )

    private fun createCity(
        id: Long = 1,
        factionId: Long = 1,
        supplyState: Short = 1,
    ): Planet = Planet(
        id = id,
        sessionId = 1,
        name = "테스트도시$id",
        factionId = factionId,
        supplyState = supplyState,
        production = 500,
        productionMax = 1000,
        commerce = 500,
        commerceMax = 1000,
        security = 500,
        securityMax = 1000,
        orbitalDefense = 500,
        orbitalDefenseMax = 1000,
        fortress = 500,
        fortressMax = 1000,
        population = 10000,
        populationMax = 50000,
        approval = 80,
    )

    private fun createNation(
        id: Long = 1,
        funds: Int = 200000,
        supplies: Int = 200000,
        strategicCmdLimit: Short = 0,
    ): Faction = Faction(
        id = id,
        sessionId = 1,
        name = "테스트국가$id",
        color = "#FF0000",
        funds = funds,
        supplies = supplies,
        level = 7,
        strategicCmdLimit = strategicCmdLimit,
        chiefOfficerId = 1,
    )

    private fun env(
        year: Int = 200,
        month: Int = 1,
        startYear: Int = 190,
    ): CommandEnv = CommandEnv(
        year = year,
        month = month,
        startYear = startYear,
        sessionId = 1,
        realtimeMode = false,
    )

    // ========== Golden Value Parity Tests ==========

    @Test
    fun `Nation휴식 always passes condition and runs`() {
        val cmd = Nation휴식(createGeneral(officerLevel = 1), env())

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.logs.isEmpty())
    }

    @Test
    fun `Nation휴식 golden value -- no entity mutation`() {
        val general = createGeneral(officerLevel = 1, funds = 500, supplies = 600)
        val beforeGold = general.funds
        val beforeRice = general.supplies
        val cmd = Nation휴식(general, env())

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP: Faction휴식 does nothing -- no entity mutation
        assertEquals(beforeGold, general.funds, "gold should not change")
        assertEquals(beforeRice, general.supplies, "rice should not change")
        assertEquals(0, result.logs.size, "no logs emitted")
    }

    @Test
    fun `포상 golden value -- rice reward entity diff and color-tagged log`() {
        val chief = createGeneral(officerLevel = 20, factionId = 1)
        val nation = createNation(id = 1, funds = 50000, supplies = 50000)
        val target = createGeneral(id = 2, factionId = 1, funds = 1000, supplies = 2000)
        val cmd = che_포상(chief, env(), mapOf("isGold" to false, "amount" to 3000))
        cmd.city = createCity(factionId = 1)
        cmd.nation = nation
        cmd.destOfficer = target

        val beforeNationRice = nation.supplies
        val beforeTargetRice = target.supplies
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: nation.supplies -= amount, target.supplies += amount
        assertEquals(beforeNationRice - 3000, nation.supplies, "nation rice reduced by 3000")
        assertEquals(beforeTargetRice + 3000, target.supplies, "target rice increased by 3000")
        // Color-tagged log (per D-05)
        assertTrue(result.logs.any { it.contains("수여") && it.contains("쌀") })
    }

    @Test
    fun `포상 golden value -- gold reward entity diff`() {
        val chief = createGeneral(officerLevel = 20, factionId = 1)
        val nation = createNation(id = 1, funds = 50000, supplies = 50000)
        val target = createGeneral(id = 2, factionId = 1, funds = 1000, supplies = 2000)
        val cmd = che_포상(chief, env(), mapOf("isGold" to true, "amount" to 5000))
        cmd.city = createCity(factionId = 1)
        cmd.nation = nation
        cmd.destOfficer = target

        val beforeNationGold = nation.funds
        val beforeTargetGold = target.funds
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: nation.funds -= amount, target.funds += amount
        assertEquals(beforeNationGold - 5000, nation.funds, "nation gold reduced by 5000")
        assertEquals(beforeTargetGold + 5000, target.funds, "target gold increased by 5000")
        assertTrue(result.logs.any { it.contains("수여") && it.contains("금") })
    }

    @Test
    fun `몰수 fails for non-chief and runs with resource transfer`() {
        val nonChief = createGeneral(officerLevel = 5, factionId = 1)
        val failCmd = che_몰수(nonChief, env(), mapOf("isGold" to true, "amount" to 500))
        failCmd.city = createCity(factionId = 1)
        failCmd.nation = createNation(id = 1)
        failCmd.destOfficer = createGeneral(id = 2, factionId = 1, funds = 1500)
        val fail = failCmd.checkFullCondition()
        assertTrue(fail is ConstraintResult.Fail)

        val chief = createGeneral(officerLevel = 20, factionId = 1)
        val nation = createNation(id = 1, funds = 10000, supplies = 10000)
        val target = createGeneral(id = 2, factionId = 1, funds = 1500, supplies = 2000)
        val cmd = che_몰수(chief, env(), mapOf("isGold" to true, "amount" to 500))
        cmd.city = createCity(factionId = 1)
        cmd.nation = nation
        cmd.destOfficer = target

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(1000, target.funds)
        assertEquals(10500, nation.funds)
        assertEquals(1, target.betray.toInt())
        assertTrue(result.logs.any { it.contains("몰수") })
    }

    @Test
    fun `감축 fails for non-chief and runs with level down and capacity shrink`() {
        val nonChief = createGeneral(officerLevel = 5)
        val failCmd = che_감축(nonChief, env())
        failCmd.city = createCity(factionId = 1)
        failCmd.nation = createNation(id = 1)
        val fail = failCmd.checkFullCondition()
        assertTrue(fail is ConstraintResult.Fail)

        val chief = createGeneral(officerLevel = 20)
        val nation = createNation(id = 1, funds = 10000, supplies = 10000)
        val city = createCity(factionId = 1)
        city.level = 2
        city.populationMax = 50000
        val cmd = che_감축(chief, env())
        cmd.city = city
        cmd.nation = nation

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(1, city.level.toInt())
        // PHP: reduces all 6 stats + maxes, refunds cost
        assertTrue(city.populationMax < 50000) // populationMax reduced
        assertTrue(city.productionMax < 1000) // productionMax reduced
        assertTrue(nation.funds > 10000) // cost refunded (gold increased)
        assertTrue(nation.supplies > 10000) // cost refunded (rice increased)
    }

    @Test
    fun `증축 fails with low nation resource and runs with level up and max increase`() {
        // cost = develCost(100) * 500 + 60000 = 110000
        // basegold=0, baserice=2000 → need 110000 gold, 112000 rice
        val chief = createGeneral(officerLevel = 20)
        val failCmd = che_증축(chief, env())
        failCmd.city = createCity(factionId = 1)
        failCmd.nation = createNation(id = 1, funds = 2000, supplies = 3000)
        failCmd.destPlanet = createCity(id = 1, factionId = 1).also { it.level = 5 }
        val fail = failCmd.checkFullCondition()
        assertTrue(fail is ConstraintResult.Fail)

        val nation = createNation(id = 1, funds = 200000, supplies = 200000)
        val city = createCity(factionId = 1)
        city.level = 5
        city.populationMax = 40000
        city.productionMax = 1000
        city.commerceMax = 1000
        city.securityMax = 1000
        city.orbitalDefenseMax = 1000
        city.fortressMax = 1000
        val cmd = che_증축(chief, env())
        cmd.city = city
        cmd.destPlanet = city
        cmd.nation = nation

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(6, city.level.toInt())
        assertEquals(140000, city.populationMax)
        assertEquals(3000, city.productionMax)
        assertEquals(3000, city.commerceMax)
        assertEquals(3000, city.securityMax)
        assertEquals(3000, city.orbitalDefenseMax)
        assertEquals(3000, city.fortressMax)
        assertEquals(90000, nation.funds)
        assertEquals(90000, nation.supplies)
        assertTrue(result.logs.any { it.contains("증축") })
    }

    @Test
    fun `발령 fails without destination general and runs to move officer city`() {
        val chief = createGeneral(officerLevel = 20)
        val failCmd = che_발령(chief, env())
        failCmd.city = createCity(factionId = 1)
        failCmd.nation = createNation(id = 1)
        failCmd.destPlanet = createCity(id = 2, factionId = 1)
        val fail = failCmd.checkFullCondition()
        assertTrue(fail is ConstraintResult.Fail)

        val target = createGeneral(id = 2, factionId = 1, planetId = 1)
        target.fleetId = 99
        val cmd = che_발령(chief, env())
        cmd.city = createCity(factionId = 1)
        cmd.nation = createNation(id = 1)
        cmd.destOfficer = target
        cmd.destPlanet = createCity(id = 2, factionId = 1)

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(2L, target.planetId)
        assertEquals(0L, target.fleetId)
        assertTrue(result.logs.any { it.contains("발령") })
    }

    @Test
    fun `천도 fails for non-chief and runs to change nation capital`() {
        val nonChief = createGeneral(officerLevel = 5)
        val failCmd = che_천도(nonChief, env())
        failCmd.city = createCity(factionId = 1)
        failCmd.destPlanet = createCity(id = 2, factionId = 1)
        failCmd.nation = createNation(id = 1)
        val fail = failCmd.checkFullCondition()
        assertTrue(fail is ConstraintResult.Fail)

        val chief = createGeneral(officerLevel = 20)
        val nation = createNation(id = 1, funds = 10000, supplies = 10000)
        nation.capitalPlanetId = 1
        val cmd = che_천도(chief, env())
        cmd.city = createCity(id = 1, factionId = 1)
        cmd.destPlanet = createCity(id = 2, factionId = 1)
        cmd.nation = nation

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(2L, nation.capitalPlanetId)
        assertEquals(8000, nation.funds)
        assertEquals(8000, nation.supplies)
        assertTrue(result.logs.any { it.contains("천도") })
    }

    @Test
    fun `천도 back to original city succeeds after first move`() {
        val chief = createGeneral(officerLevel = 20)
        val nation = createNation(id = 1, funds = 10000, supplies = 10000)
        nation.capitalPlanetId = 1
        val cityA = createCity(id = 1, factionId = 1)
        val cityB = createCity(id = 2, factionId = 1)

        // First move: A → B
        val cmd1 = che_천도(chief, env())
        cmd1.city = cityA
        cmd1.destPlanet = cityB
        cmd1.nation = nation
        val r1 = runBlocking { cmd1.run(fixedRng) }
        assertTrue(r1.success)
        assertEquals(2L, nation.capitalPlanetId)

        // Second move: B → A (back to original)
        val cmd2 = che_천도(chief, env())
        cmd2.city = cityB
        cmd2.destPlanet = cityA
        cmd2.nation = nation
        val check2 = cmd2.checkFullCondition()
        assertTrue(check2 is ConstraintResult.Pass, "Constraints should pass for return 천도: $check2")
        val r2 = runBlocking { cmd2.run(fixedRng) }
        assertTrue(r2.success, "Return 천도 should succeed")
        assertEquals(1L, nation.capitalPlanetId)
        assertEquals(6000, nation.funds)
        assertEquals(6000, nation.supplies)
    }

    @Test
    fun `천도 to current capital fails with already-capital message`() {
        val chief = createGeneral(officerLevel = 20)
        val nation = createNation(id = 1, funds = 10000, supplies = 10000)
        nation.capitalPlanetId = 1

        val cmd = che_천도(chief, env())
        cmd.city = createCity(id = 1, factionId = 1)
        cmd.destPlanet = createCity(id = 1, factionId = 1)
        cmd.nation = nation
        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(!result.success)
    }

    @Test
    fun `백성동원 fails when strategic command is blocked and runs with NPC saves`() {
        val chief = createGeneral(officerLevel = 20)
        val failCmd = che_백성동원(chief, env())
        failCmd.city = createCity(factionId = 1)
        failCmd.destPlanet = createCity(id = 2, factionId = 1)
        failCmd.nation = createNation(id = 1, strategicCmdLimit = 3)
        val fail = failCmd.checkFullCondition()
        assertTrue(fail is ConstraintResult.Fail)

        val officerRepository = mock(OfficerRepository::class.java)
        val mockServices = CommandServices(
            officerRepository = officerRepository,
            planetRepository = mock(PlanetRepository::class.java),
            factionRepository = mock(FactionRepository::class.java),
            diplomacyService = mock(DiplomacyService::class.java),
        )

        val nation = createNation(id = 1, strategicCmdLimit = 0)
        val destPlanet = createCity(id = 2, factionId = 1)
        destPlanet.population = 25000

        val cmd = che_백성동원(chief, env())
        cmd.city = createCity(factionId = 1)
        cmd.destPlanet = destPlanet
        cmd.nation = nation
        cmd.services = mockServices

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(9, nation.strategicCmdLimit.toInt())
        // PHP: orbitalDefense = GREATEST(def_max * 0.8, orbitalDefense), fortress = GREATEST(wall_max * 0.8, fortress)
        assertEquals(maxOf((destPlanet.orbitalDefenseMax * 0.8).toInt(), 500), destPlanet.orbitalDefense)
        assertEquals(maxOf((destPlanet.fortressMax * 0.8).toInt(), 500), destPlanet.fortress)
        assertTrue(result.logs.any { it.contains("백성") })
    }

    @Test
    fun `물자원조 fails for same destination nation and runs with resource transfer`() {
        val chief = createGeneral(officerLevel = 20, factionId = 1)
        val failCmd = che_물자원조(chief, env(), mapOf("goldAmount" to 500, "riceAmount" to 600))
        failCmd.city = createCity(factionId = 1)
        failCmd.nation = createNation(id = 1, funds = 10000, supplies = 10000)
        failCmd.destFaction = createNation(id = 1, funds = 1000, supplies = 1000)
        val fail = failCmd.checkFullCondition()
        assertTrue(fail is ConstraintResult.Fail)

        val nation = createNation(id = 1, funds = 10000, supplies = 10000)
        val allyNation = createNation(id = 2, funds = 1000, supplies = 2000)
        val cmd = che_물자원조(chief, env(), mapOf("goldAmount" to 500, "riceAmount" to 600))
        cmd.city = createCity(factionId = 1)
        cmd.nation = nation
        cmd.destFaction = allyNation

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(9500, nation.funds)
        assertEquals(9400, nation.supplies)
        assertEquals(1500, allyNation.funds)
        assertEquals(2600, allyNation.supplies)
        assertTrue(result.logs.any { it.contains("지원") })
    }

    @Test
    fun `국기변경 fails for non-chief and runs to mutate nation color`() {
        val failCmd = che_국기변경(createGeneral(officerLevel = 5), env(), mapOf("colorType" to "blue"))
        failCmd.city = createCity(factionId = 1)
        failCmd.nation = createNation(id = 1)
        val fail = failCmd.checkFullCondition()
        assertTrue(fail is ConstraintResult.Fail)

        val nation = createNation(id = 1)
        val cmd = che_국기변경(createGeneral(officerLevel = 20), env(), mapOf("colorType" to "blue"))
        cmd.city = createCity(factionId = 1)
        cmd.nation = nation

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals("blue", nation.color)
        assertTrue(result.logs.any { it.contains("국기") })
    }

    @Test
    fun `국호변경 fails for non-chief and runs to mutate nation name`() {
        val failCmd = che_국호변경(createGeneral(officerLevel = 5), env(), mapOf("factionName" to "신국호"))
        failCmd.city = createCity(factionId = 1)
        failCmd.nation = createNation(id = 1)
        val fail = failCmd.checkFullCondition()
        assertTrue(fail is ConstraintResult.Fail)

        val nation = createNation(id = 1)
        val cmd = che_국호변경(createGeneral(officerLevel = 20), env(), mapOf("factionName" to "신국호"))
        cmd.city = createCity(factionId = 1)
        cmd.nation = nation

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals("신국호", nation.name)
        assertTrue(result.logs.any { it.contains("국호") })
    }

    // ========== Golden Value: Color-tagged Log Parity (D-05) ==========

    @Test
    fun `몰수 golden value -- entity diff and betray increment`() {
        val chief = createGeneral(officerLevel = 20, factionId = 1)
        val nation = createNation(id = 1, funds = 10000, supplies = 10000)
        val target = createGeneral(id = 2, factionId = 1, funds = 3000, supplies = 4000)
        target.betray = 0
        val cmd = che_몰수(chief, env(), mapOf("isGold" to true, "amount" to 1500))
        cmd.city = createCity(factionId = 1)
        cmd.nation = nation
        cmd.destOfficer = target

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: target.funds -= amount, nation.funds += amount, target.betray++
        assertEquals(1500, target.funds, "target gold reduced from 3000 by 1500")
        assertEquals(11500, nation.funds, "nation gold increased from 10000 by 1500")
        assertEquals(1, target.betray.toInt(), "betray incremented by 1")
        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("몰수") })
    }

    @Test
    fun `감축 golden value -- level down and capacity shrink with cost refund`() {
        val chief = createGeneral(officerLevel = 20)
        val nation = createNation(id = 1, funds = 10000, supplies = 10000)
        val city = createCity(factionId = 1)
        city.level = 3
        city.populationMax = 80000
        city.productionMax = 2000
        city.commerceMax = 2000
        city.securityMax = 2000
        city.orbitalDefenseMax = 2000
        city.fortressMax = 2000
        val cmd = che_감축(chief, env())
        cmd.city = city
        cmd.nation = nation

        val beforeLevel = city.level.toInt()
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: level decremented by 1
        assertEquals(beforeLevel - 1, city.level.toInt(), "city level decremented by 1")
        // PHP: all max values reduced, current values clamped
        assertTrue(city.populationMax < 80000, "populationMax reduced")
        assertTrue(city.productionMax < 2000, "productionMax reduced")
        assertTrue(city.commerceMax < 2000, "commerceMax reduced")
        // PHP: nation receives cost refund
        assertTrue(nation.funds > 10000, "gold refunded")
        assertTrue(nation.supplies > 10000, "rice refunded")
    }

    @Test
    fun `증축 golden value -- level 5 to 6 exact values`() {
        val chief = createGeneral(officerLevel = 20)
        val nation = createNation(id = 1, funds = 200000, supplies = 200000)
        val city = createCity(factionId = 1)
        city.level = 5
        city.populationMax = 40000
        city.productionMax = 1000
        city.commerceMax = 1000
        city.securityMax = 1000
        city.orbitalDefenseMax = 1000
        city.fortressMax = 1000
        val cmd = che_증축(chief, env())
        cmd.city = city
        cmd.destPlanet = city
        cmd.nation = nation

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: level 5 → 6
        assertEquals(6, city.level.toInt())
        // PHP: populationMax += 100000, each max += 2000
        assertEquals(140000, city.populationMax, "populationMax = 40000 + 100000")
        assertEquals(3000, city.productionMax, "productionMax = 1000 + 2000")
        assertEquals(3000, city.commerceMax, "commerceMax = 1000 + 2000")
        assertEquals(3000, city.securityMax, "securityMax = 1000 + 2000")
        assertEquals(3000, city.orbitalDefenseMax, "orbitalDefenseMax = 1000 + 2000")
        assertEquals(3000, city.fortressMax, "fortressMax = 1000 + 2000")
        // PHP: cost = develCost(100) * 500 + 60000 = 110000
        assertEquals(90000, nation.funds, "funds = 200000 - 110000")
        assertEquals(90000, nation.supplies, "supplies = 200000 - 110000")
        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("증축") })
    }

    @Test
    fun `발령 golden value -- officerPlanet change and fleetId reset`() {
        val chief = createGeneral(officerLevel = 20)
        val target = createGeneral(id = 2, factionId = 1, planetId = 1)
        target.fleetId = 99
        val destPlanet = createCity(id = 3, factionId = 1)
        val cmd = che_발령(chief, env())
        cmd.city = createCity(factionId = 1)
        cmd.nation = createNation(id = 1)
        cmd.destOfficer = target
        cmd.destPlanet = destPlanet

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: target.planetId = destPlanet.id, target.fleetId = 0
        assertEquals(3L, target.planetId, "target moved to dest city")
        assertEquals(0L, target.fleetId, "fleetId reset to 0")
        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("발령") })
    }

    @Test
    fun `천도 golden value -- capital change and gold rice cost`() {
        val chief = createGeneral(officerLevel = 20)
        val nation = createNation(id = 1, funds = 20000, supplies = 20000)
        nation.capitalPlanetId = 1
        val cmd = che_천도(chief, env())
        cmd.city = createCity(id = 1, factionId = 1)
        cmd.destPlanet = createCity(id = 5, factionId = 1)
        cmd.nation = nation

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: nation.capitalPlanetId = destPlanet.id, cost = 2000 gold + 2000 rice
        assertEquals(5L, nation.capitalPlanetId, "capital changed to dest city")
        assertEquals(18000, nation.funds, "funds = 20000 - 2000")
        assertEquals(18000, nation.supplies, "supplies = 20000 - 2000")
        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("천도") })
    }

    @Test
    fun `백성동원 golden value -- population halved and orbitalDefense fortress clamped`() {
        val chief = createGeneral(officerLevel = 20)
        val officerRepository = mock(OfficerRepository::class.java)
        val mockServices = CommandServices(
            officerRepository = officerRepository,
            planetRepository = mock(PlanetRepository::class.java),
            factionRepository = mock(FactionRepository::class.java),
            diplomacyService = mock(DiplomacyService::class.java),
        )

        val nation = createNation(id = 1, strategicCmdLimit = 0)
        val destPlanet = createCity(id = 2, factionId = 1)
        destPlanet.population = 40000
        destPlanet.orbitalDefense = 200
        destPlanet.fortress = 300
        destPlanet.orbitalDefenseMax = 1000
        destPlanet.fortressMax = 1000

        val cmd = che_백성동원(chief, env())
        cmd.city = createCity(factionId = 1)
        cmd.destPlanet = destPlanet
        cmd.nation = nation
        cmd.services = mockServices

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: strategicCmdLimit = 9
        assertEquals(9, nation.strategicCmdLimit.toInt())
        // PHP: orbitalDefense = GREATEST(def_max * 0.8, orbitalDefense) → max(800, 200) = 800
        assertEquals(800, destPlanet.orbitalDefense, "orbitalDefense clamped to orbitalDefenseMax * 0.8")
        // PHP: fortress = GREATEST(wall_max * 0.8, fortress) → max(800, 300) = 800
        assertEquals(800, destPlanet.fortress, "fortress clamped to fortressMax * 0.8")
        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("백성") })
    }

    @Test
    fun `물자원조 golden value -- exact resource transfer both directions`() {
        val chief = createGeneral(officerLevel = 20, factionId = 1)
        val nation = createNation(id = 1, funds = 50000, supplies = 30000)
        val allyNation = createNation(id = 2, funds = 5000, supplies = 8000)
        val cmd = che_물자원조(chief, env(), mapOf("goldAmount" to 12000, "riceAmount" to 7000))
        cmd.city = createCity(factionId = 1)
        cmd.nation = nation
        cmd.destFaction = allyNation

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: src -= amount, dest += amount
        assertEquals(38000, nation.funds, "nation funds = 50000 - 12000")
        assertEquals(23000, nation.supplies, "nation supplies = 30000 - 7000")
        assertEquals(17000, allyNation.funds, "ally funds = 5000 + 12000")
        assertEquals(15000, allyNation.supplies, "ally supplies = 8000 + 7000")
        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("지원") })
    }

    @Test
    fun `국기변경 golden value -- exact color mutation`() {
        val nation = createNation(id = 1)
        assertEquals("#FF0000", nation.color, "initial color is red")
        val cmd = che_국기변경(createGeneral(officerLevel = 20), env(), mapOf("colorType" to "#00FF00"))
        cmd.city = createCity(factionId = 1)
        cmd.nation = nation

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: nation.color = arg.colorType
        assertEquals("#00FF00", nation.color, "color mutated to green")
        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("국기") })
    }

    @Test
    fun `국호변경 golden value -- exact name mutation`() {
        val nation = createNation(id = 1)
        assertEquals("테스트국가1", nation.name, "initial name")
        val cmd = che_국호변경(createGeneral(officerLevel = 20), env(), mapOf("factionName" to "대한제국"))
        cmd.city = createCity(factionId = 1)
        cmd.nation = nation

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: nation.name = arg.factionName
        assertEquals("대한제국", nation.name, "name mutated to 대한제국")
        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("국호") })
    }
}
