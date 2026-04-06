package com.openlogh.controller

import com.openlogh.service.AddressBookEntry
import com.openlogh.service.AddressBookService
import com.openlogh.service.MailAddress
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
     * Includes addresses obtained via name card exchange (명함교환).
     */
    @GetMapping("/addressbook")
    fun getAddressBook(
        @PathVariable sessionId: Long,
        @RequestParam generalId: Long,
    ): ResponseEntity<List<AddressBookEntry>> {
        return ResponseEntity.ok(addressBookService.getAddressBook(sessionId, generalId))
    }

    /**
     * Get all mail addresses (personal + position card) for an officer.
     * Used to show what addresses an officer has for receiving mail.
     */
    @GetMapping("/addresses")
    fun getMailAddresses(
        @PathVariable sessionId: Long,
        @RequestParam generalId: Long,
    ): ResponseEntity<List<MailAddress>> {
        return ResponseEntity.ok(addressBookService.getOfficerMailAddresses(sessionId, generalId))
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

    /**
     * Exchange name cards (명함교환) between two officers.
     * Both officers' personal + position card addresses are added to each other's address book.
     */
    @PostMapping("/exchange-namecard")
    fun exchangeNameCards(
        @PathVariable sessionId: Long,
        @RequestParam officerAId: Long,
        @RequestParam officerBId: Long,
    ): ResponseEntity<Map<String, Any>> {
        val added = addressBookService.exchangeNameCards(sessionId, officerAId, officerBId)
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "newAddressesAdded" to added,
            )
        )
    }
}
