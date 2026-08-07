# Refactorings

Claude Opus 5 (1M context) — created 2026-08-07

A log of significant refactorings applied to Ayvalık Bank HA-1. Each entry records what the code
looked like before, what it looks like after, and — most importantly — *why* the change was worth
making. New entries are appended.

For further enquiry please contact Akin Kaldiroglu at akin@kaldiroglu.dev

---

## Entry 1 — `TransactionAmount`: making impossible states unrepresentable

**Date:** 2026-08-07
**Branch:** `refactor/transaction-amount`
**Baseline commit:** `bed1ebb` — all "before" line numbers below refer to this commit
**Principle:** make illegal states unrepresentable

### The symptom

The same guard appeared seven times across the three account subclasses:

```java
if (amount.isNegative())
    throw new IllegalArgumentException("Withdrawal amount cannot be negative");
```

| File | Lines (before) |
|---|---|
| `CheckingAccount.java` | 62, 72, 89 |
| `SavingsAccount.java` | 72, 82, 94 |
| `TimeDepositAccount.java` | 134 |

Once per money-moving method, per account type. Adding a fourth account product would have meant
writing it three more times, and forgetting it in one place would have been silent.

The count is verifiable against the baseline commit:

```bash
for f in CheckingAccount SavingsAccount TimeDepositAccount; do
  git show bed1ebb:src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/account/$f.java \
    | grep -c "amount.isNegative()"
done | paste -sd+ - | bc      # => 7
```

And that none remain:

```bash
grep -rn "amount.isNegative()" src/main/java      # => no matches
```

### The obvious fix, and why it is wrong

The instinct is to push the check down into `Money` — validate once in the constructor and let every
caller stop worrying. **That cannot work here, and the reason is the interesting part.**

`Money` must be allowed to be negative. Three pieces of evidence, all from the code as it stood:

1. `CheckingAccount.java:75` — `Money lowerBound = overdraftLimit.negate();` deliberately constructs
   a negative `Money` to express the overdraft floor.
2. `CheckingAccount.java:81` — `this.balance = projected;` assigns a balance that may legitimately be
   as low as `-overdraftLimit`. A customer $500 into their overdraft *has* a balance of `-500.00`.
3. `MoneyTest.java:22, 54, 69` — three existing tests assert `isNegative()` returns `true`.

Validating in `Money`'s constructor would delete overdraft support from the bank and break three
tests. The constraint is real, but it does not belong to `Money`.

### The diagnosis

`Money` was carrying two different concepts under one name:

| Concept | Signed? | Example |
|---|---|---|
| A **balance** — a position | Yes | `-500.00` means the customer owes the bank |
| An **amount** — a magnitude to move | No | "withdraw `-500.00`" is meaningless |

Direction is already carried by *which method you call* — `deposit` versus `withdraw`. A signed
amount is therefore not just invalid, it is incoherent.

A single type serving both concepts can enforce **neither** constraint. That is why the guard had to
be written by hand at every call site: the type could not be trusted, so every method re-established
the invariant itself. The duplication was the symptom; the conflated type was the disease.

### The change

A second value object, holding the constraint the first one cannot:

```java
public record TransactionAmount(Money value) {
    public TransactionAmount {
        if (value == null)
            throw new IllegalArgumentException("Transaction amount must not be null");
        if (value.amount().signum() <= 0)
            throw new IllegalArgumentException("Transaction amount must be positive, was " + value);
    }
    public Money asMoney()     { return value; }
    public Currency currency() { return value.currency(); }
}
```

It **wraps** `Money` rather than re-implementing it, so all arithmetic and the 2-decimal `HALF_UP`
scaling stay in exactly one place. Note the resulting ordering: `Money` scales first, so `0.001`
becomes `0.00` and is *then* rejected — a composition that comes for free from wrapping.

**Zero is rejected as well as negative.** Before this change a zero-value transfer succeeded and
wrote two ledger rows recording no movement of money.

### Before and after

`CheckingAccount.withdraw`, the worked example:

**Before**

```java
public Transaction withdraw(Money amount) {
    requireActive();
    requireSameCurrency(amount);
    if (amount.isNegative())
        throw new IllegalArgumentException("Withdrawal amount cannot be negative");
    Money projected = this.balance.subtract(amount);
    Money lowerBound = overdraftLimit.negate();
    if (projected.amount().compareTo(lowerBound.amount()) < 0) {
        if (overdraftLimit.isZero())
            throw new IllegalArgumentException("Insufficient funds");
        throw new IllegalArgumentException("Withdrawal exceeds overdraft limit");
    }
    this.balance = projected;
    return Transaction.create(this.id, TransactionType.WITHDRAWAL, amount, "Withdrawal");
}
```

**After**

```java
public Transaction withdraw(TransactionAmount amount) {
    requireActive();
    requireSameCurrency(amount);
    Money projected = this.balance.subtract(amount.asMoney());
    Money lowerBound = overdraftLimit.negate();
    if (projected.amount().compareTo(lowerBound.amount()) < 0) {
        if (overdraftLimit.isZero())
            throw new InsufficientBalanceException("Insufficient funds");
        throw new InsufficientBalanceException("Withdrawal exceeds overdraft limit");
    }
    this.balance = projected;
    return Transaction.create(this.id, TransactionType.WITHDRAWAL, amount.asMoney(), "Withdrawal");
}
```

Two changes are visible: the guard is gone (the parameter type now carries it), and the two
`IllegalArgumentException` throws became `InsufficientBalanceException` — see *Defect 2* below.

### Why this is "making impossible states unrepresentable"

Before, a negative amount was **representable but rejected**. The object could be built; every
method that received one had to remember to check. Correctness depended on discipline repeated
seven times.

After, a negative amount is **unrepresentable**. `TransactionAmount.of(-50.0, USD)` throws at
construction — the value never comes into existence, so no downstream method can receive it, so no
downstream method needs to check.

The check moved from *runtime, repeated per call site* to *the type, verified once*. Two concrete
consequences:

- **The compiler enumerates the work.** Changing the parameter type produced 104 compile errors
  across 52 call sites, each with an exact file, line and column. Nothing could be missed by
  oversight. A shared helper method — the alternative design considered — would have compiled
  silently and left it to the test suite to discover which call sites were still wrong.
- **New code cannot regress.** A fourth account product added next year physically cannot accept a
  negative amount; there is no guard for its author to forget.

### Where the type stops, and why

`TransactionAmount` models **a request to move money**, not **a record of money having moved**.
Three things deliberately keep using `Money`:

| Stays `Money` | Because zero or negative is legal |
|---|---|
| `Account.balance` | Negative is a real overdraft position |
| The `fee` argument of `transferOut` | `TransferDomainService.calculateFee` returns zero for same-customer transfers |
| `Transaction.amount` | `SavingsAccount.accrueInterest` credits `0.00` interest on a zero balance and still writes an `INTEREST` row |

That third case is the one that decides the boundary. Had `Transaction` been changed to hold a
`TransactionAmount`, a legal zero-interest accrual would have started throwing.

The split is visible in a single call — one argument constrained, the other not:

```java
account.transferOut(TransactionAmount.of(200.0, USD), Money.of(2.0, USD), "target-id");
//                  ^ amount: must be positive        ^ fee: may be zero
```

**The payoff of drawing the line there: the persistence layer needed no changes at all.** No JPA
entity, no mapper, no repository adapter, no response DTO. A value object's authority ends where its
invariant stops being true, and respecting that kept the blast radius inside the domain.

### Two defects found along the way

Both were discovered while verifying the claims above, not by looking for bugs.

**Defect 1 — a disabled guard.** `CheckingAccount.deposit` had `requireSameCurrency(amount)`
commented out, while `SavingsAccount` and `TimeDepositAccount` both called it. A mismatched-currency
deposit still failed — but two frames later, inside `Money.add`, reporting `Money`'s message
(`"Currency mismatch: USD vs EUR"`) instead of the account's. The invariant held by luck rather than
by design, and `AccountTest.shouldRejectDepositWithWrongCurrency` failed as a result.

**Defect 2 — insufficient funds returned 400 instead of 422.** The lesson here is about testing, not
about banking.

`GlobalExceptionHandler` mapped `InsufficientFundsException` to 422, but **no production code ever
threw it**. The real paths threw `IllegalArgumentException`, which `AccountApplicationService` does
not catch, so it fell through to the catch-all handler and surfaced as **400 Bad Request**.

Two tests were green over this:

- `AccountApplicationServiceTest` asserted `IllegalArgumentException` — the type that yields **400**.
- `AccountControllerTest` mocked `InsufficientFundsException` — the type that yields **422**.

Neither test was wrong on its own. Each accurately described its own layer. But **together they
asserted an end-to-end path the application could not produce**, because nothing converted the first
into the second. Each layer was tested against its own assumption about its neighbor rather than
against the neighbor's actual output, and the gap sat exactly in between where neither test looked.

The fix introduces a domain `InsufficientBalanceException extends IllegalStateException`, thrown at
all five shortfall sites and translated by the application service:

```java
try {
    tx = account.withdraw(command.amount());
} catch (InsufficientBalanceException e) {
    throw new InsufficientFundsException(e.getMessage());
} catch (IllegalStateException e) {
    throw new InvalidAccountOperationException(e.getMessage());
}
```

It extends `IllegalStateException` rather than `IllegalArgumentException` because a shortfall is a
property of the account's *state*, not a defect in the argument: the requested amount is perfectly
well-formed, there is simply not enough money behind it. That distinction is precisely what
separates HTTP 422 from HTTP 400.

The catch clauses must be ordered specific-first — and **Java enforces this**: a subclass catch
placed after its superclass is a compile error, so the compiler prevents this translation from
silently regressing.

It is named `InsufficientBalanceException` rather than `InsufficientFundsException` so that the
domain type and the application type do not collide; two identically-named classes in different
layers make every import ambiguous.

### Scope

| | |
|---|---|
| Files added | `TransactionAmount.java`, `InsufficientBalanceException.java`, `TransactionAmountTest.java` |
| Domain files changed | `Account`, `CheckingAccount`, `SavingsAccount`, `TimeDepositAccount`, `TransferDomainService` |
| Ports changed | `DepositMoneyUseCase`, `WithdrawMoneyUseCase`, `TransferMoneyUseCase` (`Command` records) |
| Application changed | `AccountApplicationService` — exception translation only; the pass-through of `command.amount()` needed no edit |
| Adapters changed | `AccountController` — the single place a `TransactionAmount` is constructed |
| **Persistence changed** | **None** |
| Guards removed | 7 |
| Guards added | 1 (a record constructor) |
| Tests | 176 → 184, all passing |

### Behavior changes

1. **Zero-value deposits, withdrawals and transfers are now rejected.** Over REST this changes
   nothing observable — `MoneyOperationRequest` and `TransferRequest` already carry
   `@NotNull @Positive`, so a non-positive amount was already a 400. The change is visible only to
   direct domain and application-service callers.
2. **Insufficient funds now returns 422 instead of 400**, including the overdraft-limit-exceeded case.

Point 1 is worth dwelling on: the adapter was *already* validating. The domain guards were not
redundant with it — they existed because the domain cannot trust that an adapter validated. A second
inbound adapter (a batch job, a gRPC endpoint, a test) bypasses Bean Validation entirely.
`TransactionAmount` converts that hope into a guarantee that holds no matter who calls.

### Discussion questions

1. `Money` allows negatives and `TransactionAmount` does not. Which other value object in this
   codebase is carrying two concepts under one name?
2. The refactoring stopped before `Transaction.amount`. Argue the other side — what would it cost,
   and what would it buy?
3. Defect 2's two tests were each individually correct. What kind of test would have caught the gap
   between them?
