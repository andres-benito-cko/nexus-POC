package com.checkout.nexus.rulesengine.repository;

import com.checkout.nexus.rulesengine.model.entity.LeEventRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeEventRepository extends JpaRepository<LeEventRecord, String> {
}
