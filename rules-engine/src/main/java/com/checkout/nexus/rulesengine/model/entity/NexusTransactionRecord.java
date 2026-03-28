package com.checkout.nexus.rulesengine.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "nexus_transactions")
public class NexusTransactionRecord {

    @Id
    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "action_id", nullable = false)
    private String actionId;

    @Column(name = "action_root_id")
    private String actionRootId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "entity_id")
    private String entityId;

    @Column(name = "cko_entity_id")
    private String ckoEntityId;

    @Column(name = "trade_family")
    private String tradeFamily;

    @Column(name = "trade_type")
    private String tradeType;

    @Column(name = "trade_status")
    private String tradeStatus;

    @Column(name = "trade_amount", precision = 20, scale = 6)
    private BigDecimal tradeAmount;

    @Column(name = "trade_currency", length = 3)
    private String tradeCurrency;

    @Column(name = "raw_json", nullable = false, columnDefinition = "TEXT")
    private String rawJson;

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private LocalDateTime receivedAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
