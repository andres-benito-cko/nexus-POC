package com.checkout.nexus.rulesengine.repository;

import com.checkout.nexus.rulesengine.model.entity.Rule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RuleRepository extends JpaRepository<Rule, UUID> {

    List<Rule> findByEnabledTrue();
}
