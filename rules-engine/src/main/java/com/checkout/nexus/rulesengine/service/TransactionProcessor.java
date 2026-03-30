package com.checkout.nexus.rulesengine.service;

import com.checkout.nexus.rulesengine.model.FiringContext;
import com.checkout.nexus.rulesengine.model.LedgerEntryMessage;
import com.checkout.nexus.rulesengine.model.NexusBlock;
import com.checkout.nexus.rulesengine.model.PostingSide;
import com.checkout.nexus.rulesengine.model.entity.LedgerEntry;
import com.checkout.nexus.rulesengine.model.entity.PostingError;
import com.checkout.nexus.rulesengine.model.entity.Rule;
import com.checkout.nexus.rulesengine.repository.LedgerEntryRepository;
import com.checkout.nexus.rulesengine.repository.PostingErrorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionProcessor {

    private static final String LEDGER_TOPIC = "nexus.ledger.entries";

    private final LedgerEntryRepository ledgerEntryRepository;
    private final PostingErrorRepository postingErrorRepository;
    private final BalanceValidator balanceValidator;
    private final KafkaTemplate<String, LedgerEntryMessage> kafkaTemplate;

    @Transactional
    public void process(NexusBlock nexus, NexusBlock.Transaction txn, List<Rule> rules) {
        List<LedgerEntry> entries = evaluate(nexus, txn, rules);

        List<BalanceValidator.BalanceError> errors = balanceValidator.validate(entries);
        if (!errors.isEmpty()) {
            BalanceValidator.BalanceError first = errors.get(0);
            String ruleIds = entries.stream()
                .filter(e -> e.getRuleId() != null)
                .map(e -> e.getRuleId().toString())
                .distinct()
                .collect(Collectors.joining(","));
            postingErrorRepository.save(PostingError.builder()
                .nexusId(nexus.getNexusId())
                .transactionId(txn.getTransactionId())
                .currency(first.currency())
                .debitTotal(first.debitTotal())
                .creditTotal(first.creditTotal())
                .ruleIds(ruleIds)
                .build());
            log.warn("Balance check failed: nexusId={} txnId={} currency={} debit={} credit={}",
                nexus.getNexusId(), txn.getTransactionId(),
                first.currency(), first.debitTotal(), first.creditTotal());
            return;
        }

        ledgerEntryRepository.saveAll(entries);
        entries.forEach(entry ->
            kafkaTemplate.send(LEDGER_TOPIC, nexus.getNexusId(), toLedgerMessage(entry)));
        log.info("Committed {} postings for txnId={}", entries.size(), txn.getTransactionId());
    }

    List<LedgerEntry> evaluate(NexusBlock nexus, NexusBlock.Transaction txn, List<Rule> rules) {
        List<LedgerEntry> entries = new ArrayList<>();
        if (txn.getLegs() == null) return entries;

        for (NexusBlock.Leg leg : txn.getLegs()) {
            for (Rule rule : rules) {
                if (!matchesTransaction(rule, txn)) continue;
                if (!matchesLeg(rule, leg)) continue;

                if (rule.getFiringContext() == FiringContext.FEE) {
                    if (leg.getFees() == null) continue;
                    for (NexusBlock.Fee fee : leg.getFees()) {
                        if (!matchesFee(rule, fee)) continue;
                        if (fee.getFeeAmount() <= 0) continue;
                        entries.addAll(buildPostings(rule, nexus, txn, leg,
                            BigDecimal.valueOf(fee.getFeeAmount()),
                            fee.getFeeCurrency() != null ? fee.getFeeCurrency() : "EUR"));
                    }
                } else {
                    if (leg.getLegAmount() <= 0) continue;
                    entries.addAll(buildPostings(rule, nexus, txn, leg,
                        BigDecimal.valueOf(leg.getLegAmount()),
                        leg.getLegCurrency() != null ? leg.getLegCurrency() : "EUR"));
                }
            }
        }
        return entries;
    }

    private boolean matchesTransaction(Rule rule, NexusBlock.Transaction txn) {
        if (rule.getProductType() != null && !rule.getProductType().equals(txn.getProductType())) return false;
        if (rule.getTransactionType() != null && !rule.getTransactionType().equals(txn.getTransactionType())) return false;
        if (rule.getTransactionStatus() != null && !rule.getTransactionStatus().equals(txn.getTransactionStatus())) return false;
        return true;
    }

    private boolean matchesLeg(Rule rule, NexusBlock.Leg leg) {
        if (rule.getLegType() != null && !rule.getLegType().equals(leg.getLegType())) return false;
        if (rule.getLegStatus() != null && !rule.getLegStatus().equals(leg.getLegStatus())) return false;
        return true;
    }

    private boolean matchesFee(Rule rule, NexusBlock.Fee fee) {
        if (rule.getFeeType() != null && !rule.getFeeType().equals(fee.getFeeType())) return false;
        if (rule.getPassthrough() != null && !rule.getPassthrough().equals(fee.getPassthrough())) return false;
        return true;
    }

    private List<LedgerEntry> buildPostings(Rule rule, NexusBlock nexus,
            NexusBlock.Transaction txn, NexusBlock.Leg leg,
            BigDecimal amount, String currency) {
        LocalDateTime now = LocalDateTime.now();
        LedgerEntry debit = LedgerEntry.builder()
            .ruleId(rule.getId()).ruleName(rule.getName())
            .nexusId(nexus.getNexusId()).transactionId(txn.getTransactionId()).legId(leg.getLegId())
            .account(rule.getDebitAccount()).side(PostingSide.DEBIT)
            .amount(amount).currency(currency)
            .productType(txn.getProductType()).transactionType(txn.getTransactionType())
            .transactionStatus(txn.getTransactionStatus()).createdAt(now).build();
        LedgerEntry credit = LedgerEntry.builder()
            .ruleId(rule.getId()).ruleName(rule.getName())
            .nexusId(nexus.getNexusId()).transactionId(txn.getTransactionId()).legId(leg.getLegId())
            .account(rule.getCreditAccount()).side(PostingSide.CREDIT)
            .amount(amount).currency(currency)
            .productType(txn.getProductType()).transactionType(txn.getTransactionType())
            .transactionStatus(txn.getTransactionStatus()).createdAt(now).build();
        return List.of(debit, credit);
    }

    private LedgerEntryMessage toLedgerMessage(LedgerEntry entry) {
        return LedgerEntryMessage.builder()
            .id(entry.getId()).ruleId(entry.getRuleId()).ruleName(entry.getRuleName())
            .nexusId(entry.getNexusId()).transactionId(entry.getTransactionId()).legId(entry.getLegId())
            .account(entry.getAccount()).side(entry.getSide())
            .amount(entry.getAmount()).currency(entry.getCurrency())
            .productType(entry.getProductType()).transactionType(entry.getTransactionType())
            .transactionStatus(entry.getTransactionStatus()).build();
    }
}
