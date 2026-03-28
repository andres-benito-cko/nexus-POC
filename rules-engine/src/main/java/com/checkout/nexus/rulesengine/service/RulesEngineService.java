package com.checkout.nexus.rulesengine.service;

import com.checkout.nexus.rulesengine.model.LedgerEntryMessage;
import com.checkout.nexus.rulesengine.model.NexusTransaction;
import com.checkout.nexus.rulesengine.model.entity.LedgerEntry;
import com.checkout.nexus.rulesengine.model.entity.NexusTransactionRecord;
import com.checkout.nexus.rulesengine.model.entity.Rule;
import com.checkout.nexus.rulesengine.repository.LedgerEntryRepository;
import com.checkout.nexus.rulesengine.repository.NexusTransactionRepository;
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
    private final NexusTransactionRepository nexusTransactionRepository;
    private final KafkaTemplate<String, LedgerEntryMessage> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "nexus.transactions", groupId = "rules-engine",
            containerFactory = "nexusContainerFactory")
    public void onNexusTransaction(NexusTransaction nexus) {
        log.info("Received Nexus transaction: transactionId={}, status={}",
            nexus.getTransactionId(), nexus.getStatus());

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
                kafkaTemplate.send(LEDGER_TOPIC, nexus.getTransactionId(), msg);
                log.info("Produced ledger entry: rule={}, debit={}, credit={}, amount={} {}",
                    saved.getRuleName(), saved.getDebitAccount(), saved.getCreditAccount(),
                    saved.getAmount(), saved.getCurrency());
            }

        } catch (Exception e) {
            log.error("Error processing Nexus transaction: transactionId={}", nexus.getTransactionId(), e);
        }
    }

    private void persistTransaction(NexusTransaction nexus) {
        try {
            String rawJson = objectMapper.writeValueAsString(nexus);

            String tradeFamily = null, tradeType = null, tradeStatus = null;
            BigDecimal tradeAmount = null;
            String tradeCurrency = null;

            if (nexus.getTrades() != null && !nexus.getTrades().isEmpty()) {
                NexusTransaction.Trade trade = nexus.getTrades().get(0);
                tradeFamily = trade.getTradeFamily();
                tradeType = trade.getTradeType();
                tradeStatus = trade.getTradeStatus();
                tradeAmount = BigDecimal.valueOf(trade.getTradeAmount());
                tradeCurrency = trade.getTradeCurrency();
            }

            NexusTransactionRecord record = NexusTransactionRecord.builder()
                .transactionId(nexus.getTransactionId())
                .actionId(nexus.getActionId())
                .actionRootId(nexus.getActionRootId())
                .status(nexus.getStatus())
                .entityId(nexus.getEntity() != null ? nexus.getEntity().getId() : null)
                .ckoEntityId(nexus.getCkoEntityId())
                .tradeFamily(tradeFamily)
                .tradeType(tradeType)
                .tradeStatus(tradeStatus)
                .tradeAmount(tradeAmount)
                .tradeCurrency(tradeCurrency)
                .rawJson(rawJson)
                .receivedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

            nexusTransactionRepository.save(record);
        } catch (Exception e) {
            log.error("Error persisting Nexus transaction", e);
        }
    }

    private List<LedgerEntry> applyRules(NexusTransaction nexus, List<Rule> rules) {
        List<LedgerEntry> entries = new ArrayList<>();

        if (nexus.getTrades() == null) return entries;

        for (NexusTransaction.Trade trade : nexus.getTrades()) {
            for (Rule rule : rules) {
                if (!matches(rule, trade)) continue;

                if (trade.getLegs() == null) continue;

                for (NexusTransaction.Leg leg : trade.getLegs()) {
                    // Check leg_type match
                    if (rule.getLegType() != null && !rule.getLegType().equals(leg.getLegType())) {
                        continue;
                    }

                    if ("fee_amount".equals(rule.getAmountSource())) {
                        // Create entries for each fee
                        if (leg.getFees() != null) {
                            for (NexusTransaction.Fee fee : leg.getFees()) {
                                if (fee.getFeeAmount() > 0) {
                                    entries.add(buildEntry(rule, nexus, trade, leg,
                                        BigDecimal.valueOf(fee.getFeeAmount()), fee.getFeeCurrency()));
                                }
                            }
                        }
                    } else {
                        // Use leg_amount
                        if (leg.getLegAmount() > 0) {
                            entries.add(buildEntry(rule, nexus, trade, leg,
                                BigDecimal.valueOf(leg.getLegAmount()), leg.getLegCurrency()));
                        }
                    }
                }
            }
        }

        return entries;
    }

    private boolean matches(Rule rule, NexusTransaction.Trade trade) {
        if (rule.getTradeFamily() != null && !rule.getTradeFamily().equals(trade.getTradeFamily())) {
            return false;
        }
        if (rule.getTradeType() != null && !rule.getTradeType().equals(trade.getTradeType())) {
            return false;
        }
        if (rule.getTradeStatus() != null && !rule.getTradeStatus().equals(trade.getTradeStatus())) {
            return false;
        }
        return true;
    }

    private LedgerEntry buildEntry(Rule rule, NexusTransaction nexus, NexusTransaction.Trade trade,
            NexusTransaction.Leg leg, BigDecimal amount, String currency) {
        return LedgerEntry.builder()
            .ruleId(rule.getId())
            .ruleName(rule.getName())
            .transactionId(nexus.getTransactionId())
            .tradeId(trade.getTradeId())
            .legId(leg.getLegId())
            .debitAccount(rule.getDebitAccount())
            .creditAccount(rule.getCreditAccount())
            .amount(amount)
            .currency(currency != null ? currency : "EUR")
            .tradeFamily(trade.getTradeFamily())
            .tradeType(trade.getTradeType())
            .tradeStatus(trade.getTradeStatus())
            .createdAt(LocalDateTime.now())
            .build();
    }

    private LedgerEntryMessage toLedgerMessage(LedgerEntry entry) {
        return LedgerEntryMessage.builder()
            .id(entry.getId())
            .ruleId(entry.getRuleId())
            .ruleName(entry.getRuleName())
            .transactionId(entry.getTransactionId())
            .tradeId(entry.getTradeId())
            .legId(entry.getLegId())
            .debitAccount(entry.getDebitAccount())
            .creditAccount(entry.getCreditAccount())
            .amount(entry.getAmount())
            .currency(entry.getCurrency())
            .tradeFamily(entry.getTradeFamily())
            .tradeType(entry.getTradeType())
            .tradeStatus(entry.getTradeStatus())
            .build();
    }
}
