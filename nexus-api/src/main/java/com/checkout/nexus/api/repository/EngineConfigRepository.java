package com.checkout.nexus.api.repository;

import com.checkout.nexus.api.entity.EngineConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EngineConfigRepository extends JpaRepository<EngineConfigEntity, UUID> {

    Optional<EngineConfigEntity> findByActiveTrue();
}
