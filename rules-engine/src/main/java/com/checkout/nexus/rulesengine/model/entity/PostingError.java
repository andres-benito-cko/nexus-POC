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
@Table(name = "posting_errors")
public class PostingError {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "nexus_id", nullable = false)
    private String nexusId;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(name = "debit_total", nullable = false, precision = 19, scale = 6)
    private BigDecimal debitTotal;

    @Column(name = "credit_total", nullable = false, precision = 19, scale = 6)
    private BigDecimal creditTotal;

    @Column(name = "rule_ids")
    private String ruleIds;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
