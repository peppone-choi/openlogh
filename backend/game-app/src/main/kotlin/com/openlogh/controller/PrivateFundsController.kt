package com.openlogh.controller

import com.openlogh.repository.AppUserRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.service.PrivateFundsService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/officer/funds")
class PrivateFundsController(
    private val privateFundsService: PrivateFundsService,
    private val officerRepository: OfficerRepository,
    private val appUserRepository: AppUserRepository,
    private val planetRepository: PlanetRepository,
) {
    // GET /api/officer/funds?officerId={id} — 사적 구좌 잔액 조회
    @GetMapping
    fun getBalance(@RequestParam officerId: Long): ResponseEntity<Any> {
        val officer = officerRepository.findById(officerId).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(mapOf("balance" to privateFundsService.getBalance(officer)))
    }

    // POST /api/officer/funds/deposit — 사적 구좌 입금
    @PostMapping("/deposit")
    fun deposit(@RequestBody body: Map<String, Any>): ResponseEntity<Any> {
        val officer = resolveOfficerFromBody(body)
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "officerId required"))
        val amount = (body["amount"] as? Number)?.toInt()
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "amount required"))

        val success = privateFundsService.deposit(officer, amount)
        return if (success) {
            ResponseEntity.ok(mapOf("success" to true, "balance" to privateFundsService.getBalance(officer)))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "자금이 부족합니다."))
        }
    }

    // POST /api/officer/funds/invest-planet — 지방자금고 투입
    @PostMapping("/invest-planet")
    fun investInPlanet(@RequestBody body: Map<String, Any>): ResponseEntity<Any> {
        val officer = resolveOfficerFromBody(body)
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "officerId required"))
        val planetId = (body["planetId"] as? Number)?.toLong()
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "planetId required"))
        val amount = (body["amount"] as? Number)?.toInt()
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "amount required"))

        val planet = planetRepository.findById(planetId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val success = privateFundsService.investInPlanet(officer, planet, amount)
        return if (success) {
            ResponseEntity.ok(mapOf("success" to true, "balance" to privateFundsService.getBalance(officer)))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "투입 실패 (잔액 부족 또는 권한 없음)"))
        }
    }

    // POST /api/officer/funds/vote-confidence — 신임/불신임 투표
    @PostMapping("/vote-confidence")
    fun voteConfidence(@RequestBody body: Map<String, Any>): ResponseEntity<Any> {
        val officer = resolveOfficerFromBody(body)
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "officerId required"))
        val targetOfficerId = (body["targetOfficerId"] as? Number)?.toLong()
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "targetOfficerId required"))
        val amount = (body["amount"] as? Number)?.toInt()
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "amount required"))
        val isConfidence = body["isConfidence"] as? Boolean ?: true

        val success = privateFundsService.voteConfidence(officer, targetOfficerId, amount, isConfidence)
        return if (success) {
            ResponseEntity.ok(mapOf("success" to true, "balance" to privateFundsService.getBalance(officer)))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "투표 실패 (잔액 부족)"))
        }
    }

    // POST /api/officer/funds/vote-support — 지지 투표
    @PostMapping("/vote-support")
    fun voteSupport(@RequestBody body: Map<String, Any>): ResponseEntity<Any> {
        val officer = resolveOfficerFromBody(body)
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "officerId required"))
        val candidateOfficerId = (body["candidateOfficerId"] as? Number)?.toLong()
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "candidateOfficerId required"))
        val amount = (body["amount"] as? Number)?.toInt()
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "amount required"))

        val success = privateFundsService.voteSupport(officer, candidateOfficerId, amount)
        return if (success) {
            ResponseEntity.ok(mapOf("success" to true, "balance" to privateFundsService.getBalance(officer)))
        } else {
            ResponseEntity.badRequest().body(mapOf("error" to "지지 투표 실패 (잔액 부족)"))
        }
    }

    private fun resolveOfficerFromBody(body: Map<String, Any>): com.openlogh.entity.Officer? {
        val officerId = (body["officerId"] as? Number)?.toLong() ?: return null
        return officerRepository.findById(officerId).orElse(null)
    }
}
