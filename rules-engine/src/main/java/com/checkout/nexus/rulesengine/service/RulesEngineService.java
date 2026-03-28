package com.checkout.nexus.rulesengine.service;

import com.checkout.nexus.rulesengine.model.LedgerEntryMessage;
import com.checkout.nexus.rulesengine.model.NexusBlock;
import com.checkout.nexus.rulesengine.model.entity.LedgerEntry;
import com.checkout.nexus.rulesengine.model.entity.NexusBlockRecord;
import com.checkout.nexus.rulesengine.model.entity.Rule;
import com.checkout.nexus.rulesengine.repository.LedgerEntryRepository;
import com.checkout.nexus.rulesengine.repository.NexusBlockRepository;
import com.checkout.nexus.rulesengine.repository.RuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RulesEngineService {

    private static final String LEDGER_TOPIC = "nexus.ledger.entries";

    private final RuleRepository ruleRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final NexusBlockRepository nexusBlockRepository;
    private final KafkaTemplate<String, LedgerEntryMessage> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "nexus.blocks", groupId = "rules-engine",
            containerFactory = "nexusContainerFactory")
    public void onNexusBlock(NexusBlock nexus) {
        log.info("Received Nexus transaction: nexusId={}, status={}",
            nexus.getNexusId(), nexus.getStatus());

        try {
            // Persist the Nexus transaction
            persistTransaction(nexus);

            // Load enabled rules
            List<Rule> rules = ruleRepository.findByEnabledTrue();

            // Apply rules
            List<LedgerEntry> entries = applyRules(nexus, rules);

            // Persist and publish entries
            for (LedgerEntry entry : entries) {
                LedgerEntry saved = ledgerEntryRepository.save(entry);
                LedgerEntryMessage msg = toLedgerMessage(saved);
                kafkaTemplate.send(LEDGER_TOPIC, nexus.getNexusId(), msg);
                log.info("Produced ledger entry: rule={}, debit={}, credit={}, amount={} {}",
                    saved.getRuleName(), saved.getDebitAccount(), saved.getCreditAccount(),
                    saved.getAmount(), saved.getCurrency());
            }

        } catch (Exception e) {
            log.error("Error processing Nexus transaction: nexusId={}", nexus.getNexusId(), e);
        }
    }

    private void persistTransaction(NexusBlock nexus) {
        try {
            String rawJson = objectMapper.writeValueAsString(nexus);

            String productType = null, transactionType = null, transactionStatus = null;
            BigDecimal transactionAmount = null;
            String transactionCurrency = null;

            if (nexus.getTransactions() != null && !nexus.getTransactions().isEmpty()) {
                NexusBlock.Transaction txn = nexus.getTransactions().get(0);
                productType = txn.getProductType();
                transactionType = txn.getTransactionType();
                transactionStatus = txn.getTransactionStatus();
                transactionAmount = BigDecimal.valueOf(txn.getTransactionAmount());
                transactionCurrency = txn.getTransactionCurrency();
            }

            NexusBlockRecord record = NexusBlockRecord.builder()
                .nexusId(nexus.getNexusId())
                .actionId(nexus.getActionId())
                .actionRootId(nexus.getActionRootId())
                .status(nexus.getStatus())
                .entityId(nexus.getEntity() != null ? nexus.getEntity().getId() : null)
                .ckoEntityId(nexus.getCkoEntityId())
                .productType(productType)
                .transactionType(transactionType)
                .transactionStatus(transactionStatus)
                .transactionAmount(transactionAmount)
                .transactionCurrency(transactionCurrency)
                .rawJson(rawJson)
                .receivedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

            nexusBlockRepository.save(record);
        } catch (Exception e) {
            log.error("Error persisting Nexus transaction", e);
        }
    }

    private List<LedgerEntry> applyRules(NexusBlock nexus, List<Rule> rules) {
        List<LedgerEntry> entries = new ArrayList<>();

        if (nexus.getTransactions() == null) return entries;

        for (NexusBlock.Transaction txn : nexus.getTransactions()) {
            for (Rule rule : rules) {
                if (!matches(rule, txn)) continue;

                if (txn.getLegs() == null) continue;

                for (NexusBlock.Leg leg : txn.getLegs()) {
                    // Check leg_type match
                    if (rule.getLegType() != null && !rule.getLegType().equals(leg.getLegType())) {
                        continue;
                    }

                    if ("fee_amount".equals(rule.getAmountSource())) {
                        // Create entries for each fee
                        if (leg.getFees() != null) {
                            for (NexusBlock.Fee fee : leg.getFees()) {
                                if (fee.getFeeAmount() > 0) {
                                    entries.add(buildEntry(rule, nexus, txn, leg,
                                        BigDecimal.valueOf(fee.getFeeAmount()), fee.getFeeCurrency()));
                                }
                            }
                        }
                    } else {
                        // Use leg_amount
                        if (leg.getLegAmount() > 0) {
                            entries.add(buildEntry(rule, nexus, txn, leg,
                                BigDecimal.valueOf(leg.getLegAmount()), leg.getLegCurrency()));
                        }
                    }
                }
            }
        }

        return entries;
    }

    private boolean matches(Rule rule, NexusBlock.Transaction txn) {
        if (rule.getProductType() != null && !rule.getProductType().equals(txn.getProductType())) {
            return false;
        }
        if (rule.getTransactionType() != null && !rule.getTransactionType().equals(txn.getTransactionType())) {
            return false;
        }
        if (rule.getTransactionStatus() != null && !rule.getTransactionStatus().equals(txn.getTransactionStatus())) {
            return false;
        }
        return true;
    }

    private LedgerEntry buildEntry(Rule rule, NexusBlock nexus, NexusBlock.Transaction txn,
            NexusBlock.Leg leg, BigDecimal amount, String currency) {
        return LedgerEntry.builder()
            .ruleId(rule.getId())
            .ruleName(rule.getName())
            .nexusId(nexus.getNexusId())
            .transactionId(txn.getTransactionId())
            .legId(leg.getLegId())
            .debitAccount(rule.getDebitAccount())
            .creditAccount(rule.getCreditAccount())
            .amount(amount)
            .currency(currency != null ? currency : "EUR")
            .productType(txn.getProductType())
            .transactionType(txn.getTransactionType())
            .transactionStatus(txn.getTransactionStatus())
            .createdAt(LocalDateTime.now())
            .build();
    }

    private LedgerEntryMessage toLedgerMessage(LedgerEntry entry) {
        return LedgerEntryMessage.builder()
            .id(entry.getId())
            .ruleId(entry.getRuleId())
            .ruleName(entry.getRuleName())
            .nexusId(entry.getNexusId())
            .transactionId(entry.getTransactionId())
            .legId(entry.getLegId())
            .debitAccount(entry.getDebitAccount())
            .creditAccount(entry.getCreditAccount())
            .amount(entry.getAmount())
            .currency(entry.getCurrency())
            .productType(entry.getProductType())
            .transactionType(entry.getTransactionType())
            .transactionStatus(entry.getTransactionStatus())
            .build();
    }
}
