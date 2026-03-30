package com.checkout.nexus.rulesengine.repository;

import com.checkout.nexus.rulesengine.model.entity.PostingError;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PostingErrorRepository extends JpaRepository<PostingError, UUID> {
    List<PostingError> findByNexusId(String nexusId);
    List<PostingError> findByTransactionId(String transactionId);
    List<PostingError> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
