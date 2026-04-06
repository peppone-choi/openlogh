package com.openlogh.controller

import com.openlogh.command.CommandResult
import com.openlogh.dto.*
import com.openlogh.service.CommandService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class CommandController(
    private val commandService: CommandService,
) {
    @PostMapping("/generals/{generalId}/execute")
    fun executeCommand(
        @PathVariable generalId: Long,
        @RequestBody request: ExecuteRequest,
    ): ResponseEntity<CommandResult> {
        val loginId = SecurityContextHolder.getContext().authentication?.name
            ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        if (!commandService.verifyOwnership(generalId, loginId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val result = commandService.executeCommand(generalId, request.actionCode, request.arg)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result)
    }

    @PostMapping("/generals/{generalId}/execute-nation")
    fun executeFactionCommand(
        @PathVariable generalId: Long,
        @RequestBody request: ExecuteRequest,
    ): ResponseEntity<CommandResult> {
        val loginId = SecurityContextHolder.getContext().authentication?.name
            ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        if (!commandService.verifyOwnership(generalId, loginId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val result = commandService.executeFactionCommand(generalId, request.actionCode, request.arg)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result)
    }

    @GetMapping("/generals/{generalId}/command-table")
    fun getCommandTable(@PathVariable generalId: Long): ResponseEntity<Map<String, List<CommandTableEntry>>> {
        val table = commandService.getCommandTable(generalId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(table)
    }

    @GetMapping("/generals/{generalId}/nation-command-table")
    fun getNationCommandTable(@PathVariable generalId: Long): ResponseEntity<Map<String, List<CommandTableEntry>>> {
        val table = commandService.getNationCommandTable(generalId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(table)
    }
}
