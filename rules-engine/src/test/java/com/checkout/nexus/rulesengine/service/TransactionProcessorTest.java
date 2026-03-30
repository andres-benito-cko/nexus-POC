package com.checkout.nexus.rulesengine.service;

import com.checkout.nexus.rulesengine.model.FiringContext;
import com.checkout.nexus.rulesengine.model.PostingSide;
import com.checkout.nexus.rulesengine.model.NexusBlock;
import com.checkout.nexus.rulesengine.model.entity.LedgerEntry;
import com.checkout.nexus.rulesengine.model.entity.Rule;
import com.checkout.nexus.rulesengine.model.entity.PostingError;
import com.checkout.nexus.rulesengine.repository.LedgerEntryRepository;
import com.checkout.nexus.rulesengine.repository.PostingErrorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionProcessorTest {

    @Mock LedgerEntryRepository ledgerEntryRepository;
    @Mock PostingErrorRepository postingErrorRepository;
    @Mock KafkaTemplate kafkaTemplate;

    private TransactionProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new TransactionProcessor(
            ledgerEntryRepository, postingErrorRepository,
            new BalanceValidator(), kafkaTemplate
        );
    }

    @Test
    void leg_rule_generates_two_atomic_postings() {
        NexusBlock nexus = nexusBlock();
        NexusBlock.Transaction txn = transaction("ACQUIRING", "CAPTURE", "SETTLED",
            List.of(leg("leg-1", "SCHEME_SETTLEMENT", null, 100.0, "EUR", List.of())));
        Rule rule = legRule("ACQUIRING", "CAPTURE", "SETTLED", "SCHEME_SETTLEMENT",
            "scheme_clearing_settlement", "client");

        List<LedgerEntry> entries = processor.evaluate(nexus, txn, List.of(rule));

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getAccount()).isEqualTo("scheme_clearing_settlement");
        assertThat(entries.get(0).getSide()).isEqualTo(PostingSide.DEBIT);
        assertThat(entries.get(1).getAccount()).isEqualTo("client");
        assertThat(entries.get(1).getSide()).isEqualTo(PostingSide.CREDIT);
    }

    @Test
    void fee_rule_fires_once_per_matching_fee() {
        NexusBlock nexus = nexusBlock();
        NexusBlock.Fee fee1 = fee("INTERCHANGE", 10.0, "EUR", true);
        NexusBlock.Fee fee2 = fee("SCHEME_FEE",  5.0,  "EUR", false);
        NexusBlock.Transaction txn = transaction("ACQUIRING", "CAPTURE", null,
            List.of(leg("leg-1", "SCHEME_SETTLEMENT", "PREDICTED", 100.0, "EUR", List.of(fee1, fee2))));
        Rule rule = feeRule("ACQUIRING", "CAPTURE", null, "SCHEME_SETTLEMENT",
            null, "INTERCHANGE", null, "passthrough_cos", "accrued_cos");

        List<LedgerEntry> entries = processor.evaluate(nexus, txn, List.of(rule));

        // Only INTERCHANGE fee matches — 2 postings
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getAmount()).isEqualByComparingTo("10.0");
    }

    @Test
    void leg_status_filter_is_applied() {
        NexusBlock nexus = nexusBlock();
        NexusBlock.Transaction txn = transaction("ACQUIRING", "CAPTURE", null,
            List.of(
                leg("leg-1", "SCHEME_SETTLEMENT", "PREDICTED", 100.0, "EUR", List.of()),
                leg("leg-2", "SCHEME_SETTLEMENT", "ACTUAL",    100.0, "EUR", List.of())
            ));
        Rule rule = legRule("ACQUIRING", "CAPTURE", null, "SCHEME_SETTLEMENT",
            "scheme_clearing_settlement", "client");
        rule.setLegStatus("ACTUAL");

        List<LedgerEntry> entries = processor.evaluate(nexus, txn, List.of(rule));

        // Only ACTUAL leg matches
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getLegId()).isEqualTo("leg-2");
    }

    @Test
    void passthrough_filter_is_applied_on_fee_rules() {
        NexusBlock nexus = nexusBlock();
        NexusBlock.Fee passthroughFee    = fee("INTERCHANGE", 10.0, "EUR", true);
        NexusBlock.Fee nonPassthroughFee = fee("INTERCHANGE",  5.0, "EUR", false);
        NexusBlock.Transaction txn = transaction("ACQUIRING", "CAPTURE", null,
            List.of(leg("leg-1", "SCHEME_SETTLEMENT", null, 100.0, "EUR",
                List.of(passthroughFee, nonPassthroughFee))));
        Rule rule = feeRule("ACQUIRING", "CAPTURE", null, "SCHEME_SETTLEMENT",
            null, "INTERCHANGE", true, "passthrough_cos", "accrued_cos");

        List<LedgerEntry> entries = processor.evaluate(nexus, txn, List.of(rule));

        // Only the passthrough=true fee matches
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getAmount()).isEqualByComparingTo("10.0");
    }

    @Test
    void unbalanced_transaction_saves_posting_error_and_skips_entries() {
        NexusBlock nexus = nexusBlock();
        NexusBlock.Transaction txn = transaction("ACQUIRING", "CAPTURE", "SETTLED",
            List.of(leg("leg-1", "SCHEME_SETTLEMENT", null, 100.0, "EUR", List.of())));
        Rule debitOnlyRule = legRule("ACQUIRING", "CAPTURE", "SETTLED", "SCHEME_SETTLEMENT",
            "scheme_clearing_settlement", "client");

        BalanceValidator spyValidator = spy(new BalanceValidator());
        TransactionProcessor spyProcessor = new TransactionProcessor(
            ledgerEntryRepository, postingErrorRepository, spyValidator, kafkaTemplate
        );
        doReturn(List.of(new BalanceValidator.BalanceError("EUR",
            java.math.BigDecimal.TEN, java.math.BigDecimal.ZERO)))
            .when(spyValidator).validate(anyList());

        spyProcessor.process(nexus, txn, List.of(debitOnlyRule));

        verify(postingErrorRepository).save(any(PostingError.class));
        verify(ledgerEntryRepository, never()).saveAll(anyList());
    }

    // --- Helpers ---

    private NexusBlock nexusBlock() {
        NexusBlock b = new NexusBlock();
        b.setNexusId("nexus-test-001");
        return b;
    }

    private NexusBlock.Transaction transaction(String product, String type, String status,
            List<NexusBlock.Leg> legs) {
        NexusBlock.Transaction t = new NexusBlock.Transaction();
        t.setTransactionId("txn-001");
        t.setProductType(product);
        t.setTransactionType(type);
        t.setTransactionStatus(status);
        t.setLegs(legs);
        return t;
    }

    private NexusBlock.Leg leg(String id, String type, String status,
            double amount, String currency, List<NexusBlock.Fee> fees) {
        NexusBlock.Leg l = new NexusBlock.Leg();
        l.setLegId(id);
        l.setLegType(type);
        l.setLegStatus(status);
        l.setLegAmount(amount);
        l.setLegCurrency(currency);
        l.setFees(fees);
        return l;
    }

    private NexusBlock.Fee fee(String type, double amount, String currency, boolean passthrough) {
        NexusBlock.Fee f = new NexusBlock.Fee();
        f.setFeeId(UUID.randomUUID().toString());
        f.setFeeType(type);
        f.setFeeAmount(amount);
        f.setFeeCurrency(currency);
        f.setPassthrough(passthrough);
        return f;
    }

    private Rule legRule(String product, String type, String status, String legType,
            String debit, String credit) {
        return Rule.builder()
            .id(UUID.randomUUID())
            .name("test-leg-rule")
            .firingContext(FiringContext.LEG)
            .productType(product)
            .transactionType(type)
            .transactionStatus(status)
            .legType(legType)
            .debitAccount(debit)
            .creditAccount(credit)
            .amountSource("leg_amount")
            .enabled(true)
            .build();
    }

    private Rule feeRule(String product, String type, String status, String legType,
            String legStatus, String feeType, Boolean passthrough, String debit, String credit) {
        return Rule.builder()
            .id(UUID.randomUUID())
            .name("test-fee-rule")
            .firingContext(FiringContext.FEE)
            .productType(product)
            .transactionType(type)
            .transactionStatus(status)
            .legType(legType)
            .legStatus(legStatus)
            .feeType(feeType)
            .passthrough(passthrough)
            .debitAccount(debit)
            .creditAccount(credit)
            .amountSource("fee_amount")
            .enabled(true)
            .build();
    }
}
