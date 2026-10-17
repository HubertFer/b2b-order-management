package com.humberto.b2b.ordermanagement.partner.infrastructure.persistence;

import com.humberto.b2b.ordermanagement.TestcontainersConfiguration;
import com.humberto.b2b.ordermanagement.partner.domain.Partner;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PartnerPersistenceAdapterTest {

    @Autowired
    private PartnerPersistenceAdapter adapter;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void findsExistingPartner() {
        UUID partnerId = insertPartner("100.00", "40.00");

        Optional<Partner> partner = adapter.findById(partnerId);

        assertTrue(partner.isPresent());
        assertEquals(new Partner(partnerId, new BigDecimal("100.00"), new BigDecimal("40.00")), partner.get());
    }

    @Test
    void savesNewPartner() {
        Partner partner = Partner.create(UUID.randomUUID(), new BigDecimal("250.00"));

        Partner saved = adapter.save(partner);
        entityManager.flush();
        entityManager.clear();

        assertEquals(partner, saved);
        assertEquals(new Partner(partner.id(), new BigDecimal("250.00"), new BigDecimal("0.00")),
                adapter.findById(partner.id()).orElseThrow());
        assertEquals(new BigDecimal("0.00"), usedCredit(partner.id()));
    }

    @Test
    void returnsEmptyForUnknownPartner() {
        assertTrue(adapter.findById(UUID.randomUUID()).isEmpty());
    }

    @Test
    void debitsWhenCreditIsAvailable() {
        UUID partnerId = insertPartner("100.00", "40.00");

        assertTrue(adapter.debitIfAvailable(partnerId, new BigDecimal("25.00")));

        assertEquals(new BigDecimal("65.00"), usedCredit(partnerId));
    }

    @Test
    void debitsExactlyTheRemainingCredit() {
        UUID partnerId = insertPartner("100.00", "40.00");

        assertTrue(adapter.debitIfAvailable(partnerId, new BigDecimal("60.00")));

        assertEquals(new BigDecimal("100.00"), usedCredit(partnerId));
    }

    @Test
    void doesNotDebitWhenCreditIsInsufficient() {
        UUID partnerId = insertPartner("100.00", "40.00");

        assertFalse(adapter.debitIfAvailable(partnerId, new BigDecimal("60.01")));

        assertEquals(new BigDecimal("40.00"), usedCredit(partnerId));
    }

    @Test
    void doesNotDebitUnknownPartner() {
        assertFalse(adapter.debitIfAvailable(UUID.randomUUID(), BigDecimal.ONE));
    }

    @Test
    void rejectsNegativeAmount() {
        UUID partnerId = insertPartner("100.00", "40.00");

        assertThrows(IllegalArgumentException.class,
                () -> adapter.debitIfAvailable(partnerId, new BigDecimal("-1.00")));
        assertEquals(new BigDecimal("40.00"), usedCredit(partnerId));
    }

    @Test
    void refundsUsedCredit() {
        UUID partnerId = insertPartner("100.00", "40.00");

        adapter.refund(partnerId, new BigDecimal("25.00"));

        assertEquals(new BigDecimal("15.00"), usedCredit(partnerId));
    }

    @Test
    void refundsAllUsedCredit() {
        UUID partnerId = insertPartner("100.00", "40.00");

        adapter.refund(partnerId, new BigDecimal("40.00"));

        assertEquals(new BigDecimal("0.00"), usedCredit(partnerId));
    }

    @Test
    void rejectsRefundAboveUsedCredit() {
        UUID partnerId = insertPartner("100.00", "40.00");

        assertThrows(IllegalStateException.class, () -> adapter.refund(partnerId, new BigDecimal("40.01")));
        assertEquals(new BigDecimal("40.00"), usedCredit(partnerId));
    }

    @Test
    void rejectsRefundForUnknownPartner() {
        assertThrows(IllegalStateException.class, () -> adapter.refund(UUID.randomUUID(), BigDecimal.ONE));
    }

    @Test
    void rejectsNegativeRefund() {
        UUID partnerId = insertPartner("100.00", "40.00");

        assertThrows(IllegalArgumentException.class, () -> adapter.refund(partnerId, new BigDecimal("-1.00")));
        assertEquals(new BigDecimal("40.00"), usedCredit(partnerId));
    }

    private UUID insertPartner(String creditLimit, String usedCredit) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO partner (id, credit_limit, used_credit) VALUES (?, ?, ?)",
                id, new BigDecimal(creditLimit), new BigDecimal(usedCredit));
        return id;
    }

    private BigDecimal usedCredit(UUID partnerId) {
        return jdbcTemplate.queryForObject("SELECT used_credit FROM partner WHERE id = ?",
                BigDecimal.class, partnerId);
    }
}