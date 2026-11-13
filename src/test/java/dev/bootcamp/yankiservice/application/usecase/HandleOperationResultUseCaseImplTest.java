package dev.bootcamp.yankiservice.application.usecase;

import dev.bootcamp.yankiservice.application.port.AccountOperationPublisher;
import dev.bootcamp.yankiservice.application.port.DebitCardLinkRepository;
import dev.bootcamp.yankiservice.application.port.WalletRepository;
import dev.bootcamp.yankiservice.application.port.YankiPaymentRepository;
import dev.bootcamp.yankiservice.domain.model.link.DebitCardLink;
import dev.bootcamp.yankiservice.domain.model.link.LinkStatus;
import dev.bootcamp.yankiservice.domain.model.payment.PaymentStatus;
import dev.bootcamp.yankiservice.domain.model.payment.PaymentStep;
import dev.bootcamp.yankiservice.domain.model.payment.YankiPayment;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandleOperationResultUseCaseImplTest {

    private static final BigDecimal AMOUNT = new BigDecimal("30");

    @Mock private YankiPaymentRepository paymentRepository;

    @Mock private DebitCardLinkRepository linkRepository;

    @Mock private WalletRepository walletRepository;

    @Mock private AccountOperationPublisher operationPublisher;

    private HandleOperationResultUseCaseImpl useCase;

    @BeforeEach
    void createUseCase() {
        Clock clock = Clock.fixed(
                Fixtures.DATE.atStartOfDay().toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC);
        useCase = new HandleOperationResultUseCaseImpl(
                paymentRepository,
                linkRepository,
                walletRepository,
                operationPublisher,
                clock);
    }

    private YankiPayment stored(YankiPayment payment) {
        when(paymentRepository.findById(payment.getId())).thenReturn(Maybe.just(payment));
        return payment;
    }

    private void savesPayments() {
        when(paymentRepository.save(any()))
                .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));
    }

    @Test
    void acceptedDebitRequestsCreditToTargetAccount() {
        savesPayments();
        YankiPayment payment = stored(Fixtures.payment("p1", "a1", "a2"));
        when(
                operationPublisher.requestCredit(
                        "payment:p1:credit",
                        "a2",
                        AMOUNT,
                        Fixtures.DATE,
                        false))
                .thenReturn(Completable.complete());

        useCase.execute("payment:p1:debit", true, null).test().assertComplete();
        assertThat(payment.getStep()).isEqualTo(PaymentStep.CREDITING_TARGET);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void acceptedDebitCreditsTargetWalletAndCompletes() {
        savesPayments();
        YankiPayment payment = stored(Fixtures.payment("p1", "a1", null));
        when(walletRepository.creditBalance("w2", AMOUNT)).thenReturn(Completable.complete());

        useCase.execute("payment:p1:debit", true, null).test().assertComplete();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void rejectedDebitRejectsThePayment() {
        savesPayments();
        YankiPayment payment = stored(Fixtures.payment("p1", "a1", null));

        useCase.execute("payment:p1:debit", false, "Saldo insuficiente").test().assertComplete();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(payment.getRejectionReason()).isEqualTo("Saldo insuficiente");
        verifyNoInteractions(walletRepository, operationPublisher);
    }

    @Test
    void acceptedCreditCompletesThePayment() {
        savesPayments();
        YankiPayment payment = stored(Fixtures.payment("p1", null, "a2"));

        useCase.execute("payment:p1:credit", true, null).test().assertComplete();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void rejectedCreditFromWalletRefundsTheWallet() {
        savesPayments();
        YankiPayment payment = stored(Fixtures.payment("p1", null, "a2"));
        when(walletRepository.creditBalance("w1", AMOUNT)).thenReturn(Completable.complete());

        useCase.execute("payment:p1:credit", false, "Cuenta bloqueada").test().assertComplete();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(payment.getRejectionReason()).isEqualTo("Cuenta bloqueada");
    }

    @Test
    void rejectedCreditFromAccountRequestsReversalOfTheDebit() {
        YankiPayment payment = Fixtures.payment("p1", "a1", "a2");
        payment.setStep(PaymentStep.CREDITING_TARGET);
        stored(payment);
        savesPayments();
        when(
                operationPublisher.requestCredit(
                        "payment:p1:refund",
                        "a1",
                        AMOUNT,
                        Fixtures.DATE,
                        true))
                .thenReturn(Completable.complete());

        useCase.execute("payment:p1:credit", false, "Cuenta bloqueada").test().assertComplete();
        assertThat(payment.getStep()).isEqualTo(PaymentStep.REFUNDING_SOURCE);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void refundResultRejectsThePaymentWithTheOriginalReason() {
        YankiPayment refunded = Fixtures.payment("p1", "a1", "a2");
        refunded.setStep(PaymentStep.REFUNDING_SOURCE);
        refunded.setRejectionReason("Cuenta bloqueada");
        stored(refunded);
        YankiPayment notRefunded = Fixtures.payment("p2", "a1", "a2");
        notRefunded.setStep(PaymentStep.REFUNDING_SOURCE);
        notRefunded.setRejectionReason("Cuenta bloqueada");
        stored(notRefunded);
        savesPayments();

        useCase.execute("payment:p1:refund", true, null).test().assertComplete();
        useCase.execute("payment:p2:refund", false, "Cuenta cerrada").test().assertComplete();
        assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(refunded.getRejectionReason()).isEqualTo("Cuenta bloqueada");
        assertThat(notRefunded.getRejectionReason()).contains("Cuenta cerrada");
    }

    @Test
    void ignoresResultThatDoesNotMatchTheCurrentStep() {
        YankiPayment payment = stored(Fixtures.payment("p1", null, "a2"));
        payment.complete();

        useCase.execute("payment:p1:credit", true, null).test().assertComplete();
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void acceptedTransferFinishesTheLink() {
        DebitCardLink link = Fixtures.link("l1");
        link.setStatus(LinkStatus.TRANSFERRING);
        link.setTransferredBalance(new BigDecimal("100"));
        when(linkRepository.findById("l1")).thenReturn(Maybe.just(link));
        when(linkRepository.save(link)).thenReturn(Single.just(link));
        when(walletRepository.finishLinking("w1", "d1")).thenReturn(Completable.complete());

        useCase.execute("link:l1:transfer", true, null).test().assertComplete();
        assertThat(link.getStatus()).isEqualTo(LinkStatus.ACTIVE);
    }

    @Test
    void rejectedTransferGivesTheBalanceBack() {
        DebitCardLink link = Fixtures.link("l1");
        link.setStatus(LinkStatus.TRANSFERRING);
        link.setTransferredBalance(new BigDecimal("100"));
        when(linkRepository.findById("l1")).thenReturn(Maybe.just(link));
        when(linkRepository.save(link)).thenReturn(Single.just(link));
        when(walletRepository.cancelLinking("w1", new BigDecimal("100")))
                .thenReturn(Completable.complete());

        useCase.execute("link:l1:transfer", false, "Cuenta bloqueada").test().assertComplete();
        assertThat(link.getStatus()).isEqualTo(LinkStatus.REJECTED);
        assertThat(link.getRejectionReason()).isEqualTo("Cuenta bloqueada");
    }

    @Test
    void ignoresTransferResultForLinkThatIsNotTransferring() {
        DebitCardLink link = Fixtures.link("l1");
        link.setStatus(LinkStatus.ACTIVE);
        when(linkRepository.findById("l1")).thenReturn(Maybe.just(link));

        useCase.execute("link:l1:transfer", true, null).test().assertComplete();
        verify(linkRepository, never()).save(any());
        verifyNoInteractions(walletRepository);
    }
}
