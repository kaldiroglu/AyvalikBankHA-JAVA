# Architecture: Ayvalık Bank CC-1

## Architectural Pattern: Hexagonal Architecture (Ports & Adapters)

The core idea is the **Dependency Rule**: every arrow points inward. The domain knows nothing about Spring, JPA, HTTP, or BCrypt. The outer layers know about the inner ones — never the reverse.

```
┌─────────────────────────────────────────────────────┐
│  ADAPTERS (outer)                                   │
│  ┌───────────────┐         ┌──────────────────────┐ │
│  │ REST          │         │ JPA Persistence      │ │
│  │ Controllers   │         │ + BCrypt Adapter     │ │
│  └──────┬────────┘         └──────────┬───────────┘ │
│         │                             │             │
│  ┌──────▼─────────────────────────────▼───────────┐ │
│  │  APPLICATION SERVICES                          │ │
│  │  (orchestration, transactions, error handling) │ │
│  └──────────────────┬─────────────────────────────┘ │
│                     │                               │
│  ┌──────────────────▼─────────────────────────────┐ │
│  │  DOMAIN  (pure Java — zero framework imports)  │ │
│  │  Entities · Value Objects · Domain Services    │ │
│  │  Ports In (use cases) · Ports Out (interfaces) │ │
│  └────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

---

## Layer-by-Layer Breakdown

### 1. Domain Layer (`domain/`)
The heart of the application. **No Spring, no JPA, no BCrypt** — plain Java only. The domain is split per aggregate (`account` and `customer`) at every layer — model, service, ports — so each aggregate's vocabulary is locally complete.

**Entities — account aggregate** (`domain/model/account/`): `Account` (sealed hierarchy — see below), `CheckingAccount`, `SavingsAccount`, `TimeDepositAccount`, `Transaction`

**Entities — customer aggregate** (`domain/model/customer/`): `Customer` (carries a `CustomerTier` — `STANDARD` / `PREMIUM` / `PRIVATE`)

These are *rich* objects. `Account` owns its own invariants:
```java
account.deposit(money)      // validates currency + ACTIVE status, updates balance, returns Transaction
account.withdraw(money)     // validates sufficient funds + ACTIVE status (rules differ per subtype)
account.transferOut(...)    // deducts amount + fee, requires ACTIVE status
account.freeze()            // ACTIVE → FROZEN
account.unfreeze()          // FROZEN → ACTIVE
account.close()             // ACTIVE|FROZEN → CLOSED (terminal)
```

**Enums** (`domain/model/account/`): `AccountStatus` (`ACTIVE`, `FROZEN`, `CLOSED`), `AccountType` (`CHECKING`, `SAVINGS`, `TIME_DEPOSIT`), `Currency`, `TransactionType` (includes `INTEREST`)

`AccountStatus` drives a state machine inside `Account` via the `AccountState` sealed interface (see [Account hierarchy](#account-hierarchy)). All mutating operations require `ACTIVE`; invalid transitions throw `IllegalStateException`.

**Value Objects as Records:**
- Account-side (`domain/model/account/`): `Money`, `AccountId`, `TransactionId`
- Customer-side (`domain/model/customer/`): `Password`, `CustomerId`

Immutable and self-validating. `Money` permits negative amounts (required to represent overdraft balances) but rejects null. `Password` stores only BCrypt hashes. The `account` package references `customer.CustomerId` via an explicit cross-aggregate import — the only coupling between the two aggregates.

**Domain Services:**
- `domain/service/customer/PasswordValidationService` — enforces 8-16 char, upper/lower/digit/special rules
- `domain/service/account/TransferDomainService` — computes fees (0% same customer; cross-customer fee = admin's % × source customer's tier multiplier) and validates per-transaction transfer / withdrawal caps from the tier

**Ports In:**
- `domain/port/in/account/` — 15 account use cases: `OpenCheckingAccountUseCase`, `OpenSavingsAccountUseCase`, `OpenTimeDepositAccountUseCase`, `DepositMoneyUseCase`, `WithdrawMoneyUseCase`, `TransferMoneyUseCase`, `GetBalanceUseCase`, `GetTransactionsUseCase`, `ListAccountsUseCase`, `FreezeAccountUseCase`, `UnfreezeAccountUseCase`, `CloseAccountUseCase`, `AccrueInterestUseCase`, `MatureTimeDepositUseCase`, `SetTransferFeeUseCase`
- `domain/port/in/customer/` — 5 customer use cases: `CreateCustomerUseCase`, `DeleteCustomerUseCase`, `ListCustomersUseCase`, `ChangePasswordUseCase`, `ChangeCustomerTierUseCase`

Each use case carries a nested `Command` record for its input (or takes a typed ID directly for simple operations).

**Ports Out:**
- `domain/port/out/account/` — `AccountRepositoryPort`, `TransactionRepositoryPort`, `SettingsRepositoryPort`
- `domain/port/out/customer/` — `CustomerRepositoryPort`, `PasswordHasherPort`

The domain *declares* what it needs; the adapters *provide* it.

---

## Account hierarchy

`Account` is a `sealed abstract class` that permits exactly three concrete subtypes: `CheckingAccount`, `SavingsAccount`, and `TimeDepositAccount`. All three share the same status state machine (`ACTIVE → FROZEN → ACTIVE`, `ACTIVE|FROZEN → CLOSED`), the same `transferIn` implementation, and the same `requireActive()` guard — but each overrides `deposit`, `withdraw`, and `transferOut` with its own rules.

The state machine itself is implemented with the **State pattern**: `AccountState` is a sealed interface with three stateless singleton implementations — `ActiveState`, `FrozenState`, `ClosedState` — each owning its own valid transitions (`freeze`/`unfreeze`/`close`) and operability check. `Account` holds an `AccountState` reference and delegates: `freeze()` becomes `state = state.freeze()`, `requireActive()` becomes `state.requireOperable()`. Invalid transitions throw `IllegalStateException` with the same messages the previous enum-based code used. The `AccountStatus` enum is preserved at the persistence and REST boundaries (`AccountState.of(status)` converts at construction time, `state.status()` returns the enum on reads), so the JPA mapper and REST response shapes are unchanged.

**CheckingAccount** carries a single extra field: `overdraftLimit` (a `Money` value). `withdraw` and `transferOut` allow the balance to go negative, but only down to `-overdraftLimit`. A zero overdraft limit means standard no-overdraft behavior. No extra lifecycle operations.

**SavingsAccount** carries `annualInterestRate` (a `BigDecimal`) and `lastAccrualDate` (a `LocalDate`). `withdraw` and `transferOut` reject any amount that would take the balance negative. The extra operation `accrueInterest(YearMonth)` computes monthly interest (`balance × rate / 12`), credits it as an `INTEREST` transaction, and records the end-of-month date in `lastAccrualDate`. Accrual works on ACTIVE and FROZEN accounts (it is an admin-triggered bookkeeping step, not a customer-initiated mutation), but is rejected on CLOSED accounts. Double-accrual for the same month is rejected.

**TimeDepositAccount** carries `principal`, `openDate`, `maturityDate`, `annualInterestRate`, and a `matured` flag. At open, the principal is placed as the opening balance. `deposit` is always rejected ("principal is locked"). `withdraw` and `transferOut` are rejected until `matured` is true. The extra operation `mature(LocalDate)` validates that the given date is on or after the maturity date, then credits `principal × annualInterestRate` as an `INTEREST` transaction and sets the `matured` flag — enabling withdrawals. Maturation works on ACTIVE and FROZEN accounts but not CLOSED ones. Double maturation is rejected.

This hierarchy enriches the `domain/model/` layer only. No new layers are introduced. The persistence adapter handles the three subtypes via a discriminator column (`account_type`) on the existing `accounts` table, and the mapper reconstructs the correct subtype when loading.

---

### 2. Application Layer (`application/`)
**Orchestration only** — no business rules live here. Application services:
- Implement the inbound use-case interfaces
- Call outbound ports (repositories, password hasher)
- Manage transactions (`@Transactional`)
- Translate domain exceptions into typed application exceptions

Example flow for `changePassword`:
```
Controller → ChangePasswordUseCase.Command
           → CustomerApplicationService
               → PasswordValidationService.validate()      (format check)
               → PasswordHasherPort.matches()              (reuse check vs last 3)
               → PasswordHasherPort.hash()                 (hash new password)
               → Customer.changePassword()                 (domain entity updates history)
               → CustomerRepositoryPort.save()             (persist)
```

---

### 3. Adapters — Inbound (`adapter/in/web/`)
REST controllers that translate HTTP ↔ use-case commands:
- `AdminController` → `/api/admin/**` — create/delete/list customers, set transfer fee, freeze/unfreeze/close accounts
- `CustomerController` → `/api/customers/**` — change password
- `AccountController` → `/api/accounts/**` — open account, deposit, withdraw, transfer, balance, history
- `GlobalExceptionHandler` — maps domain/application exceptions to RFC 7807 `ProblemDetail` responses

---

### 4. Adapters — Outbound (`adapter/out/`)
- **Persistence**: JPA entities (`*JpaEntity`) are pure DTOs — only these cross the persistence boundary. Mappers convert between domain models and JPA entities. Adapters implement the outbound ports.
- **Security**: `BCryptPasswordHasherAdapter` implements `PasswordHasherPort` using Spring Security's `BCryptPasswordEncoder`.

---

### 5. Config (`config/`)
- `SecurityConfig` — HTTP Basic Auth, role-based access (`ADMIN` vs `CUSTOMER`), and `@Bean` declarations for the two domain services (so they can be injected without making the domain Spring-aware)
- `BankUserDetailsService` — loads users from the `customers` table for Spring Security
- `AdminDataInitializer` — seeds the admin on first startup with a properly BCrypt-hashed password

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| Records for value objects | Immutability and structural equality for free |
| No setters on entities | Forces all state changes through meaningful business methods |
| Domain services instantiated as `@Bean` in config | Keeps domain package annotation-free |
| Password reuse check in application service | BCrypt comparison requires the hasher port, which cannot be in the domain |
| JPA entities separate from domain entities | Domain model can evolve without breaking the DB schema and vice versa |
| Nested `Command` records in use-case interfaces | Self-documenting, no separate command classes needed |
| `AccountStatus` state machine in `Account` entity | Status transitions and operation guards live in the domain where the rules belong; the application service only translates `IllegalStateException` → `AccountNotOperableException` |
| State pattern (`AccountState` + 3 singletons) for status | Replaces a chain of `if (status == ...)` conditionals with polymorphic dispatch; each state class owns the rules for its own transitions, so adding a future state requires no changes to existing states or to `Account` |
| `CustomerTier` policy data on the enum, enforcement in `TransferDomainService` | Tier multipliers and per-transaction caps live on `CustomerTier` itself (one place to add a new tier). `Account` knows nothing about tiers — `AccountApplicationService` fetches the source `Customer` and passes the tier to the domain service for fee calculation and limit enforcement |
