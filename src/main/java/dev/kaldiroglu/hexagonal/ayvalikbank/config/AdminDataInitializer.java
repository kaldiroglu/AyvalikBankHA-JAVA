package dev.kaldiroglu.hexagonal.ayvalikbank.config;

import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.Customer;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.CustomerId;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.model.customer.Password;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.customer.CustomerRepositoryPort;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.customer.PasswordHasherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.UUID;

@Component
public class AdminDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminDataInitializer.class);

    private static final String ADMIN_ID    = "00000000-0000-0000-0000-000000000001";
    private static final String ADMIN_EMAIL = "admin@ayvalikbank.dev";
    private static final String ADMIN_PASS  = "Admin@123!";

    private final CustomerRepositoryPort customerRepository;
    private final PasswordHasherPort passwordHasher;

    public AdminDataInitializer(CustomerRepositoryPort customerRepository,
                                PasswordHasherPort passwordHasher) {
        this.customerRepository = customerRepository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public void run(ApplicationArguments args) {
        CustomerId adminId = CustomerId.of(UUID.fromString(ADMIN_ID));

        // Delete any existing admin with an invalid (fabricated) hash so we can re-seed properly
        if (customerRepository.existsById(adminId)) {
            boolean passwordValid = customerRepository.findById(adminId)
                    .map(c -> passwordHasher.matches(ADMIN_PASS, c.getCurrentPassword().hashedValue()))
                    .orElse(false);
            if (passwordValid) {
                return; // already seeded correctly
            }
            customerRepository.deleteById(adminId);
            log.warn("Admin user had an invalid password hash — re-seeding.");
        }

        String hash = passwordHasher.hash(ADMIN_PASS);
        Customer admin = new Customer(
                adminId,
                "System Admin",
                ADMIN_EMAIL,
                "ADMIN",
                Password.ofHashed(hash),
                new ArrayList<>()
        );
        customerRepository.save(admin);
        log.info("Admin user seeded: {}", ADMIN_EMAIL);
    }
}
