package dev.bootcamp.yankiservice.domain.model.payment;

import dev.bootcamp.yankiservice.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YankiPaymentTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 15);

    @Test
    void walletSourceStartsCreditingTheTarget() {
        YankiPayment payment = payment(null, "a2", BigDecimal.TEN);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getStep()).isEqualTo(PaymentStep.CREDITING_TARGET);
        assertThat(payment.fromAccount()).isFalse();
        assertThat(payment.toAccount()).isTrue();
    }

    @Test
    void accountSourceStartsDebitingTheSource() {
        YankiPayment payment = payment("a1", null, BigDecimal.TEN);

        assertThat(payment.getStep()).isEqualTo(PaymentStep.DEBITING_SOURCE);
        assertThat(payment.fromAccount()).isTrue();
        assertThat(payment.toAccount()).isFalse();
    }

    @Test
    void completesAndRejectsEndingTheSaga() {
        YankiPayment completed = payment(null, null, BigDecimal.ONE);
        completed.complete();
        assertThat(completed.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(completed.getStep()).isEqualTo(PaymentStep.DONE);

        YankiPayment rejected = payment(null, null, BigDecimal.ONE);
        rejected.reject("Saldo insuficiente");
        assertThat(rejected.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(rejected.getStep()).isEqualTo(PaymentStep.DONE);
        assertThat(rejected.getRejectionReason()).isEqualTo("Saldo insuficiente");
    }

    @Test
    void rejectsZeroNegativeOrMissingAmount() {
        assertThatThrownBy(() -> payment(null, null, BigDecimal.ZERO))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> payment(null, null, new BigDecimal("-1")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> payment(null, null, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsPaymentToTheSameWallet() {
        assertThatThrownBy(() -> new YankiPayment(
                "w1", "987654321", null, "w1", "987654321", null, BigDecimal.ONE, DATE))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("propio");
    }

    private static YankiPayment payment(
            String sourceAccount,
            String targetAccount,
            BigDecimal amount) {
        return new YankiPayment(
                "w1",
                "987654321",
                sourceAccount,
                "w2",
                "912345678",
                targetAccount,
                amount,
                DATE);
    }
}
