package dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence;

import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.entity.SettingsJpaEntity;
import dev.kaldiroglu.hexagonal.ayvalikbank.adapter.out.persistence.repository.SettingsJpaRepository;
import dev.kaldiroglu.hexagonal.ayvalikbank.domain.port.out.account.SettingsRepositoryPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class SettingsPersistenceAdapter implements SettingsRepositoryPort {

    private static final String TRANSFER_FEE_KEY = "TRANSFER_FEE_PERCENT";
    private static final BigDecimal DEFAULT_FEE = BigDecimal.ONE;

    private final SettingsJpaRepository repository;

    public SettingsPersistenceAdapter(SettingsJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public BigDecimal getTransferFeePercent() {
        return repository.findById(TRANSFER_FEE_KEY)
                .map(e -> new BigDecimal(e.getValue()))
                .orElse(DEFAULT_FEE);
    }

    @Override
    public void setTransferFeePercent(BigDecimal percent) {
        var entity = repository.findById(TRANSFER_FEE_KEY)
                .orElse(new SettingsJpaEntity(TRANSFER_FEE_KEY, percent.toPlainString()));
        entity.setValue(percent.toPlainString());
        repository.save(entity);
    }
}
