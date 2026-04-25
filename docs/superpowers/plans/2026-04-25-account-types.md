# Account Types (Sealed Hierarchy) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single `Account` class with a sealed hierarchy of `CheckingAccount` (overdraft), `SavingsAccount` (interest accrual), and `TimeDepositAccount` (locked principal until maturity), and expose them through dedicated open-account endpoints plus admin operations for interest accrual and maturity.

**Architecture:** `Account` becomes a sealed abstract class permitting three concrete subclasses. Each subclass overrides `deposit`/`withdraw` and adds type-specific behavior. Persistence stays in a single `accounts` table with a `type` discriminator and nullable type-specific columns; the mapper switches on `type` to construct the right subclass. JPA `ddl-auto=update` adds new columns automatically — no migration script needed. Use cases split per-type to keep each `Command` record honest.

**Tech Stack:** Java 25 sealed classes, Spring Boot 3.4, Spring Data JPA, JUnit 5 + AssertJ + Mockito, Spring Security (HTTP Basic).

**Defaults locked from brainstorming:**
- Manual interest-accrual endpoint (admin), no scheduler
- No early withdrawal on time deposits — funds locked until `mature()` is called
- Frozen savings accounts still accrue interest (system action, not customer action)
- Time-deposit principal supplied at open, locked immediately
- Customer tiers deferred to a follow-up plan

---

## File Structure

**New domain files:**
- `domain/model/AccountType.java` — enum: `CHECKING`, `SAVINGS`, `TIME_DEPOSIT`
- `domain/model/CheckingAccount.java` — overdraft semantics
- `domain/model/SavingsAccount.java` — interest accrual
- `domain/model/TimeDepositAccount.java` — locked principal + maturity

**Modified domain files:**
- `domain/model/Account.java` — becomes `sealed abstract`, exposes protected mutators
- `domain/model/TransactionType.java` — add `INTEREST`

**New / replaced ports:**
- `domain/port/in/OpenCheckingAccountUseCase.java` — replaces `CreateAccountUseCase`
- `domain/port/in/OpenSavingsAccountUseCase.java`
- `domain/port/in/OpenTimeDepositAccountUseCase.java`
- `domain/port/in/AccrueInterestUseCase.java`
- `domain/port/in/MatureTimeDepositUseCase.java`

**Deleted ports:**
- `domain/port/in/CreateAccountUseCase.java`

**Modified persistence:**
- `adapter/out/persistence/entity/AccountJpaEntity.java` — adds `type`, `overdraftLimit`, `interestRate`, `maturityDate`, `principal`, `lastAccrualDate`
- `adapter/out/persistence/mapper/AccountPersistenceMapper.java` — type-aware mapping

**Modified application service:**
- `application/service/AccountApplicationService.java` — implements new use cases, drops `createAccount`

**Modified web layer:**
- `adapter/in/web/AccountController.java` — three open endpoints, response now polymorphic
- `adapter/in/web/AdminController.java` — add accrue-interest and mature endpoints
- `adapter/in/web/dto/request/OpenCheckingAccountRequest.java` — new
- `adapter/in/web/dto/request/OpenSavingsAccountRequest.java` — new
- `adapter/in/web/dto/request/OpenTimeDepositAccountRequest.java` — new
- `adapter/in/web/dto/request/CreateAccountRequest.java` — deleted
- `adapter/in/web/dto/response/AccountResponse.java` — adds `type` and nullable type-specific fields

**New tests:**
- `test/.../domain/model/CheckingAccountTest.java`
- `test/.../domain/model/SavingsAccountTest.java`
- `test/.../domain/model/TimeDepositAccountTest.java`

**Modified tests:**
- `test/.../domain/model/AccountTest.java` — uses `CheckingAccount.open(...)` factory
- `test/.../application/service/AccountApplicationServiceTest.java` — new factory + new use cases
- `test/.../adapter/in/web/AccountControllerTest.java` — new endpoints
- `test/.../adapter/in/web/AdminControllerTest.java` — accrue-interest + mature

**Docs:**
- `CLAUDE.md` — REST API table + design-decisions section
- `README.md` — Java version fix (21 → 25), domain section
- `Architecture.md` — account hierarchy section
- `Tests.md` — updated test counts

---

## Task 0: Worktree (optional but recommended)

**Files:** none — git plumbing only

- [ ] **Step 1: Create isolated worktree**

The user has uncommitted edits to `CLAUDE.md`, `Tests.md`, and `pom.xml` on `master` that are unrelated to this work. Use a worktree to keep them separate.

Run:
```bash
git -C /Users/akin/Development/Claude/AyvalikBankHA1 worktree add ../AyvalikBankHA1-account-types -b account-types
cd ../AyvalikBankHA1-account-types
```

Expected: a new directory `../AyvalikBankHA1-account-types` on a fresh `account-types` branch.

If the user prefers to skip the worktree and just create a branch in place, run `git checkout -b account-types` instead.

---

## Task 1: Add `AccountType` enum

**Files:**
- Create: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/AccountType.java`

- [ ] **Step 1: Create the enum**

```java
package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model;

public enum AccountType {
    CHECKING,
    SAVINGS,
    TIME_DEPOSIT
}
```

- [ ] **Step 2: Verify compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/AccountType.java
git commit -m "Add AccountType enum (CHECKING, SAVINGS, TIME_DEPOSIT)"
```

---

## Task 2: Add `INTEREST` to `TransactionType`

**Files:**
- Modify: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/TransactionType.java`

- [ ] **Step 1: Add the new constant**

Replace file contents with:
```java
package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model;

public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER_OUT,
    TRANSFER_IN,
    INTEREST
}
```

- [ ] **Step 2: Verify compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/TransactionType.java
git commit -m "Add INTEREST transaction type for savings accrual"
```

---

## Task 3: Convert `Account` to sealed abstract base

**Files:**
- Modify: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/Account.java`

This task only refactors the existing class — no behavior changes for callers. The static `open(...)` factory is kept temporarily so existing tests still compile; it now returns a `CheckingAccount` (created in Task 4). Since `CheckingAccount` doesn't exist yet, this task will leave the project briefly uncompilable; we fix that in Task 4.

- [ ] **Step 1: Rewrite `Account.java`**

Replace file contents with:
```java
package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model;

public sealed abstract class Account
        permits CheckingAccount, SavingsAccount, TimeDepositAccount {

    protected final AccountId id;
    protected final CustomerId ownerId;
    protected final Currency currency;
    protected Money balance;
    protected AccountStatus status;

    protected Account(AccountId id, CustomerId ownerId, Currency currency, Money balance, AccountStatus status) {
        if (!balance.currency().equals(currency))
            throw new IllegalArgumentException("Balance currency must match account currency");
        this.id = id;
        this.ownerId = ownerId;
        this.currency = currency;
        this.balance = balance;
        this.status = status;
    }

    public abstract AccountType type();

    // ── Status transitions (shared, final) ────────────────────────────────

    public final void freeze() {
        if (status == AccountStatus.CLOSED)
            throw new IllegalStateException("Cannot freeze a closed account");
        if (status == AccountStatus.FROZEN)
            throw new IllegalStateException("Account is already frozen");
        this.status = AccountStatus.FROZEN;
    }

    public final void unfreeze() {
        if (status == AccountStatus.CLOSED)
            throw new IllegalStateException("Cannot unfreeze a closed account");
        if (status == AccountStatus.ACTIVE)
            throw new IllegalStateException("Account is not frozen");
        this.status = AccountStatus.ACTIVE;
    }

    public final void close() {
        if (status == AccountStatus.CLOSED)
            throw new IllegalStateException("Account is already closed");
        this.status = AccountStatus.CLOSED;
    }

    // ── Operations: each subtype overrides ────────────────────────────────

    public abstract Transaction deposit(Money amount);

    public abstract Transaction withdraw(Money amount);

    public abstract Transaction transferOut(Money amount, Money fee, String targetAccountId);

    public final Transaction transferIn(Money amount, String sourceAccountId) {
        requireActive();
        requireSameCurrency(amount);
        this.balance = this.balance.add(amount);
        return Transaction.create(this.id, TransactionType.TRANSFER_IN, amount, "Transfer in from account " + sourceAccountId);
    }

    // ── Guards (visible to subclasses) ────────────────────────────────────

    protected final void requireActive() {
        if (status == AccountStatus.FROZEN)
            throw new IllegalStateException("Account is frozen");
        if (status == AccountStatus.CLOSED)
            throw new IllegalStateException("Account is closed");
    }

    protected final void requireSameCurrency(Money amount) {
        if (!amount.currency().equals(this.currency))
            throw new IllegalArgumentException("Currency " + amount.currency() + " does not match account currency " + this.currency);
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public AccountId getId() { return id; }
    public CustomerId getOwnerId() { return ownerId; }
    public Currency getCurrency() { return currency; }
    public Money getBalance() { return balance; }
    public AccountStatus getStatus() { return status; }
}
```

Note: the static `open(...)` factory is removed. All call sites are updated to use the per-type factories starting in Task 4.

- [ ] **Step 2: Do not compile yet**

The project will not compile until Task 4 — that is expected. Skip the compile check and move on.

- [ ] **Step 3: Do not commit yet**

Combined commit happens at the end of Task 4.

---

## Task 4: Create `CheckingAccount`

**Files:**
- Create: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/CheckingAccount.java`
- Test: `src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/CheckingAccountTest.java`
- Modify: `src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/AccountTest.java` (replace `Account.open(...)` calls)

`CheckingAccount` adds an `overdraftLimit` (default `Money.zero(currency)` = no overdraft). Withdrawals are allowed if `balance − amount >= −overdraftLimit`. Behavior with `overdraftLimit = 0` is identical to the previous `Account`, so the existing AccountTest cases remain valid after a factory swap.

- [ ] **Step 1: Write the failing test for the no-overdraft case**

Create `src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/CheckingAccountTest.java`:
```java
package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CheckingAccountTest {

    @Test
    void shouldOpenWithZeroBalanceAndNoOverdraftByDefault() {
        CheckingAccount account = CheckingAccount.open(CustomerId.generate(), Currency.USD);
        assertThat(account.type()).isEqualTo(AccountType.CHECKING);
        assertThat(account.getBalance().amount()).isEqualByComparingTo("0.00");
        assertThat(account.getOverdraftLimit().amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldWithdrawIntoOverdraftWhenLimitAllows() {
        CheckingAccount account = CheckingAccount.open(
                CustomerId.generate(), Currency.USD, Money.of(100.0, Currency.USD));
        account.deposit(Money.of(50.0, Currency.USD));
        account.withdraw(Money.of(120.0, Currency.USD));
        assertThat(account.getBalance().amount()).isEqualByComparingTo("-70.00");
    }

    @Test
    void shouldRejectWithdrawalBeyondOverdraftLimit() {
        CheckingAccount account = CheckingAccount.open(
                CustomerId.generate(), Currency.USD, Money.of(50.0, Currency.USD));
        assertThatThrownBy(() -> account.withdraw(Money.of(60.0, Currency.USD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overdraft");
    }

    @Test
    void shouldRejectWithdrawalWhenNoOverdraftAndInsufficientFunds() {
        CheckingAccount account = CheckingAccount.open(CustomerId.generate(), Currency.USD);
        account.deposit(Money.of(50.0, Currency.USD));
        assertThatThrownBy(() -> account.withdraw(Money.of(60.0, Currency.USD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient");
    }
}
```

`Money.of(-70.0, USD)` would throw because `Money` rejects negative amounts. So balance representation must change for overdraft. Important design note: `Money` enforces non-negative. Either:
- (A) Loosen `Money` to allow negative, or
- (B) Track balance as `BigDecimal` internally on `CheckingAccount`, with `getBalance()` returning `Money` only when non-negative, or returning a new `SignedMoney` type.

Choose **(A)**: loosen `Money` to allow negative amounts. This is the simplest change and matches real banking semantics. Money was designed too restrictively — bank balances and ledger entries can be negative. The `subtract` method's "Insufficient funds" check is callsite-specific anyway and will be removed in Step 2 below; per-account-type rules apply instead.

- [ ] **Step 2: Loosen `Money` to allow negative amounts**

This change requires updating `Money.java` first. Modify `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/Money.java`:

```java
package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(BigDecimal amount, Currency currency) {

    public Money {
        if (amount == null) throw new IllegalArgumentException("Amount must not be null");
        if (currency == null) throw new IllegalArgumentException("Currency must not be null");
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money of(double amount, Currency currency) {
        return new Money(BigDecimal.valueOf(amount), currency);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public Money negate() {
        return new Money(this.amount.negate(), this.currency);
    }

    public Money multiply(BigDecimal factor) {
        return new Money(this.amount.multiply(factor).setScale(2, RoundingMode.HALF_UP), this.currency);
    }

    public boolean isGreaterThanOrEqualTo(Money other) {
        requireSameCurrency(other);
        return this.amount.compareTo(other.amount) >= 0;
    }

    public boolean isNegative() {
        return this.amount.signum() < 0;
    }

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency))
            throw new IllegalArgumentException("Currency mismatch: " + this.currency + " vs " + other.currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
```

Two behavior changes: (1) negative amounts are allowed; (2) `subtract` no longer enforces "insufficient funds" — callers do that at the right level (each account type knows its own rule).

Update `MoneyTest` accordingly: any test that asserts negative-amount rejection or subtract-throws-on-overdraw is no longer valid. Open `src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/MoneyTest.java`, find tests asserting these behaviors, and either delete them or replace with the new behavior. (Run tests after the update — failures will pinpoint the cases.)

- [ ] **Step 3: Create `CheckingAccount.java`**

Create `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/CheckingAccount.java`:
```java
package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model;

import java.math.BigDecimal;

public final class CheckingAccount extends Account {

    private final Money overdraftLimit;

    public CheckingAccount(AccountId id, CustomerId ownerId, Currency currency,
                           Money balance, AccountStatus status, Money overdraftLimit) {
        super(id, ownerId, currency, balance, status);
        if (!overdraftLimit.currency().equals(currency))
            throw new IllegalArgumentException("Overdraft limit currency must match account currency");
        if (overdraftLimit.isNegative())
            throw new IllegalArgumentException("Overdraft limit cannot be negative");
        this.overdraftLimit = overdraftLimit;
    }

    public static CheckingAccount open(CustomerId ownerId, Currency currency) {
        return open(ownerId, currency, Money.zero(currency));
    }

    public static CheckingAccount open(CustomerId ownerId, Currency currency, Money overdraftLimit) {
        return new CheckingAccount(
                AccountId.generate(), ownerId, currency,
                Money.zero(currency), AccountStatus.ACTIVE, overdraftLimit);
    }

    @Override
    public AccountType type() { return AccountType.CHECKING; }

    public Money getOverdraftLimit() { return overdraftLimit; }

    @Override
    public Transaction deposit(Money amount) {
        requireActive();
        requireSameCurrency(amount);
        if (amount.isNegative())
            throw new IllegalArgumentException("Deposit amount cannot be negative");
        this.balance = this.balance.add(amount);
        return Transaction.create(this.id, TransactionType.DEPOSIT, amount, "Deposit");
    }

    @Override
    public Transaction withdraw(Money amount) {
        requireActive();
        requireSameCurrency(amount);
        if (amount.isNegative())
            throw new IllegalArgumentException("Withdrawal amount cannot be negative");
        Money projected = this.balance.subtract(amount);
        Money lowerBound = overdraftLimit.negate();
        if (projected.amount().compareTo(lowerBound.amount()) < 0) {
            if (overdraftLimit.amount().compareTo(BigDecimal.ZERO) == 0)
                throw new IllegalArgumentException("Insufficient funds");
            throw new IllegalArgumentException("Withdrawal exceeds overdraft limit");
        }
        this.balance = projected;
        return Transaction.create(this.id, TransactionType.WITHDRAWAL, amount, "Withdrawal");
    }

    @Override
    public Transaction transferOut(Money amount, Money fee, String targetAccountId) {
        requireActive();
        requireSameCurrency(amount);
        if (amount.isNegative())
            throw new IllegalArgumentException("Transfer amount cannot be negative");
        Money totalDebit = fee.amount().compareTo(BigDecimal.ZERO) > 0 ? amount.add(fee) : amount;
        Money projected = this.balance.subtract(totalDebit);
        Money lowerBound = overdraftLimit.negate();
        if (projected.amount().compareTo(lowerBound.amount()) < 0) {
            if (overdraftLimit.amount().compareTo(BigDecimal.ZERO) == 0)
                throw new IllegalArgumentException("Insufficient funds for transfer including fee");
            throw new IllegalArgumentException("Transfer exceeds overdraft limit");
        }
        this.balance = projected;
        String desc = "Transfer out to account " + targetAccountId +
                (fee.amount().compareTo(BigDecimal.ZERO) > 0 ? " (fee: " + fee + ")" : "");
        return Transaction.create(this.id, TransactionType.TRANSFER_OUT, amount, desc);
    }
}
```

- [ ] **Step 4: Update `AccountTest.java` to use the new factory**

In `src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/AccountTest.java`, replace the helper:
```java
private Account openUsdAccount() {
    return Account.open(CustomerId.generate(), Currency.USD);
}
```
with:
```java
private Account openUsdAccount() {
    return CheckingAccount.open(CustomerId.generate(), Currency.USD);
}
```

The body of all other tests is unchanged: `CheckingAccount` with `overdraftLimit = 0` behaves identically to the old `Account` for these cases.

- [ ] **Step 5: Run tests**

Run: `mvn -q test -Dtest='AccountTest,CheckingAccountTest,MoneyTest'`
Expected: PASS for AccountTest and CheckingAccountTest. MoneyTest may show failures for the now-removed "negative amount rejected" / "subtract throws on overdraw" cases — fix those by either deleting the obsolete tests or rewriting them to assert the new behavior (negative amounts allowed; subtract returns negative result without throwing).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/Account.java \
        src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/CheckingAccount.java \
        src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/Money.java \
        src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/CheckingAccountTest.java \
        src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/AccountTest.java \
        src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/MoneyTest.java
git commit -m "Make Account sealed; add CheckingAccount with overdraft; allow negative Money"
```

---

## Task 5: Create `SavingsAccount`

**Files:**
- Create: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/SavingsAccount.java`
- Test: `src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/SavingsAccountTest.java`

Savings accounts hold an annual interest rate as a `BigDecimal` (e.g. `0.03` = 3%). They reject overdraft — withdrawals require non-negative resulting balance. `accrueInterest(YearMonth)` calculates interest as `balance * (annualRate / 12)`, credits it, updates `lastAccrualDate` to the first of the month after the accrued one, and returns a `Transaction` of type `INTEREST`. Calling `accrueInterest` for the same month twice is rejected. Frozen savings accounts still accrue interest (system action); closed accounts do not.

- [ ] **Step 1: Write failing tests**

Create `src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/SavingsAccountTest.java`:
```java
package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.*;

class SavingsAccountTest {

    private SavingsAccount openUsdSavings(String annualRate) {
        return SavingsAccount.open(CustomerId.generate(), Currency.USD, new BigDecimal(annualRate));
    }

    @Test
    void shouldOpenWithGivenInterestRateAndZeroBalance() {
        SavingsAccount account = openUsdSavings("0.03");
        assertThat(account.type()).isEqualTo(AccountType.SAVINGS);
        assertThat(account.getAnnualInterestRate()).isEqualByComparingTo("0.03");
        assertThat(account.getBalance().amount()).isEqualByComparingTo("0.00");
    }

    @Test
    void shouldRejectNegativeInterestRate() {
        assertThatThrownBy(() ->
                SavingsAccount.open(CustomerId.generate(), Currency.USD, new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectWithdrawalThatWouldOverdraw() {
        SavingsAccount account = openUsdSavings("0.03");
        account.deposit(Money.of(100.0, Currency.USD));
        assertThatThrownBy(() -> account.withdraw(Money.of(101.0, Currency.USD)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient");
    }

    @Test
    void shouldAccrueInterestForAMonth() {
        SavingsAccount account = openUsdSavings("0.12"); // 1% per month
        account.deposit(Money.of(1000.0, Currency.USD));
        Transaction tx = account.accrueInterest(YearMonth.of(2026, 4));
        assertThat(tx.getType()).isEqualTo(TransactionType.INTEREST);
        assertThat(tx.getAmount().amount()).isEqualByComparingTo("10.00");
        assertThat(account.getBalance().amount()).isEqualByComparingTo("1010.00");
        assertThat(account.getLastAccrualDate()).isEqualTo(LocalDate.of(2026, 5, 1));
    }

    @Test
    void shouldAccrueInterestEvenWhenFrozen() {
        SavingsAccount account = openUsdSavings("0.12");
        account.deposit(Money.of(1000.0, Currency.USD));
        account.freeze();
        Transaction tx = account.accrueInterest(YearMonth.of(2026, 4));
        assertThat(tx.getAmount().amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void shouldRejectAccrualOnClosedAccount() {
        SavingsAccount account = openUsdSavings("0.12");
        account.close();
        assertThatThrownBy(() -> account.accrueInterest(YearMonth.of(2026, 4)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void shouldRejectDoubleAccrualForSameMonth() {
        SavingsAccount account = openUsdSavings("0.12");
        account.deposit(Money.of(1000.0, Currency.USD));
        account.accrueInterest(YearMonth.of(2026, 4));
        assertThatThrownBy(() -> account.accrueInterest(YearMonth.of(2026, 4)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already accrued");
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn -q test -Dtest=SavingsAccountTest`
Expected: COMPILATION FAILURE (`SavingsAccount` doesn't exist).

- [ ] **Step 3: Create `SavingsAccount.java`**

Create `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/SavingsAccount.java`:
```java
package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

public final class SavingsAccount extends Account {

    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    private final BigDecimal annualInterestRate;
    private LocalDate lastAccrualDate;

    public SavingsAccount(AccountId id, CustomerId ownerId, Currency currency,
                          Money balance, AccountStatus status,
                          BigDecimal annualInterestRate, LocalDate lastAccrualDate) {
        super(id, ownerId, currency, balance, status);
        if (annualInterestRate == null || annualInterestRate.signum() < 0)
            throw new IllegalArgumentException("Annual interest rate must be non-negative");
        this.annualInterestRate = annualInterestRate;
        this.lastAccrualDate = lastAccrualDate;
    }

    public static SavingsAccount open(CustomerId ownerId, Currency currency, BigDecimal annualInterestRate) {
        return new SavingsAccount(
                AccountId.generate(), ownerId, currency,
                Money.zero(currency), AccountStatus.ACTIVE,
                annualInterestRate, null);
    }

    @Override
    public AccountType type() { return AccountType.SAVINGS; }

    public BigDecimal getAnnualInterestRate() { return annualInterestRate; }
    public LocalDate getLastAccrualDate() { return lastAccrualDate; }

    @Override
    public Transaction deposit(Money amount) {
        requireActive();
        requireSameCurrency(amount);
        if (amount.isNegative())
            throw new IllegalArgumentException("Deposit amount cannot be negative");
        this.balance = this.balance.add(amount);
        return Transaction.create(this.id, TransactionType.DEPOSIT, amount, "Deposit");
    }

    @Override
    public Transaction withdraw(Money amount) {
        requireActive();
        requireSameCurrency(amount);
        if (amount.isNegative())
            throw new IllegalArgumentException("Withdrawal amount cannot be negative");
        if (!this.balance.isGreaterThanOrEqualTo(amount))
            throw new IllegalArgumentException("Insufficient funds");
        this.balance = this.balance.subtract(amount);
        return Transaction.create(this.id, TransactionType.WITHDRAWAL, amount, "Withdrawal");
    }

    @Override
    public Transaction transferOut(Money amount, Money fee, String targetAccountId) {
        requireActive();
        requireSameCurrency(amount);
        if (amount.isNegative())
            throw new IllegalArgumentException("Transfer amount cannot be negative");
        Money totalDebit = fee.amount().compareTo(BigDecimal.ZERO) > 0 ? amount.add(fee) : amount;
        if (!this.balance.isGreaterThanOrEqualTo(totalDebit))
            throw new IllegalArgumentException("Insufficient funds for transfer including fee");
        this.balance = this.balance.subtract(totalDebit);
        String desc = "Transfer out to account " + targetAccountId +
                (fee.amount().compareTo(BigDecimal.ZERO) > 0 ? " (fee: " + fee + ")" : "");
        return Transaction.create(this.id, TransactionType.TRANSFER_OUT, amount, desc);
    }

    public Transaction accrueInterest(YearMonth month) {
        if (status == AccountStatus.CLOSED)
            throw new IllegalStateException("Cannot accrue interest on a closed account");
        LocalDate firstOfNextMonth = month.plusMonths(1).atDay(1);
        if (lastAccrualDate != null && !firstOfNextMonth.isAfter(lastAccrualDate))
            throw new IllegalStateException("Interest already accrued for or after " + month);
        BigDecimal monthlyRate = annualInterestRate.divide(MONTHS_PER_YEAR, 10, RoundingMode.HALF_UP);
        Money interest = this.balance.multiply(monthlyRate);
        this.balance = this.balance.add(interest);
        this.lastAccrualDate = firstOfNextMonth;
        return Transaction.create(this.id, TransactionType.INTEREST, interest,
                "Interest accrual for " + month);
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=SavingsAccountTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/SavingsAccount.java \
        src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/SavingsAccountTest.java
git commit -m "Add SavingsAccount with monthly interest accrual"
```

---

## Task 6: Create `TimeDepositAccount`

**Files:**
- Create: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/TimeDepositAccount.java`
- Test: `src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/TimeDepositAccountTest.java`

Time deposit holds `principal`, `maturityDate`, `annualInterestRate`. Opens with the principal already deposited (balance = principal). Once opened: deposits are rejected. Withdrawals are rejected before maturity. Calling `mature(LocalDate today)` is allowed when `today >= maturityDate` and the account is `ACTIVE` or `FROZEN`; it credits accrued interest as a single `INTEREST` transaction (`principal * rate * yearsHeld`) and unlocks withdrawals. After maturity the account behaves like a free-withdraw account (still no deposits — principal is "fixed").

For simplicity, `yearsHeld` = number of full months between `openedAt` (passed in) and `maturityDate` divided by 12. Track `openedAt` (LocalDate) on the entity for this calculation.

- [ ] **Step 1: Write failing tests**

Create `src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/TimeDepositAccountTest.java`:
```java
package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class TimeDepositAccountTest {

    private TimeDepositAccount openOneYearUsdDeposit() {
        return TimeDepositAccount.open(
                CustomerId.generate(), Currency.USD,
                Money.of(1000.0, Currency.USD),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2027, 4, 1),
                new BigDecimal("0.05"));
    }

    @Test
    void shouldOpenWithPrincipalAsBalance() {
        TimeDepositAccount account = openOneYearUsdDeposit();
        assertThat(account.type()).isEqualTo(AccountType.TIME_DEPOSIT);
        assertThat(account.getBalance().amount()).isEqualByComparingTo("1000.00");
        assertThat(account.isMatured()).isFalse();
    }

    @Test
    void shouldRejectFurtherDeposits() {
        TimeDepositAccount account = openOneYearUsdDeposit();
        assertThatThrownBy(() -> account.deposit(Money.of(100.0, Currency.USD)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("locked");
    }

    @Test
    void shouldRejectWithdrawalBeforeMaturity() {
        TimeDepositAccount account = openOneYearUsdDeposit();
        assertThatThrownBy(() -> account.withdraw(Money.of(100.0, Currency.USD)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("matured");
    }

    @Test
    void shouldRejectMaturationBeforeMaturityDate() {
        TimeDepositAccount account = openOneYearUsdDeposit();
        assertThatThrownBy(() -> account.mature(LocalDate.of(2027, 3, 31)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not yet");
    }

    @Test
    void shouldMatureOnOrAfterMaturityDateAndCreditInterest() {
        TimeDepositAccount account = openOneYearUsdDeposit();
        Transaction tx = account.mature(LocalDate.of(2027, 4, 1));
        assertThat(tx.getType()).isEqualTo(TransactionType.INTEREST);
        assertThat(tx.getAmount().amount()).isEqualByComparingTo("50.00"); // 1000 * 0.05 * 1yr
        assertThat(account.getBalance().amount()).isEqualByComparingTo("1050.00");
        assertThat(account.isMatured()).isTrue();
    }

    @Test
    void shouldAllowWithdrawalAfterMaturity() {
        TimeDepositAccount account = openOneYearUsdDeposit();
        account.mature(LocalDate.of(2027, 4, 1));
        account.withdraw(Money.of(500.0, Currency.USD));
        assertThat(account.getBalance().amount()).isEqualByComparingTo("550.00");
    }

    @Test
    void shouldRejectDoubleMaturation() {
        TimeDepositAccount account = openOneYearUsdDeposit();
        account.mature(LocalDate.of(2027, 4, 1));
        assertThatThrownBy(() -> account.mature(LocalDate.of(2027, 4, 2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already matured");
    }

    @Test
    void shouldRejectMaturationOnClosedAccount() {
        TimeDepositAccount account = openOneYearUsdDeposit();
        account.close();
        assertThatThrownBy(() -> account.mature(LocalDate.of(2027, 4, 1)))
                .isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn -q test -Dtest=TimeDepositAccountTest`
Expected: COMPILATION FAILURE.

- [ ] **Step 3: Create `TimeDepositAccount.java`**

Create `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/TimeDepositAccount.java`:
```java
package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class TimeDepositAccount extends Account {

    private final Money principal;
    private final LocalDate openedOn;
    private final LocalDate maturityDate;
    private final BigDecimal annualInterestRate;
    private boolean matured;

    public TimeDepositAccount(AccountId id, CustomerId ownerId, Currency currency,
                              Money balance, AccountStatus status,
                              Money principal, LocalDate openedOn, LocalDate maturityDate,
                              BigDecimal annualInterestRate, boolean matured) {
        super(id, ownerId, currency, balance, status);
        if (!principal.currency().equals(currency))
            throw new IllegalArgumentException("Principal currency must match account currency");
        if (principal.amount().signum() <= 0)
            throw new IllegalArgumentException("Principal must be positive");
        if (annualInterestRate == null || annualInterestRate.signum() < 0)
            throw new IllegalArgumentException("Annual interest rate must be non-negative");
        if (openedOn == null || maturityDate == null)
            throw new IllegalArgumentException("Opened-on and maturity dates are required");
        if (!maturityDate.isAfter(openedOn))
            throw new IllegalArgumentException("Maturity date must be after opened-on date");
        this.principal = principal;
        this.openedOn = openedOn;
        this.maturityDate = maturityDate;
        this.annualInterestRate = annualInterestRate;
        this.matured = matured;
    }

    public static TimeDepositAccount open(CustomerId ownerId, Currency currency,
                                          Money principal, LocalDate openedOn,
                                          LocalDate maturityDate, BigDecimal annualInterestRate) {
        return new TimeDepositAccount(
                AccountId.generate(), ownerId, currency,
                principal, AccountStatus.ACTIVE,
                principal, openedOn, maturityDate, annualInterestRate, false);
    }

    @Override
    public AccountType type() { return AccountType.TIME_DEPOSIT; }

    public Money getPrincipal() { return principal; }
    public LocalDate getOpenedOn() { return openedOn; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public BigDecimal getAnnualInterestRate() { return annualInterestRate; }
    public boolean isMatured() { return matured; }

    @Override
    public Transaction deposit(Money amount) {
        throw new IllegalStateException("Time deposit principal is locked — further deposits are not allowed");
    }

    @Override
    public Transaction withdraw(Money amount) {
        requireActive();
        if (!matured)
            throw new IllegalStateException("Time deposit has not matured");
        requireSameCurrency(amount);
        if (amount.isNegative())
            throw new IllegalArgumentException("Withdrawal amount cannot be negative");
        if (!this.balance.isGreaterThanOrEqualTo(amount))
            throw new IllegalArgumentException("Insufficient funds");
        this.balance = this.balance.subtract(amount);
        return Transaction.create(this.id, TransactionType.WITHDRAWAL, amount, "Withdrawal");
    }

    @Override
    public Transaction transferOut(Money amount, Money fee, String targetAccountId) {
        throw new IllegalStateException("Time deposit accounts do not support transfers");
    }

    public Transaction mature(LocalDate today) {
        if (status == AccountStatus.CLOSED)
            throw new IllegalStateException("Cannot mature a closed account");
        if (matured)
            throw new IllegalStateException("Account is already matured");
        if (today.isBefore(maturityDate))
            throw new IllegalStateException("Maturity date not yet reached");
        long months = ChronoUnit.MONTHS.between(openedOn, maturityDate);
        BigDecimal years = BigDecimal.valueOf(months).divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        Money interest = principal.multiply(annualInterestRate.multiply(years));
        this.balance = this.balance.add(interest);
        this.matured = true;
        return Transaction.create(this.id, TransactionType.INTEREST, interest,
                "Maturity interest credit");
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn -q test -Dtest=TimeDepositAccountTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/TimeDepositAccount.java \
        src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/model/TimeDepositAccountTest.java
git commit -m "Add TimeDepositAccount with locked principal and maturity"
```

---

## Task 7: Update `AccountJpaEntity` with type-specific columns

**Files:**
- Modify: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/out/persistence/entity/AccountJpaEntity.java`

JPA `ddl-auto=update` will add the new columns automatically — they must be nullable since older rows pre-date them.

- [ ] **Step 1: Update entity with new fields**

Replace file contents with:
```java
package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class AccountJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "type", nullable = false, length = 16)
    private String type;

    // Checking
    @Column(name = "overdraft_limit", precision = 19, scale = 2)
    private BigDecimal overdraftLimit;

    // Savings
    @Column(name = "interest_rate", precision = 10, scale = 6)
    private BigDecimal interestRate;

    @Column(name = "last_accrual_date")
    private LocalDate lastAccrualDate;

    // Time deposit
    @Column(name = "principal", precision = 19, scale = 2)
    private BigDecimal principal;

    @Column(name = "opened_on")
    private LocalDate openedOn;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "matured")
    private Boolean matured;

    public AccountJpaEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public BigDecimal getOverdraftLimit() { return overdraftLimit; }
    public void setOverdraftLimit(BigDecimal overdraftLimit) { this.overdraftLimit = overdraftLimit; }
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }
    public LocalDate getLastAccrualDate() { return lastAccrualDate; }
    public void setLastAccrualDate(LocalDate lastAccrualDate) { this.lastAccrualDate = lastAccrualDate; }
    public BigDecimal getPrincipal() { return principal; }
    public void setPrincipal(BigDecimal principal) { this.principal = principal; }
    public LocalDate getOpenedOn() { return openedOn; }
    public void setOpenedOn(LocalDate openedOn) { this.openedOn = openedOn; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate; }
    public Boolean getMatured() { return matured; }
    public void setMatured(Boolean matured) { this.matured = matured; }
}
```

- [ ] **Step 2: Verify compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Update `data.sql` to backfill `type` for legacy rows**

Modify `src/main/resources/data.sql`:
```sql
-- Default transfer fee: 1%
INSERT INTO settings (key, value)
VALUES ('TRANSFER_FEE_PERCENT', '1.0')
ON CONFLICT (key) DO NOTHING;

-- Backfill: any pre-existing accounts (created before account types) are CHECKING with no overdraft.
UPDATE accounts
SET type = 'CHECKING', overdraft_limit = 0
WHERE type IS NULL;
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/out/persistence/entity/AccountJpaEntity.java \
        src/main/resources/data.sql
git commit -m "Add account type columns to AccountJpaEntity and backfill in data.sql"
```

---

## Task 8: Update `AccountPersistenceMapper` for sealed hierarchy

**Files:**
- Modify: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/out/persistence/mapper/AccountPersistenceMapper.java`

- [ ] **Step 1: Replace mapper with type-aware logic**

Replace file contents with:
```java
package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.mapper;

import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.entity.AccountJpaEntity;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.*;
import org.springframework.stereotype.Component;

@Component
public class AccountPersistenceMapper {

    public Account toDomain(AccountJpaEntity entity) {
        Currency currency = Currency.valueOf(entity.getCurrency());
        Money balance = Money.of(entity.getBalance(), currency);
        AccountStatus status = AccountStatus.valueOf(entity.getStatus());
        AccountType type = AccountType.valueOf(entity.getType());
        return switch (type) {
            case CHECKING -> new CheckingAccount(
                    AccountId.of(entity.getId()),
                    CustomerId.of(entity.getOwnerId()),
                    currency, balance, status,
                    Money.of(entity.getOverdraftLimit(), currency));
            case SAVINGS -> new SavingsAccount(
                    AccountId.of(entity.getId()),
                    CustomerId.of(entity.getOwnerId()),
                    currency, balance, status,
                    entity.getInterestRate(),
                    entity.getLastAccrualDate());
            case TIME_DEPOSIT -> new TimeDepositAccount(
                    AccountId.of(entity.getId()),
                    CustomerId.of(entity.getOwnerId()),
                    currency, balance, status,
                    Money.of(entity.getPrincipal(), currency),
                    entity.getOpenedOn(),
                    entity.getMaturityDate(),
                    entity.getInterestRate(),
                    Boolean.TRUE.equals(entity.getMatured()));
        };
    }

    public AccountJpaEntity toJpaEntity(Account account) {
        AccountJpaEntity entity = new AccountJpaEntity();
        entity.setId(account.getId().value());
        entity.setOwnerId(account.getOwnerId().value());
        entity.setCurrency(account.getCurrency().name());
        entity.setBalance(account.getBalance().amount());
        entity.setStatus(account.getStatus().name());
        entity.setType(account.type().name());
        switch (account) {
            case CheckingAccount c -> entity.setOverdraftLimit(c.getOverdraftLimit().amount());
            case SavingsAccount s -> {
                entity.setInterestRate(s.getAnnualInterestRate());
                entity.setLastAccrualDate(s.getLastAccrualDate());
            }
            case TimeDepositAccount t -> {
                entity.setPrincipal(t.getPrincipal().amount());
                entity.setOpenedOn(t.getOpenedOn());
                entity.setMaturityDate(t.getMaturityDate());
                entity.setInterestRate(t.getAnnualInterestRate());
                entity.setMatured(t.isMatured());
            }
        }
        return entity;
    }
}
```

- [ ] **Step 2: Verify compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/out/persistence/mapper/AccountPersistenceMapper.java
git commit -m "Make AccountPersistenceMapper aware of account type hierarchy"
```

---

## Task 9: Replace `CreateAccountUseCase` with three open-account use cases

**Files:**
- Delete: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/port/in/CreateAccountUseCase.java`
- Create: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/port/in/OpenCheckingAccountUseCase.java`
- Create: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/port/in/OpenSavingsAccountUseCase.java`
- Create: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/port/in/OpenTimeDepositAccountUseCase.java`

- [ ] **Step 1: Delete the old port**

Run: `rm src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/port/in/CreateAccountUseCase.java`

- [ ] **Step 2: Create the three new ports**

`OpenCheckingAccountUseCase.java`:
```java
package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.CheckingAccount;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.Currency;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.Money;

public interface OpenCheckingAccountUseCase {
    record Command(CustomerId ownerId, Currency currency, Money overdraftLimit) {}
    CheckingAccount openChecking(Command command);
}
```

`OpenSavingsAccountUseCase.java`:
```java
package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.Currency;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.SavingsAccount;

import java.math.BigDecimal;

public interface OpenSavingsAccountUseCase {
    record Command(CustomerId ownerId, Currency currency, BigDecimal annualInterestRate) {}
    SavingsAccount openSavings(Command command);
}
```

`OpenTimeDepositAccountUseCase.java`:
```java
package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.Currency;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.Money;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.TimeDepositAccount;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface OpenTimeDepositAccountUseCase {
    record Command(CustomerId ownerId, Currency currency, Money principal,
                   LocalDate maturityDate, BigDecimal annualInterestRate) {}
    TimeDepositAccount openTimeDeposit(Command command);
}
```

- [ ] **Step 3: Do not compile yet**

`AccountApplicationService` and `AccountController` still reference the old port. Compilation fixed in Task 10.

- [ ] **Step 4: Stage but don't commit**

Combined commit at the end of Task 10.

---

## Task 10: Add accrue-interest and mature use cases + update `AccountApplicationService`

**Files:**
- Create: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/port/in/AccrueInterestUseCase.java`
- Create: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/port/in/MatureTimeDepositUseCase.java`
- Modify: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/application/service/AccountApplicationService.java`
- Create: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/application/exception/InvalidAccountOperationException.java`

The new exception lets us convert "wrong account type" or "operation not supported" failures from the domain (`IllegalStateException`) into a typed application error → HTTP 422 in the global handler.

- [ ] **Step 1: Create new use case ports**

`AccrueInterestUseCase.java`:
```java
package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.AccountId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.Transaction;

import java.time.YearMonth;

public interface AccrueInterestUseCase {
    record Command(AccountId accountId, YearMonth month) {}
    Transaction accrueInterest(Command command);
}
```

`MatureTimeDepositUseCase.java`:
```java
package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.AccountId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.Transaction;

public interface MatureTimeDepositUseCase {
    Transaction mature(AccountId accountId);
}
```

- [ ] **Step 2: Create `InvalidAccountOperationException`**

Create `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/application/exception/InvalidAccountOperationException.java`:
```java
package dev.kaldiroglu.hexagonal.ayvalikbank.application.exception;

public class InvalidAccountOperationException extends RuntimeException {
    public InvalidAccountOperationException(String message) {
        super(message);
    }
}
```

- [ ] **Step 3: Update `GlobalExceptionHandler` to map it to 422**

Open `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/GlobalExceptionHandler.java` and add a handler mirroring the existing `AccountNotOperableException` handler. Add the import and the method:
```java
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.InvalidAccountOperationException;
// ...
@ExceptionHandler(InvalidAccountOperationException.class)
public ResponseEntity<ProblemDetail> handleInvalidOperation(InvalidAccountOperationException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    pd.setTitle("Invalid Account Operation");
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(pd);
}
```
(Use the exact `ProblemDetail`/response shape that the existing handlers in this file use — match their style.)

- [ ] **Step 4: Replace `AccountApplicationService.java`**

Replace file contents with:
```java
package dev.kaldiroglu.hexagonal.ayvalikbank.application.service;

import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.AccountNotFoundException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.AccountNotOperableException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.CustomerNotFoundException;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.InvalidAccountOperationException;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.AccountRepositoryPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.CustomerRepositoryPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.SettingsRepositoryPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.TransactionRepositoryPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.service.TransferDomainService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class AccountApplicationService implements
        OpenCheckingAccountUseCase,
        OpenSavingsAccountUseCase,
        OpenTimeDepositAccountUseCase,
        DepositMoneyUseCase,
        WithdrawMoneyUseCase,
        GetBalanceUseCase,
        GetTransactionsUseCase,
        TransferMoneyUseCase,
        ListAccountsUseCase,
        FreezeAccountUseCase,
        UnfreezeAccountUseCase,
        CloseAccountUseCase,
        AccrueInterestUseCase,
        MatureTimeDepositUseCase {

    private final AccountRepositoryPort accountRepository;
    private final CustomerRepositoryPort customerRepository;
    private final TransactionRepositoryPort transactionRepository;
    private final SettingsRepositoryPort settingsRepository;
    private final TransferDomainService transferDomainService;

    public AccountApplicationService(AccountRepositoryPort accountRepository,
                                     CustomerRepositoryPort customerRepository,
                                     TransactionRepositoryPort transactionRepository,
                                     SettingsRepositoryPort settingsRepository,
                                     TransferDomainService transferDomainService) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
        this.settingsRepository = settingsRepository;
        this.transferDomainService = transferDomainService;
    }

    @Override
    public CheckingAccount openChecking(OpenCheckingAccountUseCase.Command command) {
        requireCustomerExists(command.ownerId());
        Money limit = command.overdraftLimit() == null ? Money.zero(command.currency()) : command.overdraftLimit();
        CheckingAccount account = CheckingAccount.open(command.ownerId(), command.currency(), limit);
        return (CheckingAccount) accountRepository.save(account);
    }

    @Override
    public SavingsAccount openSavings(OpenSavingsAccountUseCase.Command command) {
        requireCustomerExists(command.ownerId());
        SavingsAccount account = SavingsAccount.open(command.ownerId(), command.currency(), command.annualInterestRate());
        return (SavingsAccount) accountRepository.save(account);
    }

    @Override
    public TimeDepositAccount openTimeDeposit(OpenTimeDepositAccountUseCase.Command command) {
        requireCustomerExists(command.ownerId());
        TimeDepositAccount account = TimeDepositAccount.open(
                command.ownerId(), command.currency(), command.principal(),
                LocalDate.now(), command.maturityDate(), command.annualInterestRate());
        return (TimeDepositAccount) accountRepository.save(account);
    }

    @Override
    public Transaction deposit(DepositMoneyUseCase.Command command) {
        Account account = findAccountOrThrow(command.accountId());
        Transaction tx;
        try {
            tx = account.deposit(command.amount());
        } catch (IllegalStateException e) {
            throw new InvalidAccountOperationException(e.getMessage());
        }
        accountRepository.save(account);
        return transactionRepository.save(tx);
    }

    @Override
    public Transaction withdraw(WithdrawMoneyUseCase.Command command) {
        Account account = findAccountOrThrow(command.accountId());
        Transaction tx;
        try {
            tx = account.withdraw(command.amount());
        } catch (IllegalStateException e) {
            throw new InvalidAccountOperationException(e.getMessage());
        }
        accountRepository.save(account);
        return transactionRepository.save(tx);
    }

    @Override
    @Transactional(readOnly = true)
    public Money getBalance(AccountId accountId) {
        return findAccountOrThrow(accountId).getBalance();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transaction> getTransactions(AccountId accountId) {
        findAccountOrThrow(accountId);
        return transactionRepository.findByAccountId(accountId);
    }

    @Override
    public void transfer(TransferMoneyUseCase.Command command) {
        Account source = findAccountOrThrow(command.sourceAccountId());
        Account target = findAccountOrThrow(command.targetAccountId());

        boolean sameCustomer = source.getOwnerId().equals(target.getOwnerId());
        BigDecimal feePercent = settingsRepository.getTransferFeePercent();
        Money fee = transferDomainService.calculateFee(command.amount(), sameCustomer, feePercent);

        Transaction outTx, inTx;
        try {
            outTx = source.transferOut(command.amount(), fee, target.getId().toString());
            inTx = target.transferIn(command.amount(), source.getId().toString());
        } catch (IllegalStateException e) {
            throw new InvalidAccountOperationException(e.getMessage());
        }

        accountRepository.save(source);
        accountRepository.save(target);
        transactionRepository.save(outTx);
        transactionRepository.save(inTx);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Account> listAccounts(CustomerId ownerId) {
        requireCustomerExists(ownerId);
        return accountRepository.findByOwnerId(ownerId);
    }

    @Override
    public void freezeAccount(AccountId accountId) {
        Account account = findAccountOrThrow(accountId);
        try { account.freeze(); }
        catch (IllegalStateException e) { throw new AccountNotOperableException(e.getMessage()); }
        accountRepository.save(account);
    }

    @Override
    public void unfreezeAccount(AccountId accountId) {
        Account account = findAccountOrThrow(accountId);
        try { account.unfreeze(); }
        catch (IllegalStateException e) { throw new AccountNotOperableException(e.getMessage()); }
        accountRepository.save(account);
    }

    @Override
    public void closeAccount(AccountId accountId) {
        Account account = findAccountOrThrow(accountId);
        try { account.close(); }
        catch (IllegalStateException e) { throw new AccountNotOperableException(e.getMessage()); }
        accountRepository.save(account);
    }

    @Override
    public Transaction accrueInterest(AccrueInterestUseCase.Command command) {
        Account account = findAccountOrThrow(command.accountId());
        if (!(account instanceof SavingsAccount savings))
            throw new InvalidAccountOperationException("Account is not a savings account");
        Transaction tx;
        try { tx = savings.accrueInterest(command.month()); }
        catch (IllegalStateException e) { throw new InvalidAccountOperationException(e.getMessage()); }
        accountRepository.save(savings);
        return transactionRepository.save(tx);
    }

    @Override
    public Transaction mature(AccountId accountId) {
        Account account = findAccountOrThrow(accountId);
        if (!(account instanceof TimeDepositAccount td))
            throw new InvalidAccountOperationException("Account is not a time deposit");
        Transaction tx;
        try { tx = td.mature(LocalDate.now()); }
        catch (IllegalStateException e) { throw new InvalidAccountOperationException(e.getMessage()); }
        accountRepository.save(td);
        return transactionRepository.save(tx);
    }

    private void requireCustomerExists(CustomerId id) {
        if (!customerRepository.existsById(id))
            throw new CustomerNotFoundException("Customer not found: " + id);
    }

    private Account findAccountOrThrow(AccountId accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));
    }
}
```

- [ ] **Step 5: Verify compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS (controllers will fail their compile in the next task — confirm with the explicit-target compile here, not the full test compile).

- [ ] **Step 6: Stage; commit at end of Task 11**

---

## Task 11: Update `AccountController` and `AdminController`; update DTOs

**Files:**
- Delete: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/dto/request/CreateAccountRequest.java`
- Create: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/dto/request/OpenCheckingAccountRequest.java`
- Create: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/dto/request/OpenSavingsAccountRequest.java`
- Create: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/dto/request/OpenTimeDepositAccountRequest.java`
- Create: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/dto/request/AccrueInterestRequest.java`
- Modify: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/dto/response/AccountResponse.java`
- Modify: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/AccountController.java`
- Modify: `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/AdminController.java`

- [ ] **Step 1: Delete old request DTO**

Run: `rm src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/dto/request/CreateAccountRequest.java`

- [ ] **Step 2: Create the new request DTOs**

`OpenCheckingAccountRequest.java`:
```java
package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.request;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.Currency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record OpenCheckingAccountRequest(
        @NotNull Currency currency,
        @PositiveOrZero BigDecimal overdraftLimit) {}
```

`OpenSavingsAccountRequest.java`:
```java
package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.request;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.Currency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record OpenSavingsAccountRequest(
        @NotNull Currency currency,
        @NotNull @PositiveOrZero BigDecimal annualInterestRate) {}
```

`OpenTimeDepositAccountRequest.java`:
```java
package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.request;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.Currency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OpenTimeDepositAccountRequest(
        @NotNull Currency currency,
        @NotNull @Positive BigDecimal principal,
        @NotNull LocalDate maturityDate,
        @NotNull @PositiveOrZero BigDecimal annualInterestRate) {}
```

`AccrueInterestRequest.java`:
```java
package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.YearMonth;

public record AccrueInterestRequest(@NotNull YearMonth month) {}
```

- [ ] **Step 3: Update `AccountResponse` to be polymorphic**

Replace `AccountResponse.java` contents with:
```java
package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.response;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AccountResponse(
        String id,
        String ownerId,
        String currency,
        BigDecimal balance,
        String status,
        String type,
        BigDecimal overdraftLimit,
        BigDecimal interestRate,
        LocalDate lastAccrualDate,
        BigDecimal principal,
        LocalDate openedOn,
        LocalDate maturityDate,
        Boolean matured) {

    public static AccountResponse from(Account account) {
        String id = account.getId().toString();
        String ownerId = account.getOwnerId().toString();
        String currency = account.getCurrency().name();
        BigDecimal balance = account.getBalance().amount();
        String status = account.getStatus().name();
        String type = account.type().name();
        return switch (account) {
            case CheckingAccount c -> new AccountResponse(
                    id, ownerId, currency, balance, status, type,
                    c.getOverdraftLimit().amount(),
                    null, null, null, null, null, null);
            case SavingsAccount s -> new AccountResponse(
                    id, ownerId, currency, balance, status, type,
                    null,
                    s.getAnnualInterestRate(), s.getLastAccrualDate(),
                    null, null, null, null);
            case TimeDepositAccount t -> new AccountResponse(
                    id, ownerId, currency, balance, status, type,
                    null,
                    t.getAnnualInterestRate(), null,
                    t.getPrincipal().amount(), t.getOpenedOn(), t.getMaturityDate(), t.isMatured());
        };
    }
}
```

- [ ] **Step 4: Replace `AccountController.java`**

Replace file contents with:
```java
package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web;

import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.request.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.response.AccountResponse;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.response.BalanceResponse;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web.dto.response.TransactionResponse;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.AccountId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.Money;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.in.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AccountController {

    private final OpenCheckingAccountUseCase openChecking;
    private final OpenSavingsAccountUseCase openSavings;
    private final OpenTimeDepositAccountUseCase openTimeDeposit;
    private final DepositMoneyUseCase depositMoney;
    private final WithdrawMoneyUseCase withdrawMoney;
    private final GetBalanceUseCase getBalance;
    private final GetTransactionsUseCase getTransactions;
    private final TransferMoneyUseCase transferMoney;
    private final ListAccountsUseCase listAccounts;

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

    @PostMapping("/accounts/checking")
    public ResponseEntity<AccountResponse> openCheckingAccount(@RequestParam String ownerId,
                                                                @Valid @RequestBody OpenCheckingAccountRequest request) {
        Money overdraft = request.overdraftLimit() == null
                ? Money.zero(request.currency())
                : Money.of(request.overdraftLimit(), request.currency());
        var account = openChecking.openChecking(new OpenCheckingAccountUseCase.Command(
                CustomerId.of(ownerId), request.currency(), overdraft));
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @PostMapping("/accounts/savings")
    public ResponseEntity<AccountResponse> openSavingsAccount(@RequestParam String ownerId,
                                                                @Valid @RequestBody OpenSavingsAccountRequest request) {
        var account = openSavings.openSavings(new OpenSavingsAccountUseCase.Command(
                CustomerId.of(ownerId), request.currency(), request.annualInterestRate()));
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @PostMapping("/accounts/time-deposit")
    public ResponseEntity<AccountResponse> openTimeDepositAccount(@RequestParam String ownerId,
                                                                    @Valid @RequestBody OpenTimeDepositAccountRequest request) {
        var account = openTimeDeposit.openTimeDeposit(new OpenTimeDepositAccountUseCase.Command(
                CustomerId.of(ownerId), request.currency(),
                Money.of(request.principal(), request.currency()),
                request.maturityDate(), request.annualInterestRate()));
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @GetMapping("/customers/{customerId}/accounts")
    public ResponseEntity<List<AccountResponse>> listAccounts(@PathVariable String customerId) {
        var accounts = listAccounts.listAccounts(CustomerId.of(customerId)).stream()
                .map(AccountResponse::from).toList();
        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable String accountId) {
        Money balance = getBalance.getBalance(AccountId.of(accountId));
        return ResponseEntity.ok(BalanceResponse.from(balance));
    }

    @PostMapping("/accounts/{accountId}/deposit")
    public ResponseEntity<TransactionResponse> deposit(@PathVariable String accountId,
                                                        @Valid @RequestBody MoneyOperationRequest request) {
        var tx = depositMoney.deposit(new DepositMoneyUseCase.Command(
                AccountId.of(accountId), Money.of(request.amount(), request.currency())));
        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(tx));
    }

    @PostMapping("/accounts/{accountId}/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(@PathVariable String accountId,
                                                         @Valid @RequestBody MoneyOperationRequest request) {
        var tx = withdrawMoney.withdraw(new WithdrawMoneyUseCase.Command(
                AccountId.of(accountId), Money.of(request.amount(), request.currency())));
        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(tx));
    }

    @PostMapping("/accounts/{accountId}/transfer")
    public ResponseEntity<Void> transfer(@PathVariable String accountId,
                                          @Valid @RequestBody TransferRequest request) {
        transferMoney.transfer(new TransferMoneyUseCase.Command(
                AccountId.of(accountId),
                AccountId.of(request.targetAccountId()),
                Money.of(request.amount(), request.currency())));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<List<TransactionResponse>> getTransactions(@PathVariable String accountId) {
        var txs = getTransactions.getTransactions(AccountId.of(accountId)).stream()
                .map(TransactionResponse::from).toList();
        return ResponseEntity.ok(txs);
    }
}
```

- [ ] **Step 5: Update `AdminController`**

Open `src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/AdminController.java` and add two new dependencies + two new endpoints, following the existing pattern:

Constructor parameters to add:
- `AccrueInterestUseCase accrueInterest`
- `MatureTimeDepositUseCase matureTimeDeposit`

Add fields, constructor assignments, and methods:
```java
private final AccrueInterestUseCase accrueInterest;
private final MatureTimeDepositUseCase matureTimeDeposit;

// (in constructor) this.accrueInterest = accrueInterest; this.matureTimeDeposit = matureTimeDeposit;

@PutMapping("/accounts/{accountId}/accrue-interest")
public ResponseEntity<TransactionResponse> accrueInterest(@PathVariable String accountId,
                                                           @Valid @RequestBody AccrueInterestRequest request) {
    var tx = accrueInterest.accrueInterest(new AccrueInterestUseCase.Command(
            AccountId.of(accountId), request.month()));
    return ResponseEntity.ok(TransactionResponse.from(tx));
}

@PutMapping("/accounts/{accountId}/mature")
public ResponseEntity<TransactionResponse> matureTimeDeposit(@PathVariable String accountId) {
    var tx = matureTimeDeposit.mature(AccountId.of(accountId));
    return ResponseEntity.ok(TransactionResponse.from(tx));
}
```
Add the necessary imports (`AccrueInterestUseCase`, `MatureTimeDepositUseCase`, `AccrueInterestRequest`, `TransactionResponse`).

- [ ] **Step 6: Verify compile**

Run: `mvn -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit (combined: tasks 9–11)**

```bash
git add src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/port/in/ \
        src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/application/exception/InvalidAccountOperationException.java \
        src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/application/service/AccountApplicationService.java \
        src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/AccountController.java \
        src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/AdminController.java \
        src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/GlobalExceptionHandler.java \
        src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/dto/request/ \
        src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/dto/response/AccountResponse.java
git rm src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/domain/port/in/CreateAccountUseCase.java \
       src/main/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/dto/request/CreateAccountRequest.java
git commit -m "Wire account types through use cases, application service, and REST endpoints"
```

---

## Task 12: Fix existing test classes for new use cases and factories

**Files:**
- Modify: `src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/application/service/AccountApplicationServiceTest.java`
- Modify: `src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/AccountControllerTest.java`
- Modify: `src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/AdminControllerTest.java`

These tests currently reference `CreateAccountUseCase`, `Account.open(...)`, and `POST /api/accounts`. They need targeted edits — the structural pattern stays, only types and endpoints change.

- [ ] **Step 1: Run all tests to discover the breakage surface**

Run: `mvn -q test`
Expected: numerous compilation/assertion failures in the three test classes above.

- [ ] **Step 2: Fix `AccountApplicationServiceTest.java`**

Search-and-replace patterns (use the IDE or `sed` carefully):
- `CreateAccountUseCase` → `OpenCheckingAccountUseCase` (where the test exercises the open-account path)
- `Account.open(ownerId, currency)` → `CheckingAccount.open(ownerId, currency)`
- `createAccount.createAccount(new CreateAccountUseCase.Command(...))` → `service.openChecking(new OpenCheckingAccountUseCase.Command(ownerId, currency, Money.zero(currency)))`

Where the test injects mocks of use cases, no change is needed if those tests are constructor-driven and the service implements all interfaces.

- [ ] **Step 3: Fix `AccountControllerTest.java`**

Replace mocks of `CreateAccountUseCase` with mocks of `OpenCheckingAccountUseCase`, `OpenSavingsAccountUseCase`, `OpenTimeDepositAccountUseCase` (only the ones the existing tests exercise — usually just checking).

Endpoint changes:
- `POST /api/accounts` → `POST /api/accounts/checking`
- Request body now `OpenCheckingAccountRequest` (carries `currency` and `overdraftLimit`).

For tests that asserted the previous response shape, update assertions to include `"type": "CHECKING"` and `"overdraftLimit": 0`.

- [ ] **Step 4: Fix `AdminControllerTest.java`**

Inject `AccrueInterestUseCase` and `MatureTimeDepositUseCase` mocks into the `@MockBean`/`@WebMvcTest` setup so the controller wires up. Existing freeze/unfreeze/close tests need no behavioral change.

Add at minimum two smoke tests:
1. `PUT /api/admin/accounts/{id}/accrue-interest` with `{"month":"2026-04"}` returns 200 with the transaction body when the use case returns a transaction.
2. `PUT /api/admin/accounts/{id}/mature` returns 200 with the transaction body.

- [ ] **Step 5: Run all tests**

Run: `mvn -q test`
Expected: PASS for all suites.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/application/service/AccountApplicationServiceTest.java \
        src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/AccountControllerTest.java \
        src/test/java/dev/kaldiroglu/hexagonal/ayvalikbank/adapter/in/web/AdminControllerTest.java
git commit -m "Update existing tests for sealed Account hierarchy and new admin endpoints"
```

---

## Task 13: End-to-end smoke test against running app

**Files:** none — manual verification

- [ ] **Step 1: Start PostgreSQL**

Run: `docker compose up -d`
Expected: container starts; `docker ps` shows postgres.

- [ ] **Step 2: Boot the app**

Run: `mvn -q spring-boot:run` (in a separate terminal, or with `&` and capture logs).
Expected: app starts on port 8080. JPA logs show new columns added to `accounts` table (`overdraft_limit`, `interest_rate`, etc.).

- [ ] **Step 3: Create a customer**

```bash
curl -u admin@ayvalikbank.dev:'Admin@123!' http://localhost:8080/api/admin/customers \
  -X POST -H "Content-Type: application/json" \
  -d '{"name":"Test User","email":"test@example.com","password":"Sanane12!"}'
```
Expected: 201 with the new customer's id. Save the id as `$CID`.

- [ ] **Step 4: Open one of each account type**

```bash
curl -u test@example.com:'Sanane12!' "http://localhost:8080/api/accounts/checking?ownerId=$CID" \
  -X POST -H "Content-Type: application/json" -d '{"currency":"USD","overdraftLimit":100}'

curl -u test@example.com:'Sanane12!' "http://localhost:8080/api/accounts/savings?ownerId=$CID" \
  -X POST -H "Content-Type: application/json" -d '{"currency":"USD","annualInterestRate":0.03}'

curl -u test@example.com:'Sanane12!' "http://localhost:8080/api/accounts/time-deposit?ownerId=$CID" \
  -X POST -H "Content-Type: application/json" \
  -d '{"currency":"USD","principal":1000,"maturityDate":"2027-04-25","annualInterestRate":0.05}'
```
Expected: each returns 201 with the type-specific response fields populated correctly.

- [ ] **Step 5: Trigger a savings interest accrual**

```bash
curl -u admin@ayvalikbank.dev:'Admin@123!' \
  "http://localhost:8080/api/admin/accounts/$SAVINGS_ID/accrue-interest" \
  -X PUT -H "Content-Type: application/json" -d '{"month":"2026-04"}'
```
Expected: 200 with an `INTEREST` transaction (amount may be 0 if the account is empty — confirm by depositing first if needed).

- [ ] **Step 6: Try to withdraw from time deposit before maturity**

```bash
curl -u test@example.com:'Sanane12!' "http://localhost:8080/api/accounts/$TD_ID/withdraw" \
  -X POST -H "Content-Type: application/json" -d '{"amount":100,"currency":"USD"}'
```
Expected: 422 `Invalid Account Operation` with message about not yet matured.

- [ ] **Step 7: Stop the app**

Ctrl-C in the spring-boot:run terminal, or `kill %1` for the background variant.

- [ ] **Step 8: No commit (manual verification only)**

---

## Task 14: Update documentation

**Files:**
- Modify: `CLAUDE.md`
- Modify: `README.md`
- Modify: `Architecture.md`
- Modify: `Tests.md`

- [ ] **Step 1: Update `CLAUDE.md`**

In the "Key Design Decisions" section, replace the bullet about rich entities with one that mentions the sealed hierarchy. In the REST API table, replace the single `POST /api/accounts` row with three rows for `checking`, `savings`, `time-deposit`, and add rows for `PUT /api/admin/accounts/{id}/accrue-interest` and `.../mature`.

- [ ] **Step 2: Update `README.md`**

Fix Java version: change `Java 21` to `Java 25`. Add a paragraph in the Domain section listing the three account types and one-line behavior summary for each.

- [ ] **Step 3: Update `Architecture.md`**

Add a section "Account hierarchy" describing the sealed class, the three subtypes, what data each carries, and what the behavioral differences are.

- [ ] **Step 4: Update `Tests.md`**

Add the three new test classes (CheckingAccountTest, SavingsAccountTest, TimeDepositAccountTest) to the per-class table with their test counts. Update the total test count.

- [ ] **Step 5: Run full test suite + coverage to refresh numbers in `Tests.md`**

Run: `mvn -q verify`
Expected: PASS. Then update the coverage section in `Tests.md` from `target/site/jacoco/index.html`.

- [ ] **Step 6: Commit**

```bash
git add CLAUDE.md README.md Architecture.md Tests.md
git commit -m "Document account type hierarchy and updated REST surface"
```

---

## Self-Review

**Spec coverage check:**
- Sealed `Account` hierarchy with three subtypes: Tasks 3–6 ✓
- Per-type behavior (overdraft, interest, locked principal): Tasks 4–6 ✓
- Persistence with type discriminator: Tasks 7–8 ✓
- Three open-account use cases + accrue + mature: Tasks 9–10 ✓
- New REST endpoints: Task 11 ✓
- Backward-compatible data backfill: Task 7 ✓
- Tests for each subtype + updates to existing tests: Tasks 4–6 + Task 12 ✓
- End-to-end smoke: Task 13 ✓
- Docs: Task 14 ✓

**Type consistency check:**
- `CheckingAccount.open(...)` two overloads — used consistently
- `SavingsAccount.accrueInterest(YearMonth)` — same signature in port (`AccrueInterestUseCase.Command(AccountId, YearMonth)`), application service, controller request DTO (`AccrueInterestRequest{month: YearMonth}`)
- `TimeDepositAccount.mature(LocalDate today)` — application service passes `LocalDate.now()`; the use case port hides the date (`mature(AccountId)`) which is the right ergonomic
- `AccountType.CHECKING / SAVINGS / TIME_DEPOSIT` — same names in domain enum, JPA `type` column values, and JSON response

**Placeholder scan:** no `TBD`, no "implement later," all code blocks present where steps modify code.

**Open risk:** Task 12 deliberately leaves a small amount of judgment to the implementer for which existing test cases need updates (the test bodies aren't fully reproduced, only the search-and-replace patterns). This is acceptable because the existing tests are already in the repo and the changes are mechanical; the running test suite will pinpoint exactly what needs fixing.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-04-25-account-types.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks. Best for a 14-task plan like this where each task is non-trivial and the parent agent's context stays clean.

**2. Inline Execution** — I execute tasks in this session with checkpoints for your review every few tasks. Faster turnaround, more context for me, but my context window fills up over the run.

**Which approach?**
