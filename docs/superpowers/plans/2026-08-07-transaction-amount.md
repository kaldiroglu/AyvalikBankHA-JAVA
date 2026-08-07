# TransactionAmount Implementation Plan

Claude Opus 5 (1M context) — created 2026-08-07

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace 7 hand-written "amount must not be negative" guards with a `TransactionAmount` value object that is strictly positive by construction, and fix the two defects found alongside them.

**Architecture:** `TransactionAmount` wraps `Money` as a pure constraint-carrier — all arithmetic stays in `Money`, which remains signed because `CheckingAccount` balances legitimately go negative under overdraft. The new type covers the domain's *command* surface only (`deposit` / `withdraw` / `transferOut` / `transferIn` and their use-case `Command` records); it deliberately stops before `Transaction.amount` and the transfer fee, both of which may legally be zero. Consequence: the persistence layer is untouched.

**Tech Stack:** Java 25, Spring Boot 3.4, JUnit 5, AssertJ, Mockito, Maven.

## Global Constraints

- Root package `dev.kaldiroglu` — new classes go in `dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account`.
- The domain layer must have **zero** Spring or JPA imports.
- All code and comments in English. American spelling.
- Every `.md` file created must carry the model name, version, and creation date at the top.
- `Money` must not be modified. It stays signed.
- Baseline before any change: **176 tests, 1 failure** (`AccountTest.shouldRejectDepositWithWrongCurrency`), caused by an uncommitted working-tree edit at `CheckingAccount.java:61`.
- `mvn clean verify` must pass before the work is reported complete.

---

## File Structure

**New (main):**

| Path | Responsibility |
|---|---|
| `domain/model/account/TransactionAmount.java` | Strictly-positive amount value object |
| `domain/model/account/InsufficientBalanceException.java` | Domain signal for a balance shortfall; `extends IllegalStateException` |

**New (test):** `src/test/java/.../domain/model/account/TransactionAmountTest.java`

**New (docs):** `Refactorings.md` at project root

**Modified (main):** `Account`, `CheckingAccount`, `SavingsAccount`, `TimeDepositAccount`, `DepositMoneyUseCase`, `WithdrawMoneyUseCase`, `TransferMoneyUseCase`, `TransferDomainService`, `AccountApplicationService`, `AccountController`

**Modified (test):** `AccountTest`, `CheckingAccountTest`, `SavingsAccountTest`, `TimeDepositAccountTest`, `TransferDomainServiceTest`, `AccountApplicationServiceTest`

**Modified (docs):** `CLAUDE.md`, `Domain.md`, `Tests.md`

**Untouched by design:** every file under `adapter/out/persistence/`, `Transaction.java`, `Money.java`, `TransactionResponse.java`.

---

### Task 1: Restore the currency guard (Defect 1)

Turns the working tree green before anything else changes. One line.

**Files:**
- Modify: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/account/CheckingAccount.java:61`
- Test: `src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/account/AccountTest.java` (existing test, already failing)

**Interfaces:**
- Consumes: nothing
- Produces: nothing new — restores `Account.requireSameCurrency(Money)` being called from `CheckingAccount.deposit`

- [ ] **Step 1: Run the failing test to see the baseline**

Run: `mvn -o test -Dtest=AccountTest#shouldRejectDepositWithWrongCurrency`
Expected: FAIL — `Expecting throwable message: "Currency mismatch: USD vs EUR" to contain: "currency"`

- [ ] **Step 2: Restore the guard**

In `CheckingAccount.java`, inside `deposit`, replace the commented line:

```java
    @Override
    public Transaction deposit(Money amount) {
        requireActive();
        requireSameCurrency(amount);
        if (amount.isNegative())
            throw new IllegalArgumentException("Deposit amount cannot be negative");
        this.balance = this.balance.add(amount);
        return Transaction.create(this.id, TransactionType.DEPOSIT, amount, "Deposit");
    }
```

- [ ] **Step 3: Run the test to verify it passes**

Run: `mvn -o test -Dtest=AccountTest#shouldRejectDepositWithWrongCurrency`
Expected: PASS

- [ ] **Step 4: Run the whole suite**

Run: `mvn -o test`
Expected: 176 tests, 0 failures

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/account/CheckingAccount.java
git commit -m "Restore currency guard in CheckingAccount.deposit

The guard was commented out, so a mismatched-currency deposit failed two
frames later inside Money.add with Money's message instead of the account's.
All three account types now enforce the invariant identically."
```

---

### Task 2: Insufficient funds returns 422, not 400 (Defect 2)

**Files:**
- Create: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/account/InsufficientBalanceException.java`
- Modify: `CheckingAccount.java:78,96`, `SavingsAccount.java:85,98`, `TimeDepositAccount.java:137`
- Modify: `application/service/AccountApplicationService.java` (`withdraw`, `transfer`)
- Test: `AccountTest.java:174`, `CheckingAccountTest.java:33,43`, `SavingsAccountTest.java:40`, `AccountApplicationServiceTest.java:251`

**Interfaces:**
- Consumes: `application/exception/InsufficientFundsException` (exists; `GlobalExceptionHandler:24-27` already maps it to 422)
- Produces: `dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.InsufficientBalanceException extends IllegalStateException`, with a single `String` constructor

- [ ] **Step 1: Write the failing test**

In `AccountApplicationServiceTest.java`, change the assertion in `shouldThrowOnWithdrawExceedingBalance` (currently at line 249-252) from `IllegalArgumentException` to the application exception:

```java
        assertThatThrownBy(() -> service.withdraw(
                new WithdrawMoneyUseCase.Command(account.getId(), Money.of(500.0, Currency.USD))))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient");
```

Add the import:

```java
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.InsufficientFundsException;
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -o test -Dtest=AccountApplicationServiceTest#shouldThrowOnWithdrawExceedingBalance`
Expected: FAIL — actual type is `IllegalArgumentException`

- [ ] **Step 3: Create the domain exception**

Create `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/account/InsufficientBalanceException.java`:

```java
package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

/**
 * Signals that an account's balance (plus any overdraft allowance) cannot cover a requested debit.
 *
 * <p>Extends {@link IllegalStateException} because a shortfall is a property of the account's
 * current state, not of the argument passed in — the requested amount is perfectly valid, there
 * is simply not enough money. This distinction is what lets {@code AccountApplicationService}
 * translate it to HTTP 422 (Unprocessable Entity) rather than 400 (Bad Request).
 *
 * <p>Named {@code InsufficientBalance} rather than {@code InsufficientFunds} to stay distinct from
 * {@code application.exception.InsufficientFundsException}, which is the outward-facing
 * application-layer equivalent it gets translated into.
 */
public class InsufficientBalanceException extends IllegalStateException {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}
```

- [ ] **Step 4: Throw it from all five domain sites**

`CheckingAccount.withdraw`:

```java
        if (projected.amount().compareTo(lowerBound.amount()) < 0) {
            if (overdraftLimit.isZero())
                throw new InsufficientBalanceException("Insufficient funds");
            throw new InsufficientBalanceException("Withdrawal exceeds overdraft limit");
        }
```

`CheckingAccount.transferOut`:

```java
        if (projected.amount().compareTo(lowerBound.amount()) < 0) {
            if (overdraftLimit.isZero())
                throw new InsufficientBalanceException("Insufficient funds for transfer including fee");
            throw new InsufficientBalanceException("Transfer exceeds overdraft limit");
        }
```

`SavingsAccount.withdraw`:

```java
        if (!this.balance.isGreaterThanOrEqualTo(amount))
            throw new InsufficientBalanceException("Insufficient funds");
```

`SavingsAccount.transferOut`:

```java
        if (!this.balance.isGreaterThanOrEqualTo(totalDebit))
            throw new InsufficientBalanceException("Insufficient funds for transfer including fee");
```

`TimeDepositAccount.withdraw`:

```java
        if (!this.balance.isGreaterThanOrEqualTo(amount))
            throw new InsufficientBalanceException("Insufficient funds");
```

- [ ] **Step 5: Translate it in the application service**

In `AccountApplicationService.withdraw`, replace the single catch with two — **specific first**. Java makes this ordering mandatory: a subclass catch placed after its superclass is a compile error, so the compiler prevents this translation from silently regressing.

```java
        Transaction tx;
        try {
            tx = account.withdraw(command.amount());
        } catch (InsufficientBalanceException e) {
            throw new InsufficientFundsException(e.getMessage());
        } catch (IllegalStateException e) {
            throw new InvalidAccountOperationException(e.getMessage());
        }
```

Same in `AccountApplicationService.transfer`:

```java
        Transaction outTx, inTx;
        try {
            outTx = source.transferOut(command.amount(), fee, target.getId().toString());
            inTx = target.transferIn(command.amount(), source.getId().toString());
        } catch (InsufficientBalanceException e) {
            throw new InsufficientFundsException(e.getMessage());
        } catch (IllegalStateException e) {
            throw new InvalidAccountOperationException(e.getMessage());
        }
```

Add the imports:

```java
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.InsufficientFundsException;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.InsufficientBalanceException;
```

(`domain.model.account.*` is already wildcard-imported at line 8, but the explicit import documents the dependency.)

- [ ] **Step 6: Update the four domain-test assertions**

These currently assert `IllegalArgumentException`. `InsufficientBalanceException` extends `IllegalStateException`, so they will fail until changed.

`AccountTest.shouldRejectWithdrawalExceedingBalance` (line ~174):

```java
        assertThatThrownBy(() -> account.withdraw(Money.of(200.0, Currency.USD)))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient");
```

`CheckingAccountTest` — the overdraft-exceeded test (line ~33):

```java
        assertThatThrownBy(() -> account.withdraw(Money.of(60.0, Currency.USD)))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("overdraft");
```

`CheckingAccountTest.shouldRejectWithdrawalWhenNoOverdraftAndInsufficientFunds` (line ~43):

```java
        assertThatThrownBy(() -> account.withdraw(Money.of(60.0, Currency.USD)))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient");
```

`SavingsAccountTest` (line ~40):

```java
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient");
```

- [ ] **Step 7: Run the whole suite**

Run: `mvn -o test`
Expected: 176 tests, 0 failures

- [ ] **Step 8: Commit**

```bash
git add src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/account/InsufficientBalanceException.java \
        src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/account/CheckingAccount.java \
        src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/account/SavingsAccount.java \
        src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/account/TimeDepositAccount.java \
        src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/application/service/AccountApplicationService.java \
        src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/
git commit -m "Return 422 for insufficient funds instead of 400

The domain threw IllegalArgumentException, which bypassed the application
service's translation and fell through to the catch-all handler as 400,
while the dedicated InsufficientFundsException -> 422 handler was never
reached by any production path."
```

---

### Task 3: The TransactionAmount value object

Introduces the type with nothing yet consuming it, so the suite stays green.

**Files:**
- Create: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/account/TransactionAmount.java`
- Test: `src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/account/TransactionAmountTest.java`

**Interfaces:**
- Consumes: `Money`, `Currency`
- Produces: `TransactionAmount` — a `record` over `Money value`, with statics `of(Money)`, `of(BigDecimal, Currency)`, `of(double, Currency)` and instance methods `asMoney()` → `Money`, `currency()` → `Currency`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/account/TransactionAmountTest.java`:

```java
package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TransactionAmount")
class TransactionAmountTest {

    @Test
    void shouldAcceptPositiveAmount() {
        TransactionAmount amount = TransactionAmount.of(100.0, Currency.USD);
        assertThat(amount.asMoney().amount()).isEqualByComparingTo("100.00");
        assertThat(amount.currency()).isEqualTo(Currency.USD);
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThatThrownBy(() -> TransactionAmount.of(-50.0, Currency.USD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    void shouldRejectZeroAmount() {
        assertThatThrownBy(() -> TransactionAmount.of(0.0, Currency.USD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    void shouldRejectNullMoney() {
        assertThatThrownBy(() -> TransactionAmount.of((Money) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    @DisplayName("rejects an amount that rounds down to zero at 2-decimal scale")
    void shouldRejectAmountRoundingToZero() {
        assertThatThrownBy(() -> TransactionAmount.of(new BigDecimal("0.001"), Currency.USD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");
    }

    @Test
    void shouldInheritTwoDecimalScalingFromMoney() {
        TransactionAmount amount = TransactionAmount.of(new BigDecimal("10.005"), Currency.EUR);
        assertThat(amount.asMoney().amount()).isEqualByComparingTo("10.01");
    }

    @Test
    void shouldBeEqualByValue() {
        assertThat(TransactionAmount.of(25.0, Currency.TRY))
                .isEqualTo(TransactionAmount.of(25.0, Currency.TRY))
                .isNotEqualTo(TransactionAmount.of(25.0, Currency.USD));
    }
}
```

Before running, confirm `Currency` declares `TRY` — check `Currency.java`. If it does not, substitute a constant that exists.

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -o test -Dtest=TransactionAmountTest`
Expected: FAIL — compilation error, `TransactionAmount` does not exist

- [ ] **Step 3: Write the implementation**

Create `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/account/TransactionAmount.java`:

```java
package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

import java.math.BigDecimal;

/**
 * A <b>strictly positive</b> monetary amount — the magnitude of a requested money movement.
 *
 * <p>This type exists because {@link Money} deliberately allows negative values: a
 * {@link CheckingAccount} balance goes negative under overdraft, and {@code Money.negate()}
 * builds the overdraft lower bound. {@code Money} therefore cannot enforce positivity, and
 * before this type existed every method taking an amount re-asserted the rule by hand — the
 * same guard written out seven times across the three account subclasses.
 *
 * <p>Making the constraint a property of the <i>type</i> means it is checked once, at
 * construction, and every downstream method can simply trust it: an amount that is not positive
 * cannot be built, so it cannot be passed. Impossible states become unrepresentable.
 *
 * <p><b>Zero is rejected as well as negative.</b> Direction is already carried by which operation
 * was called ({@code deposit} versus {@code withdraw}), so a signed amount is meaningless, and a
 * zero-value transfer would write two ledger rows recording no movement of money.
 *
 * <p>Wraps {@link Money} rather than re-implementing it, so all arithmetic and the 2-decimal
 * HALF_UP scaling stay in one place. Note that scaling happens first: an amount of
 * {@code 0.001} scales to {@code 0.00} and is then rejected.
 *
 * <h2>What deliberately keeps using Money</h2>
 * <ul>
 *   <li>{@code Account.balance} — signed by design; negative is a real overdraft position.
 *   <li>The {@code fee} argument of {@link Account#transferOut} — legitimately zero for
 *       same-customer transfers.
 *   <li>{@link Transaction#getAmount()} — a zero-interest accrual on a zero balance is a legal
 *       ledger entry.
 * </ul>
 * The type covers requests to move money, not records of money having moved. That boundary is
 * why this refactoring does not reach the persistence layer at all.
 */
public record TransactionAmount(Money value) {

    public TransactionAmount {
        if (value == null)
            throw new IllegalArgumentException("Transaction amount must not be null");
        if (value.amount().signum() <= 0)
            throw new IllegalArgumentException("Transaction amount must be positive, was " + value);
    }

    public static TransactionAmount of(Money money) {
        return new TransactionAmount(money);
    }

    public static TransactionAmount of(BigDecimal amount, Currency currency) {
        return new TransactionAmount(Money.of(amount, currency));
    }

    public static TransactionAmount of(double amount, Currency currency) {
        return new TransactionAmount(Money.of(amount, currency));
    }

    /** The underlying signed-capable {@link Money}, for arithmetic against balances. */
    public Money asMoney() { return value; }

    public Currency currency() { return value.currency(); }

    @Override
    public String toString() { return value.toString(); }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -o test -Dtest=TransactionAmountTest`
Expected: PASS, 7 tests

- [ ] **Step 5: Run the whole suite**

Run: `mvn -o test`
Expected: 183 tests, 0 failures

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/account/TransactionAmount.java \
        src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/account/TransactionAmountTest.java
git commit -m "Add TransactionAmount value object

Strictly positive by construction. Not yet wired into the account
operations - that is the next commit."
```

---

### Task 4: Thread TransactionAmount through the command surface

This task is necessarily atomic. Changing an abstract method's parameter type in a statically typed language breaks every implementor and caller in the same compile, so there is no smaller change that still builds. Do not commit partway through.

**Files:**
- Modify: `Account.java`, `CheckingAccount.java`, `SavingsAccount.java`, `TimeDepositAccount.java`
- Modify: `port/in/account/DepositMoneyUseCase.java`, `WithdrawMoneyUseCase.java`, `TransferMoneyUseCase.java`
- Modify: `domain/service/account/TransferDomainService.java`
- Modify: `application/service/AccountApplicationService.java`
- Modify: `adapter/in/web/AccountController.java`
- Test: `AccountTest`, `CheckingAccountTest`, `SavingsAccountTest`, `TimeDepositAccountTest`, `TransferDomainServiceTest`, `AccountApplicationServiceTest`

**Interfaces:**
- Consumes: `TransactionAmount` from Task 3, `InsufficientBalanceException` from Task 2
- Produces: `Account.deposit(TransactionAmount)`, `Account.withdraw(TransactionAmount)`, `Account.transferOut(TransactionAmount, Money, String)`, `Account.transferIn(TransactionAmount, String)`, `Account.requireSameCurrency(TransactionAmount)`; `DepositMoneyUseCase.Command(AccountId, TransactionAmount)`, `WithdrawMoneyUseCase.Command(AccountId, TransactionAmount)`, `TransferMoneyUseCase.Command(AccountId, AccountId, TransactionAmount)`; `TransferDomainService.calculateFee(TransactionAmount, boolean, BigDecimal, CustomerTier)`, `requireTransferWithinLimit(TransactionAmount, CustomerTier)`, `requireWithdrawalWithinLimit(TransactionAmount, CustomerTier)`

- [ ] **Step 1: Change the abstract signatures and the shared guard in `Account.java`**

```java
    public abstract Transaction deposit(TransactionAmount amount);

    public abstract Transaction withdraw(TransactionAmount amount);

    public abstract Transaction transferOut(TransactionAmount amount, Money fee, String targetAccountId);

    public final Transaction transferIn(TransactionAmount amount, String sourceAccountId) {
        requireActive();
        requireSameCurrency(amount);
        this.balance = this.balance.add(amount.asMoney());
        return Transaction.create(this.id, TransactionType.TRANSFER_IN, amount.asMoney(),
                "Transfer in from account " + sourceAccountId);
    }

    protected final void requireSameCurrency(TransactionAmount amount) {
        if (!amount.currency().equals(this.currency))
            throw new IllegalArgumentException("Currency " + amount.currency()
                    + " does not match account currency " + this.currency);
    }
```

Also update the Javadoc on `deposit` / `withdraw` / `transferOut` / `transferIn`: the existing text says `@throws IllegalArgumentException if the amount is negative or the currency does not match`. Negative amounts are now impossible to construct, so that clause must be reduced to the currency case.

- [ ] **Step 2: Rewrite the three `CheckingAccount` operations**

Each loses its `isNegative` guard. Note `deposit` keeps the guard restored in Task 1.

```java
    @Override
    public Transaction deposit(TransactionAmount amount) {
        requireActive();
        requireSameCurrency(amount);
        this.balance = this.balance.add(amount.asMoney());
        return Transaction.create(this.id, TransactionType.DEPOSIT, amount.asMoney(), "Deposit");
    }

    @Override
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

    @Override
    public Transaction transferOut(TransactionAmount amount, Money fee, String targetAccountId) {
        requireActive();
        requireSameCurrency(amount);
        Money totalDebit = fee.isZero() ? amount.asMoney() : amount.asMoney().add(fee);
        Money projected = this.balance.subtract(totalDebit);
        Money lowerBound = overdraftLimit.negate();
        if (projected.amount().compareTo(lowerBound.amount()) < 0) {
            if (overdraftLimit.isZero())
                throw new InsufficientBalanceException("Insufficient funds for transfer including fee");
            throw new InsufficientBalanceException("Transfer exceeds overdraft limit");
        }
        this.balance = projected;
        String desc = "Transfer out to account " + targetAccountId +
                (fee.isZero() ? "" : " (fee: " + fee + ")");
        return Transaction.create(this.id, TransactionType.TRANSFER_OUT, amount.asMoney(), desc);
    }
```

- [ ] **Step 3: Rewrite the three `SavingsAccount` operations**

```java
    @Override
    public Transaction deposit(TransactionAmount amount) {
        requireActive();
        requireSameCurrency(amount);
        this.balance = this.balance.add(amount.asMoney());
        return Transaction.create(this.id, TransactionType.DEPOSIT, amount.asMoney(), "Deposit");
    }

    @Override
    public Transaction withdraw(TransactionAmount amount) {
        requireActive();
        requireSameCurrency(amount);
        if (!this.balance.isGreaterThanOrEqualTo(amount.asMoney()))
            throw new InsufficientBalanceException("Insufficient funds");
        this.balance = this.balance.subtract(amount.asMoney());
        return Transaction.create(this.id, TransactionType.WITHDRAWAL, amount.asMoney(), "Withdrawal");
    }

    @Override
    public Transaction transferOut(TransactionAmount amount, Money fee, String targetAccountId) {
        requireActive();
        requireSameCurrency(amount);
        Money totalDebit = fee.isZero() ? amount.asMoney() : amount.asMoney().add(fee);
        if (!this.balance.isGreaterThanOrEqualTo(totalDebit))
            throw new InsufficientBalanceException("Insufficient funds for transfer including fee");
        this.balance = this.balance.subtract(totalDebit);
        String desc = "Transfer out to account " + targetAccountId +
                (fee.isZero() ? "" : " (fee: " + fee + ")");
        return Transaction.create(this.id, TransactionType.TRANSFER_OUT, amount.asMoney(), desc);
    }
```

`accrueInterest` is **not** changed — it builds a `Money` interest value that may legally be zero and passes it straight to `Transaction.create`.

- [ ] **Step 4: Rewrite the three `TimeDepositAccount` operations**

```java
    @Override
    public Transaction deposit(TransactionAmount amount) {
        throw new IllegalStateException("Time deposit principal is locked — further deposits are not allowed");
    }

    @Override
    public Transaction withdraw(TransactionAmount amount) {
        requireActive();
        if (!matured)
            throw new IllegalStateException("Time deposit has not matured");
        requireSameCurrency(amount);
        if (!this.balance.isGreaterThanOrEqualTo(amount.asMoney()))
            throw new InsufficientBalanceException("Insufficient funds");
        this.balance = this.balance.subtract(amount.asMoney());
        return Transaction.create(this.id, TransactionType.WITHDRAWAL, amount.asMoney(), "Withdrawal");
    }

    @Override
    public Transaction transferOut(TransactionAmount amount, Money fee, String targetAccountId) {
        throw new IllegalStateException("Time deposit accounts do not support transfers");
    }
```

`mature` is **not** changed — like `accrueInterest`, it creates a `Money` interest value.

- [ ] **Step 5: Change the three use-case `Command` records**

`DepositMoneyUseCase.java` — replace the `Money` import with `TransactionAmount`:

```java
public interface DepositMoneyUseCase {
    record Command(AccountId accountId, TransactionAmount amount) {}
    Transaction deposit(Command command);
}
```

`WithdrawMoneyUseCase.java`:

```java
public interface WithdrawMoneyUseCase {
    record Command(AccountId accountId, TransactionAmount amount) {}
    Transaction withdraw(Command command);
}
```

`TransferMoneyUseCase.java`:

```java
public interface TransferMoneyUseCase {
    record Command(AccountId sourceAccountId, AccountId targetAccountId, TransactionAmount amount) {}
    void transfer(Command command);
}
```

- [ ] **Step 6: Change the three `TransferDomainService` methods**

```java
    public Money calculateFee(TransactionAmount amount, boolean sameCustomer,
                              BigDecimal feePercent, CustomerTier sourceTier) {
        if (sameCustomer) {
            return Money.zero(amount.currency());
        }
        BigDecimal scaledPercent = feePercent.multiply(sourceTier.feeMultiplier());
        BigDecimal feeAmount = amount.asMoney().amount()
                .multiply(scaledPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return Money.of(feeAmount, amount.currency());
    }

    public void requireTransferWithinLimit(TransactionAmount amount, CustomerTier tier) {
        tier.maxPerTransfer().ifPresent(cap -> {
            if (amount.asMoney().amount().compareTo(cap) > 0)
                throw new IllegalStateException(
                        "Transfer amount " + amount + " exceeds " + tier + " tier limit of " + cap);
        });
    }

    public void requireWithdrawalWithinLimit(TransactionAmount amount, CustomerTier tier) {
        tier.maxPerWithdrawal().ifPresent(cap -> {
            if (amount.asMoney().amount().compareTo(cap) > 0)
                throw new IllegalStateException(
                        "Withdrawal amount " + amount + " exceeds " + tier + " tier limit of " + cap);
        });
    }
```

The return type stays `Money` — a fee is legitimately zero.

Add the import: `import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.TransactionAmount;`

- [ ] **Step 7: Update `AccountController` — the single construction point**

Three call sites; each swaps `Money.of(...)` for `TransactionAmount.of(...)`:

```java
        var tx = depositMoney.deposit(new DepositMoneyUseCase.Command(
                AccountId.of(accountId), TransactionAmount.of(request.amount(), request.currency())));
```

```java
        var tx = withdrawMoney.withdraw(new WithdrawMoneyUseCase.Command(
                AccountId.of(accountId), TransactionAmount.of(request.amount(), request.currency())));
```

```java
        transferMoney.transfer(new TransferMoneyUseCase.Command(
                AccountId.of(accountId),
                AccountId.of(request.targetAccountId()),
                TransactionAmount.of(request.amount(), request.currency())));
```

Add `import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.TransactionAmount;`.

Leave the `Money` import and the two `Money.of` / `Money.zero` calls in `openCheckingAccount` and `openTimeDepositAccount` alone — an overdraft limit and a time-deposit principal are not transaction amounts.

- [ ] **Step 8: `AccountApplicationService` needs no body changes, only verification**

`command.amount()` is now a `TransactionAmount` and flows straight into `account.deposit(...)` / `withdraw(...)` / `transferOut(...)` / `transferIn(...)` and into the three `transferDomainService` calls, all of which now expect that type. Compile and confirm; make no edits unless the compiler demands one.

- [ ] **Step 9: Update the test call sites**

Across `AccountTest`, `CheckingAccountTest`, `SavingsAccountTest`, `TimeDepositAccountTest`, `TransferDomainServiceTest`, and `AccountApplicationServiceTest`, every `Money.of(...)` passed as an *amount* becomes `TransactionAmount.of(...)`. Add the import where needed.

Leave as `Money`: overdraft limits, transfer fees (the second argument to `transferOut`), time-deposit principals, and balance assertions. Specifically keep `AccountApplicationServiceTest:105` (`Money.zero(Currency.EUR)` — an overdraft limit) and `AccountTest:124` (`Money.zero(Currency.USD)` — a fee) unchanged.

Read the compiler errors and fix them one file at a time rather than editing blind.

- [ ] **Step 10: Delete the now-dead negative-amount tests**

Search for tests asserting that a negative amount is rejected by an account operation. Such a test can no longer be written — the argument cannot be constructed — and its coverage now lives in `TransactionAmountTest.shouldRejectNegativeAmount`. Run:

```bash
grep -rn "cannot be negative" src/test/java
```

Remove any test that this returns. If it returns nothing, there is nothing to remove.

- [ ] **Step 11: Run the whole suite**

Run: `mvn -o clean test`
Expected: 0 failures. Record the exact test count for Task 5.

- [ ] **Step 12: Verify the persistence layer really was untouched**

Run:

```bash
git status --short src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/out/
```

Expected: **no output**. If any persistence file is listed, the scope boundary described in the design has been crossed — stop and re-read the design before continuing.

- [ ] **Step 13: Commit**

```bash
git add -A src/
git commit -m "Thread TransactionAmount through the account command surface

Replaces seven hand-written negative-amount guards with one constructor
check. Money stays signed so overdraft keeps working; fees, balances and
Transaction.amount keep using Money because zero is legal for all three.
The persistence layer is untouched."
```

---

### Task 5: Documentation

**Files:**
- Create: `Refactorings.md`
- Modify: `CLAUDE.md`, `Domain.md`, `Tests.md`
- Modify: `docs/superpowers/specs/2026-08-07-transaction-amount-design.md`

**Interfaces:**
- Consumes: the final test count from Task 4 Step 11
- Produces: nothing consumed by other tasks

- [ ] **Step 1: Write `Refactorings.md`**

Create it at the project root, starting with the model name, version, and creation date per the global standard. Structure it as an accumulating log so later refactorings append as entry #2, #3, and so on. Entry #1 must contain:

1. **The symptom** — the same guard written 7 times, with the file-and-line table from the design doc.
2. **The rejected fix** — validating in `Money`'s constructor, and the three pieces of evidence that rule it out (`CheckingAccount.java:75` and `:81`, `MoneyTest.java:22,54,69`).
3. **The diagnosis** — one type serving two concepts: a signed *balance* and a positive *amount*. A type serving both can enforce neither.
4. **Before and after**, shown as adjacent code blocks — use `CheckingAccount.withdraw` as the worked example, since it shows the guard removal and the exception change together.
5. **The principle** — making impossible states unrepresentable. The guard stops being a runtime check repeated at every call site and becomes a property of the type, verified once at construction. Note that the compiler now rejects a negative amount at the call site rather than the JVM rejecting it at runtime.
6. **Where the type stops and why** — the fee, the balance, and `Transaction.amount` all keep using `Money` because zero is legal for each; cite `TransferDomainService.calculateFee` for the fee and `SavingsAccount.accrueInterest` for the zero-interest accrual. State the payoff: the persistence layer needed no changes at all.
7. **The two defects**, including the corrected account of Defect 2 — `AccountApplicationServiceTest:249-252` asserted `IllegalArgumentException` (→ 400) while `AccountControllerTest:255` mocked `InsufficientFundsException` (→ 422). Both passed; together they described a path the application could not produce. The lesson is about testing a layer against its neighbor's actual output rather than against an assumption about it.

Quote no number that the test suite does not assert.

- [ ] **Step 2: Update `CLAUDE.md`**

Add a bullet to **Key Design Decisions**, after the "Value objects as records" bullet:

```markdown
- **`TransactionAmount` vs `Money`**: `Money` is signed — negative balances are real (overdraft), so it cannot enforce positivity. `TransactionAmount` wraps `Money` and is strictly positive by construction; it types the *command* surface (`deposit`/`withdraw`/`transferOut`/`transferIn` and their `Command` records). Fees, balances and `Transaction.amount` keep using `Money`, because zero is legal for all three. See `Refactorings.md` entry #1.
```

Also amend the "Value objects as records" bullet to list `TransactionAmount` alongside the others.

- [ ] **Step 3: Update `Domain.md`**

Add `TransactionAmount` to the account-aggregate value objects, stating the invariant (strictly positive, 2-decimal HALF_UP scaling inherited from `Money`) and its relationship to `Money`. Add `InsufficientBalanceException` to the domain's exception vocabulary, noting it extends `IllegalStateException` and is translated to `InsufficientFundsException` → HTTP 422 by the application service.

- [ ] **Step 4: Update `Tests.md`**

Add the `TransactionAmountTest` section with its per-test descriptions, and correct the per-class counts changed by this work. Use the actual count recorded in Task 4 Step 11 — do not estimate.

- [ ] **Step 5: Correct the design doc**

In `docs/superpowers/specs/2026-08-07-transaction-amount-design.md`, the "Defect 2" section says the mocked controller tests hid the bug. Replace that with the accurate version: `AccountApplicationServiceTest:249-252` pinned `IllegalArgumentException` and `AccountControllerTest:255` mocked `InsufficientFundsException`; each passed alone, but jointly they asserted an unreachable end-to-end path.

- [ ] **Step 6: Verify every documented claim**

For each file-and-line citation written in Task 5, run the command that proves it before considering the task done — for example:

```bash
grep -n "requireSameCurrency" src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/account/CheckingAccount.java
grep -rn "isNegative()" src/main/java
```

The second must no longer match any account subclass. Fix any citation that has drifted.

- [ ] **Step 7: Final verification**

Run: `mvn clean verify`
Expected: BUILD SUCCESS, 0 failures.

Then confirm the guard count really is zero in the account classes:

```bash
grep -rn "cannot be negative" src/main/java
```

Expected: no matches in `CheckingAccount`, `SavingsAccount`, or `TimeDepositAccount`.

- [ ] **Step 8: Commit**

```bash
git add Refactorings.md CLAUDE.md Domain.md Tests.md docs/superpowers/
git commit -m "Document the TransactionAmount refactoring

Adds Refactorings.md entry #1 with the before/after and the
impossible-states rationale; syncs CLAUDE.md, Domain.md and Tests.md."
```

---

## Self-Review

**Spec coverage.** Every design section maps to a task: the value object → Task 3; the scope boundary → Task 4 Steps 3, 4, 6, 12; Defect 1 → Task 1; Defect 2 → Task 2; the zero behavior change → Task 3 Step 1; the documentation deliverable → Task 5. The design's file table is reproduced across Tasks 1–4 with no entry unclaimed.

**Placeholder scan.** No TBDs. Every code step carries the actual code. Task 4 Step 9 and Task 5 Steps 1–4 are the only judgment-based steps; each names the exact files, the rule to apply, and the cases to leave alone.

**Type consistency.** `TransactionAmount.of` / `asMoney()` / `currency()` are defined in Task 3 and used with those exact names in Task 4. `InsufficientBalanceException` is defined in Task 2 and used in Task 4 Steps 2–4. `Account.requireSameCurrency` changes from `Money` to `TransactionAmount` in Task 4 Step 1 and is called with a `TransactionAmount` everywhere thereafter.

**Known risk.** Task 4 is large and cannot be subdivided without leaving a non-compiling tree. Step 12's persistence check is the guard against scope creep inside it.
