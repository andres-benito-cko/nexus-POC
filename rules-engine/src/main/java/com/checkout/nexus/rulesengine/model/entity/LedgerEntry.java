package com.checkout.nexus.rulesengine.model.entity;

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

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "trade_id")
    private String tradeId;

    @Column(name = "leg_id")
    private String legId;

    @Column(name = "debit_account", nullable = false)
    private String debitAccount;

    @Column(name = "credit_account", nullable = false)
    private String creditAccount;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "trade_family")
    private String tradeFamily;

    @Column(name = "trade_type")
    private String tradeType;

    @Column(name = "trade_status")
    private String tradeStatus;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
