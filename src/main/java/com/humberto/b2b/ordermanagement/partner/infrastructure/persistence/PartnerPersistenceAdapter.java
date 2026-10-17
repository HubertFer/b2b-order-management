package com.humberto.b2b.ordermanagement.partner.infrastructure.persistence;

import com.humberto.b2b.ordermanagement.order.application.port.out.DebitPartnerCreditPort;
import com.humberto.b2b.ordermanagement.order.application.port.out.FindPartnerPort;
import com.humberto.b2b.ordermanagement.order.application.port.out.RefundPartnerCreditPort;
import com.humberto.b2b.ordermanagement.partner.application.port.out.LoadPartnerPort;
import com.humberto.b2b.ordermanagement.partner.application.port.out.SavePartnerPort;
import com.humberto.b2b.ordermanagement.partner.domain.Partner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
class PartnerPersistenceAdapter implements FindPartnerPort, LoadPartnerPort, SavePartnerPort,
        DebitPartnerCreditPort, RefundPartnerCreditPort {

    private final PartnerJpaRepository repository;

    PartnerPersistenceAdapter(PartnerJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Partner> findById(UUID partnerId) {
        return repository.findById(partnerId).map(PartnerEntity::toDomain);
    }

    @Override
    @Transactional
    public Partner save(Partner partner) {
        return repository.save(PartnerEntity.fromDomain(partner)).toDomain();
    }

    @Override
    @Transactional
    public boolean debitIfAvailable(UUID partnerId, BigDecimal amount) {
        validate(partnerId, amount);
        return repository.debitIfAvailable(partnerId, amount) == 1;
    }

    @Override
    @Transactional
    public void refund(UUID partnerId, BigDecimal amount) {
        validate(partnerId, amount);
        if (repository.refund(partnerId, amount) != 1) {
            throw new IllegalStateException("Could not refund " + amount + " to partner " + partnerId);
        }
    }

    private static void validate(UUID partnerId, BigDecimal amount) {
        Objects.requireNonNull(partnerId, "partnerId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
    }
}