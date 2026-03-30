package com.checkout.nexus.rulesengine.repository;

import com.checkout.nexus.rulesengine.model.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountRepository extends JpaRepository<Account, String> {
    List<Account> findByEnabledTrue();
    boolean existsByCodeAndEnabledTrue(String code);
}
