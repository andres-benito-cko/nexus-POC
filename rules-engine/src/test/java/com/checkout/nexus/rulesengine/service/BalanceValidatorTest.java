package com.checkout.nexus.rulesengine.service;

import com.checkout.nexus.rulesengine.model.PostingSide;
import com.checkout.nexus.rulesengine.model.entity.LedgerEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BalanceValidatorTest {

    private final BalanceValidator validator = new BalanceValidator();

    @Test
    void balanced_single_currency_returns_empty() {
        var entries = List.of(
            entry("scheme_clearing_settlement", PostingSide.DEBIT,  "EUR", "100.00"),
            entry("client",                    PostingSide.CREDIT, "EUR", "100.00")
        );
        assertThat(validator.validate(entries)).isEmpty();
    }

    @Test
    void unbalanced_returns_error_with_currency_and_totals() {
        var entries = List.of(
            entry("scheme_clearing_settlement", PostingSide.DEBIT,  "EUR", "100.00"),
            entry("client",                    PostingSide.CREDIT, "EUR", "90.00")
        );
        var errors = validator.validate(entries);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).currency()).isEqualTo("EUR");
        assertThat(errors.get(0).debitTotal()).isEqualByComparingTo("100.00");
        assertThat(errors.get(0).creditTotal()).isEqualByComparingTo("90.00");
    }

    @Test
    void validates_independently_per_currency() {
        var entries = List.of(
            entry("scheme_clearing_settlement", PostingSide.DEBIT,  "EUR", "100.00"),
            entry("client",                    PostingSide.CREDIT, "EUR", "100.00"),
            // USD imbalanced — missing debit
            entry("revenue",                   PostingSide.CREDIT, "USD", "10.00")
        );
        var errors = validator.validate(entries);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).currency()).isEqualTo("USD");
    }

    @Test
    void multiple_entries_same_currency_same_side_are_summed() {
        var entries = List.of(
            entry("scheme_clearing_settlement", PostingSide.DEBIT,  "EUR", "60.00"),
            entry("revenue",                   PostingSide.DEBIT,  "EUR", "40.00"),
            entry("client",                    PostingSide.CREDIT, "EUR", "100.00")
        );
        assertThat(validator.validate(entries)).isEmpty();
    }

    @Test
    void empty_entries_returns_empty() {
        assertThat(validator.validate(List.of())).isEmpty();
    }

    private LedgerEntry entry(String account, PostingSide side, String currency, String amount) {
        return LedgerEntry.builder()
            .account(account)
            .side(side)
            .currency(currency)
            .amount(new BigDecimal(amount))
            .nexusId("test-nexus")
            .build();
    }
}
