package dev.kaldiroglu.hexagonal.ayvalikbank.application.port.in.customer;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;

/**
 * What a <b>customer</b> can do to their own record.
 *
 * <p>One method today. It stays a separate port rather than merging into
 * {@link CustomerAdministrationPort} because the actor is different: a customer changing their own
 * password is not an administrator editing the roster. Grouping by actor keeps that distinction
 * visible even when one side has a single operation.
 */
public interface CustomerSelfServicePort {

    record ChangePasswordCommand(CustomerId callerId, CustomerId customerId, String rawNewPassword) {}

    void changePassword(ChangePasswordCommand command);
}
