package com.openlogh.engine.tactical

import com.openlogh.engine.strategic.DeathJudgment
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class TacticalBattleEngine(
    private val rng: Random,
    private val commandTimingService: CommandTimingService = CommandTimingService(),
) {

    companion object {
        /** 요새포 AoE 반경 (distance units) */
        private const val FORTRESS_CANNON_RADIUS = 100.0
        /** 요새포 최대 데미지 */
        private const val FORTRESS_CANNON_MAX_DAMAGE = 200
        /** 타겟 스냅 반경: 공격 명령 좌표 근처 유닛 탐색 */
        private const val TARGET_SNAP_RADIUS = 50.0
    }

    fun resolveTurn(session: TacticalBattleSession): TurnResult {
        session.currentTurn++
        val turn = session.currentTurn
        val events = mutableListOf<BattleEvent>()

        // Command timing: record timing metadata for each queued order type (gin7 §10.21)
        for ((_, orders) in session.pendingOrders) {
            for (order in orders) {
                commandTimingService.getTiming(order.type.name.lowercase())
                // Timing info is available for callers; canStartNew() enforced at submission time
            }
        }

        // 1. Process configuration orders (formation/energy changes)
        events.addAll(processConfigOrders(session, turn))

        // 2. Process retreat orders
        events.addAll(processRetreatOrders(session, turn))

        // 3. Process special orders
        events.addAll(processSpecialOrders(session, turn))

        // 4. Process movement orders (by initiative: officer.mobility desc)
        events.addAll(processMovementOrders(session, turn))

        // 5. Process attack orders (simultaneous resolution)
        events.addAll(processAttackOrders(session, turn))

        // 6. Update morale for destroyed units
        events.addAll(processMoraleUpdates(session, turn))

        // 7. Forced retreats for low morale
        events.addAll(processForcedRetreats(session, turn))

        // 8. Death judgment for destroyed flagships
        events.addAll(processDeathJudgments(session, turn))

        // 9. Ground assault (if active)
        events.addAll(processGroundAssault(session, turn))

        // 10. Check victory conditions
        val victory = checkVictory(session, turn)
        if (victory != null) {
            session.phase = BattlePhase.RESULT
        }

        session.clearPendingOrders()
        session.battleLog.addAll(events)

        return TurnResult(
            turn = turn,
            events = events,
            victory = victory,
            attackerFleetSummaries = session.attackerFleets.map { it.toSummary() },
            defenderFleetSummaries = session.defenderFleets.map { it.toSummary() },
        )
    }

    // -- Configuration Orders --

    private fun processConfigOrders(session: TacticalBattleSession, turn: Int): List<BattleEvent> {
        val events = mutableListOf<BattleEvent>()
        for ((officerId, orders) in session.pendingOrders) {
            for (order in orders) {
                when (order.type) {
                    OrderType.FORMATION_CHANGE -> {
                        val fleet = session.findFleet(
                            session.attackerFleets.firstOrNull { it.officerId == officerId }?.fleetId
                                ?: session.defenderFleets.firstOrNull { it.officerId == officerId }?.fleetId
                                ?: continue
                        ) ?: continue
                        val newFormation = order.formation ?: continue
                        val old = fleet.formation
                        if (old != newFormation) {
                            fleet.formation = newFormation
                            events.add(BattleEvent.FormationChangeEvent(turn, fleet.fleetId, old, newFormation))
                        }
                    }
                    OrderType.ENERGY_CHANGE -> {
                        val fleet = findFleetByOfficer(session, officerId) ?: continue
                        val newEnergy = order.energy ?: continue
                        val old = fleet.energy
                        if (old != newEnergy) {
                            fleet.energy = newEnergy
                            events.add(BattleEvent.EnergyChangeEvent(turn, fleet.fleetId, old, newEnergy))
                        }
                    }
                    else -> { /* handled in other phases */ }
                }
            }
        }
        return events
    }

    // -- Retreat Orders --

    private fun processRetreatOrders(session: TacticalBattleSession, turn: Int): List<BattleEvent> {
        val events = mutableListOf<BattleEvent>()
        for ((officerId, orders) in session.pendingOrders) {
            for (order in orders.filter { it.type == OrderType.RETREAT }) {
                val fleet = findFleetByOfficer(session, officerId) ?: continue
                retreatFleet(session, fleet)
                events.add(BattleEvent.RetreatEvent(turn, fleet.fleetId, forced = false))
            }
        }
        return events
    }

    // -- Special Orders --

    private fun processSpecialOrders(session: TacticalBattleSession, turn: Int): List<BattleEvent> {
        val events = mutableListOf<BattleEvent>()
        for ((officerId, orders) in session.pendingOrders) {
            for (order in orders.filter { it.type == OrderType.SPECIAL }) {
                val fleet = findFleetByOfficer(session, officerId) ?: continue
                val code = order.specialCode ?: continue
                when (code) {
                    "rally" -> {
                        fleet.applyMoraleGain(15)
                        events.add(BattleEvent.SpecialEvent(turn, fleet.fleetId, code, "함대격려: 사기 +15"))
                    }
                    "jamming" -> {
                        val targetFleet = findEnemyFleetByOfficer(session, fleet.factionId, order.targetUnitId?.toLong())
                        if (targetFleet != null) {
                            events.add(BattleEvent.SpecialEvent(turn, targetFleet.fleetId, code, "통신방해: 1턴 명령 불가"))
                        }
                    }
                    "decoy" -> {
                        events.add(BattleEvent.SpecialEvent(turn, fleet.fleetId, code, "위장함대: 허위 함대 표시"))
                    }
                    "ground_assault" -> {
                        val assaultUnit = fleet.units.firstOrNull {
                            it.shipClass == TacticalShipClass.ASSAULT_SHIP && it.isAlive()
                        }
                        if (assaultUnit != null && !session.groundAssault.isActive() && !session.groundAssault.isFinished()) {
                            // 지상전 개시: 궤도 방어력과 수비대 전력 초기화
                            session.groundAssault = GroundAssaultEngine.initiate(
                                orbitalDefense = 100.0, // TODO: Planet.orbitalDefense에서 가져오기
                                garrisonStrength = 100.0, // TODO: Planet garrison에서 가져오기
                            )
                            // 병종 설정 (Fleet에서)
                            session.groundAssault.attackerUnitType =
                                GroundUnitType.fromCode(order.specialCode ?: "light_infantry")
                            session.groundAssault.assaultUnitIds.add(assaultUnit.id)

                            events.add(BattleEvent.SpecialEvent(
                                turn, fleet.fleetId, code,
                                "지상강습 개시! 궤도 돌파 단계 진입 (육전 ${fleet.officer.groundCombat})",
                            ))
                        }
                    }
                    "fortress_cannon" -> {
                        val targetX = order.targetX ?: continue
                        val targetY = order.targetY ?: continue
                        val targetZ = order.targetZ ?: 0.0
                        events.addAll(processFortressCannon(session, fleet, targetX, targetY, targetZ, turn))
                    }
                }
            }
        }
        return events
    }

    private fun processFortressCannon(
        session: TacticalBattleSession,
        fleet: TacticalFleet,
        targetX: Double,
        targetY: Double,
        targetZ: Double,
        turn: Int,
    ): List<BattleEvent> {
        val events = mutableListOf<BattleEvent>()
        val fortressUnit = fleet.units.firstOrNull { it.shipClass == TacticalShipClass.FORTRESS && it.isAlive() }
            ?: return events

        val targetPos = Position(targetX, targetY, targetZ)
        val allUnits = session.allUnits().filter { it.isAlive() && it.factionId != fleet.factionId }
        for (unit in allUnits) {
            val dist = unit.position().distanceTo(targetPos)
            if (dist <= FORTRESS_CANNON_RADIUS) {
                // 거리에 비례하여 데미지 감소
                val damageRatio = 1.0 - (dist / FORTRESS_CANNON_RADIUS)
                val damage = max(40, (FORTRESS_CANNON_MAX_DAMAGE * damageRatio).toInt())
                val hpBefore = unit.hp
                unit.takeDamage(damage)
                events.add(
                    BattleEvent.AttackEvent(
                        turn, fortressUnit.id, fleet.fleetId,
                        unit.id, session.findFleetForUnit(unit.id)?.fleetId ?: 0L,
                        "FORTRESS_CANNON", damage, true, false,
                        hpBefore, unit.hp,
                    )
                )
                if (!unit.isAlive()) {
                    events.add(BattleEvent.DestroyEvent(turn, unit.id, fleet.fleetId, fortressUnit.id))
                }
            }
        }
        return events
    }

    // -- Movement --

    private fun processMovementOrders(session: TacticalBattleSession, turn: Int): List<BattleEvent> {
        val events = mutableListOf<BattleEvent>()

        data class MoveOrder(val unit: TacticalUnit, val fleet: TacticalFleet, val tx: Double, val ty: Double, val tz: Double)

        val moveOrders = mutableListOf<MoveOrder>()
        for ((officerId, orders) in session.pendingOrders) {
            val fleet = findFleetByOfficer(session, officerId) ?: continue
            for (order in orders.filter { it.type == OrderType.MOVE }) {
                val unitId = order.unitId ?: continue
                val unit = fleet.units.firstOrNull { it.id == unitId && it.isAlive() } ?: continue
                val tx = order.targetX ?: continue
                val ty = order.targetY ?: continue
                val tz = order.targetZ ?: unit.z // z 미지정 시 현재 고도 유지
                moveOrders.add(MoveOrder(unit, fleet, tx, ty, tz))
            }
        }

        // Sort by officer mobility descending for initiative
        moveOrders.sortByDescending { it.fleet.officer.mobility }

        for (mo in moveOrders) {
            // 커맨드 레인지 체크: 기함 범위 밖 유닛은 명령 불가
            val flagship = mo.fleet.flagship()
            if (flagship != null && flagship.id != mo.unit.id) {
                val cmdRange = CommandRangeSystem.createForOfficer(mo.fleet.officer)
                CommandRangeSystem.expandRange(cmdRange) // 턴 시작 시 확대
                if (!CommandRangeSystem.canCommand(
                        flagship.position(), mo.unit.position(), cmdRange, mo.fleet.morale)) {
                    continue // 범위 밖 → 명령 무시
                }
            }
            val event = processMovement(session, mo.unit, mo.fleet, mo.tx, mo.ty, mo.tz, turn)
            if (event != null) events.add(event)
        }
        return events
    }

    fun processMovement(
        session: TacticalBattleSession,
        unit: TacticalUnit,
        fleet: TacticalFleet,
        targetX: Double,
        targetY: Double,
        targetZ: Double,
        turn: Int,
    ): BattleEvent.MoveEvent? {
        if (!unit.isAlive()) return null
        val grid = session.grid
        val from = unit.position()
        val target = Position(targetX, targetY, targetZ)

        val maxRange = unit.getMovementRange(fleet.energy, fleet.formation)
        val destination = grid.computeMoveTo(from, target, maxRange)

        if (from.distanceTo(destination) < 0.1) return null // 이동 없음

        val fromX = unit.x
        val fromY = unit.y
        val fromZ = unit.z
        // 이동 방향 기록 (방향 방어 계산용)
        val dx = destination.x - fromX
        val dy = destination.y - fromY
        if (dx * dx + dy * dy > 0.01) {
            unit.lastMoveX = dx
            unit.lastMoveY = dy
        }
        unit.x = destination.x
        unit.y = destination.y
        unit.z = destination.z
        grid.moveUnit(unit.id, destination)

        val distance = from.distanceTo(destination)

        return BattleEvent.MoveEvent(
            turn, unit.id, fleet.fleetId,
            fromX, fromY, fromZ, destination.x, destination.y, destination.z,
            distance,
        )
    }

    // -- Attack --

    private data class PendingDamage(val target: TacticalUnit, val damage: Int, val event: BattleEvent.AttackEvent)

    private fun processAttackOrders(session: TacticalBattleSession, turn: Int): List<BattleEvent> {
        val events = mutableListOf<BattleEvent>()

        data class AttackOrder(val unit: TacticalUnit, val fleet: TacticalFleet, val targetUnitId: Int?, val tx: Double?, val ty: Double?, val tz: Double?)

        val attackOrders = mutableListOf<AttackOrder>()
        for ((officerId, orders) in session.pendingOrders) {
            val fleet = findFleetByOfficer(session, officerId) ?: continue
            for (order in orders.filter { it.type == OrderType.ATTACK }) {
                val unitId = order.unitId ?: continue
                val unit = fleet.units.firstOrNull { it.id == unitId && it.isAlive() } ?: continue
                attackOrders.add(AttackOrder(unit, fleet, order.targetUnitId, order.targetX, order.targetY, order.targetZ))
            }
        }

        val pendingDamages = mutableListOf<PendingDamage>()

        for (ao in attackOrders) {
            val target = findTarget(session, ao.unit.factionId, ao.targetUnitId, ao.tx, ao.ty, ao.tz) ?: continue
            val result = resolveAttack(session, ao.unit, ao.fleet, target, turn)
            if (result != null) pendingDamages.add(result)
        }

        // Apply all damage simultaneously
        for (pd in pendingDamages) {
            val hpBefore = pd.target.hp
            pd.target.takeDamage(pd.damage)
            events.add(
                pd.event.copy(targetHpBefore = hpBefore, targetHpAfter = pd.target.hp)
            )
            if (!pd.target.isAlive()) {
                events.add(BattleEvent.DestroyEvent(turn, pd.target.id, pd.event.attackerFleetId, pd.event.attackerUnitId))
            }
        }

        return events
    }

    private fun resolveAttack(
        session: TacticalBattleSession,
        attacker: TacticalUnit,
        attackerFleet: TacticalFleet,
        target: TacticalUnit,
        turn: Int,
    ): PendingDamage? {
        val targetFleet = session.findFleetForUnit(target.id) ?: return null
        val grid = session.grid

        val attackerPos = attacker.position()
        val targetPos = target.position()
        val distance = grid.getDistance(attackerPos, targetPos)

        // 색적 체크: 미탐지 유닛은 공격 불가
        val detectionRange = ReconSystem.getDetectionRange(
            sensorEnergy = attackerFleet.energy.sensor,
            intelligenceStat = attackerFleet.officer.intelligence,
            isStationary = false,
        )
        val targetStealth = ReconSystem.getStealthValue(target.shipClass, isStationary = false)
        val detectionPrecision = ReconSystem.detectUnit(distance, detectionRange, targetStealth)
        if (detectionPrecision < 2) return null // 대략 위치 이상 탐지해야 공격 가능

        // 사거리 내 최적 무기 선택
        val weapon = selectWeapon(attacker, attackerFleet, distance) ?: return null

        // 함재기/미사일 외 무기는 LoS 필요 (gin7 §10.15)
        if (!weapon.interceptable) {
            val losResult = LineOfSightChecker.checkWithGrid(
                attacker, target, session.allUnits(), grid,
            )
            if (!losResult.clear) return null
        }

        // 에너지 채널 배율
        val energyMult = if (weapon.energyChannel == "beam") {
            attackerFleet.energy.beamDamageMultiplier()
        } else {
            attackerFleet.energy.gunDamageMultiplier()
        }

        // 기본 데미지 = 함종 공격력 × 무기 배율 × 슬롯 수 보정 × 에너지 × 연사 × 진형
        val weaponMount = attacker.shipClass.defaultWeapons().firstOrNull { it.weaponType == weapon }
        val slotMult = (weaponMount?.slots ?: 1) / 4.0 // 4문 기준 정규화
        val rawDamage = attacker.shipClass.baseAttack * weapon.baseDamageMultiplier *
            slotMult * energyMult * weapon.rateOfFire * attackerFleet.formation.attackBonus

        // 장교 보너스: 함재기는 공전(fighterSkill), 그 외는 지휘(command)
        val officerBonus = if (weapon == WeaponType.FIGHTER) {
            1.0 + attackerFleet.officer.fighterSkill * 0.008
        } else {
            1.0 + attackerFleet.officer.command * 0.005
        }

        // 명중률: 기본 70% + 센서 + 장교 정보력
        val hitChance = min(
            0.95,
            0.70 + attackerFleet.energy.sensorAccuracyBonus() + attackerFleet.officer.intelligence * 0.002
        )

        // 회피
        val evasion = target.getEvasionChance(targetFleet.energy, targetFleet.formation)
        val finalHitChance = max(0.10, hitChance - evasion)

        val hit = rng.nextDouble() < finalHitChance

        // 요격 가능 무기(미사일/함재기) → 방어측 레이저 요격 판정
        val intercepted = if (hit && weapon.interceptable) {
            val defenderLaserSlots = target.shipClass.defaultWeapons()
                .filter { it.weaponType == WeaponType.LASER }
                .sumOf { it.slots }
            if (defenderLaserSlots > 0) {
                val interceptChance = min(0.6, defenderLaserSlots * 0.08)
                rng.nextDouble() < interceptChance
            } else false
        } else false

        if (!hit || intercepted) {
            return PendingDamage(
                target, 0,
                BattleEvent.AttackEvent(
                    turn, attacker.id, attackerFleet.fleetId,
                    target.id, targetFleet.fleetId,
                    weapon.code, 0, hit && !intercepted, false,
                    target.hp, target.hp,
                    distance,
                )
            )
        }

        // 크리티컬: 5% + 장교 공격력
        val critChance = 0.05 + attackerFleet.officer.attack * 0.002
        val critical = rng.nextDouble() < critChance
        val critMult = if (critical) 1.5 else 1.0

        // 방어력 계산 (방향 고려)
        val direction = computeAttackDirection(
            attackerPos, targetPos,
            target.lastMoveX, target.lastMoveY,
        )
        val defense = target.getDefensePower(targetFleet.energy, targetFleet.formation)
        val defOfficerBonus = 1.0 + targetFleet.officer.defense * 0.005
        val directionalDefense = defense * defOfficerBonus * direction.defenseMultiplier

        // 관통: 방어의 일부를 무시
        val effectiveDefense = directionalDefense * (1.0 - weapon.armorPenetration)

        // 데미지 계산
        val grossDamage = rawDamage * officerBonus * critMult
        val netDamage = max(1, (grossDamage - effectiveDefense * 0.3).toInt())

        // 랜덤 분산 ±10%
        val variance = 0.9 + rng.nextDouble() * 0.2
        val finalDamage = max(1, (netDamage * variance).toInt())

        return PendingDamage(
            target, finalDamage,
            BattleEvent.AttackEvent(
                turn, attacker.id, attackerFleet.fleetId,
                target.id, targetFleet.fleetId,
                weapon.code, finalDamage, true, critical,
                target.hp, target.hp,
                distance,
            )
        )
    }

    /**
     * 현재 거리에서 최적 무기를 선택.
     * 사거리 내 무기 중 데미지가 가장 높은 것을 선택.
     */
    private fun selectWeapon(
        attacker: TacticalUnit,
        fleet: TacticalFleet,
        distance: Double,
    ): WeaponType? {
        val sensorBonus = fleet.energy.sensorRangeBonus()
        return attacker.shipClass.defaultWeapons()
            .filter { mount -> distance <= mount.weaponType.baseRange + sensorBonus }
            .filter { mount -> mount.weaponType != WeaponType.THOR_HAMMER } // 토르 해머는 특수능력 전용
            .maxByOrNull { mount ->
                mount.weaponType.baseDamageMultiplier * mount.slots * mount.weaponType.rateOfFire
            }?.weaponType
    }

    // -- Morale --

    private fun processMoraleUpdates(session: TacticalBattleSession, turn: Int): List<BattleEvent> {
        val events = mutableListOf<BattleEvent>()

        for (fleet in session.attackerFleets + session.defenderFleets) {
            val destroyedThisTurn = fleet.units.count { !it.isAlive() && it.hp == 0 }
            if (destroyedThisTurn == 0) continue

            val oldMorale = fleet.morale

            // Unit destroyed: -3 per unit
            for (unit in fleet.units.filter { !it.isAlive() }) {
                fleet.applyMoraleLoss(3)
            }

            // Flagship hit check
            val flagship = fleet.units.firstOrNull { it.isFlagship }
            if (flagship != null && !flagship.isAlive()) {
                fleet.applyMoraleLoss(5)
            }

            // Fleet annihilation check
            if (fleet.isDefeated()) {
                // Apply -10 to all same-faction fleets
                val factionFleets = (session.attackerFleets + session.defenderFleets)
                    .filter { it.factionId == fleet.factionId && it.fleetId != fleet.fleetId }
                for (allyFleet in factionFleets) {
                    val allyOld = allyFleet.morale
                    allyFleet.applyMoraleLoss(10)
                    if (allyFleet.morale != allyOld) {
                        events.add(
                            BattleEvent.MoraleChangeEvent(
                                turn, allyFleet.fleetId, allyOld, allyFleet.morale,
                                "아군 함대 궤멸",
                            )
                        )
                    }
                }
            }

            if (fleet.morale != oldMorale) {
                events.add(
                    BattleEvent.MoraleChangeEvent(
                        turn, fleet.fleetId, oldMorale, fleet.morale,
                        "유닛 격침",
                    )
                )
            }
        }
        return events
    }

    // -- Forced Retreats --

    private fun processForcedRetreats(session: TacticalBattleSession, turn: Int): List<BattleEvent> {
        val events = mutableListOf<BattleEvent>()
        for (fleet in session.attackerFleets + session.defenderFleets) {
            if (fleet.isForcedRetreat() && !fleet.isDefeated()) {
                retreatFleet(session, fleet)
                events.add(BattleEvent.RetreatEvent(turn, fleet.fleetId, forced = true))
            }
        }
        return events
    }

    // -- Victory Check --

    fun checkVictory(session: TacticalBattleSession, turn: Int): VictoryResult? {
        val attackersAlive = session.attackerFleets.any { !it.isDefeated() && !it.isForcedRetreat() }
        val defendersAlive = session.defenderFleets.any { !it.isDefeated() && !it.isForcedRetreat() }

        // Annihilation
        if (!defendersAlive && attackersAlive) {
            return VictoryResult(
                session.attackerFleets.first().factionId,
                VictoryType.ANNIHILATION,
                "방어측 전 함대 궤멸/퇴각",
            )
        }
        if (!attackersAlive && defendersAlive) {
            return VictoryResult(
                session.defenderFleets.first().factionId,
                VictoryType.ANNIHILATION,
                "공격측 전 함대 궤멸/퇴각",
            )
        }
        if (!attackersAlive && !defendersAlive) {
            // Both sides destroyed - compare remaining HP
            return resolveByRemainingHp(session)
        }

        // Decapitation: enemy flagship destroyed
        val attackerFlagshipsAlive = session.attackerFleets.any { f -> f.flagship()?.isAlive() == true }
        val defenderFlagshipsAlive = session.defenderFleets.any { f -> f.flagship()?.isAlive() == true }

        if (!defenderFlagshipsAlive && attackerFlagshipsAlive) {
            return VictoryResult(
                session.attackerFleets.first().factionId,
                VictoryType.DECAPITATION,
                "방어측 사령관 기함 격침",
            )
        }
        if (!attackerFlagshipsAlive && defenderFlagshipsAlive) {
            return VictoryResult(
                session.defenderFleets.first().factionId,
                VictoryType.DECAPITATION,
                "공격측 사령관 기함 격침",
            )
        }

        // Rout: all enemy morale <= 20
        val allAttackerRouted = session.attackerFleets.all { it.isForcedRetreat() || it.isDefeated() }
        val allDefenderRouted = session.defenderFleets.all { it.isForcedRetreat() || it.isDefeated() }

        if (allDefenderRouted && !allAttackerRouted) {
            return VictoryResult(
                session.attackerFleets.first().factionId,
                VictoryType.ROUT,
                "방어측 전체 사기 붕괴",
            )
        }
        if (allAttackerRouted && !allDefenderRouted) {
            return VictoryResult(
                session.defenderFleets.first().factionId,
                VictoryType.ROUT,
                "공격측 전체 사기 붕괴",
            )
        }

        // Time limit
        if (turn >= session.maxTurns) {
            return resolveByRemainingHp(session)
        }

        return null
    }

    private fun resolveByRemainingHp(session: TacticalBattleSession): VictoryResult {
        val attackerHp = session.attackerFleets.sumOf { it.totalHp() }
        val defenderHp = session.defenderFleets.sumOf { it.totalHp() }
        val winnerFaction = if (attackerHp >= defenderHp) {
            session.attackerFleets.first().factionId
        } else {
            session.defenderFleets.first().factionId
        }
        return VictoryResult(winnerFaction, VictoryType.TIME_LIMIT, "턴 제한 도달 - HP 비교")
    }

    // -- Helpers --

    private fun findFleetByOfficer(session: TacticalBattleSession, officerId: Long): TacticalFleet? =
        (session.attackerFleets + session.defenderFleets).firstOrNull { it.officerId == officerId }

    private fun findEnemyFleetByOfficer(
        session: TacticalBattleSession,
        myFactionId: Long,
        targetOfficerId: Long?,
    ): TacticalFleet? {
        if (targetOfficerId == null) return null
        return (session.attackerFleets + session.defenderFleets)
            .firstOrNull { it.factionId != myFactionId && it.officerId == targetOfficerId }
    }

    /**
     * 타겟 탐색: targetUnitId가 있으면 직접 참조, 없으면 좌표 근처 적 유닛 중 가장 가까운 유닛.
     */
    private fun findTarget(
        session: TacticalBattleSession,
        attackerFactionId: Long,
        targetUnitId: Int?,
        targetX: Double?,
        targetY: Double?,
        targetZ: Double?,
    ): TacticalUnit? {
        // 직접 유닛 ID 지정
        if (targetUnitId != null) {
            val unit = session.findUnit(targetUnitId)
            if (unit != null && unit.isAlive() && unit.factionId != attackerFactionId) return unit
        }
        // 좌표 기반 스냅 타겟팅
        if (targetX != null && targetY != null) {
            val targetPos = Position(targetX, targetY, targetZ ?: 0.0)
            return session.allUnits()
                .filter { it.isAlive() && it.factionId != attackerFactionId }
                .filter { it.position().distanceTo(targetPos) <= TARGET_SNAP_RADIUS }
                .minByOrNull { it.position().distanceTo(targetPos) }
        }
        return null
    }

    private fun retreatFleet(session: TacticalBattleSession, fleet: TacticalFleet) {
        for (unit in fleet.aliveUnits()) {
            session.grid.removeUnit(unit.id)
        }
    }

    private fun TacticalFleet.toSummary() = FleetSummary(
        fleetId = fleetId,
        officerName = officer.name,
        aliveUnits = aliveUnits().size,
        totalUnits = units.size,
        totalHp = totalHp(),
        morale = morale,
        formation = formation,
    )

    // -- Death Judgment (기함 격침 시 전사 판정) --

    private fun processDeathJudgments(session: TacticalBattleSession, turn: Int): List<BattleEvent> {
        val events = mutableListOf<BattleEvent>()
        for (fleet in session.attackerFleets + session.defenderFleets) {
            val flagship = fleet.units.firstOrNull { it.isFlagship } ?: continue
            if (flagship.isAlive()) continue
            // 이미 전사 처리된 경우 스킵
            if (fleet.officer.officerId in processedDeaths) continue

            val died = DeathJudgment.judgeDeath(
                isStationed = false, // 전술전 중이므로 주류 아님
                flagshipDefense = fleet.officer.defense,
                rng = rng,
            )

            if (died) {
                processedDeaths.add(fleet.officer.officerId)
                events.add(BattleEvent.SpecialEvent(
                    turn, fleet.fleetId, "officer_killed",
                    "${fleet.officer.name} 전사! 기함 격침으로 사령관이 전사했습니다.",
                ))
            } else {
                val injury = DeathJudgment.judgeInjury(fleet.officer.defense, rng)
                if (injury > 0) {
                    events.add(BattleEvent.SpecialEvent(
                        turn, fleet.fleetId, "officer_injured",
                        "${fleet.officer.name} 부상 (부상도: $injury). 기함 격침에서 생존.",
                    ))
                }
            }
        }
        return events
    }

    private val processedDeaths = mutableSetOf<Long>()

    // -- Ground Assault Processing --

    private fun processGroundAssault(session: TacticalBattleSession, turn: Int): List<BattleEvent> {
        val events = mutableListOf<BattleEvent>()
        val ga = session.groundAssault
        if (!ga.isActive()) return events

        // 공격측 강습력 계산: groundCombat × 생존 강습양륙함 HP
        val attackerFleets = session.attackerFleets
        val assaultPower = attackerFleets.sumOf { fleet ->
            val assaultHp = fleet.units
                .filter { it.shipClass == TacticalShipClass.ASSAULT_SHIP && it.isAlive() }
                .sumOf { it.hp }
            fleet.officer.groundCombat * assaultHp / 100.0
        }

        // 궤도 제공권: 공격측 함대 총 HP > 방어측 함대 총 HP
        val attackerTotalHp = session.attackerFleets.sumOf { it.totalHp() }
        val defenderTotalHp = session.defenderFleets.sumOf { it.totalHp() }
        val hasOrbitalControl = attackerTotalHp > defenderTotalHp

        val result = GroundAssaultEngine.processTurn(
            state = ga,
            attackerAssaultPower = assaultPower,
            attackerHasOrbitalControl = hasOrbitalControl,
        )

        events.add(BattleEvent.SpecialEvent(
            turn, 0L, "ground_assault",
            "[지상전 ${result.phase.displayName}] ${result.description} " +
                "(게이지: ${result.captureGauge.toInt()}%, 공격: ${result.attackPower.toInt()}, " +
                "방어: ${result.defensePower.toInt()}, 상성: ×${String.format("%.1f", result.matchupMultiplier)})",
        ))

        return events
    }
}
