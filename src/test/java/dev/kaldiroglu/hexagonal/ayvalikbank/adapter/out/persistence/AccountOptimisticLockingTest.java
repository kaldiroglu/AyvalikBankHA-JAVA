package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence;

import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.entity.AccountJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * No threads are needed here. A lost update is a <b>stale-read</b> problem, not a timing problem, so
 * two persistence contexts committing in a fixed order reproduce it deterministically — no sleeps,
 * no races, no flakiness.
 *
 * <p>{@code NOT_SUPPORTED} stops Spring wrapping each test in a transaction that would roll the
 * fixture row back and hide it from the contexts created below.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("Optimistic locking on accounts")
class AccountOptimisticLockingTest {

    @Autowired
    private EntityManagerFactory emf;

    private UUID insertAccount(String balance) {
        UUID id = UUID.randomUUID();
        EntityManager em = emf.createEntityManager();
        em.getTransaction().begin();
        AccountJpaEntity entity = new AccountJpaEntity();
        entity.setId(id);
        entity.setOwnerId(UUID.randomUUID());
        entity.setCurrency("USD");
        entity.setBalance(new BigDecimal(balance));
        entity.setStatus("ACTIVE");
        entity.setType("CHECKING");
        entity.setOverdraftLimit(BigDecimal.ZERO);
        em.persist(entity);
        em.getTransaction().commit();
        em.close();
        return id;
    }

    @Test
    void shouldPersistANewAccountAtVersionZero() {
        UUID id = insertAccount("100.00");

        EntityManager em = emf.createEntityManager();
        assertThat(em.find(AccountJpaEntity.class, id).getVersion()).isZero();
        em.close();
    }

    @Test
    void shouldIncrementTheVersionOnEachUpdate() {
        UUID id = insertAccount("100.00");

        for (int expected = 1; expected <= 2; expected++) {
            EntityManager em = emf.createEntityManager();
            em.getTransaction().begin();
            em.find(AccountJpaEntity.class, id).setBalance(new BigDecimal("10.0" + expected));
            em.getTransaction().commit();
            em.close();

            EntityManager check = emf.createEntityManager();
            assertThat(check.find(AccountJpaEntity.class, id).getVersion()).isEqualTo(expected);
            check.close();
        }
    }

    @Test
    @DisplayName("the second writer is rejected when both loaded the same version")
    void shouldRejectTheSecondWriterWhenBothLoadedTheSameVersion() {
        UUID id = insertAccount("100.00");

        EntityManager em1 = emf.createEntityManager();
        EntityManager em2 = emf.createEntityManager();
        em1.getTransaction().begin();
        em2.getTransaction().begin();

        // Both read balance 100 at version 0 — this is the stale read.
        AccountJpaEntity first = em1.find(AccountJpaEntity.class, id);
        AccountJpaEntity second = em2.find(AccountJpaEntity.class, id);

        first.setBalance(new BigDecimal("50.00"));
        em1.getTransaction().commit();

        second.setBalance(new BigDecimal("50.00"));
        // The exception chain is RollbackException -> OptimisticLockException ->
        // StaleObjectStateException. Assert on the middle link: it is the JPA-portable type, and it
        // is what Spring translates into OptimisticLockingFailureException for the 409 response.
        assertThatThrownBy(() -> em2.getTransaction().commit())
                .isInstanceOf(RollbackException.class)
                .hasCauseInstanceOf(OptimisticLockException.class)
                .hasRootCauseMessage("Row was updated or deleted by another transaction "
                        + "(or unsaved-value mapping was incorrect): ["
                        + AccountJpaEntity.class.getName() + "#" + id + "]");

        em1.close();
        em2.close();

        // Without the version both writers would have stored 50.00 and one withdrawal would be lost.
        EntityManager check = emf.createEntityManager();
        AccountJpaEntity finalState = check.find(AccountJpaEntity.class, id);
        assertThat(finalState.getBalance()).isEqualByComparingTo("50.00");
        assertThat(finalState.getVersion()).isEqualTo(1);
        check.close();
    }
}
