package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

/**
 * Base type for every way the account domain can refuse an operation.
 *
 * <p>Before this existed the domain threw raw {@link IllegalStateException} from 17 places, all
 * meaning different things, and the application layer recovered the meaning from <i>which call it
 * had wrapped</i>. Translation was positional rather than semantic. Worse,
 * {@code catch (IllegalStateException)} also swallowed JDK and framework exceptions, so genuine
 * defects were reported to clients as HTTP 422 business errors and never flagged as faults.
 *
 * <p>Extending {@link IllegalStateException} is deliberate on two counts. A refusal really is a
 * property of state rather than a defect in the argument — the precedent set by
 * {@link InsufficientBalanceException}. And because {@code catch (AccountRuleViolation)} does
 * <b>not</b> catch a plain {@code IllegalStateException}, precision is gained at the catch site
 * without invalidating the domain tests that assert on the supertype. Inheriting from the type
 * callers already catch is what turned this from a big-bang change into an incremental one.
 *
 * <p>{@code sealed} for the same reason {@link Account} is: adding a fifth kind of refusal must be a
 * deliberate edit to {@code permits}, and it breaks the exhaustive translation switch in
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
