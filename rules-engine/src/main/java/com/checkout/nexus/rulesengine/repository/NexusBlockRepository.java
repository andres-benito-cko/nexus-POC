package com.checkout.nexus.rulesengine.repository;

import com.checkout.nexus.rulesengine.model.entity.NexusBlockRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NexusBlockRepository extends JpaRepository<NexusBlockRecord, String> {

    List<NexusBlockRecord> findAllByOrderByReceivedAtDesc(Pageable pageable);
}
