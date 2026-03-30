package com.checkout.nexus.rulesengine.controller;

import com.checkout.nexus.rulesengine.model.FiringContext;
import com.checkout.nexus.rulesengine.model.entity.Rule;
import com.checkout.nexus.rulesengine.repository.AccountRepository;
import com.checkout.nexus.rulesengine.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/rules")
@RequiredArgsConstructor
public class RulesController {

    private final RuleRepository ruleRepository;
    private final AccountRepository accountRepository;

    @GetMapping
    public List<Rule> listRules() {
        return ruleRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> createRule(@RequestBody Rule rule) {
        ResponseEntity<?> validation = validateRule(rule);
        if (validation != null) return validation;
        rule.setId(null);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(ruleRepository.save(rule));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Rule> getRule(@PathVariable UUID id) {
        return ruleRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateRule(@PathVariable UUID id, @RequestBody Rule update) {
        ResponseEntity<?> validation = validateRule(update);
        if (validation != null) return validation;
        return ruleRepository.findById(id)
            .map(existing -> {
                existing.setName(update.getName());
                existing.setDescription(update.getDescription());
                existing.setProductType(update.getProductType());
                existing.setTransactionType(update.getTransactionType());
                existing.setTransactionStatus(update.getTransactionStatus());
                existing.setLegType(update.getLegType());
                existing.setLegStatus(update.getLegStatus());
                existing.setPartyType(update.getPartyType());
                existing.setFiringContext(update.getFiringContext());
                existing.setFeeType(update.getFeeType());
                existing.setPassthrough(update.getPassthrough());
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

    private ResponseEntity<?> validateRule(Rule rule) {
        if (!accountRepository.existsByCodeAndEnabledTrue(rule.getDebitAccount())) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Unknown debit account: " + rule.getDebitAccount()));
        }
        if (!accountRepository.existsByCodeAndEnabledTrue(rule.getCreditAccount())) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Unknown credit account: " + rule.getCreditAccount()));
        }
        if (rule.getFiringContext() == FiringContext.FEE) {
            if (rule.getFeeType() == null || rule.getFeeType().isBlank()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "FEE rules must have feeType set"));
            }
            if (!"fee_amount".equals(rule.getAmountSource())) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "FEE rules must use amountSource=fee_amount"));
            }
        }
        if (rule.getFiringContext() == FiringContext.LEG) {
            if (rule.getFeeType() != null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "LEG rules must not have feeType set"));
            }
        }
        return null;
    }
}
