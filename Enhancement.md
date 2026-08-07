# Enhancement Walkthrough — Daily Withdrawal Limits

A teaching example: add **per-account, per-calendar-day cumulative withdrawal limits** to the project, then study where the change lands.

This file describes the feature in this codebase (Java / Spring Boot / hexagonal). Sibling files in `AyvalikBankLA-JAVA`, `AyvalikBankHA-NET`, `AyvalikBankLA-NET`, `AyvalikBankHA-Python`, `AyvalikBankLA-Python` describe the same feature in their respective stacks so the impact can be compared side by side.

---

## The Feature

- Each `Account` carries a nullable `dailyWithdrawalLimit: Money`. Null = use a tier-derived default.
- Cumulative withdrawals (direct withdraw + the debit side of transfers) on a single UTC calendar day must not exceed that limit.
- Admin can set/clear the limit per account: `PUT /api/admin/accounts/{id}/daily-limit`.
- Reset at UTC midnight.
- A separate, additive constraint — the existing per-transaction tier caps still apply.

---

## Why this feature is good for teaching

It crosses every layer: model, persistence, business rule, API, validation. It introduces **state that lives across transactions** ("today's running total"), which is the interesting persistence question. And it sits at the intersection of `Customer`, `Account`, and `Transaction` — three aggregates — which forces an architectural decision.

---

## Impact on this project — Java 25 / Spring Boot 3.4 / Hexagonal

### Files to add or modify

| # | Layer | Path | Change |
|---|---|---|---|
| 1 | Domain model | `domain/model/account/Account.java` | Add field `private Money dailyWithdrawalLimit;` (nullable) + getter |
| 2 | Domain service | `domain/service/account/WithdrawalPolicyService.java` *(new)* | Pure class with `requireWithinDailyLimit(Account, Money withdrawnSoFar, Money requested)` — instantiated as a `@Bean` in `SecurityConfig`, no Spring annotations on the class itself |
| 3 | Domain port (out) | `domain/port/out/account/DailyWithdrawalQueryPort.java` *(new)* | `Money sumWithdrawals(AccountId, LocalDate utcDay)` — domain defines the question |
| 4 | Application port (in) | `application/port/in/account/AccountAdministrationPort.java` | Add `setDailyLimit(SetDailyLimitCommand)` + the nested record. **No new interface** — setting a limit is an admin action, so it joins the existing admin-facing port rather than creating a sixth port. |
| 5 | Application | `application/service/AccountApplicationService.java` | Inject the new query port + `WithdrawalPolicyService`. In `withdraw` and `transfer`, two new lines: query port → policy → wrap any thrown exception as `LimitExceededException` (already mapped to 422). Implement the new `setDailyLimit` method from `AccountAdministrationPort`. |
| 6 | Adapter (out) | `adapter/out/persistence/entity/AccountJpaEntity.java` | One nullable column `daily_withdrawal_limit NUMERIC(19,2)` |
| 7 | Adapter (out) | `adapter/out/persistence/adapter/DailyWithdrawalQueryAdapter.java` *(new)* | Implements the new port via a Spring Data `@Query("select coalesce(sum(t.amount), 0) from TransactionJpaEntity t where t.accountId = :id and t.type = 'WITHDRAWAL' and t.timestamp >= :startOfDay and t.timestamp < :startOfNextDay")` |
| 8 | Adapter (out) | `adapter/out/persistence/mapper/AccountMapper.java` | Copy the new field both ways |
| 9 | Adapter (in) | `adapter/in/web/AdminController.java` | New endpoint + DTO record `SetDailyLimitRequest(BigDecimal amount, Currency currency)` |
| 10 | Config | `config/SecurityConfig.java` | `@Bean` for `WithdrawalPolicyService` + `requestMatchers("/api/admin/accounts/*/daily-limit")` |
| 11 | Tests | `test/java/.../WithdrawalPolicyServiceTest.java` *(new)* | 4–5 pure-JUnit tests — no Spring, no JPA |
| 12 | Tests | `AccountControllerTest`, `AccountControllerTransferTest` | Add cases: limit-not-exceeded, limit-exceeded → 422, limit-with-transfer-debit |
| 13 | Migration | `src/main/resources/data.sql` | `ALTER TABLE accounts ADD COLUMN IF NOT EXISTS daily_withdrawal_limit NUMERIC(19,2)` |

### Tech-stack-specific notes (Java)

- **`Money` value object** — already a `record`, immutable. Adding it as a field is trivial.
- **Spring Data JPA `@Query`** — the daily-sum query is a one-liner with HQL `coalesce(sum(...), 0)`. Don't reach for `EntityManager`; use repository derived methods or a `@Query` annotation.
- **`@Transactional` boundary** — `withdraw` and `transfer` already run inside a service transaction; the new `SUM` query just becomes another statement in the same transaction. Read consistency is automatic.
- **Date math** — `LocalDate startOfUtcDay = LocalDate.now(ZoneOffset.UTC); Instant.from(startOfUtcDay.atStartOfDay(ZoneOffset.UTC));`. Pass instants, not `LocalDate`, into the JPQL — your `Transaction.timestamp` is `Instant`/`OffsetDateTime`.
- **Exception mapping** — `GlobalExceptionHandler` already maps `LimitExceededException → 422`, so you don't need a new exception class. If you want a more specific `DailyLimitExceededException`, extend `LimitExceededException` so the existing handler still catches it.
- **Hibernate DDL** — `spring.jpa.hibernate.ddl-auto=update` will *not* add a NOT NULL column to a populated table. Keep the new column nullable, or add an explicit `ALTER TABLE` in `data.sql` (the project already uses this pattern for tier).
- **Schema isolation** — domain remains free of JPA imports; persistence layer adds the column on its own JPA entity, and `AccountMapper` shuttles the value across.

### Test impact

- **WithdrawalPolicyServiceTest**: pure JUnit, no `@SpringBootTest` — exercises the rule with hand-built `Money` values. Fast, isolated, durable.
- **Controller tests**: extend `AccountControllerTest` with `@WithMockUser` cases that cross the limit threshold; the `MockMvc` tests don't need new infrastructure since the feature is wired through existing DI.
- **Integration**: the `DailyWithdrawalQueryAdapter` gets a `@DataJpaTest` against H2/Postgres test container to verify the JPQL.

---

## Lesson Plan (apply to all six projects)

1. **Show both diffs side by side.** Count files; count *lines where the actual rule lives*.
2. **Change the rule** — "reset at customer's local midnight, not UTC." In HA you change one method on `WithdrawalPolicyService` + one query in the adapter. In LA you edit a 40-line `withdraw` method that's already doing five other things.
3. **Add a second consumer** — `GET /api/accounts/{id}/today-summary` showing withdrawn-so-far + remaining-limit. In HA: one controller method calling the existing port + policy. In LA: copy the SQL `SUM` + comparison into a new service method.

The moral: **architecture is a bet about which kinds of change are likely.** HA bets on rules changing and being reused — it pays a structural tax up front. LA bets on rules being stable and local — it pays an entanglement tax later.
