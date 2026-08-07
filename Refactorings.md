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

---

## Entry 2 — Actor-shaped ports: what a port actually is

**Date:** 2026-08-07
**Branch:** `refactor/actor-shaped-ports`
**Baseline commit:** `48cd2f2` — all "before" figures below refer to this commit
**Principle:** a port is one conversation with one kind of outside actor

### The symptom

`domain/port/in/` held **20 interfaces of 7–15 lines each**, one per use case. The cost showed up
at every consumer:

| Consumer | Before | After |
|---|---|---|
| `AccountController` constructor parameters | 9 | **1** |
| `AdminController` constructor parameters | 10 | **3** |
| `AccountApplicationService` `implements` list | 14 | **3** |
| `CustomerApplicationService` `implements` list | 5 | **2** |
| `AccountControllerTest` mock declarations | 10 | **2** |
| `AdminControllerTest` mock declarations | 11 | **4** |

### The deeper problem: no organizing principle

Counting files was the least of it. The grouping was by aggregate in some places and by whoever
happened to call it in others:

- `AccountApplicationService` implemented both `DepositMoneyUseCase` (a customer operation) and
  `FreezeAccountUseCase` (an admin operation) — two different actors in one class, with nothing
  marking the boundary.
- `SetTransferFeeUseCase` — a bank-wide setting, unrelated to any customer — was implemented by
  `CustomerApplicationService`, purely because an admin invokes it. That service injected
  `SettingsRepositoryPort` **solely** to serve that one method.

When a dependency exists only to serve one misplaced method, the grouping is telling you it is wrong.

### The principle

Cockburn's definition: **a port is one conversation with one kind of outside actor.** Not one
method, and not one aggregate.

This system has two driving actors — Customer and Admin — talking about three subjects. That yields
five ports, and the count falls out of the principle rather than being chosen:

| Port | Actor × subject | Methods |
|---|---|---|
| `CustomerAccountPort` | customer × accounts | 9 |
| `AccountAdministrationPort` | admin × accounts | 5 |
| `CustomerAdministrationPort` | admin × customers | 4 |
| `CustomerSelfServicePort` | customer × self | 1 |
| `BankSettingsPort` | admin × bank config | 1 |

**20 ports → 5**, with all 20 original methods preserved exactly.

#### "But isn't one interface per method better Interface Segregation?"

No, and this is the most common misreading of ISP. The principle says *clients should not be forced
to depend on methods they do not use*. It does not say "one method per interface."

`AccountController` uses **all nine** methods of `CustomerAccountPort`. It depends on nothing it
does not call, so ISP is satisfied. Splitting those nine into nine interfaces bought no segregation
whatsoever — it only added eight files and eight constructor parameters.

Where ISP genuinely bites here is the actor boundary: `AdminController` must not depend on
`deposit` and `withdraw`. That is precisely the split the new design makes, and precisely the split
the old one blurred.

### Before and after

`AccountController`'s constructor is the clearest single artifact.

**Before**

```java
public AccountController(OpenCheckingAccountUseCase openChecking,
                         OpenSavingsAccountUseCase openSavings,
                         OpenTimeDepositAccountUseCase openTimeDeposit,
                         DepositMoneyUseCase depositMoney,
                         WithdrawMoneyUseCase withdrawMoney,
                         GetBalanceUseCase getBalance,
                         GetTransactionsUseCase getTransactions,
                         TransferMoneyUseCase transferMoney,
                         ListAccountsUseCase listAccounts) {
    this.openChecking = openChecking;
    this.openSavings = openSavings;
    this.openTimeDeposit = openTimeDeposit;
    this.depositMoney = depositMoney;
    this.withdrawMoney = withdrawMoney;
    this.getBalance = getBalance;
    this.getTransactions = getTransactions;
    this.transferMoney = transferMoney;
    this.listAccounts = listAccounts;
}
```

**After**

```java
public AccountController(CustomerAccountPort customerAccount) {
    this.customerAccount = customerAccount;
}
```

Nine fields become one. Nothing the controller can do has changed.

### The placement asymmetry

Driving ports moved from `domain/port/in/` to `application/port/in/`. Driven ports stayed at
`domain/port/out/`. That asymmetry is deliberate and is the rule worth memorizing:

> **The domain declares the interfaces it *requires* (driven ports, `domain/port/out`).
> The application declares the operations it *offers* (driving ports, `application/port/in`).**

A driven port such as `AccountRepositoryPort` is the domain saying "to do my work I need something
that can store an account" — dependency inversion, and unambiguously a domain statement. A driving
port is the opposite: `OpenCheckingCommand` carrying an `ownerId` and a `Currency` is a *request
shape*, an application concern. Keeping driving ports under `domain/` implied the domain knows what
use cases exist, which is the coupling the layering is meant to prevent.

Hombergs' *Get Your Hands Dirty on Clean Architecture* puts both directions under `application/`,
which is symmetrical and common in Spring codebases. This project chose otherwise because the
symmetry costs the clearest statement of dependency inversion the layout can make.

### The honest costs

1. **Multi-method interfaces are harder to fake by hand.** A hand-written `CustomerAccountPort`
   stub must implement nine methods. Mockito makes this free; a hand-rolled test double does not.
2. **`verifyNoInteractions` changes meaning** when narrow mocks merge. In this codebase it happened
   to get *stronger* — all eight surviving uses are in "request rejected before reaching the
   service" tests, so asserting that *nothing* on the port was called is exactly right. But that was
   luck, not design. Where a test means "this specific method must not run," write
   `verify(port, never()).thatMethod(any())`.

### A gap the refactoring exposed

`setTransferFee` validates that the fee is not negative. `SetTransferFeeRequest` also carries
`@DecimalMin("0.0")`, so `AdminControllerTest`'s negative-value case asserts
`verifyNoInteractions(bankSettings)` — the request is rejected by Bean Validation and **never reaches
the service**. The guard was correct defense-in-depth with *zero test coverage*, and this refactoring
moved it from one class to another with nothing verifying it arrived intact.

Two tests were added to close it. This is the third instance of one shape in this codebase — a dead
exception handler (entry 1, defect 2), a never-thrown exception (entry 1, defect 2), and now an
unexercised guard. **Code that is correct, defensive, and unreachable from the outside is exactly
the code that rots silently**, because no failing test ever announces its absence.

### Scope

| | |
|---|---|
| Ports deleted | 20 (all of `domain/port/in/`) |
| Ports created | 5 (under `application/port/in/`) |
| Files changed in the migration commit | 30 — 161 insertions, 426 deletions |
| Behavior changes | none |
| Tests | 184 → 184 through the migration, then 186 after covering the fee guard |
| Persistence, domain model, `domain/port/out` | untouched |

The migration commit deliberately landed at **exactly 184 tests**. Holding the count fixed is what
makes "no behavior changed" checkable rather than merely asserted; the two new tests were added
afterwards, in their own commit, so the signal stayed clean.

### Discussion questions

1. `CustomerSelfServicePort` has one method. Justify it as a port rather than folding
   `changePassword` into `CustomerAdministrationPort`.
2. The driving/driven asymmetry is defended above. Make the opposing case for Hombergs' symmetrical
   layout — what does it buy?
3. `AccountApplicationService` still implements three ports and serves two different actors. Should
   it be split? What would decide it?

---

## Entry 3 — Ownership authorization: a rule that could not be said

**Date:** 2026-08-07
**Branch:** `refactor/ownership-authorization`
**Baseline commit:** `25a4ea2`
**Principle:** a rule the types cannot express is a rule that will go missing

### The symptom

Three holes, all the same shape.

**1. Account takeover.** `PUT /api/customers/{customerId}/password` read its target from the path,
and `SecurityConfig` gated `/api/customers/**` on `hasRole("CUSTOMER")` and nothing else. Any
customer could set any other customer's password, then log in as them.

**2. No ownership check anywhere on accounts.** Given an account id, any authenticated customer
could deposit to it, withdraw from it, transfer out of it, read its balance and read its full
transaction history.

**3. Accounts opened for other people.** `POST /api/accounts/checking?ownerId={id}` took the owner
as a request parameter and never compared it to the caller.

### The tell

`UnauthorizedAccessException` existed. `GlobalExceptionHandler` mapped it to HTTP 403. **No
production code threw it.**

```bash
grep -rn "throw new UnauthorizedAccessException" src/main/java   # before: no matches
```

That is the third instance of one pattern in this codebase, after entry 1's never-thrown
`InsufficientFundsException` and entry 2's untested fee guard. The lesson has earned generalization:

> **An exception nothing throws is a rule nothing enforces.** Handlers are cheap to write and easy
> to leave orphaned. Grepping for exception types that appear only in a handler and a test is a
> two-minute audit that finds real holes.

### The root cause

Not one customer-facing `Command` carried the caller:

```java
record DepositCommand(AccountId accountId, TransactionAmount amount) {}
record ChangePasswordCommand(CustomerId customerId, String rawNewPassword) {}
```

There was no caller to compare against, so "the caller must own this account" was not merely
unenforced — it was **inexpressible**.

This is entry 1 running in reverse, and the pairing is the most useful thing in this document:

| | Entry 1 | Entry 3 |
|---|---|---|
| The types made… | a wrong state impossible | a right rule impossible |
| Result | the bug could not be written | the rule could not be written |
| Fix | add a type that carries the constraint | add a parameter that carries the subject |

A signature is a statement about what an operation is allowed to consider. Leave something out and
no amount of care downstream can put it back.

### Fact versus policy

The rule splits into two halves that belong in different layers:

```java
// domain — Account already knows its owner
public final boolean isOwnedBy(CustomerId customerId) {
    return this.ownerId.equals(customerId);
}

// application — deciding to refuse
private void requireOwner(Account account, CustomerId callerId) {
    if (!account.isOwnedBy(callerId))
        throw new UnauthorizedAccessException("Account does not belong to the caller");
}
```

Ownership is a domain fact; a *caller* is a session notion the domain has no business knowing. Keeping
the split means `isOwnedBy` is tested with two plain `CustomerId` values and no security framework
at all.

### Three enforcement shapes, deliberately different

| Situation | Technique | Where |
|---|---|---|
| The resource *is* the caller's | **Delete the parameter** | the three `open*` endpoints |
| The path names a customer | **Require self** | `listAccounts`, `changePassword` |
| The path names an account | **Require ownership** | deposit, withdraw, transfer, balance, transactions |

The first is entry 1's move applied to security. Rather than validating `ownerId == caller`,
`ownerId` was removed — the caller *is* the owner, so opening an account for somebody else can no
longer be expressed. This is the one breaking API change in the work:

```
POST /api/accounts/checking?ownerId=<id>   →   POST /api/accounts/checking
```

**Prefer deleting a parameter to validating it.** A validated parameter still has to be validated
everywhere, forever; a deleted one is gone.

### The transfer asymmetry

The caller must own the **source**. The target is deliberately unchecked — sending money to other
people is the entire product.

```java
requireOwner(source, command.callerId());
// The TARGET is deliberately not ownership-checked.
```

`shouldAllowTransferToAnotherCustomersAccount` exists solely to pin this. It is the most valuable
test in the change: the obvious-looking hardening — "the caller must own both accounts" — reads as
correct in review and breaks transfers entirely. A test that asserts something is *deliberately not
checked* is the only thing standing between that edit and production.

### Probing, and 403 versus 404

`changePassword` checks the caller **before** the repository lookup, so a caller cannot learn which
customer ids exist by distinguishing "not found" from "not yours".

The account paths accept a weaker position, recorded here rather than hidden: a non-existent account
returns 404 while somebody else's returns 403, which does distinguish the two. Returning 404 for both
is the hardened choice. This codebase keeps 403 because it reads more clearly as teaching material,
and account ids are random UUIDs, so enumeration is impractical.

### The cost landed in the test fixtures

`@WithMockUser` builds a plain Spring `User`. Once a controller declares
`@AuthenticationPrincipal BankUserPrincipal caller`, that argument resolves to `null` under it — so
**22 annotations across two test classes had to be replaced** with a purpose-built `@WithBankUser`
plus a `WithSecurityContextFactory`.

`AdminControllerTest`'s four `@WithMockUser(roles = "CUSTOMER")` tests were deliberately left alone:
they assert Spring Security rejects a customer on an admin route *before* any controller runs, so
they need no principal.

A security fix whose production diff is small can still carry most of its weight in test
infrastructure. Budget for that.

### Where each rule is tested, and why

The design document originally called for controller tests named
`deposit_returnsForbiddenWhenAccountNotOwned`. **Those cannot work.** `AccountControllerTest` is a
`@WebMvcTest` with `CustomerAccountPort` mocked, and the rule lives in `AccountApplicationService` —
the mocked object. Such a test proves only that a mock configured to throw does throw. Writing it
would have reproduced entry 1's defect 2 exactly, while this same file documents it.

| Layer | Its actual job | How it is tested |
|---|---|---|
| Controller | Put the caller's id in the command | `ArgumentCaptor`, assert `command.callerId()` |
| Controller | Map the exception to 403 | Stub the port to throw; assert the status |
| Application service | Decide whether the caller may proceed | Real service, mocked repositories |
| Domain | Answer "is this owned by X?" | Plain JUnit on `Account` |

**Test each layer against its neighbor's real output, not against your assumption about it.**

### Scope

| | |
|---|---|
| New | `BankUserPrincipal`, `@WithBankUser` + factory, `Account.isOwnedBy` |
| Behavior changes | 3 endpoints lose `?ownerId=`; cross-customer operations now return 403 |
| Untouched | persistence, `SecurityConfig` route rules, `GlobalExceptionHandler`, all three admin ports, `AdminController` |
| Tests | 186 → 200 |

Checkpoints held at every step: 186 → 188 (`isOwnedBy`) → 188 → 188 → 188 (principal, fixture and
caller-threading, all behaviorally inert) → 196 → 200. Four consecutive no-op commits are what makes
the fifth one's diff readable as *only* the security change.

### Discussion questions

1. `requireOwner` is a private method on the application service. Argue for making it a named
   collaborator instead — what would that buy, and what would it cost?
2. The 403/404 distinction leaks which account ids exist. Change it to 404-for-both. Which tests
   break, and what does that tell you about where the decision is encoded?
3. Entries 1 and 3 are the same lesson mirrored. State it as a single rule that covers both.
