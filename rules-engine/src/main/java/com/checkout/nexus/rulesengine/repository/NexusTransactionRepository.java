package com.checkout.nexus.rulesengine.repository;

import com.checkout.nexus.rulesengine.model.entity.NexusTransactionRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NexusTransactionRepository extends JpaRepository<NexusTransactionRecord, String> {

    List<NexusTransactionRecord> findAllByOrderByReceivedAtDesc(Pageable pageable);
}
