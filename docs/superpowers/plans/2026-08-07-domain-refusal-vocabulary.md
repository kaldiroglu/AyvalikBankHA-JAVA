# Domain Refusal Vocabulary Implementation Plan

Claude Opus 5 (1M context) — created 2026-08-07

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the domain four named ways to refuse instead of one, so the application layer stops guessing meaning from call-site position and stops catching a JDK type.

**Architecture:** A sealed `AccountRuleViolation extends IllegalStateException` with four `final` subtypes. Because the base is still an `IllegalStateException`, the 17 domain throw sites can be retyped without touching a single test, and only the final task — replacing the application service's catches with one exhaustive switch — changes behavior.

**Tech Stack:** Java 25 (sealed classes, pattern-matching switch), Spring Boot 3.4, JUnit 5, AssertJ, Mockito, Maven.

## Global Constraints

- Root package `dev.kaldiroglu`. New exception types go in `dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account`.
- The domain layer must have **zero** Spring or JPA imports.
- All code and comments in English. American spelling.
- Every `.md` file created must carry the model name, version, and creation date at the top.
- Baseline: **200 tests, 0 failures**.
- Design reference: `docs/superpowers/specs/2026-08-07-domain-refusal-vocabulary-design.md`.
- `mvn clean verify` must pass before the work is reported complete.

## Why this ordering works

`AccountRuleViolation extends IllegalStateException`, so a retyped throw site is still caught by the
existing `catch (IllegalStateException)`. Tasks 1 and 2 are therefore **provably behavior-neutral**
and must hold the test count at 200. All behavior change is isolated to Task 3.

---

## File Structure

**New:** `domain/model/account/AccountRuleViolation.java`, `AccountNotActiveException.java`, `OperationNotPermittedException.java`, `TransactionLimitExceededException.java`

**Modified (main):** `InsufficientBalanceException`, `ActiveState`, `FrozenState`, `ClosedState`, `SavingsAccount`, `TimeDepositAccount`, `TransferDomainService`, `AccountApplicationService`

**Modified (test):** `AccountApplicationServiceTest` (two new tests only)

**Untouched:** persistence, controllers, ports, `GlobalExceptionHandler`, `CustomerApplicationService`, all four application exception classes, and all 25 existing `IllegalStateException.class` assertions.

---

### Task 1: The sealed hierarchy

**Files:**
- Create: `AccountRuleViolation.java`, `AccountNotActiveException.java`, `OperationNotPermittedException.java`, `TransactionLimitExceededException.java` (all in `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/account/`)
- Modify: `domain/model/account/InsufficientBalanceException.java`

**Interfaces:**
- Consumes: nothing
- Produces: `AccountRuleViolation` (sealed abstract, extends `IllegalStateException`, one `String` constructor) permitting exactly `AccountNotActiveException`, `InsufficientBalanceException`, `OperationNotPermittedException`, `TransactionLimitExceededException` — each `final` with one `String` constructor

- [ ] **Step 1: Create the sealed base**

```java
package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

/**
 * Base type for every way the account domain can refuse an operation.
 *
 * <p>Before this existed the domain threw raw {@link IllegalStateException} from 17 places, all
 * meaning different things, and the application layer recovered the meaning from <i>which call it
 * had wrapped</i>. Translation was positional rather than semantic, and
 * {@code catch (IllegalStateException)} additionally swallowed JDK and framework exceptions,
 * reporting genuine defects to clients as HTTP 422 business errors.
 *
 * <p>Extending {@link IllegalStateException} is deliberate on two counts. A refusal really is a
 * property of state rather than a defect in the argument — the precedent set by
 * {@link InsufficientBalanceException}. And because {@code catch (AccountRuleViolation)} does
 * <b>not</b> catch a plain {@code IllegalStateException}, precision is gained at the catch site
 * without invalidating the domain tests that assert on the supertype.
 *
 * <p>{@code sealed} for the same reason {@link Account} is: adding a fifth kind of refusal must be a
 * deliberate edit to {@code permits}, and it breaks the translation switch in
 * {@code AccountApplicationService} until the new case is handled.
 */
public sealed abstract class AccountRuleViolation extends IllegalStateException
        permits AccountNotActiveException,
                InsufficientBalanceException,
                OperationNotPermittedException,
                TransactionLimitExceededException {

    protected AccountRuleViolation(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Create `AccountNotActiveException`**

```java
package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

/**
 * The account's lifecycle state forbids the operation — it is frozen or closed, or the requested
 * transition is invalid from where it currently is.
 *
 * <p>Translated to {@code AccountNotOperableException} → HTTP 422.
 */
public final class AccountNotActiveException extends AccountRuleViolation {
    public AccountNotActiveException(String message) {
        super(message);
    }
}
```

- [ ] **Step 3: Create `OperationNotPermittedException`**

```java
package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

/**
 * The account product's own rules forbid the operation — a time deposit's principal is locked, it
 * has not matured, or a month's interest has already been accrued.
 *
 * <p>Distinct from {@link AccountNotActiveException}: the account may be perfectly active and the
 * operation still be meaningless for this product.
 *
 * <p>Translated to {@code InvalidAccountOperationException} → HTTP 422.
 */
public final class OperationNotPermittedException extends AccountRuleViolation {
    public OperationNotPermittedException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: Create `TransactionLimitExceededException`**

```java
package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

/**
 * The amount exceeds the per-transaction cap carried by the customer's tier.
 *
 * <p>Thrown by {@code TransferDomainService}, which lives in {@code domain/service/account} — only
 * the permitted subtypes must share this package, not the code that throws them.
 *
 * <p>Translated to {@code LimitExceededException} → HTTP 422.
 */
public final class TransactionLimitExceededException extends AccountRuleViolation {
    public TransactionLimitExceededException(String message) {
        super(message);
    }
}
```

- [ ] **Step 5: Re-parent `InsufficientBalanceException`**

Change its declaration from

```java
public class InsufficientBalanceException extends IllegalStateException {
```

to

```java
public final class InsufficientBalanceException extends AccountRuleViolation {
```

`final` is required — a sealed hierarchy permits only `final`, `sealed` or `non-sealed` subtypes. Keep
the existing Javadoc and constructor; add a sentence noting it is now one of four
`AccountRuleViolation` subtypes.

- [ ] **Step 6: Verify nothing broke**

Run: `mvn -o clean test`
Expected: **200 tests, 0 failures.** `InsufficientBalanceException` is still an
`IllegalStateException` by inheritance, so every existing catch and assertion still holds.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/account/
git commit -m "Add the sealed AccountRuleViolation hierarchy

Four named ways for the account domain to refuse. Nothing throws the three
new types yet."
```

---

### Task 2: Retype the 17 domain throw sites

Provably behavior-neutral: every new type is still an `IllegalStateException`, so the application
service's existing catches behave identically.

**Files:**
- Modify: `ActiveState.java`, `FrozenState.java`, `ClosedState.java`, `SavingsAccount.java`, `TimeDepositAccount.java`, `domain/service/account/TransferDomainService.java`

**Interfaces:**
- Consumes: the four subtypes from Task 1
- Produces: no raw `IllegalStateException` remains anywhere under `domain/`

- [ ] **Step 1: Retype the three state classes → `AccountNotActiveException`**

Seven sites, all currently `throw new IllegalStateException(...)`, keeping their messages verbatim:

| File | Line | Message |
|---|---|---|
| `ActiveState.java` | 25 | `"Account is not frozen"` |
| `FrozenState.java` | 24 | `"Account is already frozen"` |
| `FrozenState.java` | 35 | `"Account is frozen"` |
| `ClosedState.java` | 21 | `"Cannot freeze a closed account"` |
| `ClosedState.java` | 26 | `"Cannot unfreeze a closed account"` |
| `ClosedState.java` | 31 | `"Account is already closed"` |
| `ClosedState.java` | 36 | `"Account is closed"` |

Replace only the type; do not touch the messages — 25 domain tests assert on them.

- [ ] **Step 2: Retype `SavingsAccount`**

| Line | Message | New type |
|---|---|---|
| 112 | `"Cannot accrue interest on a closed account"` | `AccountNotActiveException` |
| 115 | `"Interest already accrued for or after " + month` | `OperationNotPermittedException` |

- [ ] **Step 3: Retype `TimeDepositAccount`**

| Line | Message | New type |
|---|---|---|
| 119 | `"Time deposit principal is locked — further deposits are not allowed"` | `OperationNotPermittedException` |
| 132 | `"Time deposit has not matured"` | `OperationNotPermittedException` |
| 149 | `"Time deposit accounts do not support transfers"` | `OperationNotPermittedException` |
| 168 | `"Cannot mature a closed account"` | `AccountNotActiveException` |
| 170 | `"Account is already matured"` | `OperationNotPermittedException` |
| 172 | `"Maturity date not yet reached"` | `OperationNotPermittedException` |

- [ ] **Step 4: Retype `TransferDomainService`**

Both sites (lines 31 and 39) become `TransactionLimitExceededException`, messages unchanged. Add
`import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.TransactionLimitExceededException;`.

- [ ] **Step 5: Prove no raw throws remain**

```bash
grep -rn "throw new IllegalStateException" src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/
```

Expected: **no matches.**

- [ ] **Step 6: Verify behavior is unchanged**

Run: `mvn -o clean test`
Expected: **200 tests, 0 failures.** A different number means a message was altered — check Step 1's
warning.

- [ ] **Step 7: Commit**

```bash
git add -A src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/
git commit -m "Retype the 17 domain refusals to the named hierarchy

Behavior-neutral: every new type is still an IllegalStateException, so the
application service's existing catches behave identically. 200 tests
unchanged."
```

---

### Task 3: Precise catches and one exhaustive switch

The only task that changes behavior.

**Files:**
- Modify: `application/service/AccountApplicationService.java`
- Test: `application/service/AccountApplicationServiceTest.java`

**Interfaces:**
- Consumes: `AccountRuleViolation` and its four subtypes
- Produces: `private RuntimeException translate(AccountRuleViolation)` in `AccountApplicationService`

- [ ] **Step 1: Write the two failing tests**

Append to `AccountApplicationServiceTest`:

```java
    // ── refusal translation ───────────────────────────────────────────────

    @Test
    @DisplayName("a frozen account reports AccountNotOperable, not the generic invalid-operation type")
    void shouldReportAccountNotOperableWhenWithdrawingFromFrozenAccount() {
        CustomerId ownerId = CustomerId.generate();
        Account account = CheckingAccount.open(ownerId, Currency.USD);
        account.deposit(TransactionAmount.of(500.0, Currency.USD));
        account.freeze();
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(customerRepository.findById(ownerId)).thenReturn(Optional.of(stubCustomer(ownerId, CustomerTier.STANDARD)));

        assertThatThrownBy(() -> service.withdraw(new CustomerAccountPort.WithdrawCommand(
                ownerId, account.getId(), TransactionAmount.of(10.0, Currency.USD))))
                .isInstanceOf(AccountNotOperableException.class)
                .hasMessageContaining("frozen");
    }

    @Test
    @DisplayName("an unrelated IllegalStateException is a defect, not a 422 business error")
    void shouldNotSwallowAnUnrelatedIllegalStateException() {
        TransferDomainService faultyPolicy = mock(TransferDomainService.class);
        doThrow(new IllegalStateException("connection pool exhausted"))
                .when(faultyPolicy).requireWithdrawalWithinLimit(any(), any());
        AccountApplicationService serviceWithFaultyPolicy = new AccountApplicationService(
                accountRepository, customerRepository, transactionRepository,
                settingsRepository, faultyPolicy);

        CustomerId ownerId = CustomerId.generate();
        Account account = CheckingAccount.open(ownerId, Currency.USD);
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(customerRepository.findById(ownerId)).thenReturn(Optional.of(stubCustomer(ownerId, CustomerTier.STANDARD)));

        assertThatThrownBy(() -> serviceWithFaultyPolicy.withdraw(new CustomerAccountPort.WithdrawCommand(
                ownerId, account.getId(), TransactionAmount.of(10.0, Currency.USD))))
                .isInstanceOf(IllegalStateException.class)
                .isNotInstanceOf(LimitExceededException.class)
                .isNotInstanceOf(InvalidAccountOperationException.class)
                .hasMessageContaining("connection pool");
    }
```

`TransferDomainService` is constructed for real in `setUp`, so this second test builds its own service
with a mocked policy — that is what puts the fault *inside* the guarded region rather than outside it.

- [ ] **Step 2: Run to verify they fail**

Run: `mvn -o test -Dtest=AccountApplicationServiceTest`
Expected: 2 failures. The first gets `InvalidAccountOperationException` (the positional misnaming);
the second gets `LimitExceededException` (the swallowing defect).

- [ ] **Step 3: Add the translation switch**

Beside the other private helpers in `AccountApplicationService`:

```java
    /**
     * Maps a domain refusal to the application exception that carries its HTTP meaning.
     *
     * <p>No {@code default} clause: {@link AccountRuleViolation} is sealed, so the compiler proves
     * this switch total. Adding a fifth refusal type breaks the build here until it is handled —
     * the same technique {@code AccountPersistenceMapper} uses over the sealed {@code Account}
     * hierarchy.
     */
    private RuntimeException translate(AccountRuleViolation violation) {
        return switch (violation) {
            case AccountNotActiveException e         -> new AccountNotOperableException(e.getMessage());
            case InsufficientBalanceException e      -> new InsufficientFundsException(e.getMessage());
            case OperationNotPermittedException e    -> new InvalidAccountOperationException(e.getMessage());
            case TransactionLimitExceededException e -> new LimitExceededException(e.getMessage());
        };
    }
```

- [ ] **Step 4: Replace the catches in `deposit`**

```java
        Transaction tx;
        try {
            tx = account.deposit(command.amount());
        } catch (AccountRuleViolation e) {
            throw translate(e);
        }
```

- [ ] **Step 5: Merge the two try blocks in `withdraw`**

They needed separating only because one JDK type had to mean two things:

```java
        Transaction tx;
        try {
            transferDomainService.requireWithdrawalWithinLimit(command.amount(), owner.getTier());
            tx = account.withdraw(command.amount());
        } catch (AccountRuleViolation e) {
            throw translate(e);
        }
```

- [ ] **Step 6: Merge the two try blocks in `transfer`**

The fee calculation sits between the limit check and the debit, so the guarded region covers all
three:

```java
        Transaction outTx, inTx;
        try {
            transferDomainService.requireTransferWithinLimit(command.amount(), sourceOwner.getTier());
            boolean sameCustomer = source.getOwnerId().equals(target.getOwnerId());
            BigDecimal feePercent = settingsRepository.getTransferFeePercent();
            Money fee = transferDomainService.calculateFee(command.amount(), sameCustomer, feePercent, sourceOwner.getTier());
            outTx = source.transferOut(command.amount(), fee, target.getId().toString());
            inTx = target.transferIn(command.amount(), source.getId().toString());
        } catch (AccountRuleViolation e) {
            throw translate(e);
        }
```

`settingsRepository.getTransferFeePercent()` is now inside the try. That is safe and intended: it
cannot throw `AccountRuleViolation`, so an infrastructure failure there propagates untouched — which
is precisely the property Step 1's second test asserts.

- [ ] **Step 7: Replace the catches in `freezeAccount`, `unfreezeAccount`, `closeAccount`**

Each becomes:

```java
        try { account.freeze(); }
        catch (AccountRuleViolation e) { throw translate(e); }
```

with `unfreeze()` and `close()` respectively.

- [ ] **Step 8: Replace the catches in `accrueInterest` and `mature`**

```java
        try { tx = savings.accrueInterest(command.month()); }
        catch (AccountRuleViolation e) { throw translate(e); }
```

```java
        try { tx = td.mature(LocalDate.now()); }
        catch (AccountRuleViolation e) { throw translate(e); }
```

**Leave the two `instanceof` guards alone.** `"Account is not a savings account"` and `"Account is not
a time deposit"` keep throwing `InvalidAccountOperationException` directly — they are application-level
dispatch on a runtime type, not domain refusals, and `AccountApplicationServiceTest:340` and `:372`
pin them.

- [ ] **Step 9: Run the suite**

Run: `mvn -o clean test`
Expected: **202 tests, 0 failures.**

- [ ] **Step 10: Verify the catches are now precise**

```bash
grep -c "catch (" src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/application/service/AccountApplicationService.java
grep -n "catch (IllegalStateException" src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/application/service/AccountApplicationService.java
```

Expected: **8**, and **no matches** for the second command.

- [ ] **Step 11: Commit**

```bash
git add -A src/
git commit -m "Translate domain refusals through one exhaustive switch

Replaces catch (IllegalStateException) with catch (AccountRuleViolation),
so a JDK or framework IllegalStateException now surfaces as a 500 defect
instead of a fake 422 business error.

Frozen and closed accounts now report AccountNotOperableException rather
than the generic invalid-operation type. Both are 422, so no client is
affected; the type and message simply become accurate.

12 catch clauses to 8."
```

---

### Task 4: Documentation

**Files:**
- Modify: `Refactorings.md`, `CLAUDE.md`, `Tests.md`

- [ ] **Step 1: Add `Refactorings.md` entry 4**

Following entries 1–3, recording the baseline commit. Cover:

1. **The symptom** — 12 catch clauses, and the table of 17 raw `IllegalStateException` sites grouped
   by what they actually meant.
2. **Positional translation**, with the `FrozenState:35` versus `ClosedState:21` pair as evidence: the
   same JDK type becoming two different application exceptions purely by call-site position.
3. **The real defect** — `catch (IllegalStateException)` catches a type the JDK and Spring both throw,
   so genuine bugs were reported to clients as 422 business errors and never flagged by monitoring.
4. **Why the base extends `IllegalStateException`** and what that bought: catching the *subtype* is
   what delivers precision, and inheriting from the old type let all 25 domain assertions and both
   behavior-neutral tasks pass untouched. Make the general point — when introducing a type hierarchy
   over an existing exception, inheriting from what callers already catch turns a big-bang change
   into an incremental one.
5. **The exhaustive switch** with no `default`, and why sealing makes that safe.
6. **Before and after** of `withdraw`, showing two try blocks becoming one.
7. **The two behavior changes**, and that `shouldNotSwallowAnUnrelatedIllegalStateException` is the
   test that would have caught the original defect.

- [ ] **Step 2: Update `CLAUDE.md`**

Add to Key Design Decisions:

```markdown
- **Domain refusal vocabulary**: the account domain refuses through a sealed `AccountRuleViolation` (extending `IllegalStateException`) with four `final` subtypes — `AccountNotActiveException`, `InsufficientBalanceException`, `OperationNotPermittedException`, `TransactionLimitExceededException`. `AccountApplicationService.translate` maps them to the application exceptions via an exhaustive switch with no `default`, so a fifth refusal type breaks the build until handled. Catching `AccountRuleViolation` rather than `IllegalStateException` means a JDK or framework exception surfaces as a 500 rather than a fake 422. See `Refactorings.md` entry 4.
```

- [ ] **Step 3: Update `Tests.md`**

Set the header count to the figure from Task 3 Step 9, and add the two new tests to the
`AccountApplicationServiceTest` section with descriptions.

- [ ] **Step 4: Verify every documented claim**

```bash
grep -rn "throw new IllegalStateException" src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/ || echo "clean"
grep -c "catch (" src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/application/service/AccountApplicationService.java
```

- [ ] **Step 5: Final verification**

Run: `mvn clean verify`
Expected: BUILD SUCCESS, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add Refactorings.md CLAUDE.md Tests.md docs/superpowers/
git commit -m "Document the domain refusal vocabulary refactoring"
```

---

## Self-Review

**Spec coverage.** Sealed hierarchy → Task 1; the 17 retypings → Task 2; precise catches and the
exhaustive switch → Task 3 Steps 3–8; the `instanceof` guards staying put → Task 3 Step 8; both
behavior changes and their tests → Task 3 Step 1; documentation → Task 4. The spec's decision to
leave the 25 domain assertions untightened is honored by never touching them.

**Placeholder scan.** No TBDs. All four new classes and both new tests are given in full. Every
retyping is a table of exact file, line and message.

**Type consistency.** `AccountRuleViolation`, `AccountNotActiveException`,
`OperationNotPermittedException`, `TransactionLimitExceededException` are defined in Task 1 and used
with those exact names in Tasks 2 and 3. `translate(AccountRuleViolation)` is defined in Task 3
Step 3 and called in Steps 4–8.

**Checkpoints.** 200 (Task 1) → 200 (Task 2) → 202 (Task 3). The two 200s are the proof that the
retyping changed nothing; only Task 3 may move the number.
