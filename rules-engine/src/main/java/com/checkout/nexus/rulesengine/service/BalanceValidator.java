package com.checkout.nexus.rulesengine.service;

import com.checkout.nexus.rulesengine.model.PostingSide;
import com.checkout.nexus.rulesengine.model.entity.LedgerEntry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class BalanceValidator {

    public record BalanceError(String currency, BigDecimal debitTotal, BigDecimal creditTotal) {}

    public List<BalanceError> validate(List<LedgerEntry> entries) {
        Map<String, BigDecimal> debits  = new HashMap<>();
        Map<String, BigDecimal> credits = new HashMap<>();

        for (LedgerEntry entry : entries) {
            if (entry.getSide() == PostingSide.DEBIT) {
                debits.merge(entry.getCurrency(), entry.getAmount(), BigDecimal::add);
            } else {
                credits.merge(entry.getCurrency(), entry.getAmount(), BigDecimal::add);
            }
        }

        Set<String> currencies = new HashSet<>();
        currencies.addAll(debits.keySet());
        currencies.addAll(credits.keySet());

        return currencies.stream()
            .filter(currency -> {
                BigDecimal d = debits.getOrDefault(currency, BigDecimal.ZERO);
                BigDecimal c = credits.getOrDefault(currency, BigDecimal.ZERO);
                return d.compareTo(c) != 0;
            })
            .map(currency -> new BalanceError(
                currency,
                debits.getOrDefault(currency, BigDecimal.ZERO),
                credits.getOrDefault(currency, BigDecimal.ZERO)
            ))
            .toList();
    }
}
