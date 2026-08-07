package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.in.web;

import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.*;
import dev.kaldiroglu.hexagonal.ayvalikbank.application.exception.AccountNotOperableException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomerNotFoundException.class)
    public ProblemDetail handleCustomerNotFound(CustomerNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleAccountNotFound(AccountNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ProblemDetail handleInsufficientFunds(InsufficientFundsException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ProblemDetail handleInvalidPassword(InvalidPasswordException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(PasswordReusedException.class)
    public ProblemDetail handlePasswordReused(PasswordReusedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(AccountNotOperableException.class)
    public ProblemDetail handleAccountNotOperable(AccountNotOperableException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(InvalidAccountOperationException.class)
    public ProblemDetail handleInvalidAccountOperation(InvalidAccountOperationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(LimitExceededException.class)
    public ProblemDetail handleLimitExceeded(LimitExceededException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedAccessException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    /**
     * Two operations modified the same account concurrently and the second one lost.
     *
     * <p>Hibernate raises this at transaction commit — inside the {@code @Transactional} proxy and
     * therefore <i>after</i> the application service method has already returned — so the service
     * cannot catch it. The handler is the only place that can map it.
     *
     * <p>The detail is fixed rather than {@code ex.getMessage()}: Hibernate's text names the entity
     * class and primary key, which is internal detail that should not reach a client.
     *
     * <p>Catching Spring's {@code OptimisticLockingFailureException} base covers
     * {@code ObjectOptimisticLockingFailureException} and any other subtype.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrentModification(OptimisticLockingFailureException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The account was modified by another operation. Please retry.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "; " + b);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }
}
