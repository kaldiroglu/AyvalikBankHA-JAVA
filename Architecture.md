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
The heart of the application. **No Spring, no JPA, no BCrypt** — plain Java only.

**Entities** (`domain/model/`): `Customer`, `Account`, `Transaction`

These are *rich* objects. `Account` owns its own invariants:
```java
account.deposit(money)      // validates currency, updates balance, returns Transaction
account.withdraw(money)     // validates sufficient funds
account.transferOut(...)    // deducts amount + fee
```

**Value Objects as Records** (`domain/model/`): `Money`, `Password`, `CustomerId`, `AccountId`, `TransactionId`

Immutable and self-validating. `Money.subtract()` throws if balance is insufficient. `Password` stores only BCrypt hashes.

**Domain Services** (`domain/service/`):
- `PasswordValidationService` — enforces 8-16 char, upper/lower/digit/special rules
- `TransferDomainService` — computes fees (0% same customer, configurable % cross-customer)

**Ports In** (`domain/port/in/`): Use-case interfaces such as `CreateCustomerUseCase`, `DepositMoneyUseCase`. Each carries a nested `Command` record for its input.

**Ports Out** (`domain/port/out/`): Repository interfaces (`CustomerRepositoryPort`, `AccountRepositoryPort`, etc.) and infrastructure interfaces (`PasswordHasherPort`, `SettingsRepositoryPort`). The domain *declares* what it needs; the adapters *provide* it.

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
- `AdminController` → `/api/admin/**` — create/delete/list customers, set transfer fee
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
