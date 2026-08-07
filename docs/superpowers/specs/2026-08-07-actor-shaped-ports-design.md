# Actor-Shaped Driving Ports — Design

Claude Opus 5 (1M context) — created 2026-08-07

## Problem

Two separate issues, one cause.

**1. Twenty single-method driving ports.** `domain/port/in/` holds 20 interfaces of 7–15 lines
each, one per use case. The consequences show up at the consumers:

| Consumer | Constructor parameters |
|---|---|
| `AccountController` | 9 |
| `AdminController` | 10 |
| `AccountApplicationService` (`implements`) | 14 |
| `CustomerApplicationService` (`implements`) | 5 |

Controller tests carry 10, 11 and 2 mock declarations respectively.

**2. The grouping has no organizing principle.** It is by aggregate in some places and by
whoever happens to call it in others:

- `AccountApplicationService` implements both `DepositMoneyUseCase` (a customer operation) and
  `FreezeAccountUseCase` (an admin operation).
- `SetTransferFeeUseCase` — a bank-wide settings concern with nothing to do with customers — is
  implemented by `CustomerApplicationService` (line 88), purely because an admin invokes it.
  `CustomerApplicationService` injects `SettingsRepositoryPort` (line 33) **solely** to serve it.

**3. Driving ports live under `domain/`.** `domain/port/in/` implies the domain knows about use
cases, which is what the layering is meant to prevent. A `Command` record carrying
`CustomerId ownerId` and `Currency currency` is an application-shaped DTO, not a domain concept.

## Approach

Apply Cockburn's actual definition: **a port is one conversation with one kind of outside actor** —
not one method, and not one aggregate.

This system has two driving actors (Customer, Admin) talking about three subjects, which yields
five ports:

| Port | Actor × subject | Methods | Consumer |
|---|---|---|---|
| `CustomerAccountPort` | customer × accounts | `openChecking`, `openSavings`, `openTimeDeposit`, `deposit`, `withdraw`, `transfer`, `getBalance`, `listAccounts`, `getTransactions` | `AccountController` |
| `AccountAdministrationPort` | admin × accounts | `freezeAccount`, `unfreezeAccount`, `closeAccount`, `accrueInterest`, `mature` | `AdminController` |
| `CustomerAdministrationPort` | admin × customers | `createCustomer`, `deleteCustomer`, `listCustomers`, `changeCustomerTier` | `AdminController` |
| `CustomerSelfServicePort` | customer × self | `changePassword` | `CustomerController` |
| `BankSettingsPort` | admin × bank config | `setTransferFee` | `AdminController` |

**20 ports → 5.** `AccountController` goes 9 constructor parameters → 1; `AdminController` 10 → 3.

### Placement

- **Driving ports move** from `domain/port/in/` to `application/port/in/`. A use case is an
  application concern.
- **Driven ports stay** at `domain/port/out/`. The domain declares the interfaces it requires, and
  adapters implement them — that is dependency inversion, and it is why the two directions are
  *not* symmetrical.

The asymmetry is the teachable point, and it is what Cockburn and Vernon describe. (Hombergs' *Get
Your Hands Dirty on Clean Architecture* puts both under `application/`; that alternative was
considered and rejected because it weakens the argument that the domain declares its own
requirements.)

Sub-packages mirror the existing per-aggregate split:

```
application/port/in/account/CustomerAccountPort.java
application/port/in/account/AccountAdministrationPort.java
application/port/in/account/BankSettingsPort.java
application/port/in/customer/CustomerAdministrationPort.java
application/port/in/customer/CustomerSelfServicePort.java
```

`BankSettingsPort` is placed under `account/` rather than in a new `settings/` package, to stay
consistent with `SettingsRepositoryPort`, which already lives at `domain/port/out/account/`.

### Command records

Stay nested inside their port interface, as today — `CustomerAccountPort.DepositCommand`. Because
several commands now share one interface, the nested records need disambiguating names:
`DepositCommand`, `WithdrawCommand`, `TransferCommand`, `OpenCheckingCommand`, `OpenSavingsCommand`,
`OpenTimeDepositCommand`, `AccrueInterestCommand`, `MatureCommand`, `CreateCustomerCommand`,
`ChangePasswordCommand`, `ChangeCustomerTierCommand`, `SetTransferFeeCommand`.

The 20 methods across the five ports account for exactly the 20 public service methods that exist
today — verified against the two service classes; no method is dropped, added or renamed.

Methods taking only an identifier (`freezeAccount(AccountId)`, `deleteCustomer(CustomerId)`,
`getBalance(AccountId)`) keep their bare-parameter signatures — no command record is invented for them.

### Service allocation

| Service | Implements | Change |
|---|---|---|
| `AccountApplicationService` | `CustomerAccountPort`, `AccountAdministrationPort`, `BankSettingsPort` | gains `setTransferFee` |
| `CustomerApplicationService` | `CustomerAdministrationPort`, `CustomerSelfServicePort` | loses `setTransferFee` **and** its `SettingsRepositoryPort` dependency |

`AccountApplicationService` already injects `SettingsRepositoryPort` to read the fee during
transfers, so `setTransferFee` lands beside the only other user of that port.

The two service classes are **not** split further in this piece of work. Splitting them by actor is
a live option, but it belongs to the separate application-service slimming item so that the two
changes stay reviewable independently.

## Why this ordering matters

This lands before the ownership-authorization work, which will add a `CustomerId callerId` to every
customer-facing `Command`. Doing ports first means those records are written once rather than
rewritten. It also makes the authorization rule cleaner to state: the ports needing an ownership
check are exactly the two `Customer*` ones, so the requirement becomes a property of the port rather
than a per-method decision.

## Scope

| | |
|---|---|
| Ports deleted | 20 (all of `domain/port/in/`) |
| Ports created | 5 (under `application/port/in/`) |
| Source files importing `domain.port.in` | 30 — every one needs its import updated |
| Controller mock declarations | 23 → about 6 |
| Docs to update | `Architecture.md`, `CLAUDE.md`, `Enhancement.md` |
| Docs **not** to update | `docs/superpowers/plans/*` and the earlier specs — they are records of past decisions and must not be rewritten |

Persistence, the domain model, and `domain/port/out/` are untouched.

## Error handling

No change. Exception types, translation and HTTP mappings all stay as they are. The
application-service slimming item covers the 12 catch clauses.

## Testing

- No new behavior, so **no new tests** — this is a pure restructuring. The suite must still report
  **184 tests, 0 failures**, which is the primary correctness signal.
- Controller tests: the individual `@MockitoBean` use-case mocks collapse to one mock per port.
  Stubbing changes from `when(depositMoney.deposit(...))` to `when(customerAccount.deposit(...))`.
- `AccountApplicationServiceTest` and `CustomerApplicationServiceTest`: import and `Command` type
  names change; assertions do not.
- `CustomerApplicationServiceTest` must drop its `SettingsRepositoryPort` mock, and the
  `setTransferFee` test moves to `AccountApplicationServiceTest`.

## Risks

The 30-file import churn is mechanical but wide, and unlike the `TransactionAmount` refactoring the
compiler cannot drive it from type errors alone — a missing import is a "cannot find symbol", which
is still exhaustive but noisier. Renaming the `Command` records at the same time compounds this.

Mitigation: move and rename in one commit with no behavior change, and treat "184 tests still
passing" as the gate. If the churn proves unwieldy, the fallback is to land the port consolidation
first and the `domain/` → `application/` move as a second commit.

## Documentation deliverable

`Refactorings.md` entry 2, covering:

- the before/after port inventory and the consumer parameter counts
- Cockburn's port definition as the organizing principle, and why "one port per method" is not what
  Interface Segregation asks for
- the `SetTransferFeeUseCase` misplacement as evidence that the old grouping had no principle
- why driving and driven ports land in different layers — the asymmetry, stated as a rule
- the honest cost: multi-method interfaces are harder to fake by hand
