package dev.bootcamp.yankiservice.application.usecase;

import dev.bootcamp.yankiservice.domain.model.link.DebitCardLink;
import dev.bootcamp.yankiservice.domain.model.payment.YankiPayment;
import dev.bootcamp.yankiservice.domain.model.wallet.DocumentType;
import dev.bootcamp.yankiservice.domain.model.wallet.Wallet;
import dev.bootcamp.yankiservice.domain.model.wallet.WalletStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Datos de prueba de los casos de uso.
 */
final class Fixtures {

    static final LocalDate DATE = LocalDate.of(2026, 11, 15);
    static final String IMEI = "490154203237518";

    private Fixtures() {}

    static Wallet wallet(String id, String phone, String debitCardId) {
        Wallet wallet = new Wallet(DocumentType.DNI, "12345678", phone, IMEI, "a@b.pe");
        wallet.setId(id);
        wallet.setBalance(new BigDecimal("100"));
        wallet.setDebitCardId(debitCardId);
        return wallet;
    }

    static Wallet linking(String id, String phone) {
        Wallet wallet = wallet(id, phone, null);
        wallet.setStatus(WalletStatus.LINKING);
        return wallet;
    }

    static YankiPayment payment(String id, String sourceAccount, String targetAccount) {
        YankiPayment payment = new YankiPayment(
                "w1",
                "987654321",
                sourceAccount,
                "w2",
                "912345678",
                targetAccount,
                new BigDecimal("30"),
                DATE);
        payment.setId(id);
        return payment;
    }

    static DebitCardLink link(String id) {
        DebitCardLink link = new DebitCardLink("c1", "d1", "w1", "987654321", DATE);
        link.setId(id);
        return link;
    }
}
