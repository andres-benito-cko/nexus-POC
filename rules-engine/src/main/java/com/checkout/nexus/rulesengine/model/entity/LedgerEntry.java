package com.checkout.nexus.rulesengine.model.entity;

import com.checkout.nexus.rulesengine.model.PostingSide;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "rule_name")
    private String ruleName;

    @Column(name = "nexus_id", nullable = false)
    private String nexusId;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "leg_id")
    private String legId;

    @Column(nullable = false)
    private String account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostingSide side;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "product_type")
    private String productType;

    @Column(name = "transaction_type")
    private String transactionType;

    @Column(name = "transaction_status")
    private String transactionStatus;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
