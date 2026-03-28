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
@Table(name = "nexus_blocks")
public class NexusBlockRecord {

    @Id
    @Column(name = "nexus_id")
    private String nexusId;

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

    @Column(name = "product_type")
    private String productType;

    @Column(name = "transaction_type")
    private String transactionType;

    @Column(name = "transaction_status")
    private String transactionStatus;

    @Column(name = "transaction_amount", precision = 20, scale = 6)
    private BigDecimal transactionAmount;

    @Column(name = "transaction_currency", length = 3)
    private String transactionCurrency;

    @Column(name = "raw_json", nullable = false, columnDefinition = "TEXT")
    private String rawJson;

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private LocalDateTime receivedAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
