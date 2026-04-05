package com.checkout.nexus.rulesengine.controller;

import com.checkout.nexus.rulesengine.model.entity.LedgerEntry;
import com.checkout.nexus.rulesengine.model.entity.PostingError;
import com.checkout.nexus.rulesengine.repository.LedgerEntryRepository;
import com.checkout.nexus.rulesengine.repository.PostingErrorRepository;
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
    private final PostingErrorRepository postingErrorRepository;

    @GetMapping("/entries")
    public List<LedgerEntry> getEntries(
            @RequestParam(required = false) String nexusId,
            @RequestParam(defaultValue = "500") int limit) {
        if (nexusId != null && !nexusId.isEmpty()) {
            return ledgerEntryRepository.findByNexusId(nexusId);
        }
        return ledgerEntryRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }

    @GetMapping("/entries/summary")
    public Map<String, Object> getSummary() {
        long totalEntries = ledgerEntryRepository.countAll();
        List<String> nexusIds = ledgerEntryRepository.findDistinctNexusIds();
        return Map.of(
            "totalEntries", totalEntries,
            "totalTransactions", nexusIds.size()
        );
    }

    @GetMapping("/errors")
    public List<PostingError> getErrors(
            @RequestParam(required = false) String nexusId,
            @RequestParam(required = false) String transactionId,
            @RequestParam(defaultValue = "500") int limit) {
        if (nexusId != null && !nexusId.isEmpty()) {
            return postingErrorRepository.findByNexusId(nexusId);
        }
        if (transactionId != null && !transactionId.isEmpty()) {
            return postingErrorRepository.findByTransactionId(transactionId);
        }
        return postingErrorRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }
}
