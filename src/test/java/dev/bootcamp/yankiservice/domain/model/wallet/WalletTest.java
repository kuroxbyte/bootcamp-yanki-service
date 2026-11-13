package dev.bootcamp.yankiservice.domain.model.wallet;

import dev.bootcamp.yankiservice.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTest {

    static final String IMEI = "490154203237518";

    @Test
    void startsActiveWithoutCardAndWithZeroBalance() {
        Wallet wallet = new Wallet(DocumentType.DNI, "45678912", "987654321", IMEI, "a@b.pe");

        assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(wallet.hasDebitCard()).isFalse();
        wallet.setDebitCardId("d1");
        assertThat(wallet.hasDebitCard()).isTrue();
    }

    @Test
    void validatesDocumentNumberByType() {
        assertThat(DocumentType.DNI.accepts("12345678")).isTrue();
        assertThat(DocumentType.DNI.accepts("1234567")).isFalse();
        assertThat(DocumentType.CEX.accepts("AB1234567890")).isTrue();
        assertThat(DocumentType.PASSPORT.accepts("AB12345678901")).isFalse();
        assertThat(DocumentType.PASSPORT.accepts(null)).isFalse();
        assertThatThrownBy(() -> new Wallet(DocumentType.DNI, "ABC", "987654321", IMEI, "a@b.pe"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> new Wallet(null, "12345678", "987654321", IMEI, "a@b.pe"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsInvalidPhone() {
        assertThatThrownBy(
                () -> new Wallet(DocumentType.DNI,
                "12345678",
                "887654321",
                IMEI,
                "a@b.pe"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("celular");
        assertThatThrownBy(() -> new Wallet(DocumentType.DNI, "12345678", null, IMEI, "a@b.pe"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsImeiWithWrongFormatOrCheckDigit() {
        assertThatThrownBy(() -> wallet("49015420323751"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("IMEI");
        assertThatThrownBy(() -> wallet("490154203237519"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> wallet(null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsInvalidEmail() {
        assertThatThrownBy(
                () -> new Wallet(DocumentType.DNI,
                "12345678",
                "987654321",
                IMEI,
                "ab.pe"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("correo");
        assertThatThrownBy(() -> new Wallet(DocumentType.DNI, "12345678", "987654321", IMEI, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    private static Wallet wallet(String imei) {
        return new Wallet(DocumentType.DNI, "12345678", "987654321", imei, "a@b.pe");
    }
}
