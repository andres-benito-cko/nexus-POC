package com.checkout.nexus.transformer.controller;

import com.checkout.nexus.transformer.model.NexusBlock;
import com.checkout.nexus.transformer.service.BlockStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/blocks")
@RequiredArgsConstructor
public class TransformerController {

    private final BlockStore blockStore;

    @GetMapping
    public List<NexusBlock> getTransactions(@RequestParam(defaultValue = "20") int limit) {
        return blockStore.getLatest(limit);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NexusBlock> getTransaction(@PathVariable String id) {
        return blockStore.get(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
