package com.checkout.nexus.rulesengine.repository;

import com.checkout.nexus.rulesengine.model.entity.LedgerEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByNexusId(String nexusId);

    List<LedgerEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT COUNT(e) FROM LedgerEntry e")
    long countAll();

    @Query("SELECT DISTINCT e.nexusId FROM LedgerEntry e")
    List<String> findDistinctNexusIds();
}
