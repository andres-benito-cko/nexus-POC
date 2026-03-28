package com.checkout.nexus.api.repository;

import com.checkout.nexus.api.entity.DlqEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

public interface DlqEventRepository extends JpaRepository<DlqEventEntity, UUID> {

    List<DlqEventEntity> findAll(Sort sort);
}
