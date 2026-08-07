package dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.account;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.AccountId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account.Transaction;

import java.time.YearMonth;

/**
 * Everything an <b>administrator</b> can do to an account they do not own.
 *
 * <p>Separate from {@link CustomerAccountPort} because it is a different actor having a different
 * conversation: freezing, closing, accruing interest and maturing a deposit are bank actions, not
 * customer actions. The separation is enforced at the route level by {@code SecurityConfig}'s
 * {@code hasRole("ADMIN")} matcher.
 *
 * <p>Splitting by actor rather than by aggregate is what makes that enforcement checkable: every
 * method reachable from an admin-only route lives here, and nowhere else.
 */
public interface AccountAdministrationPort {

    record AccrueInterestCommand(AccountId accountId, YearMonth month) {}

    record MatureCommand(AccountId accountId) {}

    void freezeAccount(AccountId accountId);

    void unfreezeAccount(AccountId accountId);

    void closeAccount(AccountId accountId);

    Transaction accrueInterest(AccrueInterestCommand command);

    Transaction mature(MatureCommand command);
}
