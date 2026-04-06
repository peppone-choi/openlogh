package com.openlogh.controller

import com.openlogh.service.AddressBookEntry
import com.openlogh.service.AddressBookService
import com.openlogh.service.MessageService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/world/{sessionId}/mail")
class MailController(
    private val messageService: MessageService,
    private val addressBookService: AddressBookService,
) {
    /**
     * Get mailbox message counts by type.
     */
    @GetMapping("/count")
    fun getMailCount(
        @PathVariable sessionId: Long,
        @RequestParam generalId: Long,
    ): ResponseEntity<Map<String, Long>> {
        return ResponseEntity.ok(messageService.getMailboxCounts(generalId))
    }

    /**
     * Get address book for composing mail.
     */
    @GetMapping("/addressbook")
    fun getAddressBook(
        @PathVariable sessionId: Long,
        @RequestParam generalId: Long,
    ): ResponseEntity<List<AddressBookEntry>> {
        return ResponseEntity.ok(addressBookService.getAddressBook(sessionId, generalId))
    }

    /**
     * Search officers by name for mail addressing.
     */
    @GetMapping("/search")
    fun searchOfficers(
        @PathVariable sessionId: Long,
        @RequestParam q: String,
    ): ResponseEntity<List<AddressBookEntry>> {
        return ResponseEntity.ok(addressBookService.searchOfficers(sessionId, q))
    }
}
