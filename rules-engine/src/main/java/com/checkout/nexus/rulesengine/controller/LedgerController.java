package com.checkout.nexus.rulesengine.controller;

import com.checkout.nexus.rulesengine.model.entity.LedgerEntry;
import com.checkout.nexus.rulesengine.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerEntryRepository ledgerEntryRepository;

    @GetMapping("/entries")
    public List<LedgerEntry> getEntries(
            @RequestParam(required = false) String transactionId,
            @RequestParam(defaultValue = "50") int limit) {
        if (transactionId != null && !transactionId.isEmpty()) {
            return ledgerEntryRepository.findByTransactionId(transactionId);
        }
        return ledgerEntryRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }

    @GetMapping("/entries/summary")
    public Map<String, Object> getSummary() {
        long totalEntries = ledgerEntryRepository.countAll();
        List<String> transactionIds = ledgerEntryRepository.findDistinctTransactionIds();
        return Map.of(
            "totalEntries", totalEntries,
            "totalTransactions", transactionIds.size()
        );
    }
}
