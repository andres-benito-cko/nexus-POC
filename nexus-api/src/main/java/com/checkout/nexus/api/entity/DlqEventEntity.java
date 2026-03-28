package com.checkout.nexus.api.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dlq_events")
@Data
@NoArgsConstructor
public class DlqEventEntity {

    @Id
    private UUID id;

    @Column(name = "action_id")
    private String actionId;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "errors", columnDefinition = "text")
    private String errors;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "replayed_at")
    private Instant replayedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }
}
