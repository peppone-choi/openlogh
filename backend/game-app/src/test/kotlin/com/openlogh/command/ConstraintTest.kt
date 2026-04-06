package com.openlogh.command

import com.openlogh.command.constraint.*
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class ConstraintTest {

    private fun createGeneral(
        factionId: Long = 1,
        planetId: Long = 1,
        funds: Int = 1000,
        supplies: Int = 1000,
        ships: Int = 100,
        training: Short = 50,
        morale: Short = 50,
        officerLevel: Short = 0,
        fleetId: Long = 0,
        npcState: Short = 0,
        age: Short = 30,
    ): Officer {
        return Officer(
            id = 1,
            sessionId = 1,
            name = "테스트",
            factionId = factionId,
            planetId = planetId,
            funds = funds,
            supplies = supplies,
            ships = ships,
            training = training,
            morale = morale,
            officerLevel = officerLevel,
            fleetId = fleetId,
            npcState = npcState,
            age = age,
            turnTime = OffsetDateTime.now(),
        )
    }

    private fun createCity(
        factionId: Long = 1,
        supplyState: Short = 1,
        production: Int = 500,
        productionMax: Int = 1000,
        commerce: Int = 500,
        commerceMax: Int = 1000,
        security: Int = 500,
        securityMax: Int = 1000,
        orbitalDefense: Int = 500,
        orbitalDefenseMax: Int = 1000,
        fortress: Int = 500,
        fortressMax: Int = 1000,
        population: Int = 10000,
        populationMax: Int = 50000,
        approval: Float = 80f,
    ): Planet {
        return Planet(
            id = 1,
            sessionId = 1,
            name = "테스트도시",
            factionId = factionId,
            supplyState = supplyState,
            production = production,
            productionMax = productionMax,
            commerce = commerce,
            commerceMax = commerceMax,
            security = security,
            securityMax = securityMax,
            orbitalDefense = orbitalDefense,
            orbitalDefenseMax = orbitalDefenseMax,
            fortress = fortress,
            fortressMax = fortressMax,
            population = population,
            populationMax = populationMax,
            approval = approval,
        )
    }

    private fun createNation(
        id: Long = 1,
        funds: Int = 10000,
        supplies: Int = 10000,
        level: Short = 1,
        capitalPlanetId: Long? = 1,
        strategicCmdLimit: Short = 0,
    ): Faction {
        return Faction(
            id = id,
            sessionId = 1,
            name = "테스트국가",
            color = "#FF0000",
            funds = funds,
            supplies = supplies,
            level = level,
            capitalPlanetId = capitalPlanetId,
            strategicCmdLimit = strategicCmdLimit,
        )
    }

    private fun ctx(
        general: Officer = createGeneral(),
        city: Planet? = null,
        nation: Faction? = null,
        destOfficer: Officer? = null,
        destPlanet: Planet? = null,
        destFaction: Faction? = null,
    ) = ConstraintContext(
        general = general,
        city = city,
        nation = nation,
        destOfficer = destOfficer,
        destPlanet = destPlanet,
        destFaction = destFaction,
    )

    // ========== NotBeNeutral ==========

    @Test
    fun `NotBeNeutral passes when general has nation`() {
        val result = NotBeNeutral().test(ctx(general = createGeneral(factionId = 1)))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `NotBeNeutral fails when general has no nation`() {
        val result = NotBeNeutral().test(ctx(general = createGeneral(factionId = 0)))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("소속 국가"))
    }

    // ========== BeNeutral ==========

    @Test
    fun `BeNeutral passes when general is neutral`() {
        val result = BeNeutral().test(ctx(general = createGeneral(factionId = 0)))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `BeNeutral fails when general has nation`() {
        val result = BeNeutral().test(ctx(general = createGeneral(factionId = 1)))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("재야"))
    }

    // ========== OccupiedCity ==========

    @Test
    fun `OccupiedCity passes when city belongs to general nation`() {
        val general = createGeneral(factionId = 1)
        val city = createCity(factionId = 1)
        val result = OccupiedCity().test(ctx(general = general, city = city))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `OccupiedCity fails when city belongs to different nation`() {
        val general = createGeneral(factionId = 1)
        val city = createCity(factionId = 2)
        val result = OccupiedCity().test(ctx(general = general, city = city))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("아군 도시"))
    }

    @Test
    fun `OccupiedCity fails when no city provided`() {
        val result = OccupiedCity().test(ctx())
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("도시 정보"))
    }

    // ========== SuppliedCity ==========

    @Test
    fun `SuppliedCity passes when city is supplied`() {
        val city = createCity(supplyState = 1)
        val result = SuppliedCity().test(ctx(city = city))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `SuppliedCity fails when city supply is cut`() {
        val city = createCity(supplyState = 0)
        val result = SuppliedCity().test(ctx(city = city))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("보급"))
    }

    // ========== ReqGeneralGold ==========

    @Test
    fun `ReqGeneralGold passes when general has enough gold`() {
        val general = createGeneral(funds = 500)
        val result = ReqGeneralGold(500).test(ctx(general = general))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `ReqGeneralGold passes when general has more than required`() {
        val general = createGeneral(funds = 1000)
        val result = ReqGeneralGold(500).test(ctx(general = general))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `ReqGeneralGold fails when general lacks gold`() {
        val general = createGeneral(funds = 99)
        val result = ReqGeneralGold(100).test(ctx(general = general))
        assertTrue(result is ConstraintResult.Fail)
        val failResult = result as ConstraintResult.Fail
        assertTrue(failResult.reason.contains("자금"))
        assertTrue(failResult.reason.contains("100"))
        assertTrue(failResult.reason.contains("99"))
    }

    // ========== ReqGeneralRice ==========

    @Test
    fun `ReqGeneralRice passes when general has enough rice`() {
        val general = createGeneral(supplies = 500)
        val result = ReqGeneralRice(500).test(ctx(general = general))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `ReqGeneralRice fails when general lacks rice`() {
        val general = createGeneral(supplies = 50)
        val result = ReqGeneralRice(100).test(ctx(general = general))
        assertTrue(result is ConstraintResult.Fail)
        val failResult = result as ConstraintResult.Fail
        assertTrue(failResult.reason.contains("군량"))
    }

    // ========== ReqGeneralCrew ==========

    @Test
    fun `ReqGeneralCrew passes with enough ships`() {
        val general = createGeneral(ships = 100)
        val result = ReqGeneralCrew(100).test(ctx(general = general))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `ReqGeneralCrew fails with zero ships`() {
        val general = createGeneral(ships = 0)
        val result = ReqGeneralCrew().test(ctx(general = general))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("병사"))
    }

    // ========== RemainCityCapacity ==========

    @Test
    fun `RemainCityCapacity passes when production is below max`() {
        val city = createCity(production = 500, productionMax = 1000)
        val result = RemainCityCapacity("production", "농지 개간").test(ctx(city = city))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `RemainCityCapacity fails when production equals max`() {
        val city = createCity(production = 1000, productionMax = 1000)
        val result = RemainCityCapacity("production", "농지 개간").test(ctx(city = city))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("최대치"))
    }

    @Test
    fun `RemainCityCapacity works for commerce`() {
        val city = createCity(commerce = 1000, commerceMax = 1000)
        val result = RemainCityCapacity("commerce", "상업 투자").test(ctx(city = city))
        assertTrue(result is ConstraintResult.Fail)
    }

    @Test
    fun `RemainCityCapacity works for fortress`() {
        val city = createCity(fortress = 999, fortressMax = 1000)
        val result = RemainCityCapacity("fortress", "성벽 보수").test(ctx(city = city))
        assertTrue(result is ConstraintResult.Pass)
    }

    // ========== BeLord / BeChief ==========

    @Test
    fun `BeLord passes for lord`() {
        val general = createGeneral(officerLevel = 20)
        val result = BeLord().test(ctx(general = general))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `BeLord fails for non-lord`() {
        val general = createGeneral(officerLevel = 5)
        val result = BeLord().test(ctx(general = general))
        assertTrue(result is ConstraintResult.Fail)
    }

    @Test
    fun `BeChief passes for officer level 20 or above`() {
        val general = createGeneral(officerLevel = 20)
        val result = BeChief().test(ctx(general = general))
        assertTrue(result is ConstraintResult.Pass)
    }

    // ========== NotLord ==========

    @Test
    fun `NotLord passes for non-lord`() {
        val general = createGeneral(officerLevel = 5)
        val result = NotLord().test(ctx(general = general))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `NotLord fails for lord`() {
        val general = createGeneral(officerLevel = 20)
        val result = NotLord().test(ctx(general = general))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("군주"))
    }

    // ========== ReqOfficerLevel ==========

    @Test
    fun `ReqOfficerLevel passes when level is sufficient`() {
        val general = createGeneral(officerLevel = 5)
        val result = ReqOfficerLevel(5).test(ctx(general = general))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `ReqOfficerLevel fails when level is insufficient`() {
        val general = createGeneral(officerLevel = 3)
        val result = ReqOfficerLevel(5).test(ctx(general = general))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("관직"))
    }

    // ========== ReqGeneralTrainMargin / ReqGeneralAtmosMargin ==========

    @Test
    fun `ReqGeneralTrainMargin passes when train is below max`() {
        val general = createGeneral(training = 50)
        val result = ReqGeneralTrainMargin(80).test(ctx(general = general))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `ReqGeneralTrainMargin fails when train is at max`() {
        val general = createGeneral(training = 80)
        val result = ReqGeneralTrainMargin(80).test(ctx(general = general))
        assertTrue(result is ConstraintResult.Fail)
    }

    @Test
    fun `ReqGeneralAtmosMargin passes when morale is below max`() {
        val general = createGeneral(morale = 50)
        val result = ReqGeneralAtmosMargin(80).test(ctx(general = general))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `ReqGeneralAtmosMargin fails when morale is at max`() {
        val general = createGeneral(morale = 80)
        val result = ReqGeneralAtmosMargin(80).test(ctx(general = general))
        assertTrue(result is ConstraintResult.Fail)
    }

    // ========== MustBeTroopLeader ==========

    @Test
    fun `MustBeTroopLeader passes when general is troop leader`() {
        val general = createGeneral(fleetId = 1) // fleetId == general.id
        val result = MustBeTroopLeader().test(ctx(general = general))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `MustBeTroopLeader passes when fleetId is zero`() {
        val general = createGeneral(fleetId = 0)
        val result = MustBeTroopLeader().test(ctx(general = general))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `MustBeTroopLeader fails when general belongs to another troop`() {
        val general = createGeneral(fleetId = 99)
        val result = MustBeTroopLeader().test(ctx(general = general))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("부대장"))
    }

    // ========== NotSameDestCity ==========

    @Test
    fun `NotSameDestCity passes when dest city differs`() {
        val general = createGeneral(planetId = 1)
        val destPlanet = createCity().apply { id = 2 }
        val result = NotSameDestCity().test(ctx(general = general, destPlanet = destPlanet))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `NotSameDestCity fails when dest city is same`() {
        val general = createGeneral(planetId = 1)
        val destPlanet = createCity().apply { id = 1 }
        val result = NotSameDestCity().test(ctx(general = general, destPlanet = destPlanet))
        assertTrue(result is ConstraintResult.Fail)
    }

    // ========== ReqNationGold / ReqNationRice ==========

    @Test
    fun `ReqNationGold passes with enough gold`() {
        val nation = createNation(funds = 5000)
        val result = ReqNationGold(5000).test(ctx(nation = nation))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `ReqNationGold fails without enough gold`() {
        val nation = createNation(funds = 100)
        val result = ReqNationGold(5000).test(ctx(nation = nation))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("국고"))
    }

    @Test
    fun `ReqNationRice passes with enough rice`() {
        val nation = createNation(supplies = 5000)
        val result = ReqNationRice(5000).test(ctx(nation = nation))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `ReqNationRice fails without enough rice`() {
        val nation = createNation(supplies = 100)
        val result = ReqNationRice(5000).test(ctx(nation = nation))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("병량"))
    }

    // ========== ExistsDestGeneral / FriendlyDestGeneral ==========

    @Test
    fun `ExistsDestGeneral passes when dest general exists`() {
        val destOfficer = createGeneral()
        val result = ExistsDestGeneral().test(ctx(destOfficer = destOfficer))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `ExistsDestGeneral fails when dest general is null`() {
        val result = ExistsDestGeneral().test(ctx())
        assertTrue(result is ConstraintResult.Fail)
    }

    @Test
    fun `FriendlyDestGeneral passes when same nation`() {
        val general = createGeneral(factionId = 1)
        val destOfficer = createGeneral(factionId = 1)
        val result = FriendlyDestGeneral().test(ctx(general = general, destOfficer = destOfficer))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `FriendlyDestGeneral fails when different nation`() {
        val general = createGeneral(factionId = 1)
        val destOfficer = createGeneral(factionId = 2)
        val result = FriendlyDestGeneral().test(ctx(general = general, destOfficer = destOfficer))
        assertTrue(result is ConstraintResult.Fail)
    }

    // ========== WanderingNation ==========

    @Test
    fun `WanderingNation passes when nation level is 0`() {
        val nation = createNation(level = 0)
        val result = WanderingNation().test(ctx(nation = nation))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `WanderingNation passes when no nation provided`() {
        val result = WanderingNation().test(ctx())
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `WanderingNation fails when nation has level`() {
        val nation = createNation(level = 3)
        val result = WanderingNation().test(ctx(nation = nation))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("방랑군"))
    }

    // ========== BeOpeningPart / NotOpeningPart ==========

    @Test
    fun `BeOpeningPart passes during opening`() {
        // relYear < 1 => opening part
        val result = BeOpeningPart(0).test(ctx())
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `BeOpeningPart fails after opening`() {
        val result = BeOpeningPart(5).test(ctx())
        assertTrue(result is ConstraintResult.Fail)
    }

    @Test
    fun `NotOpeningPart passes after opening`() {
        val result = NotOpeningPart(5).test(ctx())
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `NotOpeningPart fails during opening`() {
        val result = NotOpeningPart(0).test(ctx())
        assertTrue(result is ConstraintResult.Fail)
    }

    // ========== AlwaysFail ==========

    @Test
    fun `AlwaysFail always fails with given reason`() {
        val result = AlwaysFail("사용 불가").test(ctx())
        assertTrue(result is ConstraintResult.Fail)
        assertEquals("사용 불가", (result as ConstraintResult.Fail).reason)
    }

    // ========== MustBeNPC ==========

    @Test
    fun `MustBeNPC passes for NPC`() {
        val general = createGeneral(npcState = 1)
        val result = MustBeNPC().test(ctx(general = general))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `MustBeNPC fails for player general`() {
        val general = createGeneral(npcState = 0)
        val result = MustBeNPC().test(ctx(general = general))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("NPC"))
    }

    // ========== NotCapital ==========

    @Test
    fun `NotCapital passes when not at capital`() {
        val general = createGeneral(planetId = 2)
        val nation = createNation(capitalPlanetId = 1)
        val result = NotCapital().test(ctx(general = general, nation = nation))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `NotCapital fails when at capital`() {
        val general = createGeneral(planetId = 1)
        val nation = createNation(capitalPlanetId = 1)
        val result = NotCapital().test(ctx(general = general, nation = nation))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("수도"))
    }

    // ========== AvailableStrategicCommand ==========

    @Test
    fun `AvailableStrategicCommand passes when limit is zero`() {
        val nation = createNation(strategicCmdLimit = 0)
        val result = AvailableStrategicCommand().test(ctx(nation = nation))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `AvailableStrategicCommand fails when limit is positive`() {
        val nation = createNation(strategicCmdLimit = 5)
        val result = AvailableStrategicCommand().test(ctx(nation = nation))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("전략 명령"))
    }

    // ========== ReqGeneralAge ==========

    @Test
    fun `ReqGeneralAge passes when old enough`() {
        val general = createGeneral(age = 30)
        val result = ReqGeneralAge(20).test(ctx(general = general))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `ReqGeneralAge fails when too young`() {
        val general = createGeneral(age = 15)
        val result = ReqGeneralAge(20).test(ctx(general = general))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("나이"))
    }

    // ========== NeutralCity ==========

    @Test
    fun `NeutralCity passes when city is neutral`() {
        val city = createCity(factionId = 0)
        val result = NeutralCity().test(ctx(city = city))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `NeutralCity fails when city is owned`() {
        val city = createCity(factionId = 1)
        val result = NeutralCity().test(ctx(city = city))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("공백지"))
    }

    // ========== RemainCityTrust ==========

    @Test
    fun `RemainCityTrust passes when approval is below max`() {
        val city = createCity(approval = 80f)
        val result = RemainCityTrust(100).test(ctx(city = city))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `RemainCityTrust fails when approval is at max`() {
        val city = createCity(approval = 100f)
        val result = RemainCityTrust(100).test(ctx(city = city))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("민심"))
    }

    // ========== ReqGeneralStatValue ==========

    @Test
    fun `ReqGeneralStatValue passes when stat is sufficient`() {
        val general = createGeneral()
        general.intelligence = 80
        val result = ReqGeneralStatValue({ it.intelligence }, "지력", 50).test(ctx(general = general))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `ReqGeneralStatValue fails when stat is insufficient`() {
        val general = createGeneral()
        general.intelligence = 30
        val result = ReqGeneralStatValue({ it.intelligence }, "지력", 50).test(ctx(general = general))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("지력"))
    }

    // ========== EmperorSystemActive ==========

    @Test
    fun `EmperorSystemActive passes when emperor system is active`() {
        val context = ConstraintContext(general = createGeneral(), env = mapOf("emperorSystem" to true))
        val result = EmperorSystemActive().test(context)
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `EmperorSystemActive fails when emperor system is inactive`() {
        val context = ConstraintContext(general = createGeneral(), env = mapOf("emperorSystem" to false))
        val result = EmperorSystemActive().test(context)
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("황제 시스템"))
    }

    // ========== NationNotExempt ==========

    @Test
    fun `NationNotExempt passes when nation is not exempt`() {
        val nation = createNation()
        nation.meta["imperialStatus"] = "independent"
        val result = NationNotExempt().test(ctx(nation = nation))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `NationNotExempt fails when nation is exempt`() {
        val nation = createNation()
        nation.meta["imperialStatus"] = "exempt"
        val result = NationNotExempt().test(ctx(nation = nation))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("독자적 체계"))
    }

    // ========== NationIsIndependent ==========

    @Test
    fun `NationIsIndependent passes when nation is independent`() {
        val nation = createNation()
        nation.meta["imperialStatus"] = "independent"
        val result = NationIsIndependent().test(ctx(nation = nation))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `NationIsIndependent fails when nation is not independent`() {
        val nation = createNation()
        nation.meta["imperialStatus"] = "vassal"
        val result = NationIsIndependent().test(ctx(nation = nation))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("독립 세력"))
    }

    // ========== NationIsVassal ==========

    @Test
    fun `NationIsVassal passes when nation is vassal`() {
        val nation = createNation()
        nation.meta["imperialStatus"] = "vassal"
        val result = NationIsVassal().test(ctx(nation = nation))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `NationIsVassal fails when nation is not vassal`() {
        val nation = createNation()
        nation.meta["imperialStatus"] = "independent"
        val result = NationIsVassal().test(ctx(nation = nation))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("제후국"))
    }

    // ========== NationNotEmperor ==========

    @Test
    fun `NationNotEmperor passes when nation is not emperor`() {
        val nation = createNation()
        nation.meta["imperialStatus"] = "independent"
        val result = NationNotEmperor().test(ctx(nation = nation))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `NationNotEmperor fails when nation is emperor`() {
        val nation = createNation()
        nation.meta["imperialStatus"] = "emperor"
        val result = NationNotEmperor().test(ctx(nation = nation))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("황제국"))
    }

    // ========== NationIsEmperor ==========

    @Test
    fun `NationIsEmperor passes when nation is emperor`() {
        val nation = createNation()
        nation.meta["imperialStatus"] = "emperor"
        val result = NationIsEmperor().test(ctx(nation = nation))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `NationIsEmperor fails when nation is not emperor`() {
        val nation = createNation()
        nation.meta["imperialStatus"] = "independent"
        val result = NationIsEmperor().test(ctx(nation = nation))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("황제국이 아닙니다"))
    }

    // ========== DestNationIsEmperor ==========

    @Test
    fun `DestNationIsEmperor passes when dest nation is emperor`() {
        val destFaction = createNation(id = 2)
        destFaction.meta["imperialStatus"] = "emperor"
        val result = DestNationIsEmperor().test(ctx(destFaction = destFaction))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `DestNationIsEmperor fails when dest nation is not emperor`() {
        val destFaction = createNation(id = 2)
        destFaction.meta["imperialStatus"] = "independent"
        val result = DestNationIsEmperor().test(ctx(destFaction = destFaction))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("황제국이 아닙니다"))
    }

    // ========== NationHasEmperorGeneral ==========

    @Test
    fun `NationHasEmperorGeneral passes when nation is emperor with legitimate emperor`() {
        val nation = createNation()
        nation.meta["imperialStatus"] = "emperor"
        nation.meta["emperorType"] = "legitimate"
        val result = NationHasEmperorGeneral().test(ctx(nation = nation))
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `NationHasEmperorGeneral fails when nation is not emperor`() {
        val nation = createNation()
        nation.meta["imperialStatus"] = "independent"
        val result = NationHasEmperorGeneral().test(ctx(nation = nation))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("황제국이 아닙니다"))
    }

    @Test
    fun `NationHasEmperorGeneral fails when emperor type is not legitimate`() {
        val nation = createNation()
        nation.meta["imperialStatus"] = "emperor"
        nation.meta["emperorType"] = "self-proclaimed"
        val result = NationHasEmperorGeneral().test(ctx(nation = nation))
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("정통 황제"))
    }

    // ========== WanderingEmperorExists ==========

    @Test
    fun `WanderingEmperorExists passes when wandering emperor city id is positive`() {
        val context = ConstraintContext(general = createGeneral(), env = mapOf("wanderingEmperorCityId" to 5L))
        val result = WanderingEmperorExists().test(context)
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `WanderingEmperorExists fails when wandering emperor city id is zero`() {
        val context = ConstraintContext(general = createGeneral(), env = mapOf("wanderingEmperorCityId" to 0L))
        val result = WanderingEmperorExists().test(context)
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("유랑 중인 천자"))
    }

    @Test
    fun `WanderingEmperorExists fails when wandering emperor city id is missing`() {
        val context = ConstraintContext(general = createGeneral(), env = emptyMap())
        val result = WanderingEmperorExists().test(context)
        assertTrue(result is ConstraintResult.Fail)
    }

    // ========== WanderingEmperorInTerritory ==========

    @Test
    fun `WanderingEmperorInTerritory passes when emperor is in nation territory`() {
        val general = createGeneral(factionId = 1)
        val env = mapOf(
            "wanderingEmperorCityId" to 5L,
            "cityNationById" to mapOf(5L to 1L),
        )
        val context = ConstraintContext(general = general, env = env)
        val result = WanderingEmperorInTerritory().test(context)
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `WanderingEmperorInTerritory fails when emperor is not in territory`() {
        val general = createGeneral(factionId = 1)
        val env = mapOf(
            "wanderingEmperorCityId" to 5L,
            "cityNationById" to mapOf(5L to 2L),
            "mapAdjacency" to emptyMap<Long, List<Long>>(),
            "dbToMapId" to mapOf(5L to 100L),
            "cityNationByMapId" to emptyMap<Long, Long>(),
        )
        val context = ConstraintContext(general = general, env = env)
        val result = WanderingEmperorInTerritory().test(context)
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("천자가 아국 영토"))
    }

    // ========== ReqNationCityCount ==========

    @Test
    fun `ReqNationCityCount passes when nation has enough cities`() {
        val general = createGeneral(factionId = 1)
        val env = mapOf(
            "cityNationById" to mapOf(1L to 1L, 2L to 1L, 3L to 1L, 4L to 2L),
        )
        val context = ConstraintContext(general = general, env = env)
        val result = ReqNationCityCount(3).test(context)
        assertTrue(result is ConstraintResult.Pass)
    }

    @Test
    fun `ReqNationCityCount fails when nation has fewer cities than required`() {
        val general = createGeneral(factionId = 1)
        val env = mapOf(
            "cityNationById" to mapOf(1L to 1L, 2L to 2L, 3L to 2L),
        )
        val context = ConstraintContext(general = general, env = env)
        val result = ReqNationCityCount(5).test(context)
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("도시가"))
    }
}
