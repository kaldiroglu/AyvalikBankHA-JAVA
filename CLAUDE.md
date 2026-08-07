# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Ayvalık Bank HA-1** — a hexagonal-architecture banking application in Java 25 / Spring Boot 3.4.

## Commands

```bash
# Start local PostgreSQL
docker compose up -d

# Build & test
mvn clean verify

# Run a single test class
mvn test -Dtest=CustomerApplicationServiceTest

# Run the application
mvn spring-boot:run
```

## Architecture

The project strictly enforces the Dependency Rule: all dependencies point inward toward the domain.

```
adapter/in/web/                → REST controllers + request/response DTOs
adapter/out/persistence/       → JPA entities (DTOs), Spring Data repos, mappers, adapters
adapter/out/security/          → BCryptPasswordHasherAdapter
application/port/in/account/   → CustomerAccountPort, AccountAdministrationPort, BankSettingsPort (driving)
application/port/in/customer/  → CustomerAdministrationPort, CustomerSelfServicePort (driving)
application/service/           → CustomerApplicationService, AccountApplicationService
config/                        → SecurityConfig (Spring beans), BankUserDetailsService, BankUserPrincipal
domain/model/account/          → Account hierarchy (sealed) + AccountState pattern + Money, Currency, Transaction, AccountId/Type/Status
domain/model/customer/         → Customer, CustomerId, Password
domain/service/account/        → TransferDomainService
domain/service/customer/       → PasswordValidationService
domain/port/out/account/       → AccountRepositoryPort, TransactionRepositoryPort, SettingsRepositoryPort (driven)
domain/port/out/customer/      → CustomerRepositoryPort, PasswordHasherPort (driven)
```

Each layer is split per aggregate (`account` vs `customer`) — model, service, and ports in both directions — so each aggregate's vocabulary is locally complete. Note the layers differ by direction: driving ports sit in `application/`, driven ports in `domain/`.

**Domain layer has zero Spring/JPA imports.** Domain services are instantiated as `@Bean` in `SecurityConfig` so they can be injected without becoming Spring components.

## Key Design Decisions

- **Value objects as records**: `CustomerId`, `AccountId`, `TransactionId`, `Money`, `TransactionAmount`, `Password` — immutable, self-validating.
- **Optimistic locking**: `AccountJpaEntity` carries a Hibernate-managed `@Version`; the domain `Account` deliberately does not — a version is a claim about a database revision, not a banking concept. `AccountPersistenceAdapter.save` therefore copies onto the **managed** entity from the persistence context rather than merging a freshly built detached one, which is the only way the loaded version survives. A conflict surfaces at commit, after the service method has returned, so `GlobalExceptionHandler` maps `OptimisticLockingFailureException` → HTTP 409 with a fixed message. See `Refactorings.md` entry 5.
- **Domain refusal vocabulary**: the account domain refuses through a sealed `AccountRuleViolation` (extending `IllegalStateException`) with four `final` subtypes — `AccountNotActiveException`, `InsufficientBalanceException`, `OperationNotPermittedException`, `TransactionLimitExceededException`. `AccountApplicationService.translate` maps them to the application exceptions via an exhaustive switch with **no `default`**, so a fifth refusal type breaks the build until handled. Catching `AccountRuleViolation` rather than `IllegalStateException` means a JDK or framework exception surfaces as a 500 defect rather than a fake 422. See `Refactorings.md` entry 4.
- **Ownership authorization**: every customer-facing `Command` carries `CustomerId callerId`, supplied by `BankUserPrincipal` through `@AuthenticationPrincipal`. `Account.isOwnedBy` is the domain **fact**; the application services enforce the **policy** and throw `UnauthorizedAccessException` → HTTP 403. Transfers check the **source only** — the target is deliberately unchecked, since sending money to another customer is the point. Opening an account takes no `ownerId`: the caller is the owner. See `Refactorings.md` entry 3.
- **Actor-shaped ports**: driving ports are grouped by *actor × subject*, not one per method — `CustomerAccountPort` (9 methods), `AccountAdministrationPort` (5), `CustomerAdministrationPort` (4), `CustomerSelfServicePort` (1), `BankSettingsPort` (1). A port is one conversation with one kind of outside actor (Cockburn), which is why `AccountController` takes a single constructor parameter. See `Refactorings.md` entry 2.
- **Port placement is deliberately asymmetric**: the domain declares the interfaces it *requires* (driven ports, `domain/port/out/`); the application declares the operations it *offers* (driving ports, `application/port/in/`). A `Command` record is a request shape, not a domain concept.
- **`TransactionAmount` vs `Money`**: `Money` is signed — a negative balance is a real overdraft position, and `Money.negate()` builds the overdraft floor — so `Money` cannot enforce positivity. `TransactionAmount` wraps `Money` and is **strictly positive by construction** (zero is rejected too). It types the *command* surface: `deposit` / `withdraw` / `transferOut` / `transferIn` and the `Command` records of the deposit, withdraw and transfer use cases. Fees, balances and `Transaction.amount` keep using `Money`, because zero is legal for all three — which is why this change never reached the persistence layer. `AccountController` is the only place a `TransactionAmount` is constructed. See `Refactorings.md` entry 1.
- **Sealed account hierarchy**: `Account` is a `sealed abstract class` permitting `CheckingAccount`, `SavingsAccount`, and `TimeDepositAccount`. Each subtype overrides `deposit`/`withdraw`/`transferOut` with its own rules: `CheckingAccount` allows configurable overdraft; `SavingsAccount` rejects overdraft and supports monthly interest accrual via `accrueInterest(YearMonth)`; `TimeDepositAccount` locks principal at open and rejects withdrawals until `mature(LocalDate)` is called on or after the maturity date.
- **Three account types**: `CheckingAccount` carries an `overdraftLimit` — withdrawals may take the balance negative up to that limit. `SavingsAccount` carries an `annualInterestRate` and a `lastAccrualDate`; `accrueInterest` credits monthly interest and works on ACTIVE or FROZEN accounts (but not CLOSED). `TimeDepositAccount` locks the principal at open; `deposit` is rejected; `mature` must be called on or after the maturity date and credits the annual interest; withdrawals are then permitted.
- **Password history**: `Customer` holds `currentPassword` + up to 3 previous hashes. Reuse checking (BCrypt) lives in `CustomerApplicationService` since it requires the `PasswordHasherPort`.
- **Transfer fee**: free for same-customer transfers; `TransferDomainService.calculateFee()` applies the admin-configured percentage for cross-customer transfers, scaled by the **source customer's tier multiplier** (STANDARD=1.0×, PREMIUM=0.5×, PRIVATE=0.0×). Fee stored in `settings` table.
- **Customer tiers**: `CustomerTier` enum (`STANDARD`, `PREMIUM`, `PRIVATE`) on `Customer`. Each tier carries a fee multiplier and per-transaction caps for transfers/withdrawals (PRIVATE = unlimited). `TransferDomainService.requireTransferWithinLimit` / `requireWithdrawalWithinLimit` enforce caps; the application service wraps any violation as `LimitExceededException` → HTTP 422. Admin promotes/demotes via `PUT /api/admin/customers/{id}/tier`.
- **Account status (State pattern)**: `Account` holds an `AccountState` — a sealed interface with three stateless singleton implementations (`ActiveState`, `FrozenState`, `ClosedState`). Each state owns its valid transitions and its `requireOperable()` check; `Account.freeze()`/`unfreeze()`/`close()` are one-line delegations. The `AccountStatus` enum (`ACTIVE`, `FROZEN`, `CLOSED`) is preserved as the boundary type for persistence and REST, with `AccountState.of(status)` converting at construction time. Invalid transitions throw `IllegalStateException`; the application service converts this to `AccountNotOperableException` → HTTP 422.
- **Authentication**: HTTP Basic Auth via Spring Security. Credentials loaded from the `customers` table by `BankUserDetailsService`. Roles: `ADMIN`, `CUSTOMER`.
- **JPA DTOs**: `CustomerJpaEntity`, `AccountJpaEntity`, `TransactionJpaEntity`, `SettingsJpaEntity`, `PasswordHistoryJpaEntity` — none of these cross the persistence adapter boundary.

## REST API Summary

| Method | Path | Role | Purpose |
|--------|------|------|---------|
| POST | `/api/admin/customers` | ADMIN | Create customer |
| DELETE | `/api/admin/customers/{id}` | ADMIN | Delete customer |
| GET | `/api/admin/customers` | ADMIN | List all customers |
| PUT | `/api/admin/customers/{id}/tier` | ADMIN | Change customer tier (STANDARD / PREMIUM / PRIVATE) |
| PUT | `/api/admin/settings/transfer-fee` | ADMIN | Set transfer fee % |
| PUT | `/api/admin/accounts/{id}/freeze` | ADMIN | Freeze account |
| PUT | `/api/admin/accounts/{id}/unfreeze` | ADMIN | Unfreeze account |
| PUT | `/api/admin/accounts/{id}/close` | ADMIN | Close account (terminal) |
| PUT | `/api/admin/accounts/{id}/accrue-interest` | ADMIN | Credit monthly interest to a savings account |
| PUT | `/api/admin/accounts/{id}/mature` | ADMIN | Mature a time deposit and credit accrued interest |
| PUT | `/api/customers/{id}/password` | CUSTOMER | Change password |
| POST | `/api/accounts/checking` | CUSTOMER | Open checking account for the authenticated caller (with optional overdraft) |
| POST | `/api/accounts/savings` | CUSTOMER | Open savings account for the authenticated caller (with annual interest rate) |
| POST | `/api/accounts/time-deposit` | CUSTOMER | Open time deposit for the authenticated caller (principal locked until maturity) |
| GET | `/api/customers/{id}/accounts` | CUSTOMER | List accounts |
| GET | `/api/accounts/{id}/balance` | CUSTOMER | Get balance |
| POST | `/api/accounts/{id}/deposit` | CUSTOMER | Deposit |
| POST | `/api/accounts/{id}/withdraw` | CUSTOMER | Withdraw |
| POST | `/api/accounts/{id}/transfer` | CUSTOMER | Transfer to another account |
| GET | `/api/accounts/{id}/transactions` | CUSTOMER | Transaction history |

## Default Admin

Email: `admin@ayvalikbank.dev` / Password: `Admin@123!` (seeded by `data.sql`)
