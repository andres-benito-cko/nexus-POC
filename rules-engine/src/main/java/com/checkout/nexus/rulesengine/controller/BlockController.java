package com.checkout.nexus.rulesengine.controller;

import com.checkout.nexus.rulesengine.model.entity.LedgerEntry;
import com.checkout.nexus.rulesengine.model.entity.NexusBlockRecord;
import com.checkout.nexus.rulesengine.repository.LedgerEntryRepository;
import com.checkout.nexus.rulesengine.repository.NexusBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final NexusBlockRepository nexusBlockRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @GetMapping
    public List<NexusBlockRecord> listTransactions(@RequestParam(defaultValue = "20") int limit) {
        return nexusBlockRepository.findAllByOrderByReceivedAtDesc(PageRequest.of(0, limit));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NexusBlockRecord> getTransaction(@PathVariable String id) {
        return nexusBlockRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/ledger")
    public ResponseEntity<Map<String, Object>> getTransactionLedger(@PathVariable String id) {
        return nexusBlockRepository.findById(id)
            .map(txn -> {
                List<LedgerEntry> entries = ledgerEntryRepository.findByNexusId(id);
                return ResponseEntity.ok(Map.<String, Object>of(
                    "transaction", txn,
                    "ledgerEntries", entries
                ));
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
