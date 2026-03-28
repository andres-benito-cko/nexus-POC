package com.checkout.nexus.rulesengine.controller;

import com.checkout.nexus.rulesengine.model.entity.Rule;
import com.checkout.nexus.rulesengine.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/rules")
@RequiredArgsConstructor
public class RulesController {

    private final RuleRepository ruleRepository;

    @GetMapping
    public List<Rule> listRules() {
        return ruleRepository.findAll();
    }

    @PostMapping
    public Rule createRule(@RequestBody Rule rule) {
        rule.setId(null);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        return ruleRepository.save(rule);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Rule> getRule(@PathVariable UUID id) {
        return ruleRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Rule> updateRule(@PathVariable UUID id, @RequestBody Rule update) {
        return ruleRepository.findById(id)
            .map(existing -> {
                existing.setName(update.getName());
                existing.setDescription(update.getDescription());
                existing.setProductType(update.getProductType());
                existing.setTransactionType(update.getTransactionType());
                existing.setTransactionStatus(update.getTransactionStatus());
                existing.setLegType(update.getLegType());
                existing.setPartyType(update.getPartyType());
                existing.setDebitAccount(update.getDebitAccount());
                existing.setCreditAccount(update.getCreditAccount());
                existing.setAmountSource(update.getAmountSource());
                existing.setEnabled(update.isEnabled());
                existing.setUpdatedAt(LocalDateTime.now());
                return ResponseEntity.ok(ruleRepository.save(existing));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        if (ruleRepository.existsById(id)) {
            ruleRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
