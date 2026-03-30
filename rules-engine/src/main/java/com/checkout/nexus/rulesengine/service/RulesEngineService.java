package com.checkout.nexus.rulesengine.service;

import com.checkout.nexus.rulesengine.model.NexusBlock;
import com.checkout.nexus.rulesengine.model.entity.NexusBlockRecord;
import com.checkout.nexus.rulesengine.model.entity.Rule;
import com.checkout.nexus.rulesengine.repository.NexusBlockRepository;
import com.checkout.nexus.rulesengine.repository.RuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RulesEngineService {

    private final RuleRepository ruleRepository;
    private final NexusBlockRepository nexusBlockRepository;
    private final TransactionProcessor transactionProcessor;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "nexus.blocks", groupId = "rules-engine",
            containerFactory = "nexusContainerFactory")
    public void onNexusBlock(NexusBlock nexus) {
        log.info("Received Nexus block: nexusId={}, status={}", nexus.getNexusId(), nexus.getStatus());
        try {
            persistBlock(nexus);
            List<Rule> rules = ruleRepository.findByEnabledTrue();
            if (nexus.getTransactions() != null) {
                for (NexusBlock.Transaction txn : nexus.getTransactions()) {
                    transactionProcessor.process(nexus, txn, rules);
                }
            }
        } catch (Exception e) {
            log.error("Error processing Nexus block: nexusId={}", nexus.getNexusId(), e);
        }
    }

    private void persistBlock(NexusBlock nexus) {
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

            nexusBlockRepository.save(NexusBlockRecord.builder()
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
                .build());
        } catch (Exception e) {
            log.error("Error persisting Nexus block: nexusId={}", nexus.getNexusId(), e);
        }
    }
}
