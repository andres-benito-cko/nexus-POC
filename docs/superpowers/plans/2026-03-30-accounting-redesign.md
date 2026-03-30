# Accounting Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement lifecycle-aligned accounting with a configurable Chart of Accounts and hard transaction-balance controls, as specified in `docs/superpowers/specs/2026-03-30-accounting-redesign-design.md`.

**Architecture:** Three layers — (1) data model: `Account` entity, `LedgerEntry` atomic postings, `PostingError`; (2) evaluation engine: `TransactionProcessor` with LEG/FEE firing contexts and `BalanceValidator`; (3) UI: Chart of Accounts, Rules, and Posting Errors screens. All changes are within `rules-engine/` (Spring Boot) and `ui/` (React).

**Tech Stack:** Java 17, Spring Boot 3, JPA/Hibernate, PostgreSQL 15, Flyway, Kafka, React 18, TypeScript, Tailwind CSS, @tanstack/react-query.

---

## File Map

### Created
| File | Purpose |
|------|---------|
| `rules-engine/src/main/java/.../model/PostingSide.java` | Enum: DEBIT \| CREDIT — used on LedgerEntry.side and Account.normalBalance |
| `rules-engine/src/main/java/.../model/FiringContext.java` | Enum: LEG \| FEE — discriminates rule firing mode |
| `rules-engine/src/main/java/.../model/AccountType.java` | Enum: ASSET \| LIABILITY \| EQUITY \| REVENUE \| EXPENSE \| CONTROL |
| `rules-engine/src/main/java/.../model/entity/Account.java` | Account entity (chart of accounts) |
| `rules-engine/src/main/java/.../model/entity/PostingError.java` | Stores balance check failures per transaction |
| `rules-engine/src/main/java/.../repository/AccountRepository.java` | JPA repo for Account |
| `rules-engine/src/main/java/.../repository/PostingErrorRepository.java` | JPA repo for PostingError |
| `rules-engine/src/main/java/.../service/BalanceValidator.java` | Validates LedgerEntry list sums per currency |
| `rules-engine/src/main/java/.../service/TransactionProcessor.java` | Atomic per-transaction evaluation + commit |
| `rules-engine/src/main/java/.../controller/AccountController.java` | CRUD for accounts (GET/POST/PUT/DELETE /accounts) |
| `rules-engine/src/main/resources/db/migration/V5__create_accounts.sql` | accounts table |
| `rules-engine/src/main/resources/db/migration/V6__seed_accounts.sql` | 16 paper accounts |
| `rules-engine/src/main/resources/db/migration/V7__alter_rules.sql` | firing_context, leg_status, fee_type, passthrough cols |
| `rules-engine/src/main/resources/db/migration/V8__restructure_ledger_entries.sql` | Truncate + swap debit/credit cols for account+side |
| `rules-engine/src/main/resources/db/migration/V9__create_posting_errors.sql` | posting_errors table |
| `rules-engine/src/main/resources/db/migration/V10__clear_legacy_rules.sql` | Delete V3 seed rules (invalid account codes) |
| `rules-engine/src/test/java/.../service/BalanceValidatorTest.java` | Unit tests for BalanceValidator |
| `rules-engine/src/test/java/.../service/TransactionProcessorTest.java` | Unit tests for TransactionProcessor |
| `ui/src/pages/Rules.tsx` | Rules management screen |
| `ui/src/pages/ChartOfAccounts.tsx` | Chart of Accounts management screen |
| `ui/src/pages/PostingErrors.tsx` | Posting errors screen |

### Modified
| File | Change |
|------|--------|
| `rules-engine/src/main/java/.../model/entity/Rule.java` | Add firingContext, legStatus, feeType, passthrough |
| `rules-engine/src/main/java/.../model/entity/LedgerEntry.java` | Replace debitAccount/creditAccount with account/side |
| `rules-engine/src/main/java/.../model/LedgerEntryMessage.java` | Same replacement as LedgerEntry |
| `rules-engine/src/main/java/.../model/NexusBlock.java` | Add passthrough field to Fee inner class |
| `rules-engine/src/main/java/.../repository/RuleRepository.java` | Add existsByDebitAccountOrCreditAccount() |
| `rules-engine/src/main/java/.../service/RulesEngineService.java` | Delegate to TransactionProcessor, remove old logic |
| `rules-engine/src/main/java/.../controller/RulesController.java` | Validate account codes, handle new Rule fields |
| `rules-engine/src/main/java/.../controller/LedgerController.java` | Add GET /ledger/errors |
| `ui/src/api/client.ts` | Add Account, Rule (updated), PostingError types + API calls |
| `ui/src/App.tsx` | Add routes for /rules, /accounts, /posting-errors |
| `ui/src/components/Layout.tsx` | Add nav links for new pages |

All Java paths expand `...` to `com/checkout/nexus/rulesengine`.

---

### Task 1: Enum types + Account entity

**Files:**
- Create: `rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/PostingSide.java`
- Create: `rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/FiringContext.java`
- Create: `rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/AccountType.java`
- Create: `rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/entity/Account.java`

- [ ] **Step 1: Create PostingSide enum**

```java
// rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/PostingSide.java
package com.checkout.nexus.rulesengine.model;

public enum PostingSide {
    DEBIT,
    CREDIT
}
```

- [ ] **Step 2: Create FiringContext enum**

```java
// rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/FiringContext.java
package com.checkout.nexus.rulesengine.model;

public enum FiringContext {
    LEG,
    FEE
}
```

- [ ] **Step 3: Create AccountType enum**

```java
// rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/AccountType.java
package com.checkout.nexus.rulesengine.model;

public enum AccountType {
    ASSET,
    LIABILITY,
    EQUITY,
    REVENUE,
    EXPENSE,
    CONTROL
}
```

- [ ] **Step 4: Create Account entity**

```java
// rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/entity/Account.java
package com.checkout.nexus.rulesengine.model.entity;

import com.checkout.nexus.rulesengine.model.AccountType;
import com.checkout.nexus.rulesengine.model.PostingSide;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_balance", nullable = false)
    private PostingSide normalBalance;

    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
```

- [ ] **Step 5: Commit**

```bash
cd /Users/andres.benito/Documents/claude/projects/Nexus-POC
git add rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/PostingSide.java \
        rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/FiringContext.java \
        rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/AccountType.java \
        rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/entity/Account.java
git commit -m "feat: add PostingSide, FiringContext, AccountType enums and Account entity"
```

---

### Task 2: DB Migrations V5 + V6 (accounts table + seed)

**Files:**
- Create: `rules-engine/src/main/resources/db/migration/V5__create_accounts.sql`
- Create: `rules-engine/src/main/resources/db/migration/V6__seed_accounts.sql`

- [ ] **Step 1: Create V5 — accounts table**

```sql
-- rules-engine/src/main/resources/db/migration/V5__create_accounts.sql
CREATE TABLE accounts (
    code           VARCHAR(100) PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    account_type   VARCHAR(20)  NOT NULL,
    normal_balance VARCHAR(10)  NOT NULL,
    description    TEXT,
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);
```

- [ ] **Step 2: Create V6 — seed all 16 paper accounts**

```sql
-- rules-engine/src/main/resources/db/migration/V6__seed_accounts.sql
INSERT INTO accounts (code, name, account_type, normal_balance, description) VALUES
('scheme_clearing_settlement', 'Scheme Clearing & Settlement Control', 'CONTROL', 'DEBIT',
 'SDCA — clearing control account between scheme and client entries'),
('scheme_debtor',              'Scheme Debtor',              'ASSET',     'DEBIT',
 'Receivable from scheme after SD file matched'),
('scheme_debtor_holding',      'Scheme Debtor Holding',      'ASSET',     'DEBIT',
 'Unmatched scheme receivable pending SD reconciliation'),
('client',                     'Client (MCR)',                'LIABILITY', 'CREDIT',
 'Merchant/client clearing reconciliation account'),
('revenue',                    'Revenue',                     'REVENUE',   'CREDIT',
 'Gross revenue — fees withheld from client'),
('passthrough_cos',            'Passthrough Cost of Sales',   'EXPENSE',   'DEBIT',
 'COS passed through to client'),
('accrued_cos',                'Accrued Cost of Sales',       'LIABILITY', 'CREDIT',
 'Deferred COS pending scheme reconciliation'),
('non_passthrough_cos',        'Non-Passthrough Cost of Sales','EXPENSE',  'DEBIT',
 'COS absorbed by CKO entity'),
('input_tax',                  'Input Tax',                   'LIABILITY', 'CREDIT',
 'VAT / input tax on fees'),
('rolling_reserve',            'Rolling Reserve',             'LIABILITY', 'CREDIT',
 'Reserve withheld from client settlement'),
('expected_client_settlement', 'Expected Client Settlement',  'LIABILITY', 'CREDIT',
 'Pending settlement payable to client'),
('cash',                       'Cash',                        'ASSET',     'DEBIT',
 'Bank cash account'),
('scheme_fee_debtor',          'Scheme Fee Debtor',           'ASSET',     'DEBIT',
 'Variance — scheme fees higher than predicted'),
('scheme_fee_creditor',        'Scheme Fee Creditor',         'LIABILITY', 'CREDIT',
 'Variance — scheme fees lower than predicted'),
('intercompany_debtor_malpb',  'Intercompany Debtor (MALPB)', 'ASSET',     'DEBIT',
 'Intercompany receivable from MALPB entity'),
('intercompany_creditor_malpb','Intercompany Creditor (MALPB)','LIABILITY','CREDIT',
 'Intercompany payable to MALPB entity');
```

- [ ] **Step 3: Commit**

```bash
git add rules-engine/src/main/resources/db/migration/V5__create_accounts.sql \
        rules-engine/src/main/resources/db/migration/V6__seed_accounts.sql
git commit -m "feat: add accounts table migration and seed 16 paper accounts"
```

---

### Task 3: AccountRepository + AccountController

**Files:**
- Create: `rules-engine/src/main/java/com/checkout/nexus/rulesengine/repository/AccountRepository.java`
- Create: `rules-engine/src/main/java/com/checkout/nexus/rulesengine/controller/AccountController.java`
- Modify: `rules-engine/src/main/java/com/checkout/nexus/rulesengine/repository/RuleRepository.java`

- [ ] **Step 1: Create AccountRepository**

```java
// rules-engine/src/main/java/com/checkout/nexus/rulesengine/repository/AccountRepository.java
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
```

- [ ] **Step 2: Add existsByDebitAccountOrCreditAccount to RuleRepository**

Add this method to the existing `RuleRepository` interface:

```java
// Add inside RuleRepository interface body
boolean existsByDebitAccountOrCreditAccount(String debitAccount, String creditAccount);
```

- [ ] **Step 3: Create AccountController**

```java
// rules-engine/src/main/java/com/checkout/nexus/rulesengine/controller/AccountController.java
package com.checkout.nexus.rulesengine.controller;

import com.checkout.nexus.rulesengine.model.entity.Account;
import com.checkout.nexus.rulesengine.repository.AccountRepository;
import com.checkout.nexus.rulesengine.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountRepository accountRepository;
    private final RuleRepository ruleRepository;

    @GetMapping
    public List<Account> listAccounts() {
        return accountRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> createAccount(@RequestBody Account account) {
        if (accountRepository.existsById(account.getCode())) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Account code already exists: " + account.getCode()));
        }
        account.setCreatedAt(LocalDateTime.now());
        return ResponseEntity.ok(accountRepository.save(account));
    }

    @PutMapping("/{code}")
    public ResponseEntity<?> updateAccount(@PathVariable String code, @RequestBody Account update) {
        return accountRepository.findById(code)
            .map(existing -> {
                existing.setName(update.getName());
                existing.setAccountType(update.getAccountType());
                existing.setNormalBalance(update.getNormalBalance());
                existing.setDescription(update.getDescription());
                existing.setEnabled(update.isEnabled());
                return ResponseEntity.ok(accountRepository.save(existing));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<?> deleteAccount(@PathVariable String code) {
        if (ruleRepository.existsByDebitAccountOrCreditAccount(code, code)) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Account '" + code + "' is referenced by existing rules"));
        }
        return accountRepository.findById(code)
            .map(account -> {
                account.setEnabled(false);
                accountRepository.save(account);
                return ResponseEntity.noContent().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add rules-engine/src/main/java/com/checkout/nexus/rulesengine/repository/AccountRepository.java \
        rules-engine/src/main/java/com/checkout/nexus/rulesengine/repository/RuleRepository.java \
        rules-engine/src/main/java/com/checkout/nexus/rulesengine/controller/AccountController.java
git commit -m "feat: add AccountRepository and AccountController with soft-delete guard"
```

---

### Task 4: Rule entity extensions + DB migration V7

**Files:**
- Modify: `rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/entity/Rule.java`
- Create: `rules-engine/src/main/resources/db/migration/V7__alter_rules.sql`

- [ ] **Step 1: Add four new fields to Rule.java**

Add these fields after the existing `legType` field:

```java
// Add these imports at the top of Rule.java
import com.checkout.nexus.rulesengine.model.FiringContext;

// Add these fields after 'private String legType;'

@Column(name = "firing_context", nullable = false)
@Enumerated(EnumType.STRING)
@Builder.Default
private FiringContext firingContext = FiringContext.LEG;

@Column(name = "leg_status")
private String legStatus;

@Column(name = "fee_type")
private String feeType;

@Column(name = "passthrough")
private Boolean passthrough;
```

- [ ] **Step 2: Create V7 migration**

```sql
-- rules-engine/src/main/resources/db/migration/V7__alter_rules.sql
ALTER TABLE rules
    ADD COLUMN firing_context VARCHAR(10) NOT NULL DEFAULT 'LEG',
    ADD COLUMN leg_status     VARCHAR(50),
    ADD COLUMN fee_type       VARCHAR(50),
    ADD COLUMN passthrough    BOOLEAN;
```

- [ ] **Step 3: Commit**

```bash
git add rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/entity/Rule.java \
        rules-engine/src/main/resources/db/migration/V7__alter_rules.sql
git commit -m "feat: add firingContext, legStatus, feeType, passthrough to Rule entity"
```

---

### Task 5: PostingError entity + Migrations V8/V9/V10

**Files:**
- Create: `rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/entity/PostingError.java`
- Create: `rules-engine/src/main/java/com/checkout/nexus/rulesengine/repository/PostingErrorRepository.java`
- Create: `rules-engine/src/main/resources/db/migration/V8__restructure_ledger_entries.sql`
- Create: `rules-engine/src/main/resources/db/migration/V9__create_posting_errors.sql`
- Create: `rules-engine/src/main/resources/db/migration/V10__clear_legacy_rules.sql`

- [ ] **Step 1: Create PostingError entity**

```java
// rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/entity/PostingError.java
package com.checkout.nexus.rulesengine.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "posting_errors")
public class PostingError {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "nexus_id", nullable = false)
    private String nexusId;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(name = "debit_total", nullable = false, precision = 19, scale = 6)
    private BigDecimal debitTotal;

    @Column(name = "credit_total", nullable = false, precision = 19, scale = 6)
    private BigDecimal creditTotal;

    @Column(name = "rule_ids")
    private String ruleIds;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
```

- [ ] **Step 2: Create PostingErrorRepository**

```java
// rules-engine/src/main/java/com/checkout/nexus/rulesengine/repository/PostingErrorRepository.java
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
```

- [ ] **Step 3: Create V8 — restructure ledger_entries as atomic postings**

```sql
-- rules-engine/src/main/resources/db/migration/V8__restructure_ledger_entries.sql
-- Clear existing entries (POC: no production data to preserve)
TRUNCATE TABLE ledger_entries;

ALTER TABLE ledger_entries
    DROP COLUMN debit_account,
    DROP COLUMN credit_account,
    ADD COLUMN account VARCHAR(100) NOT NULL DEFAULT '',
    ADD COLUMN side    VARCHAR(6)   NOT NULL DEFAULT 'DEBIT';

-- Remove the placeholder defaults now that the columns are added
ALTER TABLE ledger_entries
    ALTER COLUMN account DROP DEFAULT,
    ALTER COLUMN side    DROP DEFAULT;
```

- [ ] **Step 4: Create V9 — posting_errors table**

```sql
-- rules-engine/src/main/resources/db/migration/V9__create_posting_errors.sql
CREATE TABLE posting_errors (
    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    nexus_id       VARCHAR(255)  NOT NULL,
    transaction_id VARCHAR(255)  NOT NULL,
    currency       VARCHAR(10)   NOT NULL,
    debit_total    NUMERIC(19,6) NOT NULL,
    credit_total   NUMERIC(19,6) NOT NULL,
    rule_ids       TEXT,
    created_at     TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_posting_errors_nexus_id ON posting_errors(nexus_id);
```

- [ ] **Step 5: Create V10 — remove legacy seed rules**

The V3 rules reference invalid account codes (e.g. `RECEIVABLE_FROM_SCHEME`, `GROSS_REVENUE`). These must be cleared so the system starts clean. New rules are created via the UI using valid CoA codes.

```sql
-- rules-engine/src/main/resources/db/migration/V10__clear_legacy_rules.sql
-- Remove V3 seed rules which reference account codes not in the accounts table.
-- New rules are created via the Chart of Accounts UI.
DELETE FROM rules;
```

- [ ] **Step 6: Commit**

```bash
git add rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/entity/PostingError.java \
        rules-engine/src/main/java/com/checkout/nexus/rulesengine/repository/PostingErrorRepository.java \
        rules-engine/src/main/resources/db/migration/V8__restructure_ledger_entries.sql \
        rules-engine/src/main/resources/db/migration/V9__create_posting_errors.sql \
        rules-engine/src/main/resources/db/migration/V10__clear_legacy_rules.sql
git commit -m "feat: add PostingError entity and migrations V8-V10"
```

---

### Task 6: LedgerEntry atomic refactor + LedgerEntryMessage update

**Files:**
- Modify: `rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/entity/LedgerEntry.java`
- Modify: `rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/LedgerEntryMessage.java`
- Modify: `rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/NexusBlock.java`

- [ ] **Step 1: Replace debitAccount/creditAccount with account/side in LedgerEntry**

Replace the entire `LedgerEntry.java` with:

```java
// rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/entity/LedgerEntry.java
package com.checkout.nexus.rulesengine.model.entity;

import com.checkout.nexus.rulesengine.model.PostingSide;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "rule_name")
    private String ruleName;

    @Column(name = "nexus_id", nullable = false)
    private String nexusId;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "leg_id")
    private String legId;

    @Column(nullable = false)
    private String account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostingSide side;

    @Column(nullable = false, precision = 20, scale = 6)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "product_type")
    private String productType;

    @Column(name = "transaction_type")
    private String transactionType;

    @Column(name = "transaction_status")
    private String transactionStatus;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
```

- [ ] **Step 2: Replace debitAccount/creditAccount with account/side in LedgerEntryMessage**

Replace the entire `LedgerEntryMessage.java` with:

```java
// rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/LedgerEntryMessage.java
package com.checkout.nexus.rulesengine.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntryMessage {

    @JsonProperty("id")
    private UUID id;

    @JsonProperty("ruleId")
    private UUID ruleId;

    @JsonProperty("ruleName")
    private String ruleName;

    @JsonProperty("nexusId")
    private String nexusId;

    @JsonProperty("transactionId")
    private String transactionId;

    @JsonProperty("legId")
    private String legId;

    @JsonProperty("account")
    private String account;

    @JsonProperty("side")
    private PostingSide side;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("productType")
    private String productType;

    @JsonProperty("transactionType")
    private String transactionType;

    @JsonProperty("transactionStatus")
    private String transactionStatus;
}
```

- [ ] **Step 3: Add passthrough field to NexusBlock.Fee inner class**

Inside `NexusBlock.java`, add to the `Fee` inner class after `feeStatus`:

```java
@JsonProperty("passthrough")
private Boolean passthrough;
```

- [ ] **Step 4: Compile check**

```bash
cd /Users/andres.benito/Documents/claude/projects/Nexus-POC/rules-engine
./gradlew compileJava 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`. If there are compile errors, they will be in classes that still reference the old `debitAccount`/`creditAccount` fields on `LedgerEntry` — fix those references.

- [ ] **Step 5: Commit**

```bash
git add rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/entity/LedgerEntry.java \
        rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/LedgerEntryMessage.java \
        rules-engine/src/main/java/com/checkout/nexus/rulesengine/model/NexusBlock.java
git commit -m "refactor: LedgerEntry and LedgerEntryMessage to atomic posting model (account + side)"
```

---

### Task 7: BalanceValidator (TDD)

**Files:**
- Create: `rules-engine/src/test/java/com/checkout/nexus/rulesengine/service/BalanceValidatorTest.java`
- Create: `rules-engine/src/main/java/com/checkout/nexus/rulesengine/service/BalanceValidator.java`

- [ ] **Step 1: Create test directory structure**

```bash
mkdir -p /Users/andres.benito/Documents/claude/projects/Nexus-POC/rules-engine/src/test/java/com/checkout/nexus/rulesengine/service
```

- [ ] **Step 2: Write failing test**

```java
// rules-engine/src/test/java/com/checkout/nexus/rulesengine/service/BalanceValidatorTest.java
package com.checkout.nexus.rulesengine.service;

import com.checkout.nexus.rulesengine.model.PostingSide;
import com.checkout.nexus.rulesengine.model.entity.LedgerEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BalanceValidatorTest {

    private final BalanceValidator validator = new BalanceValidator();

    @Test
    void balanced_single_currency_returns_empty() {
        var entries = List.of(
            entry("scheme_clearing_settlement", PostingSide.DEBIT,  "EUR", "100.00"),
            entry("client",                    PostingSide.CREDIT, "EUR", "100.00")
        );
        assertThat(validator.validate(entries)).isEmpty();
    }

    @Test
    void unbalanced_returns_error_with_currency_and_totals() {
        var entries = List.of(
            entry("scheme_clearing_settlement", PostingSide.DEBIT,  "EUR", "100.00"),
            entry("client",                    PostingSide.CREDIT, "EUR", "90.00")
        );
        var errors = validator.validate(entries);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).currency()).isEqualTo("EUR");
        assertThat(errors.get(0).debitTotal()).isEqualByComparingTo("100.00");
        assertThat(errors.get(0).creditTotal()).isEqualByComparingTo("90.00");
    }

    @Test
    void validates_independently_per_currency() {
        var entries = List.of(
            entry("scheme_clearing_settlement", PostingSide.DEBIT,  "EUR", "100.00"),
            entry("client",                    PostingSide.CREDIT, "EUR", "100.00"),
            // USD imbalanced — missing debit
            entry("revenue",                   PostingSide.CREDIT, "USD", "10.00")
        );
        var errors = validator.validate(entries);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).currency()).isEqualTo("USD");
    }

    @Test
    void multiple_entries_same_currency_same_side_are_summed() {
        var entries = List.of(
            entry("scheme_clearing_settlement", PostingSide.DEBIT,  "EUR", "60.00"),
            entry("revenue",                   PostingSide.DEBIT,  "EUR", "40.00"),
            entry("client",                    PostingSide.CREDIT, "EUR", "100.00")
        );
        assertThat(validator.validate(entries)).isEmpty();
    }

    @Test
    void empty_entries_returns_empty() {
        assertThat(validator.validate(List.of())).isEmpty();
    }

    private LedgerEntry entry(String account, PostingSide side, String currency, String amount) {
        return LedgerEntry.builder()
            .account(account)
            .side(side)
            .currency(currency)
            .amount(new BigDecimal(amount))
            .nexusId("test-nexus")
            .build();
    }
}
```

- [ ] **Step 3: Run test — confirm it fails (class not found)**

```bash
cd /Users/andres.benito/Documents/claude/projects/Nexus-POC/rules-engine
./gradlew test --tests "com.checkout.nexus.rulesengine.service.BalanceValidatorTest" 2>&1 | tail -15
```

Expected: `FAILED` (compilation error — `BalanceValidator` not yet defined).

- [ ] **Step 4: Implement BalanceValidator**

```java
// rules-engine/src/main/java/com/checkout/nexus/rulesengine/service/BalanceValidator.java
package com.checkout.nexus.rulesengine.service;

import com.checkout.nexus.rulesengine.model.PostingSide;
import com.checkout.nexus.rulesengine.model.entity.LedgerEntry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class BalanceValidator {

    public record BalanceError(String currency, BigDecimal debitTotal, BigDecimal creditTotal) {}

    public List<BalanceError> validate(List<LedgerEntry> entries) {
        Map<String, BigDecimal> debits  = new HashMap<>();
        Map<String, BigDecimal> credits = new HashMap<>();

        for (LedgerEntry entry : entries) {
            if (entry.getSide() == PostingSide.DEBIT) {
                debits.merge(entry.getCurrency(), entry.getAmount(), BigDecimal::add);
            } else {
                credits.merge(entry.getCurrency(), entry.getAmount(), BigDecimal::add);
            }
        }

        Set<String> currencies = new HashSet<>();
        currencies.addAll(debits.keySet());
        currencies.addAll(credits.keySet());

        return currencies.stream()
            .filter(currency -> {
                BigDecimal d = debits.getOrDefault(currency, BigDecimal.ZERO);
                BigDecimal c = credits.getOrDefault(currency, BigDecimal.ZERO);
                return d.compareTo(c) != 0;
            })
            .map(currency -> new BalanceError(
                currency,
                debits.getOrDefault(currency, BigDecimal.ZERO),
                credits.getOrDefault(currency, BigDecimal.ZERO)
            ))
            .toList();
    }
}
```

- [ ] **Step 5: Run test — confirm it passes**

```bash
cd /Users/andres.benito/Documents/claude/projects/Nexus-POC/rules-engine
./gradlew test --tests "com.checkout.nexus.rulesengine.service.BalanceValidatorTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL` with `5 tests completed, 0 failed`.

- [ ] **Step 6: Commit**

```bash
git add rules-engine/src/main/java/com/checkout/nexus/rulesengine/service/BalanceValidator.java \
        rules-engine/src/test/java/com/checkout/nexus/rulesengine/service/BalanceValidatorTest.java
git commit -m "feat: add BalanceValidator with per-currency balance checking"
```

---

### Task 8: TransactionProcessor service (TDD)

**Files:**
- Create: `rules-engine/src/test/java/com/checkout/nexus/rulesengine/service/TransactionProcessorTest.java`
- Create: `rules-engine/src/main/java/com/checkout/nexus/rulesengine/service/TransactionProcessor.java`

- [ ] **Step 1: Write failing tests**

```java
// rules-engine/src/test/java/com/checkout/nexus/rulesengine/service/TransactionProcessorTest.java
package com.checkout.nexus.rulesengine.service;

import com.checkout.nexus.rulesengine.model.FiringContext;
import com.checkout.nexus.rulesengine.model.PostingSide;
import com.checkout.nexus.rulesengine.model.NexusBlock;
import com.checkout.nexus.rulesengine.model.entity.LedgerEntry;
import com.checkout.nexus.rulesengine.model.entity.Rule;
import com.checkout.nexus.rulesengine.model.entity.PostingError;
import com.checkout.nexus.rulesengine.repository.LedgerEntryRepository;
import com.checkout.nexus.rulesengine.repository.PostingErrorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionProcessorTest {

    @Mock LedgerEntryRepository ledgerEntryRepository;
    @Mock PostingErrorRepository postingErrorRepository;
    @Mock KafkaTemplate kafkaTemplate;

    private TransactionProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new TransactionProcessor(
            ledgerEntryRepository, postingErrorRepository,
            new BalanceValidator(), kafkaTemplate
        );
    }

    @Test
    void leg_rule_generates_two_atomic_postings() {
        NexusBlock nexus = nexusBlock();
        NexusBlock.Transaction txn = transaction("ACQUIRING", "CAPTURE", "SETTLED",
            List.of(leg("leg-1", "SCHEME_SETTLEMENT", null, 100.0, "EUR", List.of())));
        Rule rule = legRule("ACQUIRING", "CAPTURE", "SETTLED", "SCHEME_SETTLEMENT",
            "scheme_clearing_settlement", "client");

        List<LedgerEntry> entries = processor.evaluate(nexus, txn, List.of(rule));

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getAccount()).isEqualTo("scheme_clearing_settlement");
        assertThat(entries.get(0).getSide()).isEqualTo(PostingSide.DEBIT);
        assertThat(entries.get(1).getAccount()).isEqualTo("client");
        assertThat(entries.get(1).getSide()).isEqualTo(PostingSide.CREDIT);
    }

    @Test
    void fee_rule_fires_once_per_matching_fee() {
        NexusBlock nexus = nexusBlock();
        NexusBlock.Fee fee1 = fee("INTERCHANGE", 10.0, "EUR", true);
        NexusBlock.Fee fee2 = fee("SCHEME_FEE",  5.0,  "EUR", false);
        NexusBlock.Transaction txn = transaction("ACQUIRING", "CAPTURE", null,
            List.of(leg("leg-1", "SCHEME_SETTLEMENT", "PREDICTED", 100.0, "EUR", List.of(fee1, fee2))));
        Rule rule = feeRule("ACQUIRING", "CAPTURE", null, "SCHEME_SETTLEMENT",
            null, "INTERCHANGE", null, "passthrough_cos", "accrued_cos");

        List<LedgerEntry> entries = processor.evaluate(nexus, txn, List.of(rule));

        // Only INTERCHANGE fee matches — 2 postings
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getAmount()).isEqualByComparingTo("10.0");
    }

    @Test
    void leg_status_filter_is_applied() {
        NexusBlock nexus = nexusBlock();
        NexusBlock.Transaction txn = transaction("ACQUIRING", "CAPTURE", null,
            List.of(
                leg("leg-1", "SCHEME_SETTLEMENT", "PREDICTED", 100.0, "EUR", List.of()),
                leg("leg-2", "SCHEME_SETTLEMENT", "ACTUAL",    100.0, "EUR", List.of())
            ));
        Rule rule = legRule("ACQUIRING", "CAPTURE", null, "SCHEME_SETTLEMENT",
            "scheme_clearing_settlement", "client");
        rule.setLegStatus("ACTUAL");

        List<LedgerEntry> entries = processor.evaluate(nexus, txn, List.of(rule));

        // Only ACTUAL leg matches
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getLegId()).isEqualTo("leg-2");
    }

    @Test
    void passthrough_filter_is_applied_on_fee_rules() {
        NexusBlock nexus = nexusBlock();
        NexusBlock.Fee passthroughFee    = fee("INTERCHANGE", 10.0, "EUR", true);
        NexusBlock.Fee nonPassthroughFee = fee("INTERCHANGE",  5.0, "EUR", false);
        NexusBlock.Transaction txn = transaction("ACQUIRING", "CAPTURE", null,
            List.of(leg("leg-1", "SCHEME_SETTLEMENT", null, 100.0, "EUR",
                List.of(passthroughFee, nonPassthroughFee))));
        Rule rule = feeRule("ACQUIRING", "CAPTURE", null, "SCHEME_SETTLEMENT",
            null, "INTERCHANGE", true, "passthrough_cos", "accrued_cos");

        List<LedgerEntry> entries = processor.evaluate(nexus, txn, List.of(rule));

        // Only the passthrough=true fee matches
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getAmount()).isEqualByComparingTo("10.0");
    }

    @Test
    void unbalanced_transaction_saves_posting_error_and_skips_entries() {
        NexusBlock nexus = nexusBlock();
        NexusBlock.Transaction txn = transaction("ACQUIRING", "CAPTURE", "SETTLED",
            List.of(leg("leg-1", "SCHEME_SETTLEMENT", null, 100.0, "EUR", List.of())));
        // Only a debit rule — no credit, so balance fails (debit 100 / credit 0)
        Rule debitOnlyRule = legRule("ACQUIRING", "CAPTURE", "SETTLED", "SCHEME_SETTLEMENT",
            "scheme_clearing_settlement", "client");
        // Override: make both accounts the same so they still produce 2 postings but force imbalance
        // by using a custom processor call: the BalanceValidator gets 1 DEBIT + 0 CREDIT scenario.
        // We achieve this by using a single entry directly via evaluate() stubbing.
        // Instead, test via process(): use a real BalanceValidator that will catch the imbalance.
        // For this test, use an empty entries scenario by having no matching rules.
        // Simplest: test with a rule that fires but verify that imbalanced calls postingErrorRepository.

        // Create a single-sided entry scenario by giving only a LEG rule where the credit account
        // and debit account produce equal amounts (they will always be balanced for a single rule).
        // To test the imbalance path, we need two rules where amounts differ.

        // Rule A: debit 100 EUR to scheme_clearing_settlement, credit 100 to client
        // Rule B: debit 50 EUR to client (another rule), credit 50 to revenue — balanced individually
        // These two rules together: debit = 150, credit = 150 — still balanced!

        // Imbalance in practice occurs when:
        // The test for imbalance path is best done by verifying the path when validate() returns non-empty.
        // Use a spy on BalanceValidator instead.

        BalanceValidator spyValidator = spy(new BalanceValidator());
        TransactionProcessor spyProcessor = new TransactionProcessor(
            ledgerEntryRepository, postingErrorRepository, spyValidator, kafkaTemplate
        );
        // Make validator always fail for this test
        doReturn(List.of(new BalanceValidator.BalanceError("EUR",
            java.math.BigDecimal.TEN, java.math.BigDecimal.ZERO)))
            .when(spyValidator).validate(anyList());

        spyProcessor.process(nexus, txn, List.of(debitOnlyRule));

        verify(postingErrorRepository).save(any(PostingError.class));
        verify(ledgerEntryRepository, never()).saveAll(anyList());
    }

    // --- Helpers ---

    private NexusBlock nexusBlock() {
        NexusBlock b = new NexusBlock();
        b.setNexusId("nexus-test-001");
        return b;
    }

    private NexusBlock.Transaction transaction(String product, String type, String status,
            List<NexusBlock.Leg> legs) {
        NexusBlock.Transaction t = new NexusBlock.Transaction();
        t.setTransactionId("txn-001");
        t.setProductType(product);
        t.setTransactionType(type);
        t.setTransactionStatus(status);
        t.setLegs(legs);
        return t;
    }

    private NexusBlock.Leg leg(String id, String type, String status,
            double amount, String currency, List<NexusBlock.Fee> fees) {
        NexusBlock.Leg l = new NexusBlock.Leg();
        l.setLegId(id);
        l.setLegType(type);
        l.setLegStatus(status);
        l.setLegAmount(amount);
        l.setLegCurrency(currency);
        l.setFees(fees);
        return l;
    }

    private NexusBlock.Fee fee(String type, double amount, String currency, boolean passthrough) {
        NexusBlock.Fee f = new NexusBlock.Fee();
        f.setFeeId(UUID.randomUUID().toString());
        f.setFeeType(type);
        f.setFeeAmount(amount);
        f.setFeeCurrency(currency);
        f.setPassthrough(passthrough);
        return f;
    }

    private Rule legRule(String product, String type, String status, String legType,
            String debit, String credit) {
        return Rule.builder()
            .id(UUID.randomUUID())
            .name("test-leg-rule")
            .firingContext(FiringContext.LEG)
            .productType(product)
            .transactionType(type)
            .transactionStatus(status)
            .legType(legType)
            .debitAccount(debit)
            .creditAccount(credit)
            .amountSource("leg_amount")
            .enabled(true)
            .build();
    }

    private Rule feeRule(String product, String type, String status, String legType,
            String legStatus, String feeType, Boolean passthrough, String debit, String credit) {
        return Rule.builder()
            .id(UUID.randomUUID())
            .name("test-fee-rule")
            .firingContext(FiringContext.FEE)
            .productType(product)
            .transactionType(type)
            .transactionStatus(status)
            .legType(legType)
            .legStatus(legStatus)
            .feeType(feeType)
            .passthrough(passthrough)
            .debitAccount(debit)
            .creditAccount(credit)
            .amountSource("fee_amount")
            .enabled(true)
            .build();
    }
}
```

- [ ] **Step 2: Run test — confirm it fails**

```bash
cd /Users/andres.benito/Documents/claude/projects/Nexus-POC/rules-engine
./gradlew test --tests "com.checkout.nexus.rulesengine.service.TransactionProcessorTest" 2>&1 | tail -15
```

Expected: `FAILED` (compilation error — `TransactionProcessor` not yet defined).

- [ ] **Step 3: Implement TransactionProcessor**

```java
// rules-engine/src/main/java/com/checkout/nexus/rulesengine/service/TransactionProcessor.java
package com.checkout.nexus.rulesengine.service;

import com.checkout.nexus.rulesengine.model.FiringContext;
import com.checkout.nexus.rulesengine.model.LedgerEntryMessage;
import com.checkout.nexus.rulesengine.model.NexusBlock;
import com.checkout.nexus.rulesengine.model.PostingSide;
import com.checkout.nexus.rulesengine.model.entity.LedgerEntry;
import com.checkout.nexus.rulesengine.model.entity.PostingError;
import com.checkout.nexus.rulesengine.model.entity.Rule;
import com.checkout.nexus.rulesengine.repository.LedgerEntryRepository;
import com.checkout.nexus.rulesengine.repository.PostingErrorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionProcessor {

    private static final String LEDGER_TOPIC = "nexus.ledger.entries";

    private final LedgerEntryRepository ledgerEntryRepository;
    private final PostingErrorRepository postingErrorRepository;
    private final BalanceValidator balanceValidator;
    private final KafkaTemplate<String, LedgerEntryMessage> kafkaTemplate;

    @Transactional
    public void process(NexusBlock nexus, NexusBlock.Transaction txn, List<Rule> rules) {
        List<LedgerEntry> entries = evaluate(nexus, txn, rules);

        List<BalanceValidator.BalanceError> errors = balanceValidator.validate(entries);
        if (!errors.isEmpty()) {
            BalanceValidator.BalanceError first = errors.get(0);
            String ruleIds = entries.stream()
                .filter(e -> e.getRuleId() != null)
                .map(e -> e.getRuleId().toString())
                .distinct()
                .collect(Collectors.joining(","));
            postingErrorRepository.save(PostingError.builder()
                .nexusId(nexus.getNexusId())
                .transactionId(txn.getTransactionId())
                .currency(first.currency())
                .debitTotal(first.debitTotal())
                .creditTotal(first.creditTotal())
                .ruleIds(ruleIds)
                .build());
            log.warn("Balance check failed: nexusId={} txnId={} currency={} debit={} credit={}",
                nexus.getNexusId(), txn.getTransactionId(),
                first.currency(), first.debitTotal(), first.creditTotal());
            return;
        }

        ledgerEntryRepository.saveAll(entries);
        entries.forEach(entry ->
            kafkaTemplate.send(LEDGER_TOPIC, nexus.getNexusId(), toLedgerMessage(entry)));
        log.info("Committed {} postings for txnId={}", entries.size(), txn.getTransactionId());
    }

    List<LedgerEntry> evaluate(NexusBlock nexus, NexusBlock.Transaction txn, List<Rule> rules) {
        List<LedgerEntry> entries = new ArrayList<>();
        if (txn.getLegs() == null) return entries;

        for (NexusBlock.Leg leg : txn.getLegs()) {
            for (Rule rule : rules) {
                if (!matchesTransaction(rule, txn)) continue;
                if (!matchesLeg(rule, leg)) continue;

                if (rule.getFiringContext() == FiringContext.FEE) {
                    if (leg.getFees() == null) continue;
                    for (NexusBlock.Fee fee : leg.getFees()) {
                        if (!matchesFee(rule, fee)) continue;
                        if (fee.getFeeAmount() <= 0) continue;
                        entries.addAll(buildPostings(rule, nexus, txn, leg,
                            BigDecimal.valueOf(fee.getFeeAmount()),
                            fee.getFeeCurrency() != null ? fee.getFeeCurrency() : "EUR"));
                    }
                } else {
                    if (leg.getLegAmount() <= 0) continue;
                    entries.addAll(buildPostings(rule, nexus, txn, leg,
                        BigDecimal.valueOf(leg.getLegAmount()),
                        leg.getLegCurrency() != null ? leg.getLegCurrency() : "EUR"));
                }
            }
        }
        return entries;
    }

    private boolean matchesTransaction(Rule rule, NexusBlock.Transaction txn) {
        if (rule.getProductType() != null && !rule.getProductType().equals(txn.getProductType())) return false;
        if (rule.getTransactionType() != null && !rule.getTransactionType().equals(txn.getTransactionType())) return false;
        if (rule.getTransactionStatus() != null && !rule.getTransactionStatus().equals(txn.getTransactionStatus())) return false;
        return true;
    }

    private boolean matchesLeg(Rule rule, NexusBlock.Leg leg) {
        if (rule.getLegType() != null && !rule.getLegType().equals(leg.getLegType())) return false;
        if (rule.getLegStatus() != null && !rule.getLegStatus().equals(leg.getLegStatus())) return false;
        return true;
    }

    private boolean matchesFee(Rule rule, NexusBlock.Fee fee) {
        if (rule.getFeeType() != null && !rule.getFeeType().equals(fee.getFeeType())) return false;
        if (rule.getPassthrough() != null && !rule.getPassthrough().equals(fee.getPassthrough())) return false;
        return true;
    }

    private List<LedgerEntry> buildPostings(Rule rule, NexusBlock nexus,
            NexusBlock.Transaction txn, NexusBlock.Leg leg,
            BigDecimal amount, String currency) {
        LocalDateTime now = LocalDateTime.now();
        LedgerEntry debit = LedgerEntry.builder()
            .ruleId(rule.getId()).ruleName(rule.getName())
            .nexusId(nexus.getNexusId()).transactionId(txn.getTransactionId()).legId(leg.getLegId())
            .account(rule.getDebitAccount()).side(PostingSide.DEBIT)
            .amount(amount).currency(currency)
            .productType(txn.getProductType()).transactionType(txn.getTransactionType())
            .transactionStatus(txn.getTransactionStatus()).createdAt(now).build();
        LedgerEntry credit = LedgerEntry.builder()
            .ruleId(rule.getId()).ruleName(rule.getName())
            .nexusId(nexus.getNexusId()).transactionId(txn.getTransactionId()).legId(leg.getLegId())
            .account(rule.getCreditAccount()).side(PostingSide.CREDIT)
            .amount(amount).currency(currency)
            .productType(txn.getProductType()).transactionType(txn.getTransactionType())
            .transactionStatus(txn.getTransactionStatus()).createdAt(now).build();
        return List.of(debit, credit);
    }

    private LedgerEntryMessage toLedgerMessage(LedgerEntry entry) {
        return LedgerEntryMessage.builder()
            .id(entry.getId()).ruleId(entry.getRuleId()).ruleName(entry.getRuleName())
            .nexusId(entry.getNexusId()).transactionId(entry.getTransactionId()).legId(entry.getLegId())
            .account(entry.getAccount()).side(entry.getSide())
            .amount(entry.getAmount()).currency(entry.getCurrency())
            .productType(entry.getProductType()).transactionType(entry.getTransactionType())
            .transactionStatus(entry.getTransactionStatus()).build();
    }
}
```

- [ ] **Step 4: Run tests — confirm they pass**

```bash
cd /Users/andres.benito/Documents/claude/projects/Nexus-POC/rules-engine
./gradlew test --tests "com.checkout.nexus.rulesengine.service.TransactionProcessorTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL` with `5 tests completed, 0 failed`.

- [ ] **Step 5: Commit**

```bash
git add rules-engine/src/main/java/com/checkout/nexus/rulesengine/service/TransactionProcessor.java \
        rules-engine/src/test/java/com/checkout/nexus/rulesengine/service/TransactionProcessorTest.java
git commit -m "feat: add TransactionProcessor with LEG/FEE firing contexts and balance gate"
```

---

### Task 9: RulesEngineService refactor

**Files:**
- Modify: `rules-engine/src/main/java/com/checkout/nexus/rulesengine/service/RulesEngineService.java`

- [ ] **Step 1: Replace RulesEngineService body**

```java
// rules-engine/src/main/java/com/checkout/nexus/rulesengine/service/RulesEngineService.java
package com.checkout.nexus.rulesengine.service;

import com.checkout.nexus.rulesengine.model.NexusBlock;
import com.checkout.nexus.rulesengine.model.entity.NexusBlockRecord;
import com.checkout.nexus.rulesengine.model.entity.Rule;
import com.checkout.nexus.rulesengine.repository.NexusBlockRepository;
import com.checkout.nexus.rulesengine.repository.RuleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RulesEngineService {

    private final RuleRepository ruleRepository;
    private final NexusBlockRepository nexusBlockRepository;
    private final TransactionProcessor transactionProcessor;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "nexus.blocks", groupId = "rules-engine",
            containerFactory = "nexusContainerFactory")
    public void onNexusBlock(NexusBlock nexus) {
        log.info("Received Nexus block: nexusId={}, status={}", nexus.getNexusId(), nexus.getStatus());
        try {
            persistBlock(nexus);
            List<Rule> rules = ruleRepository.findByEnabledTrue();
            if (nexus.getTransactions() != null) {
                for (NexusBlock.Transaction txn : nexus.getTransactions()) {
                    transactionProcessor.process(nexus, txn, rules);
                }
            }
        } catch (Exception e) {
            log.error("Error processing Nexus block: nexusId={}", nexus.getNexusId(), e);
        }
    }

    private void persistBlock(NexusBlock nexus) {
        try {
            String rawJson = objectMapper.writeValueAsString(nexus);
            String productType = null, transactionType = null, transactionStatus = null;
            BigDecimal transactionAmount = null;
            String transactionCurrency = null;

            if (nexus.getTransactions() != null && !nexus.getTransactions().isEmpty()) {
                NexusBlock.Transaction txn = nexus.getTransactions().get(0);
                productType = txn.getProductType();
                transactionType = txn.getTransactionType();
                transactionStatus = txn.getTransactionStatus();
                transactionAmount = BigDecimal.valueOf(txn.getTransactionAmount());
                transactionCurrency = txn.getTransactionCurrency();
            }

            nexusBlockRepository.save(NexusBlockRecord.builder()
                .nexusId(nexus.getNexusId())
                .actionId(nexus.getActionId())
                .actionRootId(nexus.getActionRootId())
                .status(nexus.getStatus())
                .entityId(nexus.getEntity() != null ? nexus.getEntity().getId() : null)
                .ckoEntityId(nexus.getCkoEntityId())
                .productType(productType)
                .transactionType(transactionType)
                .transactionStatus(transactionStatus)
                .transactionAmount(transactionAmount)
                .transactionCurrency(transactionCurrency)
                .rawJson(rawJson)
                .receivedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        } catch (Exception e) {
            log.error("Error persisting Nexus block: nexusId={}", nexus.getNexusId(), e);
        }
    }
}
```

- [ ] **Step 2: Compile check**

```bash
cd /Users/andres.benito/Documents/claude/projects/Nexus-POC/rules-engine
./gradlew compileJava 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add rules-engine/src/main/java/com/checkout/nexus/rulesengine/service/RulesEngineService.java
git commit -m "refactor: RulesEngineService delegates per-transaction processing to TransactionProcessor"
```

---

### Task 10: RulesController + LedgerController updates

**Files:**
- Modify: `rules-engine/src/main/java/com/checkout/nexus/rulesengine/controller/RulesController.java`
- Modify: `rules-engine/src/main/java/com/checkout/nexus/rulesengine/controller/LedgerController.java`

- [ ] **Step 1: Update RulesController — account validation + new fields**

Replace the entire `RulesController.java` with:

```java
// rules-engine/src/main/java/com/checkout/nexus/rulesengine/controller/RulesController.java
package com.checkout.nexus.rulesengine.controller;

import com.checkout.nexus.rulesengine.model.FiringContext;
import com.checkout.nexus.rulesengine.model.entity.Rule;
import com.checkout.nexus.rulesengine.repository.AccountRepository;
import com.checkout.nexus.rulesengine.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/rules")
@RequiredArgsConstructor
public class RulesController {

    private final RuleRepository ruleRepository;
    private final AccountRepository accountRepository;

    @GetMapping
    public List<Rule> listRules() {
        return ruleRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> createRule(@RequestBody Rule rule) {
        ResponseEntity<?> validation = validateRule(rule);
        if (validation != null) return validation;
        rule.setId(null);
        rule.setCreatedAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        return ResponseEntity.ok(ruleRepository.save(rule));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Rule> getRule(@PathVariable UUID id) {
        return ruleRepository.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateRule(@PathVariable UUID id, @RequestBody Rule update) {
        ResponseEntity<?> validation = validateRule(update);
        if (validation != null) return validation;
        return ruleRepository.findById(id)
            .map(existing -> {
                existing.setName(update.getName());
                existing.setDescription(update.getDescription());
                existing.setProductType(update.getProductType());
                existing.setTransactionType(update.getTransactionType());
                existing.setTransactionStatus(update.getTransactionStatus());
                existing.setLegType(update.getLegType());
                existing.setLegStatus(update.getLegStatus());
                existing.setPartyType(update.getPartyType());
                existing.setFiringContext(update.getFiringContext());
                existing.setFeeType(update.getFeeType());
                existing.setPassthrough(update.getPassthrough());
                existing.setDebitAccount(update.getDebitAccount());
                existing.setCreditAccount(update.getCreditAccount());
                existing.setAmountSource(update.getAmountSource());
                existing.setEnabled(update.isEnabled());
                existing.setUpdatedAt(LocalDateTime.now());
                return ResponseEntity.ok(ruleRepository.save(existing));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        if (ruleRepository.existsById(id)) {
            ruleRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    private ResponseEntity<?> validateRule(Rule rule) {
        if (!accountRepository.existsByCodeAndEnabledTrue(rule.getDebitAccount())) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Unknown debit account: " + rule.getDebitAccount()));
        }
        if (!accountRepository.existsByCodeAndEnabledTrue(rule.getCreditAccount())) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Unknown credit account: " + rule.getCreditAccount()));
        }
        if (rule.getFiringContext() == FiringContext.FEE) {
            if (rule.getFeeType() == null || rule.getFeeType().isBlank()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "FEE rules must have feeType set"));
            }
            if (!"fee_amount".equals(rule.getAmountSource())) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "FEE rules must use amountSource=fee_amount"));
            }
        }
        if (rule.getFiringContext() == FiringContext.LEG) {
            if (rule.getFeeType() != null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "LEG rules must not have feeType set"));
            }
        }
        return null;
    }
}
```

- [ ] **Step 2: Update LedgerController — add /errors endpoint**

Replace the entire `LedgerController.java` with:

```java
// rules-engine/src/main/java/com/checkout/nexus/rulesengine/controller/LedgerController.java
package com.checkout.nexus.rulesengine.controller;

import com.checkout.nexus.rulesengine.model.entity.LedgerEntry;
import com.checkout.nexus.rulesengine.model.entity.PostingError;
import com.checkout.nexus.rulesengine.repository.LedgerEntryRepository;
import com.checkout.nexus.rulesengine.repository.PostingErrorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final PostingErrorRepository postingErrorRepository;

    @GetMapping("/entries")
    public List<LedgerEntry> getEntries(
            @RequestParam(required = false) String nexusId,
            @RequestParam(defaultValue = "50") int limit) {
        if (nexusId != null && !nexusId.isEmpty()) {
            return ledgerEntryRepository.findByNexusId(nexusId);
        }
        return ledgerEntryRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }

    @GetMapping("/entries/summary")
    public Map<String, Object> getSummary() {
        long totalEntries = ledgerEntryRepository.countAll();
        List<String> nexusIds = ledgerEntryRepository.findDistinctNexusIds();
        return Map.of(
            "totalEntries", totalEntries,
            "totalTransactions", nexusIds.size()
        );
    }

    @GetMapping("/errors")
    public List<PostingError> getErrors(
            @RequestParam(required = false) String nexusId,
            @RequestParam(required = false) String transactionId,
            @RequestParam(defaultValue = "50") int limit) {
        if (nexusId != null && !nexusId.isEmpty()) {
            return postingErrorRepository.findByNexusId(nexusId);
        }
        if (transactionId != null && !transactionId.isEmpty()) {
            return postingErrorRepository.findByTransactionId(transactionId);
        }
        return postingErrorRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }
}
```

- [ ] **Step 3: Full build + test**

```bash
cd /Users/andres.benito/Documents/claude/projects/Nexus-POC/rules-engine
./gradlew build 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add rules-engine/src/main/java/com/checkout/nexus/rulesengine/controller/RulesController.java \
        rules-engine/src/main/java/com/checkout/nexus/rulesengine/controller/LedgerController.java
git commit -m "feat: add account validation to RulesController and posting errors endpoint to LedgerController"
```

---

### Task 11: UI — client.ts, App.tsx, Layout.tsx

**Files:**
- Modify: `ui/src/api/client.ts`
- Modify: `ui/src/App.tsx`
- Modify: `ui/src/components/Layout.tsx`

- [ ] **Step 1: Add types and API calls for rules-engine to client.ts**

Add the following to the end of `ui/src/api/client.ts`:

```typescript
// --- Rules Engine direct API (port 8080) ---

const RULES_ENGINE_API = 'http://localhost:8080'

async function fetchRulesEngine<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${RULES_ENGINE_API}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })
  if (!res.ok) throw new Error(`Rules Engine ${res.status}: ${res.statusText}`)
  return res.json()
}

// --- Account types and API ---

export interface Account {
  code: string
  name: string
  accountType: 'ASSET' | 'LIABILITY' | 'EQUITY' | 'REVENUE' | 'EXPENSE' | 'CONTROL'
  normalBalance: 'DEBIT' | 'CREDIT'
  description?: string
  enabled: boolean
  createdAt: string
}

export function getAccounts(): Promise<Account[]> {
  return fetchRulesEngine('/accounts')
}

export function createAccount(account: Omit<Account, 'createdAt'>): Promise<Account> {
  return fetchRulesEngine('/accounts', { method: 'POST', body: JSON.stringify(account) })
}

export function updateAccount(code: string, account: Partial<Account>): Promise<Account> {
  return fetchRulesEngine(`/accounts/${code}`, { method: 'PUT', body: JSON.stringify(account) })
}

export function deleteAccount(code: string): Promise<void> {
  return fetchRulesEngine(`/accounts/${code}`, { method: 'DELETE' })
}

// --- Rule types and API ---

export interface Rule {
  id?: string
  name: string
  description?: string
  productType?: string
  transactionType?: string
  transactionStatus?: string
  legType?: string
  legStatus?: string
  firingContext: 'LEG' | 'FEE'
  feeType?: string
  passthrough?: boolean | null
  debitAccount: string
  creditAccount: string
  amountSource: string
  enabled: boolean
  createdAt?: string
  updatedAt?: string
}

export function getRules(): Promise<Rule[]> {
  return fetchRulesEngine('/rules')
}

export function createRule(rule: Omit<Rule, 'id' | 'createdAt' | 'updatedAt'>): Promise<Rule> {
  return fetchRulesEngine('/rules', { method: 'POST', body: JSON.stringify(rule) })
}

export function updateRule(id: string, rule: Rule): Promise<Rule> {
  return fetchRulesEngine(`/rules/${id}`, { method: 'PUT', body: JSON.stringify(rule) })
}

export function deleteRule(id: string): Promise<void> {
  return fetchRulesEngine(`/rules/${id}`, { method: 'DELETE' })
}

// --- Posting error types and API ---

export interface PostingError {
  id: string
  nexusId: string
  transactionId: string
  currency: string
  debitTotal: number
  creditTotal: number
  ruleIds?: string
  createdAt: string
}

export function getPostingErrors(params?: { nexusId?: string; transactionId?: string }): Promise<PostingError[]> {
  const query = new URLSearchParams()
  if (params?.nexusId) query.set('nexusId', params.nexusId)
  if (params?.transactionId) query.set('transactionId', params.transactionId)
  return fetchRulesEngine(`/ledger/errors?${query}`)
}
```

- [ ] **Step 2: Add routes to App.tsx**

Replace the entire `App.tsx` with:

```tsx
// ui/src/App.tsx
import { Routes, Route } from 'react-router-dom'
import Layout from './components/Layout'
import Dashboard from './pages/Dashboard'
import DlqPage from './pages/DlqPage'
import ConfigEditor from './pages/ConfigEditor'
import TestBench from './pages/TestBench'
import LiveScreen from './pages/LiveScreen'
import Rules from './pages/Rules'
import ChartOfAccounts from './pages/ChartOfAccounts'
import PostingErrors from './pages/PostingErrors'

function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/dlq" element={<DlqPage />} />
        <Route path="/config" element={<ConfigEditor />} />
        <Route path="/test-bench" element={<TestBench />} />
        <Route path="/live" element={<LiveScreen />} />
        <Route path="/rules" element={<Rules />} />
        <Route path="/accounts" element={<ChartOfAccounts />} />
        <Route path="/posting-errors" element={<PostingErrors />} />
      </Route>
    </Routes>
  )
}

export default App
```

- [ ] **Step 3: Add nav links to Layout.tsx**

Find the `NAV_LINKS` constant and replace it with:

```tsx
const NAV_LINKS = [
  { to: '/', label: 'Dashboard' },
  { to: '/dlq', label: 'DLQ' },
  { to: '/config', label: 'Config' },
  { to: '/test-bench', label: 'Test Bench' },
  { to: '/live', label: 'Live' },
  { to: '/accounts', label: 'Accounts' },
  { to: '/rules', label: 'Rules' },
  { to: '/posting-errors', label: 'Errors' },
] as const
```

- [ ] **Step 4: Commit**

```bash
git add ui/src/api/client.ts ui/src/App.tsx ui/src/components/Layout.tsx
git commit -m "feat: add accounts, rules, posting-errors types and routes to UI"
```

---

### Task 12: UI — Chart of Accounts page

**Files:**
- Create: `ui/src/pages/ChartOfAccounts.tsx`

- [ ] **Step 1: Create ChartOfAccounts.tsx**

```tsx
// ui/src/pages/ChartOfAccounts.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  getAccounts, createAccount, updateAccount, deleteAccount,
  type Account,
} from '../api/client'
import { showToast } from '../components/Toast'

const ACCOUNT_TYPES = ['ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE', 'CONTROL'] as const
const NORMAL_BALANCES = ['DEBIT', 'CREDIT'] as const

const EMPTY_FORM: Omit<Account, 'createdAt'> = {
  code: '', name: '', accountType: 'ASSET', normalBalance: 'DEBIT',
  description: '', enabled: true,
}

export default function ChartOfAccounts() {
  const qc = useQueryClient()
  const { data: accounts = [], isLoading } = useQuery({ queryKey: ['accounts'], queryFn: getAccounts })

  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Account | null>(null)
  const [form, setForm] = useState(EMPTY_FORM)

  const createMut = useMutation({
    mutationFn: createAccount,
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['accounts'] }); closeForm(); showToast('Account created', 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const updateMut = useMutation({
    mutationFn: ({ code, data }: { code: string; data: Partial<Account> }) => updateAccount(code, data),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['accounts'] }); closeForm(); showToast('Account updated', 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const deleteMut = useMutation({
    mutationFn: deleteAccount,
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['accounts'] }); showToast('Account disabled', 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  function openCreate() { setEditing(null); setForm(EMPTY_FORM); setShowForm(true) }
  function openEdit(a: Account) { setEditing(a); setForm({ ...a }); setShowForm(true) }
  function closeForm() { setShowForm(false); setEditing(null) }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (editing) {
      updateMut.mutate({ code: editing.code, data: form })
    } else {
      createMut.mutate(form)
    }
  }

  return (
    <div className="max-w-6xl mx-auto space-y-6 fade-in">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-zinc-900">Chart of Accounts</h1>
        <button onClick={openCreate}
          className="px-4 py-2 rounded-md bg-blue-600 text-white text-sm font-medium hover:bg-blue-700 transition-colors">
          + New Account
        </button>
      </div>

      {showForm && (
        <div className="glow-border rounded-xl p-5">
          <h2 className="text-sm font-semibold text-zinc-700 mb-4">
            {editing ? `Edit: ${editing.code}` : 'New Account'}
          </h2>
          <form onSubmit={handleSubmit} className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs text-zinc-500 mb-1">Code *</label>
              <input
                className="w-full border border-zinc-200 rounded-md px-3 py-2 text-sm"
                value={form.code}
                onChange={e => setForm(f => ({ ...f, code: e.target.value }))}
                disabled={!!editing}
                required
              />
            </div>
            <div>
              <label className="block text-xs text-zinc-500 mb-1">Name *</label>
              <input
                className="w-full border border-zinc-200 rounded-md px-3 py-2 text-sm"
                value={form.name}
                onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
                required
              />
            </div>
            <div>
              <label className="block text-xs text-zinc-500 mb-1">Type *</label>
              <select
                className="w-full border border-zinc-200 rounded-md px-3 py-2 text-sm"
                value={form.accountType}
                onChange={e => setForm(f => ({ ...f, accountType: e.target.value as Account['accountType'] }))}>
                {ACCOUNT_TYPES.map(t => <option key={t}>{t}</option>)}
              </select>
            </div>
            <div>
              <label className="block text-xs text-zinc-500 mb-1">Normal Balance *</label>
              <select
                className="w-full border border-zinc-200 rounded-md px-3 py-2 text-sm"
                value={form.normalBalance}
                onChange={e => setForm(f => ({ ...f, normalBalance: e.target.value as Account['normalBalance'] }))}>
                {NORMAL_BALANCES.map(b => <option key={b}>{b}</option>)}
              </select>
            </div>
            <div className="col-span-2">
              <label className="block text-xs text-zinc-500 mb-1">Description</label>
              <textarea
                className="w-full border border-zinc-200 rounded-md px-3 py-2 text-sm"
                rows={2}
                value={form.description ?? ''}
                onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
              />
            </div>
            <div className="flex items-center gap-2">
              <input type="checkbox" id="enabled" checked={form.enabled}
                onChange={e => setForm(f => ({ ...f, enabled: e.target.checked }))} />
              <label htmlFor="enabled" className="text-sm text-zinc-700">Enabled</label>
            </div>
            <div className="col-span-2 flex gap-2 justify-end">
              <button type="button" onClick={closeForm}
                className="px-4 py-2 rounded-md border border-zinc-200 text-sm hover:bg-zinc-50">
                Cancel
              </button>
              <button type="submit"
                className="px-4 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700">
                {editing ? 'Save' : 'Create'}
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="glow-border rounded-xl p-5">
        {isLoading ? (
          <p className="text-sm text-zinc-400">Loading…</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-zinc-100">
                {['Code', 'Name', 'Type', 'Normal Balance', 'Enabled', ''].map(h => (
                  <th key={h} className="pb-2 pr-4 text-xs font-semibold text-zinc-400 uppercase tracking-wider text-left">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {accounts.map(a => (
                <tr key={a.code} className="border-b border-zinc-50 hover:bg-zinc-50 transition-colors">
                  <td className="py-2.5 pr-4 font-mono text-xs text-zinc-700">{a.code}</td>
                  <td className="py-2.5 pr-4 text-zinc-700">{a.name}</td>
                  <td className="py-2.5 pr-4 text-zinc-500">{a.accountType}</td>
                  <td className="py-2.5 pr-4 text-zinc-500">{a.normalBalance}</td>
                  <td className="py-2.5 pr-4">
                    <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${a.enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-600'}`}>
                      {a.enabled ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td className="py-2.5 flex gap-2">
                    <button onClick={() => openEdit(a)}
                      className="text-xs text-blue-600 hover:underline">Edit</button>
                    {a.enabled && (
                      <button onClick={() => deleteMut.mutate(a.code)}
                        className="text-xs text-red-500 hover:underline">Disable</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add ui/src/pages/ChartOfAccounts.tsx
git commit -m "feat: add Chart of Accounts page with create/edit/disable"
```

---

### Task 13: UI — Rules page

**Files:**
- Create: `ui/src/pages/Rules.tsx`

- [ ] **Step 1: Create Rules.tsx**

```tsx
// ui/src/pages/Rules.tsx
import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getRules, createRule, updateRule, deleteRule, getAccounts, type Rule, type Account } from '../api/client'
import { showToast } from '../components/Toast'

const EMPTY_FORM: Omit<Rule, 'id' | 'createdAt' | 'updatedAt'> = {
  name: '', description: '', productType: '', transactionType: '', transactionStatus: '',
  legType: '', legStatus: '', firingContext: 'LEG', feeType: '', passthrough: null,
  debitAccount: '', creditAccount: '', amountSource: 'leg_amount', enabled: true,
}

export default function Rules() {
  const qc = useQueryClient()
  const { data: rules = [], isLoading } = useQuery({ queryKey: ['rules'], queryFn: getRules })
  const { data: accounts = [] } = useQuery({ queryKey: ['accounts'], queryFn: getAccounts })
  const enabledAccounts = accounts.filter((a: Account) => a.enabled)

  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<Rule | null>(null)
  const [form, setForm] = useState(EMPTY_FORM)

  const createMut = useMutation({
    mutationFn: createRule,
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['rules'] }); closeForm(); showToast('Rule created', 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const updateMut = useMutation({
    mutationFn: ({ id, rule }: { id: string; rule: Rule }) => updateRule(id, rule),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['rules'] }); closeForm(); showToast('Rule updated', 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  const deleteMut = useMutation({
    mutationFn: deleteRule,
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['rules'] }); showToast('Rule deleted', 'success') },
    onError: (e: Error) => showToast(e.message, 'error'),
  })

  function openCreate() { setEditing(null); setForm(EMPTY_FORM); setShowForm(true) }
  function openEdit(r: Rule) { setEditing(r); setForm({ ...r }); setShowForm(true) }
  function closeForm() { setShowForm(false); setEditing(null) }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const payload = {
      ...form,
      productType: form.productType || undefined,
      transactionType: form.transactionType || undefined,
      transactionStatus: form.transactionStatus || undefined,
      legType: form.legType || undefined,
      legStatus: form.legStatus || undefined,
      feeType: form.firingContext === 'FEE' ? (form.feeType || undefined) : undefined,
      passthrough: form.firingContext === 'FEE' ? form.passthrough : null,
      amountSource: form.firingContext === 'FEE' ? 'fee_amount' : form.amountSource,
    }
    if (editing?.id) {
      updateMut.mutate({ id: editing.id, rule: { ...payload, id: editing.id } })
    } else {
      createMut.mutate(payload)
    }
  }

  function field(label: string, key: keyof typeof form, type: 'text' | 'select' = 'text', options?: string[]) {
    if (type === 'select' && options) {
      return (
        <div>
          <label className="block text-xs text-zinc-500 mb-1">{label}</label>
          <select className="w-full border border-zinc-200 rounded-md px-3 py-2 text-sm"
            value={(form[key] as string) ?? ''}
            onChange={e => setForm(f => ({ ...f, [key]: e.target.value }))}>
            <option value="">— any —</option>
            {options.map(o => <option key={o}>{o}</option>)}
          </select>
        </div>
      )
    }
    return (
      <div>
        <label className="block text-xs text-zinc-500 mb-1">{label}</label>
        <input className="w-full border border-zinc-200 rounded-md px-3 py-2 text-sm"
          value={(form[key] as string) ?? ''}
          onChange={e => setForm(f => ({ ...f, [key]: e.target.value }))} />
      </div>
    )
  }

  function accountSelect(label: string, key: 'debitAccount' | 'creditAccount', required = true) {
    return (
      <div>
        <label className="block text-xs text-zinc-500 mb-1">{label} {required && '*'}</label>
        <select className="w-full border border-zinc-200 rounded-md px-3 py-2 text-sm"
          value={form[key]}
          required={required}
          onChange={e => setForm(f => ({ ...f, [key]: e.target.value }))}>
          <option value="">— select account —</option>
          {enabledAccounts.map((a: Account) => (
            <option key={a.code} value={a.code}>{a.code} — {a.name}</option>
          ))}
        </select>
      </div>
    )
  }

  return (
    <div className="max-w-7xl mx-auto space-y-6 fade-in">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-zinc-900">Rules</h1>
        <button onClick={openCreate}
          className="px-4 py-2 rounded-md bg-blue-600 text-white text-sm font-medium hover:bg-blue-700 transition-colors">
          + New Rule
        </button>
      </div>

      {showForm && (
        <div className="glow-border rounded-xl p-5">
          <h2 className="text-sm font-semibold text-zinc-700 mb-4">
            {editing ? 'Edit Rule' : 'New Rule'}
          </h2>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-3 gap-4">
              <div className="col-span-2">
                <label className="block text-xs text-zinc-500 mb-1">Name *</label>
                <input className="w-full border border-zinc-200 rounded-md px-3 py-2 text-sm"
                  value={form.name} required
                  onChange={e => setForm(f => ({ ...f, name: e.target.value }))} />
              </div>
              <div>
                <label className="block text-xs text-zinc-500 mb-1">Firing Context *</label>
                <select className="w-full border border-zinc-200 rounded-md px-3 py-2 text-sm"
                  value={form.firingContext}
                  onChange={e => setForm(f => ({
                    ...f,
                    firingContext: e.target.value as 'LEG' | 'FEE',
                    amountSource: e.target.value === 'FEE' ? 'fee_amount' : 'leg_amount',
                    feeType: '',
                    passthrough: null,
                  }))}>
                  <option value="LEG">LEG</option>
                  <option value="FEE">FEE</option>
                </select>
              </div>
            </div>
            <div className="grid grid-cols-3 gap-4">
              {field('Product Type', 'productType')}
              {field('Transaction Type', 'transactionType')}
              {field('Transaction Status', 'transactionStatus')}
              {field('Leg Type', 'legType')}
              {field('Leg Status', 'legStatus')}
              {form.firingContext === 'FEE' && field('Fee Type *', 'feeType')}
              {form.firingContext === 'FEE' && (
                <div>
                  <label className="block text-xs text-zinc-500 mb-1">Passthrough</label>
                  <select className="w-full border border-zinc-200 rounded-md px-3 py-2 text-sm"
                    value={form.passthrough === null ? '' : String(form.passthrough)}
                    onChange={e => setForm(f => ({
                      ...f,
                      passthrough: e.target.value === '' ? null : e.target.value === 'true',
                    }))}>
                    <option value="">— any —</option>
                    <option value="true">true</option>
                    <option value="false">false</option>
                  </select>
                </div>
              )}
            </div>
            <div className="grid grid-cols-2 gap-4">
              {accountSelect('Debit Account', 'debitAccount')}
              {accountSelect('Credit Account', 'creditAccount')}
            </div>
            <div className="flex items-center gap-2">
              <input type="checkbox" id="rule-enabled" checked={form.enabled}
                onChange={e => setForm(f => ({ ...f, enabled: e.target.checked }))} />
              <label htmlFor="rule-enabled" className="text-sm text-zinc-700">Enabled</label>
            </div>
            <div className="flex gap-2 justify-end">
              <button type="button" onClick={closeForm}
                className="px-4 py-2 rounded-md border border-zinc-200 text-sm hover:bg-zinc-50">Cancel</button>
              <button type="submit"
                className="px-4 py-2 rounded-md bg-blue-600 text-white text-sm hover:bg-blue-700">
                {editing ? 'Save' : 'Create'}
              </button>
            </div>
          </form>
        </div>
      )}

      <div className="glow-border rounded-xl p-5 overflow-x-auto">
        {isLoading ? <p className="text-sm text-zinc-400">Loading…</p> : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-zinc-100">
                {['Name', 'Context', 'Product', 'Txn Type', 'Leg Type', 'Leg Status', 'Fee Type', 'Debit', 'Credit', 'Enabled', ''].map(h => (
                  <th key={h} className="pb-2 pr-3 text-xs font-semibold text-zinc-400 uppercase tracking-wider text-left whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rules.map((r: Rule) => (
                <tr key={r.id} className="border-b border-zinc-50 hover:bg-zinc-50 transition-colors">
                  <td className="py-2 pr-3 font-medium text-zinc-900 whitespace-nowrap">{r.name}</td>
                  <td className="py-2 pr-3">
                    <span className={`px-2 py-0.5 rounded text-xs font-mono ${r.firingContext === 'FEE' ? 'bg-purple-50 text-purple-700' : 'bg-blue-50 text-blue-700'}`}>
                      {r.firingContext}
                    </span>
                  </td>
                  <td className="py-2 pr-3 text-zinc-500 text-xs">{r.productType ?? '—'}</td>
                  <td className="py-2 pr-3 text-zinc-500 text-xs">{r.transactionType ?? '—'}</td>
                  <td className="py-2 pr-3 text-zinc-500 text-xs">{r.legType ?? '—'}</td>
                  <td className="py-2 pr-3 text-zinc-500 text-xs">{r.legStatus ?? '—'}</td>
                  <td className="py-2 pr-3 text-zinc-500 text-xs">{r.feeType ?? '—'}</td>
                  <td className="py-2 pr-3 font-mono text-xs text-zinc-600">{r.debitAccount}</td>
                  <td className="py-2 pr-3 font-mono text-xs text-zinc-600">{r.creditAccount}</td>
                  <td className="py-2 pr-3">
                    <span className={`px-2 py-0.5 rounded-full text-xs ${r.enabled ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-600'}`}>
                      {r.enabled ? 'Yes' : 'No'}
                    </span>
                  </td>
                  <td className="py-2 flex gap-2 whitespace-nowrap">
                    <button onClick={() => openEdit(r)} className="text-xs text-blue-600 hover:underline">Edit</button>
                    <button onClick={() => r.id && deleteMut.mutate(r.id)} className="text-xs text-red-500 hover:underline">Delete</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add ui/src/pages/Rules.tsx
git commit -m "feat: add Rules page with firing context, account dropdowns, and full CRUD"
```

---

### Task 14: UI — Posting Errors page

**Files:**
- Create: `ui/src/pages/PostingErrors.tsx`

- [ ] **Step 1: Create PostingErrors.tsx**

```tsx
// ui/src/pages/PostingErrors.tsx
import { useQuery } from '@tanstack/react-query'
import { getPostingErrors, type PostingError } from '../api/client'

function formatDate(iso: string) {
  try { return new Date(iso).toLocaleString() } catch { return iso }
}

export default function PostingErrors() {
  const { data: errors = [], isLoading, refetch } = useQuery({
    queryKey: ['posting-errors'],
    queryFn: () => getPostingErrors(),
  })

  return (
    <div className="max-w-6xl mx-auto space-y-6 fade-in">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold text-zinc-900">Posting Errors</h1>
        <button onClick={() => refetch()}
          className="px-4 py-2 rounded-md border border-zinc-200 text-sm hover:bg-zinc-50 transition-colors">
          Refresh
        </button>
      </div>

      <div className="glow-border rounded-xl p-5">
        {isLoading ? (
          <p className="text-sm text-zinc-400">Loading…</p>
        ) : errors.length === 0 ? (
          <div className="flex flex-col items-center py-10 text-zinc-400">
            <svg className="w-8 h-8 mb-2 text-emerald-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            <p className="text-sm">No posting errors</p>
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-zinc-100">
                {['Nexus ID', 'Transaction ID', 'Currency', 'Debit Total', 'Credit Total', 'Rules Fired', 'When'].map(h => (
                  <th key={h} className="pb-2 pr-4 text-xs font-semibold text-zinc-400 uppercase tracking-wider text-left">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {errors.map((err: PostingError) => (
                <tr key={err.id} className="border-b border-zinc-50 hover:bg-red-50/30 transition-colors">
                  <td className="py-2.5 pr-4 font-mono text-xs text-zinc-700">{err.nexusId.slice(0, 12)}…</td>
                  <td className="py-2.5 pr-4 font-mono text-xs text-zinc-600">{err.transactionId.slice(0, 12)}…</td>
                  <td className="py-2.5 pr-4 text-zinc-700 font-medium">{err.currency}</td>
                  <td className="py-2.5 pr-4 text-red-600 font-mono">{Number(err.debitTotal).toFixed(2)}</td>
                  <td className="py-2.5 pr-4 text-red-600 font-mono">{Number(err.creditTotal).toFixed(2)}</td>
                  <td className="py-2.5 pr-4 text-xs text-zinc-400 font-mono max-w-xs truncate">
                    {err.ruleIds ?? '—'}
                  </td>
                  <td className="py-2.5 text-xs text-zinc-400">{formatDate(err.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add ui/src/pages/PostingErrors.tsx
git commit -m "feat: add Posting Errors page showing balance check failures"
```

---

### Task 15: End-to-end smoke test

- [ ] **Step 1: Run full backend build + tests**

```bash
cd /Users/andres.benito/Documents/claude/projects/Nexus-POC/rules-engine
./gradlew build 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` with all tests passing.

- [ ] **Step 2: Start infrastructure**

```bash
cd /Users/andres.benito/Documents/claude/projects/Nexus-POC
docker compose up -d kafka zookeeper postgres 2>&1 | tail -5
```

Expected: containers started.

- [ ] **Step 3: Start rules-engine and verify migrations run**

```bash
cd /Users/andres.benito/Documents/claude/projects/Nexus-POC/rules-engine
./gradlew bootRun 2>&1 | grep -E "(Flyway|Successfully|ERROR)" | head -20
```

Expected: Lines like `Successfully applied 10 migrations to schema "public"`. No ERROR lines.

- [ ] **Step 4: Verify accounts seeded**

```bash
curl -s http://localhost:8080/accounts | python3 -m json.tool | head -30
```

Expected: JSON array with 16 accounts including `scheme_clearing_settlement`, `client`, `revenue`, etc.

- [ ] **Step 5: Verify rules endpoint**

```bash
curl -s http://localhost:8080/rules | python3 -m json.tool
```

Expected: `[]` (empty — V10 cleared the legacy rules).

- [ ] **Step 6: Create a test rule via API and verify validation**

```bash
# Should fail — invalid account code
curl -s -X POST http://localhost:8080/rules \
  -H 'Content-Type: application/json' \
  -d '{"name":"test","firingContext":"LEG","debitAccount":"INVALID","creditAccount":"client","amountSource":"leg_amount","enabled":true}' \
  | python3 -m json.tool
```

Expected: `{"error": "Unknown debit account: INVALID"}`

```bash
# Should succeed — valid account codes
curl -s -X POST http://localhost:8080/rules \
  -H 'Content-Type: application/json' \
  -d '{"name":"Stage 1a - MCR","firingContext":"LEG","productType":"ACQUIRING","transactionType":"CAPTURE","legType":"SCHEME_SETTLEMENT","legStatus":"PREDICTED","debitAccount":"scheme_clearing_settlement","creditAccount":"client","amountSource":"leg_amount","enabled":true}' \
  | python3 -m json.tool
```

Expected: Rule object with UUID and `firingContext: "LEG"`.

- [ ] **Step 7: Start UI and verify new pages load**

```bash
cd /Users/andres.benito/Documents/claude/projects/Nexus-POC/ui
npm run dev 2>&1 &
sleep 3
open http://localhost:5173/accounts
```

Expected: Chart of Accounts page displays all 16 seeded accounts.

- [ ] **Step 8: Final commit**

```bash
cd /Users/andres.benito/Documents/claude/projects/Nexus-POC
git add docs/superpowers/plans/2026-03-30-accounting-redesign.md \
        docs/superpowers/specs/2026-03-30-accounting-redesign-design.md
git commit -m "docs: add accounting redesign spec and implementation plan"
```
