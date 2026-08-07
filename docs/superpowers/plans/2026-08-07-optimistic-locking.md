# Optimistic Locking Implementation Plan

Claude Opus 5 (1M context) — created 2026-08-07

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop concurrent operations on one account silently losing updates, so the balance can no longer disagree with the ledger.

**Architecture:** `@Version` on `AccountJpaEntity` only — the domain never learns it exists. That forces `AccountPersistenceAdapter.save` to stop building a fresh detached entity and instead copy the domain's fields onto the **managed** entity already in the persistence context, which is the only place the loaded version survives. Hibernate raises the conflict at commit, so `GlobalExceptionHandler` maps it to HTTP 409.

**Tech Stack:** Java 25, Spring Boot 3.4, Spring Data JPA / Hibernate, H2 (test scope, new), JUnit 5, AssertJ, Maven.

## Global Constraints

- Root package `dev.kaldiroglu`.
- **The domain must not change at all.** No file under `domain/` is touched by this work. If a step seems to require it, stop — the design is being violated.
- All code and comments in English. American spelling.
- Every `.md` file created must carry the model name, version, and creation date at the top.
- Baseline: **202 tests, 0 failures**.
- Design reference: `docs/superpowers/specs/2026-08-07-optimistic-locking-design.md`.
- `mvn clean verify` must pass before the work is reported complete.

---

## File Structure

**New:**

| Path | Responsibility |
|---|---|
| `src/test/resources/application.properties` | Point `@DataJpaTest` at H2 instead of the main PostgreSQL settings |
| `src/test/java/.../adapter/out/persistence/AccountOptimisticLockingTest.java` | Prove the lost update is prevented |

**Modified:** `pom.xml`, `AccountJpaEntity`, `AccountPersistenceMapper`, `AccountPersistenceAdapter`, `GlobalExceptionHandler`, `src/main/resources/data.sql`, `Schema.sql`

**Untouched:** everything under `domain/`, both application services, all ports, all controllers.

---

### Task 1: H2 and the test datasource

Infrastructure only. Adding H2 must not change any existing test.

**Files:**
- Modify: `pom.xml`
- Create: `src/test/resources/application.properties`

**Interfaces:**
- Consumes: nothing
- Produces: an H2 datasource available to `@DataJpaTest`

- [ ] **Step 1: Add H2 in test scope**

In `pom.xml`, after the `spring-security-test` dependency and before `</dependencies>`:

```xml
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
```

The version comes from the Spring Boot parent's dependency management — do not pin one.

- [ ] **Step 2: Create the test datasource configuration**

`src/test/resources/application.properties`:

```properties
# Tests must not inherit the PostgreSQL settings from src/main/resources.
# H2 is sufficient here because the optimistic-lock comparison is performed by
# Hibernate, not by the database.
spring.datasource.url=jdbc:h2:mem:ayvalikbank;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect
spring.jpa.show-sql=false

# data.sql targets PostgreSQL (ALTER TABLE ... IF NOT EXISTS, ON CONFLICT).
# ddl-auto=create-drop builds the schema for tests, so the seed script is not needed.
spring.sql.init.mode=never
```

- [ ] **Step 3: Verify nothing changed**

Run: `mvn -o clean test`
Expected: **202 tests, 0 failures.** No existing test starts a datasource — they are unit tests and `@WebMvcTest` slices — so this must be inert.

- [ ] **Step 4: Commit**

```bash
git add pom.xml src/test/resources/application.properties
git commit -m "Add H2 for persistence tests

First test database in the project. Configured so @DataJpaTest does not
inherit the PostgreSQL settings from src/main/resources."
```

---

### Task 2: The version column

**Files:**
- Modify: `adapter/out/persistence/entity/AccountJpaEntity.java`, `src/main/resources/data.sql`, `Schema.sql`

**Interfaces:**
- Consumes: nothing
- Produces: `AccountJpaEntity.getVersion()` / `setVersion(Long)`, and a `version` column defaulting to 0

- [ ] **Step 1: Add the field**

In `AccountJpaEntity`, after the `id` field:

```java
    /**
     * Optimistic-lock token, managed entirely by Hibernate.
     *
     * <p>Deliberately absent from the domain {@code Account}: a version is a persistence concern,
     * not a banking one. Keeping it here is why {@code AccountPersistenceAdapter.save} has to mutate
     * the <i>managed</i> entity rather than merge a freshly built detached one — the loaded version
     * survives only inside the persistence context.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
```

and with the other accessors:

```java
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
```

`jakarta.persistence.*` is already wildcard-imported, so `@Version` needs no new import.

- [ ] **Step 2: Add the schema migration**

In `src/main/resources/data.sql`, beside the existing `ALTER TABLE` statements:

```sql
ALTER TABLE accounts  ADD COLUMN IF NOT EXISTS version         bigint        NOT NULL DEFAULT 0;
```

and with the belt-and-braces backfills:

```sql
UPDATE accounts  SET version         = 0          WHERE version         IS NULL;
```

- [ ] **Step 3: Record it in `Schema.sql`**

Add `version bigint NOT NULL DEFAULT 0` to the `public.accounts` table definition, after the `type` column.

- [ ] **Step 4: Verify**

Run: `mvn -o clean test`
Expected: **202 tests, 0 failures.** Nothing reads the field yet.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/out/persistence/entity/AccountJpaEntity.java \
        src/main/resources/data.sql Schema.sql
git commit -m "Add a version column to accounts

Hibernate-managed optimistic-lock token. Nothing depends on it yet -
AccountPersistenceAdapter still overwrites whole rows from a detached
entity, so the version is not yet consulted."
```

---

### Task 3: Save through the managed entity

The substance. This is what makes the version actually do something.

**Files:**
- Modify: `adapter/out/persistence/mapper/AccountPersistenceMapper.java`, `adapter/out/persistence/AccountPersistenceAdapter.java`
- Test: `src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/out/persistence/AccountOptimisticLockingTest.java` *(new)*

**Interfaces:**
- Consumes: `AccountJpaEntity.getVersion()` from Task 2
- Produces: `AccountPersistenceMapper.copyOnto(Account account, AccountJpaEntity entity)` — copies every mutable column, never `id`, never `version`

- [ ] **Step 1: Write the failing test**

Create `AccountOptimisticLockingTest`:

```java
package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence;

import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.entity.AccountJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * No threads are needed. A lost update is a stale-read problem, not a timing problem, so two
 * persistence contexts committing in a fixed order reproduce it deterministically.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("Optimistic locking on accounts")
class AccountOptimisticLockingTest {

    @Autowired
    private EntityManagerFactory emf;

    private UUID insertAccount(String balance) {
        UUID id = UUID.randomUUID();
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        AccountJpaEntity entity = new AccountJpaEntity();
        entity.setId(id);
        entity.setOwnerId(UUID.randomUUID());
        entity.setCurrency("USD");
        entity.setBalance(new BigDecimal(balance));
        entity.setStatus("ACTIVE");
        entity.setType("CHECKING");
        entity.setOverdraftLimit(BigDecimal.ZERO);
        em.persist(entity);
        em.getTransaction().commit();
        em.close();
        return id;
    }

    @Test
    void shouldPersistANewAccountAtVersionZero() {
        UUID id = insertAccount("100.00");

        EntityManager em = emf.createEntityManager();
        AccountJpaEntity loaded = em.find(AccountJpaEntity.class, id);
        assertThat(loaded.getVersion()).isZero();
        em.close();
    }

    @Test
    void shouldIncrementTheVersionOnEachUpdate() {
        UUID id = insertAccount("100.00");

        for (int expected = 1; expected <= 2; expected++) {
            EntityManager em = emf.createEntityManager();
            em.getTransaction().begin();
            em.find(AccountJpaEntity.class, id).setBalance(new BigDecimal("10.0" + expected));
            em.getTransaction().commit();
            em.close();

            EntityManager check = emf.createEntityManager();
            assertThat(check.find(AccountJpaEntity.class, id).getVersion()).isEqualTo(expected);
            check.close();
        }
    }

    @Test
    @DisplayName("the second writer is rejected when both loaded the same version")
    void shouldRejectTheSecondWriterWhenBothLoadedTheSameVersion() {
        UUID id = insertAccount("100.00");

        EntityManager em1 = emf.createEntityManager();
        EntityManager em2 = emf.createEntityManager();
        em1.getTransaction().begin();
        em2.getTransaction().begin();

        // Both read balance 100 at version 0 - this is the stale read.
        AccountJpaEntity first = em1.find(AccountJpaEntity.class, id);
        AccountJpaEntity second = em2.find(AccountJpaEntity.class, id);

        first.setBalance(new BigDecimal("50.00"));
        em1.getTransaction().commit();

        second.setBalance(new BigDecimal("50.00"));
        assertThatThrownBy(() -> em2.getTransaction().commit())
                .isInstanceOf(RollbackException.class)
                .hasRootCauseInstanceOf(OptimisticLockException.class);

        em1.close();
        em2.close();

        // Without the version the balance would read 50.00 - one withdrawal lost.
        EntityManager check = emf.createEntityManager();
        assertThat(check.find(AccountJpaEntity.class, id).getBalance()).isEqualByComparingTo("50.00");
        assertThat(check.find(AccountJpaEntity.class, id).getVersion()).isEqualTo(1);
        check.close();
    }
}
```

- [ ] **Step 2: Run it**

Run: `mvn -o test -Dtest=AccountOptimisticLockingTest`
Expected: **PASS, 3 tests.** `@Version` from Task 2 already makes Hibernate enforce this at the entity level. If any test fails, the version column or the H2 configuration is wrong — fix that before continuing.

This test does not depend on the adapter. It proves the *mechanism*. Step 3 makes the *application* use it.

- [ ] **Step 3: Add `copyOnto` to the mapper**

In `AccountPersistenceMapper`, replace the body of `toJpaEntity` and add the new method:

```java
    public AccountJpaEntity toJpaEntity(Account account) {
        AccountJpaEntity entity = new AccountJpaEntity();
        entity.setId(account.getId().value());
        copyOnto(account, entity);
        return entity;
    }

    /**
     * Copies every mutable column from the domain object onto an existing entity.
     *
     * <p>Never touches {@code id} or {@code version}. The id is immutable; the version belongs to
     * Hibernate, and overwriting it would defeat the optimistic-lock check this exists to support.
     */
    public void copyOnto(Account account, AccountJpaEntity entity) {
        entity.setOwnerId(account.getOwnerId().value());
        entity.setCurrency(account.getCurrency().name());
        entity.setBalance(account.getBalance().amount());
        entity.setStatus(account.getStatus().name());
        entity.setType(account.type().name());
        switch (account) {
            case CheckingAccount c -> entity.setOverdraftLimit(c.getOverdraftLimit().amount());
            case SavingsAccount s -> {
                entity.setInterestRate(s.getAnnualInterestRate());
                entity.setLastAccrualDate(s.getLastAccrualDate());
            }
            case TimeDepositAccount t -> {
                entity.setPrincipal(t.getPrincipal().amount());
                entity.setOpenedOn(t.getOpenedOn());
                entity.setMaturityDate(t.getMaturityDate());
                entity.setInterestRate(t.getAnnualInterestRate());
                entity.setMatured(t.isMatured());
            }
        }
    }
```

The switch stays exhaustive over the sealed `Account` hierarchy — no `default`.

- [ ] **Step 4: Rewrite `AccountPersistenceAdapter.save`**

```java
    /**
     * Writes the account back, preserving the optimistic-lock version.
     *
     * <p>The entity is <b>not</b> rebuilt from scratch. Within the service's transaction the earlier
     * {@code findById} left a managed entity in the persistence context, and that instance carries
     * the version loaded at the start of the transaction. Copying onto it lets Hibernate emit
     * {@code UPDATE ... WHERE version = ?} and detect a concurrent write. Merging a freshly built
     * detached entity would carry no version and silently overwrite the whole row.
     *
     * <p>The {@code orElseGet} branch covers a genuinely new account, where no row exists yet.
     */
    @Override
    public Account save(Account account) {
        AccountJpaEntity entity = repository.findById(account.getId().value())
                .map(managed -> {
                    mapper.copyOnto(account, managed);
                    return managed;
                })
                .orElseGet(() -> mapper.toJpaEntity(account));
        return mapper.toDomain(repository.save(entity));
    }
```

- [ ] **Step 5: Run the suite**

Run: `mvn -o clean test`
Expected: **205 tests, 0 failures** (202 + the 3 new).

- [ ] **Step 6: Confirm the domain was not touched**

```bash
git status --short src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/
```

Expected: **no output.** The global constraint says the domain must not change; this is the check.

- [ ] **Step 7: Commit**

```bash
git add -A src/
git commit -m "Save accounts through the managed entity so the version is honoured

save() rebuilt a detached entity on every call, so its version was always
null and Hibernate overwrote the whole row blind. Two concurrent
withdrawals of 50 from 100 both wrote 50 - one vanished while both
Transaction rows persisted, leaving balance and ledger disagreeing.

Copying onto the managed instance keeps the version loaded at the start of
the transaction, so Hibernate emits UPDATE ... WHERE version = ?."
```

---

### Task 4: Map the conflict to HTTP 409

**Files:**
- Modify: `adapter/in/web/GlobalExceptionHandler.java`
- Test: `src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/AccountControllerTest.java`

**Interfaces:**
- Consumes: `org.springframework.dao.OptimisticLockingFailureException`
- Produces: HTTP 409 with a fixed detail message

- [ ] **Step 1: Write the failing test**

Append to `AccountControllerTest`:

```java
    @Test
    @WithBankUser(customerId = CALLER_ID)
    @DisplayName("a concurrent modification is reported as 409, without leaking entity internals")
    void deposit_returnsConflictOnOptimisticLockFailure() throws Exception {
        doThrow(new ObjectOptimisticLockingFailureException(
                        "dev.kaldiroglu...AccountJpaEntity", UUID.randomUUID()))
                .when(customerAccount).deposit(any());

        mockMvc.perform(post("/api/accounts/{id}/deposit", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":50.00,"currency":"USD"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The account was modified by another operation. Please retry."));
    }
```

Add imports for `org.springframework.orm.ObjectOptimisticLockingFailureException` and
`org.junit.jupiter.api.DisplayName`.

The assertion on `$.detail` is the point: it pins that Hibernate's message — which names the entity
class and primary key — does **not** reach the client.

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -o test -Dtest=AccountControllerTest#deposit_returnsConflictOnOptimisticLockFailure`
Expected: FAIL — status 500, because no handler matches.

- [ ] **Step 3: Add the handler**

In `GlobalExceptionHandler`, before the `IllegalArgumentException` catch-all:

```java
    /**
     * Two operations modified the same account concurrently and the second lost.
     *
     * <p>Hibernate raises this at transaction commit — inside the {@code @Transactional} proxy and
     * therefore after the application service method has already returned — so the service cannot
     * catch it. Mapping belongs here.
     *
     * <p>The detail is fixed rather than {@code ex.getMessage()}: Hibernate's text names the entity
     * class and primary key, which is internal detail that should not reach a client.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrentModification(OptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The account was modified by another operation. Please retry.");
    }
```

Add `import org.springframework.dao.OptimisticLockingFailureException;`. Catching the Spring base
covers `ObjectOptimisticLockingFailureException` and any other subtype.

- [ ] **Step 4: Run the suite**

Run: `mvn -o clean test`
Expected: **206 tests, 0 failures.**

- [ ] **Step 5: Commit**

```bash
git add -A src/
git commit -m "Return 409 Conflict on a concurrent account modification

Hibernate raises the failure at commit, inside the @Transactional proxy and
after the service method has returned, so the handler is the only place that
can map it. The detail message is fixed rather than Hibernate's, which names
the entity class and primary key."
```

---

### Task 5: Documentation

**Files:**
- Modify: `Refactorings.md`, `CLAUDE.md`, `Tests.md`, `README.md`

- [ ] **Step 1: Add `Refactorings.md` entry 5**

Following entries 1–4, recording the baseline commit. Cover:

1. **The lost-update table** — the six interleaved steps ending with balance 50 where it should be 0,
   and both `Transaction` rows written, so ledger and balance disagree.
2. **Why `@Version` alone would not have worked** — `save` rebuilt a detached entity each call, so its
   version was always null; the version survives only on the managed instance in the persistence
   context. State the general rule: *an ORM can only protect a row you actually loaded*.
3. **Detached versus managed**, and that the same change independently ends the blind full-row
   overwrite.
4. **The domain did not change at all** — the payoff of keeping the version on the entity. Contrast
   with what carrying it on `Account` would have cost.
5. **A concurrency test with no concurrency** — two persistence contexts committing in a fixed order
   reproduce the bug deterministically. No threads, no sleeps, no flakiness.
6. **Why the 409 is mapped in the handler** — the exception surfaces at commit, after the service has
   returned.
7. **Why the detail message is fixed** — Hibernate's names the entity class and primary key.

- [ ] **Step 2: Update `CLAUDE.md`**

Add to Key Design Decisions:

```markdown
- **Optimistic locking**: `AccountJpaEntity` carries a Hibernate-managed `@Version`; the domain `Account` deliberately does not — a version is a persistence concern. `AccountPersistenceAdapter.save` therefore copies onto the **managed** entity from the persistence context rather than merging a freshly built detached one, which is the only way the loaded version survives. A conflict surfaces at commit and `GlobalExceptionHandler` maps `OptimisticLockingFailureException` to HTTP 409. See `Refactorings.md` entry 5.
```

Add the 409 to the REST API notes if status codes are listed there.

- [ ] **Step 3: Update `Tests.md`**

Set the header count to the figure from Task 4 Step 4. Add an `AccountOptimisticLockingTest` section
describing the three tests, and note that it is the project's **first `@DataJpaTest`** and why H2 is
sufficient — Hibernate performs the version comparison, not the database.

- [ ] **Step 4: Update `README.md`**

It documents how to run the project and tests. Note that tests now use an in-memory H2 database and
require no running PostgreSQL, while `mvn spring-boot:run` still needs `docker compose up -d`.

- [ ] **Step 5: Verify every documented claim**

```bash
grep -n "@Version" src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/out/persistence/entity/AccountJpaEntity.java
grep -rn "version" src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/ || echo "domain clean"
grep -n "version" src/main/resources/data.sql
```

- [ ] **Step 6: Final verification**

Run: `mvn clean verify`
Expected: BUILD SUCCESS, 0 failures.

- [ ] **Step 7: Commit**

```bash
git add Refactorings.md CLAUDE.md Tests.md README.md docs/superpowers/
git commit -m "Document the optimistic locking work"
```

---

## Self-Review

**Spec coverage.** H2 and the test datasource → Task 1; `@Version` and the migration → Task 2; the
managed-entity save and `copyOnto` → Task 3; the 409 mapping → Task 4; documentation → Task 5. The
spec's out-of-scope items (`CustomerJpaEntity`, retry-on-conflict) are carried into entry 5 as stated
non-goals rather than silently dropped.

**Placeholder scan.** No TBDs. All three persistence tests, the controller test, `copyOnto`, `save`
and the handler are given in full.

**Type consistency.** `copyOnto(Account, AccountJpaEntity)` is defined in Task 3 Step 3 and called in
Step 4. `getVersion()` / `setVersion(Long)` are defined in Task 2 and used by all three tests in
Task 3. `OptimisticLockingFailureException` (Spring, `org.springframework.dao`) is the handler's
parameter; `ObjectOptimisticLockingFailureException` (`org.springframework.orm`) is what the
controller test throws — the second extends the first, which is why catching the base works.

**Ordering note.** Task 3's tests pass as soon as Task 2 lands, because `@Version` alone makes
Hibernate enforce versioning at the *entity* level. They are placed in Task 3 anyway: they document
what Task 3's adapter change exists to make reachable from the *application*. The adapter change is
what carries the version through a real save.

**Checkpoints.** 202 (Task 1) → 202 (Task 2) → 205 (Task 3) → 206 (Task 4).
