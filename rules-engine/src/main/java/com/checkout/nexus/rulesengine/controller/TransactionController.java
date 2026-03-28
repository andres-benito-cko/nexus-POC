package com.checkout.nexus.rulesengine.controller;

import com.checkout.nexus.rulesengine.model.entity.LedgerEntry;
import com.checkout.nexus.rulesengine.model.entity.NexusTransactionRecord;
import com.checkout.nexus.rulesengine.repository.LedgerEntryRepository;
import com.checkout.nexus.rulesengine.repository.NexusTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final NexusTransactionRepository nexusTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @GetMapping
    public List<NexusTransactionRecord> listTransactions(@RequestParam(defaultValue = "20") int limit) {
        return nexusTransactionRepository.findAllByOrderByReceivedAtDesc(PageRequest.of(0, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NexusTransactionRecord> getTransaction(@PathVariable String id) {
        return nexusTransactionRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/ledger")
    public ResponseEntity<Map<String, Object>> getTransactionLedger(@PathVariable String id) {
        return nexusTransactionRepository.findById(id)
            .map(txn -> {
                List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(id);
                return ResponseEntity.ok(Map.<String, Object>of(
                    "transaction", txn,
                    "ledgerEntries", entries
                ));
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
