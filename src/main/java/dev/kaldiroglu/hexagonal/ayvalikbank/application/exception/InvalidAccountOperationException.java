package dev.kaldiroglu.hexagonal.ayvalikbank.application.exception;

public class InvalidAccountOperationException extends RuntimeException {
    public InvalidAccountOperationException(String message) {
        super(message);
    }
}
