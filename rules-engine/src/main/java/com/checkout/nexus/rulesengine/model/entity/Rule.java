package com.checkout.nexus.rulesengine.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "rules")
public class Rule {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "product_type")
    private String productType;

    @Column(name = "transaction_type")
    private String transactionType;

    @Column(name = "transaction_status")
    private String transactionStatus;

    @Column(name = "leg_type")
    private String legType;

    @Column(name = "party_type")
    private String partyType;

    @Column(name = "debit_account", nullable = false)
    private String debitAccount;

    @Column(name = "credit_account", nullable = false)
    private String creditAccount;

    @Column(name = "amount_source", nullable = false)
    @Builder.Default
    private String amountSource = "leg_amount";

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();
}
