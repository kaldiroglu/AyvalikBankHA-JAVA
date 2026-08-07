package dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.account;

/**
 * Currencies supported by the bank. Pairs with every {@link Money} value.
 * Cross-currency arithmetic is forbidden (no FX is performed inside the domain).
 *
 * <ul>
 *   <li>{@code USD} — United States dollar
 *   <li>{@code EUR} — Euro
 *   <li>{@code TL}  — Turkish lira
 * </ul>
 */
public enum Currency {
    USD, EUR, TL
}
