# TransactionAmount — Design

Claude Opus 5 (1M context) — created 2026-08-07

## Problem

`Money` serves two different concepts under one name:

- a **balance** — a signed position; negative is a real, representable banking state (overdraft)
- an **amount to move** — a magnitude; negative is meaningless, because direction is already
  encoded by which method was called (`deposit` vs `withdraw`)

Because one type serves both, the type cannot enforce either constraint. Every method that
accepts an amount re-asserts the rule by hand. The guard `if (amount.isNegative()) throw …`
appears at **7 sites**:

| File | Lines |
|---|---|
| `CheckingAccount.java` | 62, 72, 89 |
| `SavingsAccount.java` | 72, 82, 94 |
| `TimeDepositAccount.java` | 134 |

(`CheckingAccount.java:38` is excluded — "an overdraft *limit* must be non-negative" is a
different rule about a different concept, and belongs where it is.)

The rejected fix is to forbid negatives in `Money`'s constructor. That cannot work here:

- `CheckingAccount.java:75` — `overdraftLimit.negate()` deliberately builds a negative `Money`
- `CheckingAccount.java:81` — assigns a balance down to `-overdraftLimit`
- `MoneyTest.java:22, 54, 69` — assert `isNegative()` is `true`

`Money` must remain signed. The constraint belongs to a *different type*.

## Approach

Introduce `TransactionAmount`: a value object that is strictly positive by construction.
This is the "make impossible states unrepresentable" move — the guard stops being a runtime
check repeated at every call site and becomes a property of the type, checked once.

```java
public record TransactionAmount(Money value) {
    public TransactionAmount {
        if (value == null) throw new IllegalArgumentException("Transaction amount must not be null");
        if (value.amount().signum() <= 0)
            throw new IllegalArgumentException("Transaction amount must be positive, was " + value);
    }
    public static TransactionAmount of(BigDecimal amount, Currency currency) { … }
    public static TransactionAmount of(Money money) { return new TransactionAmount(money); }
    public Money asMoney()     { return value; }
    public Currency currency() { return value.currency(); }
}
```

It **wraps** `Money` rather than re-implementing it, so all arithmetic stays in one place and
`TransactionAmount` is a pure constraint-carrier. `Money` itself is not modified.

**Zero is rejected**, not merely negatives. A zero transfer currently succeeds and writes two
`Transaction` rows for no movement of money.

## Scope boundary — where the type stops

`TransactionAmount` models **a request to move money**, not **a record of money having moved**.
Three things therefore keep using `Money`:

| Stays `Money` | Reason |
|---|---|
| `Account.balance` | Signed by design — negative is a real overdraft position |
| `transferOut(…, Money fee, …)` | A fee is legitimately **zero** for same-customer transfers (`TransferDomainService.calculateFee:16-18`) |
| `Transaction.amount` | A zero-interest accrual is legal — `SavingsAccount.accrueInterest:123` computes `balance.multiply(monthlyRate)`, which is `0.00` on a zero balance, and still writes an `INTEREST` transaction at line 126 |

**Consequence: the persistence layer is not touched at all.** No JPA entity, no mapper, no
persistence adapter, no `TransactionResponse`. The refactoring stops at the domain's command
surface. That boundary is itself part of the lesson: a value object's authority ends where its
invariant stops being true.

## Files

### New

| Path | Purpose |
|---|---|
| `domain/model/account/TransactionAmount.java` | The value object |
| `domain/model/account/InsufficientBalanceException.java` | `extends IllegalStateException` |
| `src/test/…/domain/model/account/TransactionAmountTest.java` | Constructor invariants |
| `Refactorings.md` | Project-level write-up (entry #1) |

### Modified — main

| Path | Change |
|---|---|
| `Account.java` | `deposit` / `withdraw` / `transferOut` / `transferIn` take `TransactionAmount` |
| `CheckingAccount.java` | Signatures; drop 3 guards; **uncomment `requireSameCurrency` (line 61)**; throw `InsufficientBalanceException` ×2 |
| `SavingsAccount.java` | Signatures; drop 3 guards; throw `InsufficientBalanceException` ×2 |
| `TimeDepositAccount.java` | Signatures; drop 1 guard; throw `InsufficientBalanceException` ×1 |
| `port/in/account/DepositMoneyUseCase.java` | `Command` record field type |
| `port/in/account/WithdrawMoneyUseCase.java` | `Command` record field type |
| `port/in/account/TransferMoneyUseCase.java` | `Command` record field type |
| `TransferDomainService.java` | 3 methods take `TransactionAmount` |
| `AccountApplicationService.java` | Translate `InsufficientBalanceException` → `InsufficientFundsException` |
| `AccountController.java` | The single place that constructs a `TransactionAmount` |

### Modified — tests

`AccountTest`, `CheckingAccountTest`, `SavingsAccountTest`, `TimeDepositAccountTest`,
`TransferDomainServiceTest`, `AccountApplicationServiceTest`, `AccountControllerTest`.

### Modified — docs

`CLAUDE.md` (key design decisions), `Domain.md`, `Tests.md` (per-class counts).

## Defect 1 — currency check disabled

`CheckingAccount.java:61` has `//requireSameCurrency(amount);` commented out. `SavingsAccount:71`
and `TimeDepositAccount:133` both call it. A mismatched-currency deposit into a checking account
still fails today — but by accident, inside `Money.add`, reporting
`"Currency mismatch: USD vs EUR"` instead of the account's own message. The invariant holds by
luck, not design.

Fix: uncomment it; add a test asserting the *account* rejects the mismatch.

## Defect 2 — insufficient funds returns 400, not 422

`GlobalExceptionHandler:24-27` maps `InsufficientFundsException` to **422**. No file under
`src/main` ever throws it. The real paths throw `IllegalArgumentException`:

- `CheckingAccount:78` `"Insufficient funds"`, `:96` `"Insufficient funds for transfer including fee"`
- `SavingsAccount:85` `"Insufficient funds"`, `:98` `"Insufficient funds for transfer including fee"`
- `TimeDepositAccount:137` `"Insufficient funds"`

`AccountApplicationService` catches only `IllegalStateException`, so these reach the catch-all at
`GlobalExceptionHandler:60` and return **400 Bad Request**.

Two tests were green over this gap, and neither was wrong on its own:

- `AccountApplicationServiceTest:249-252` asserted `IllegalArgumentException` — the type that
  yields **400**. It accurately described the application service's real behavior.
- `AccountControllerTest:255, 299` mocked `InsufficientFundsException` — the type that yields
  **422**. It accurately described the handler's mapping.

Together, however, they asserted an end-to-end path the application could not produce, because
nothing converted the first exception into the second. Each layer was tested against its own
assumption about its neighbor rather than against the neighbor's actual output, and the defect sat
exactly in between, where neither test looked.

Fix: a domain `InsufficientBalanceException extends IllegalStateException`; all five sites throw
it; `AccountApplicationService` translates it to the existing application `InsufficientFundsException`.

The two catch clauses must be ordered specific-first:

```java
catch (InsufficientBalanceException e) { throw new InsufficientFundsException(e.getMessage()); }
catch (IllegalStateException e)        { throw new InvalidAccountOperationException(e.getMessage()); }
```

Java enforces this — a subclass catch placed after its superclass is a compile error, so the
compiler prevents the translation from silently regressing.

**Naming**: the domain exception is `InsufficientBalanceException` and the application one stays
`InsufficientFundsException`. Two identically-named classes across layers would make every import
ambiguous, which is a poor look in teaching material.

## Behavior changes

1. Zero-value deposit / withdrawal / transfer is now rejected. Over REST this changes nothing —
   `MoneyOperationRequest` and `TransferRequest` already carry `@NotNull @Positive`, so a
   non-positive amount is already a 400. The change is observable only to direct domain and
   application-service callers, i.e. the tests.
2. Insufficient funds (and overdraft-limit breach) now returns **422** instead of **400**.

## Testing

- `TransactionAmountTest` — pure JUnit: rejects null, zero, negative; accepts positive;
  preserves currency; 2-decimal scaling inherited from `Money`.
- `CheckingAccountTest` — add the mismatched-currency deposit case (Defect 1).
- Controller tests — replace the two `doThrow(new InsufficientFundsException(…))` mocks with
  cases that drive a real overdrawn account and assert 422.
- Existing account tests — update to the new signatures; any that pass a zero amount must be
  re-expressed, since zero is no longer constructible.
- `mvn clean verify` must pass before the work is reported complete.

## Documentation deliverable

`Refactorings.md` at project root, entry #1, containing:

- the before/after code, shown side by side
- why `Money` had to stay signed (the overdraft evidence above)
- the "make impossible states unrepresentable" rationale
- why the type stops before `Transaction.amount` and the fee
- the 7-guards-to-1-constructor count
- both defects, and what the mocked test hid
