# Optimistic Locking — Design

Claude Opus 5 (1M context) — created 2026-08-07

## Problem

Concurrent operations on one account silently lose updates, and the ledger stops agreeing with the
balance.

`AccountPersistenceAdapter.save`:

```java
public Account save(Account account) {
    var entity = mapper.toJpaEntity(account);   // a brand-new DETACHED object
    var saved = repository.save(entity);        // merge → full-row overwrite
    return mapper.toDomain(saved);
}
```

`grep -rn "@Version\|@Lock" src/main/java` → **no matches**.

### The failure

Two withdrawals of 50 from a balance of 100, interleaved:

| | Transaction A | Transaction B |
|---|---|---|
| 1 | `findById` → balance 100 | |
| 2 | | `findById` → balance 100 |
| 3 | `withdraw(50)` → 50 in memory | |
| 4 | | `withdraw(50)` → 50 in memory |
| 5 | `save` → row = 50 | |
| 6 | | `save` → row = 50 (overwrites) |

The balance ends at **50** when it should be **0**. Both `Transaction` rows are written. The ledger
records two withdrawals; the balance reflects one. For a banking example this is the worst available
failure — money is created out of nothing and the audit trail disagrees with the account.

## The crux — `@Version` alone does not fix this

The version has been chosen to live on the JPA entity only, invisible to the domain. That decision
forces a second change, and it is the substance of this work.

`save` builds a **fresh detached** `AccountJpaEntity` on every call, so its version field would always
be null. Hibernate merging a detached entity with a null version treats it as a new row, or fails
unconditionally — either way there is no optimistic check, just different broken behavior.

The version must come from **the row that was read**. Since the domain does not carry it, the only
place it survives is the **managed** entity already in the persistence context from the earlier
`findById` — the same transaction loaded it moments before.

So `save` must stop building a detached replacement and instead copy the domain's fields onto the
managed entity, letting Hibernate dirty-check and version-check it:

```java
@Override
public Account save(Account account) {
    AccountJpaEntity entity = repository.findById(account.getId().value())
            .map(managed -> { mapper.copyOnto(account, managed); return managed; })
            .orElseGet(() -> mapper.toJpaEntity(account));
    return mapper.toDomain(repository.save(entity));
}
```

Because `AccountApplicationService` is `@Transactional`, that second `findById` hits the first-level
cache and returns **the same instance** the earlier read produced — no extra SELECT, and crucially the
version is the one loaded at the start of the transaction. That is exactly the window optimistic
locking must cover.

This also independently fixes the blind full-row overwrite: Hibernate now issues an UPDATE of the
changed columns, guarded by `WHERE version = ?`.

The `orElseGet` branch handles genuinely new accounts, where no row exists yet.

### Mapper change

`AccountPersistenceMapper` gains `copyOnto(Account, AccountJpaEntity)` holding the field-by-field
assignment that `toJpaEntity` currently performs. `toJpaEntity` becomes: allocate, set the id, then
delegate to `copyOnto`. **`copyOnto` must never touch `id` or `version`** — the id is immutable and
the version belongs to Hibernate.

## Entity and schema

```java
@Version
@Column(name = "version", nullable = false)
private Long version;
```

with a getter and setter, both unused by application code — the field exists for Hibernate.

`spring.jpa.hibernate.ddl-auto=update` cannot add a `NOT NULL` column to a populated table, so
`data.sql` gets the same treatment `type` and `tier` already receive:

```sql
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
```

`Schema.sql` gains the column in the `accounts` definition, for consistency with the documented schema.

## Error handling

Hibernate raises the failure when the transaction **commits**, which happens inside the
`@Transactional` proxy — *after* the application service method has returned. The service therefore
cannot catch it. Mapping belongs in `GlobalExceptionHandler`:

```java
@ExceptionHandler(OptimisticLockingFailureException.class)
public ProblemDetail handleConcurrentModification(OptimisticLockingFailureException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
            "The account was modified by another operation. Please retry.");
}
```

**HTTP 409 Conflict**, and a fixed message rather than `ex.getMessage()` — Hibernate's text names the
entity class and primary key, which is internal detail that should not reach a client.

`OptimisticLockingFailureException` is Spring's abstraction; `ObjectOptimisticLockingFailureException`
extends it, so catching the base covers both. `GlobalExceptionHandler` is an inbound adapter and may
import Spring types.

## Testing

The project has **no test database** — every existing test is a unit test or a `@WebMvcTest`. Adding
one is part of this work.

**H2, test scope only.** Hibernate performs the version comparison, not the database, so H2 is
faithful for this behavior. No Docker requirement; CI is unchanged.

A `src/test/resources/application.properties` configures the test datasource so `@DataJpaTest` does
not inherit the PostgreSQL settings from main.

### The test needs no threads

Two independent persistence contexts are sufficient, and the result is deterministic — no sleeps, no
race, no flakiness:

```java
EntityManager em1 = emf.createEntityManager();
EntityManager em2 = emf.createEntityManager();
// both begin a transaction and load the same account at version 0
// em1 commits   → row moves to version 1
// em2 commits   → UPDATE ... WHERE version = 0 matches no row → OptimisticLockException
```

Simulating concurrency by *ordering* two contexts rather than by running them at the same time is the
point worth teaching: a lost update is not a timing problem, it is a stale-read problem.

The class is annotated `@Transactional(propagation = Propagation.NOT_SUPPORTED)` so Spring does not
wrap the test in a transaction that would roll back the fixture row and hide it from both contexts.

### Tests to add

| Test | Asserts |
|---|---|
| `shouldRejectTheSecondWriterWhenBothLoadedTheSameVersion` | the second commit throws; the lost update is prevented |
| `shouldIncrementTheVersionOnEachUpdate` | version moves 0 → 1 → 2, so the mechanism is actually engaged |
| `shouldPersistANewAccountAtVersionZero` | the `orElseGet` insert path still works |

Plus a `GlobalExceptionHandler` test asserting `OptimisticLockingFailureException` → 409.

## Scope

| | |
|---|---|
| Modified | `AccountJpaEntity`, `AccountPersistenceMapper`, `AccountPersistenceAdapter`, `GlobalExceptionHandler`, `data.sql`, `Schema.sql`, `pom.xml` |
| New | `src/test/resources/application.properties`, `AccountOptimisticLockingTest` |
| Untouched | the entire domain, all ports, both application services, all controllers |

The domain does not change at all — which is the payoff of keeping the version out of it, and worth
stating plainly in the write-up.

## Out of scope

`CustomerJpaEntity` has the same exposure: two concurrent password changes, or a tier change racing a
password change, can lose an update the same way. The same fix applies. It is left out so this change
stays reviewable, and recorded here so it is not mistaken for an oversight.

Retry-on-conflict is also out of scope. 409 tells the client to retry; automatic server-side retry is
a separate design with its own idempotency questions.

## Documentation deliverable

`Refactorings.md` entry 5: the lost-update table, why `@Version` alone would not have worked, the
detached-versus-managed distinction, the deterministic two-context test, and the observation that the
domain needed no change at all.
