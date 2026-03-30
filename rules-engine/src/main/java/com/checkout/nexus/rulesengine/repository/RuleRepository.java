package com.checkout.nexus.rulesengine.repository;

import com.checkout.nexus.rulesengine.model.entity.Rule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RuleRepository extends JpaRepository<Rule, UUID> {

    List<Rule> findByEnabledTrue();

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN TRUE ELSE FALSE END FROM Rule r " +
           "WHERE (r.debitAccount = :account OR r.creditAccount = :account) AND r.enabled = TRUE")
    boolean existsEnabledByAccount(@Param("account") String account);
}
