# Domain Refusal Vocabulary — Design

Claude Opus 5 (1M context) — created 2026-08-07

## Problem

`AccountApplicationService` carries **12 catch clauses** across 262 lines. That is the symptom. The
cause is that the domain has one word for "no".

**17 raw `IllegalStateException` throw sites** in `domain/`, all meaning different things:

| File | Meaning |
|---|---|
| `ActiveState`, `FrozenState`, `ClosedState` (7 sites) | wrong lifecycle state |
| `SavingsAccount` (2), `TimeDepositAccount` (6) | product rule violated |
| `TransferDomainService` (2) | tier limit exceeded |

### Consequence 1 — translation is positional, not semantic

The application layer recovers the meaning from *which line it wrapped*:

- `FrozenState.java:35` throws `"Account is frozen"` during a withdraw → caught in `withdraw` →
  `InvalidAccountOperationException`.
- `ClosedState.java:21` throws `"Cannot freeze a closed account"` → caught in `freezeAccount` →
  `AccountNotOperableException`.

Same JDK type, same category of failure, two different application exceptions, decided by position.
Both map to HTTP 422, so no client sees it — the defect is latent, not active.

### Consequence 2 — `catch (IllegalStateException)` catches a JDK type

This is the part worth fixing. `IllegalStateException` is thrown by JDK collections, Spring
internals, iterator misuse and ordinary bugs. Any of those arising inside those try blocks is
currently reported as a **422 "invalid account operation"** — a business-error response for what is
actually a defect. Monitoring will never flag it.

## Approach

Give the domain a refusal vocabulary, so translation becomes a lookup instead of a guess.

```java
public sealed abstract class AccountRuleViolation extends IllegalStateException
        permits AccountNotActiveException, InsufficientBalanceException,
                OperationNotPermittedException, TransactionLimitExceededException {
    protected AccountRuleViolation(String message) { super(message); }
}
```

### Why it extends `IllegalStateException`

Two reasons, and the second is what makes this change small.

1. **Consistency.** `InsufficientBalanceException extends IllegalStateException` already, established
   in `Refactorings.md` entry 1: a refusal is a property of state, not a defect in the argument.
2. **`catch (AccountRuleViolation)` still does not catch a plain `IllegalStateException`.** Catching
   the *subtype* is what delivers the robustness fix; the base can sit anywhere in the hierarchy. And
   because `AccountRuleViolation` **is-a** `IllegalStateException`, **all 25 existing
   `IllegalStateException.class` assertions in the domain tests keep passing untouched.**

That second point is worth stating plainly: extending `RuntimeException` instead would have forced 25
test edits for no additional benefit.

### Why `sealed`

The same reason `Account` is sealed. Adding a fifth kind of refusal requires editing `permits`
deliberately, and the translation switch below stops compiling until the new case is handled.

### The four subtypes

All live in `domain/model/account/` — Java requires permitted subtypes to share a package when there
is no `module-info.java`, and this project has none. `InsufficientBalanceException` is already there.

| Domain type | Replaces | Application type | HTTP |
|---|---|---|---|
| `AccountNotActiveException` | state-transition and `requireOperable` failures | `AccountNotOperableException` | 422 |
| `InsufficientBalanceException` *(exists)* | — | `InsufficientFundsException` | 422 |
| `OperationNotPermittedException` | product-rule failures | `InvalidAccountOperationException` | 422 |
| `TransactionLimitExceededException` | tier-cap failures | `LimitExceededException` | 422 |

Domain types are named for the **rule**; application types for the **response**. The names differ on
purpose, so no import is ambiguous.

**Each subtype must be declared `final`** — a sealed hierarchy requires every permitted subtype to be
`final`, `sealed` or `non-sealed`. The existing `InsufficientBalanceException` therefore changes from

```java
public class InsufficientBalanceException extends IllegalStateException
```

to

```java
public final class InsufficientBalanceException extends AccountRuleViolation
```

Its behavior is unchanged: it is still an `IllegalStateException` by inheritance, so entry 1's tests
and the application service's existing handling both still hold.

### Retyping the 17 sites

| Site | New type |
|---|---|
| `ActiveState:25`, `FrozenState:24,35`, `ClosedState:21,26,31,36` | `AccountNotActiveException` |
| `SavingsAccount:112` (accrue on closed), `TimeDepositAccount:168` (mature on closed) | `AccountNotActiveException` |
| `SavingsAccount:115` (already accrued) | `OperationNotPermittedException` |
| `TimeDepositAccount:119,132,149,170,172` (locked, not matured, no transfers, already matured, too early) | `OperationNotPermittedException` |
| `TransferDomainService:31,39` | `TransactionLimitExceededException` |

### Translation — one exhaustive switch

```java
private RuntimeException translate(AccountRuleViolation violation) {
    return switch (violation) {
        case AccountNotActiveException e         -> new AccountNotOperableException(e.getMessage());
        case InsufficientBalanceException e      -> new InsufficientFundsException(e.getMessage());
        case OperationNotPermittedException e    -> new InvalidAccountOperationException(e.getMessage());
        case TransactionLimitExceededException e -> new LimitExceededException(e.getMessage());
    };
}
```

No `default` clause — the hierarchy is sealed, so the compiler proves the switch total and a new
subtype breaks the build until it is handled. This is the same technique `AccountPersistenceMapper`
already uses over the sealed `Account` hierarchy.

Each method then carries **one** precise catch:

```java
    @Override
    public Transaction withdraw(WithdrawCommand command) {
        Account account = findAccountOrThrow(command.accountId());
        requireOwner(account, command.callerId());
        Customer owner = findCustomerOrThrow(account.getOwnerId());
        Transaction tx;
        try {
            transferDomainService.requireWithdrawalWithinLimit(command.amount(), owner.getTier());
            tx = account.withdraw(command.amount());
        } catch (AccountRuleViolation e) {
            throw translate(e);
        }
        accountRepository.save(account);
        return transactionRepository.save(tx);
    }
```

Note that the limit check and the withdrawal now share **one** try block. They needed two before only
because the same JDK type had to mean two different things depending on position.

**12 catch clauses → 8**, one per mutating method, each precise.

## What stays as it is

The `instanceof` checks in `accrueInterest` and `mature` — `"Account is not a savings account"`,
`"Account is not a time deposit"` — keep throwing `InvalidAccountOperationException` directly. They
are application-level dispatch on a runtime type, not domain refusals, and
`AccountApplicationServiceTest:340` and `:372` pin them.

## Behavior changes

1. **A withdraw, deposit or transfer on a frozen or closed account now yields
   `AccountNotOperableException`** instead of `InvalidAccountOperationException`. Both are HTTP 422,
   so no client is affected; the type and message simply become accurate. No existing test asserts
   the old type — verified — so this needs a **new** test to pin the corrected behavior.
2. **A stray JDK `IllegalStateException` inside a service method now propagates** and surfaces as
   HTTP 500 instead of a fake 422. This is the point of the change.

## Testing

- **No existing test changes.** All 25 domain assertions on `IllegalStateException.class` still hold,
  because `AccountRuleViolation` extends it. All 6 application-service assertions on the four
  application exception types still hold.
- **New:** `shouldReportAccountNotOperableWhenWithdrawingFromFrozenAccount` — pins behavior change 1.
- **New:** `shouldNotSwallowUnrelatedIllegalStateException` — a repository mock stubbed to throw a
  plain `IllegalStateException` must propagate it rather than convert it to a 422. This is the test
  that would have caught the original defect, and it is the most valuable one here.

Tightening the 25 domain assertions from `IllegalStateException.class` to the precise subtypes is a
worthwhile follow-up but is **not** in this change: those assertions state something still true, and
their `hasMessageContaining` clauses already discriminate. Bundling 25 edits into this commit would
bury the two that matter.

## Scope

| | |
|---|---|
| New | `AccountRuleViolation`, `AccountNotActiveException`, `OperationNotPermittedException`, `TransactionLimitExceededException` |
| Modified | `ActiveState`, `FrozenState`, `ClosedState`, `SavingsAccount`, `TimeDepositAccount`, `TransferDomainService`, `AccountApplicationService` |
| Untouched | persistence, controllers, ports, `GlobalExceptionHandler`, `CustomerApplicationService`, all four application exception classes |
| Catch clauses | 12 → 8 |
| Tests | 200 → 202 |

`GlobalExceptionHandler` needs no change: the application exception types and their HTTP mappings are
identical.

## Why this lands before optimistic locking

Optimistic locking introduces `OptimisticLockingFailureException` → HTTP 409. It needs a place in the
translation seam, and a seam that guesses from position is a bad place to add a case. Doing this
first means the locking change adds one mapping to a finished structure.

## Documentation deliverable

`Refactorings.md` entry 4: the 17-sites-one-word diagnosis, the positional-translation evidence, the
JDK-catching defect, why the base extends `IllegalStateException` (and what that saved), the
exhaustive-switch technique, and the two behavior changes.
