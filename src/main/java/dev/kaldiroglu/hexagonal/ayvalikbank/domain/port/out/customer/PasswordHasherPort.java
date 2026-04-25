package dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.customer;

public interface PasswordHasherPort {
    String hash(String rawPassword);
    boolean matches(String rawPassword, String hashedPassword);
}
