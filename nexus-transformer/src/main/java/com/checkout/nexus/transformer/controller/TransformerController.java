package com.checkout.nexus.transformer.controller;

import com.checkout.nexus.transformer.model.NexusTransaction;
import com.checkout.nexus.transformer.service.TransactionStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransformerController {

    private final TransactionStore transactionStore;

    @GetMapping
    public List<NexusTransaction> getTransactions(@RequestParam(defaultValue = "20") int limit) {
        return transactionStore.getLatest(limit);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NexusTransaction> getTransaction(@PathVariable String id) {
        return transactionStore.get(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
