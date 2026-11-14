package dev.bootcamp.yankiservice.domain.model.wallet;

import dev.bootcamp.yankiservice.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletChangesTest {

    private static Wallet wallet() {
        Wallet wallet = new Wallet(
                DocumentType.DNI, "12345678", "987654321", "490154203237518", "a@b.pe");
        wallet.setId("w1");
        wallet.setCreatedAt(LocalDate.of(2026, 11, 15));
        return wallet;
    }

    @Test
    void changesContactAndKeepsTheRest() {
        Wallet wallet = wallet();
        wallet.setBalance(new BigDecimal("30"));

        Wallet updated = wallet.withContact("356938035643809", "nuevo@mail.com");

        assertThat(updated.getImei()).isEqualTo("356938035643809");
        assertThat(updated.getEmail()).isEqualTo("nuevo@mail.com");
        assertThat(updated.getPhone()).isEqualTo("987654321");
        assertThat(updated.getId()).isEqualTo("w1");
        assertThat(updated.getBalance()).isEqualByComparingTo("30");
        assertThat(updated.getCreatedAt()).isEqualTo(LocalDate.of(2026, 11, 15));
    }

    @Test
    void rejectsInvalidContact() {
        assertThatThrownBy(() -> wallet().withContact("123", "nuevo@mail.com"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> wallet().withContact("356938035643809", "sin-arroba"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void closesOnlyActiveWalletsWithoutOwnBalance() {
        assertThatCode(wallet()::validateClosing).doesNotThrowAnyException();

        Wallet withBalance = wallet();
        withBalance.setBalance(BigDecimal.TEN);
        assertThatThrownBy(withBalance::validateClosing).isInstanceOf(BusinessRuleException.class);

        // Con tarjeta, el saldo está en la cuenta: se puede dar de baja.
        Wallet withCard = wallet();
        withCard.setDebitCardId("d1");
        assertThatCode(withCard::validateClosing).doesNotThrowAnyException();

        Wallet linking = wallet();
        linking.setStatus(WalletStatus.LINKING);
        assertThatThrownBy(linking::validateClosing).isInstanceOf(BusinessRuleException.class);

        Wallet closed = wallet();
        closed.setStatus(WalletStatus.CLOSED);
        assertThat(closed.isClosed()).isTrue();
    }
}
