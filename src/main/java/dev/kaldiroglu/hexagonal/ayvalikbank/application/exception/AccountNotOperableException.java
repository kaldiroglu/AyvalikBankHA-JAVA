package dev.kaldiroglu.hexagonal.ayvalikbank.application.exception;

public class AccountNotOperableException extends RuntimeException {
    public AccountNotOperableException(String message) {
        super(message);
    }
}
