package com.openlogh.controller

import com.openlogh.dto.*
import com.openlogh.service.InheritanceService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/worlds/{worldId}/inheritance")
class InheritanceController(
    private val inheritanceService: InheritanceService,
) {
    @GetMapping
    fun getInfo(@PathVariable worldId: Long): ResponseEntity<InheritanceInfo> {
        // TODO: build full inheritance info from user's current state
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        return ResponseEntity.ok(
            InheritanceInfo(
                points = 0,
                buffs = emptyMap(),
                log = emptyList(),
            )
        )
    }

    @PostMapping("/special")
    fun setSpecial(
        @PathVariable worldId: Long,
        @RequestBody req: SetInheritSpecialRequest,
    ): ResponseEntity<InheritanceActionResult> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val result = inheritanceService.setInheritSpecial(worldId, loginId, req.specialCode)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result)
    }

    @PostMapping("/city")
    fun setCity(
        @PathVariable worldId: Long,
        @RequestBody req: SetInheritCityRequest,
    ): ResponseEntity<InheritanceActionResult> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        // TODO: implement set inherit city
        return ResponseEntity.ok(InheritanceActionResult(error = "미구현"))
    }

    @PostMapping("/reset-turn")
    fun resetTurn(@PathVariable worldId: Long): ResponseEntity<InheritanceActionResult> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val result = inheritanceService.resetTurn(worldId, loginId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result)
    }

    @PostMapping("/random-unique")
    fun buyRandomUnique(@PathVariable worldId: Long): ResponseEntity<InheritanceActionResult> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val result = inheritanceService.buyRandomUnique(worldId, loginId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result)
    }

    @PostMapping("/reset-special-war")
    fun resetSpecialWar(@PathVariable worldId: Long): ResponseEntity<InheritanceActionResult> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val result = inheritanceService.resetSpecialWar(worldId, loginId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result)
    }

    @PostMapping("/reset-stats")
    fun resetStats(
        @PathVariable worldId: Long,
        @RequestBody req: ResetStatsRequest,
    ): ResponseEntity<InheritanceActionResult> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        // TODO: implement stat reset
        return ResponseEntity.ok(InheritanceActionResult(error = "미구현"))
    }

    @PostMapping("/check-owner")
    fun checkOwner(
        @PathVariable worldId: Long,
        @RequestBody req: CheckOwnerRequest,
    ): ResponseEntity<InheritanceOwnerCheckResponse> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        // TODO: implement owner check
        return ResponseEntity.ok(InheritanceOwnerCheckResponse(found = false))
    }

    @PostMapping("/buy-buff")
    fun buyBuff(
        @PathVariable worldId: Long,
        @RequestBody req: BuyInheritBuffRequest,
    ): ResponseEntity<InheritanceActionResult> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        // TODO: implement buff purchase
        return ResponseEntity.ok(InheritanceActionResult(error = "미구현"))
    }

    @GetMapping("/log")
    fun getLog(
        @PathVariable worldId: Long,
        @RequestParam lastID: Long,
    ): ResponseEntity<InheritanceLogResponse> {
        // TODO: implement log retrieval
        return ResponseEntity.ok(InheritanceLogResponse(logs = emptyList(), hasMore = false))
    }

    @PostMapping("/auction-unique")
    fun auctionUnique(
        @PathVariable worldId: Long,
        @RequestBody req: AuctionUniqueRequest,
    ): ResponseEntity<InheritanceActionResult> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        // TODO: implement unique item auction
        return ResponseEntity.ok(InheritanceActionResult(error = "미구현"))
    }

    @PostMapping("/buy")
    fun buy(
        @PathVariable worldId: Long,
        @RequestBody body: Map<String, String>,
    ): ResponseEntity<InheritanceActionResult> {
        val loginId = currentLoginId()
            ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        // TODO: implement general inheritance purchase
        return ResponseEntity.ok(InheritanceActionResult(error = "미구현"))
    }

    private fun currentLoginId(): String? {
        return SecurityContextHolder.getContext().authentication?.name
    }
}
