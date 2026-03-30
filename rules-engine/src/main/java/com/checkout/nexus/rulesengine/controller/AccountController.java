package com.checkout.nexus.rulesengine.controller;

import com.checkout.nexus.rulesengine.model.entity.Account;
import com.checkout.nexus.rulesengine.repository.AccountRepository;
import com.checkout.nexus.rulesengine.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountRepository accountRepository;
    private final RuleRepository ruleRepository;

    @GetMapping
    public List<Account> listAccounts() {
        return accountRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> createAccount(@RequestBody Account account) {
        if (accountRepository.existsById(account.getCode())) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Account code already exists: " + account.getCode()));
        }
        account.setCreatedAt(LocalDateTime.now());
        return ResponseEntity.ok(accountRepository.save(account));
    }

    @PutMapping("/{code}")
    public ResponseEntity<?> updateAccount(@PathVariable String code, @RequestBody Account update) {
        return accountRepository.findById(code)
            .map(existing -> {
                existing.setName(update.getName());
                existing.setAccountType(update.getAccountType());
                existing.setNormalBalance(update.getNormalBalance());
                existing.setDescription(update.getDescription());
                existing.setEnabled(update.isEnabled());
                return ResponseEntity.ok(accountRepository.save(existing));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<?> deleteAccount(@PathVariable String code) {
        if (ruleRepository.existsByDebitAccountOrCreditAccount(code, code)) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Account '" + code + "' is referenced by existing rules"));
        }
        return accountRepository.findById(code)
            .map(account -> {
                account.setEnabled(false);
                accountRepository.save(account);
                return ResponseEntity.noContent().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
