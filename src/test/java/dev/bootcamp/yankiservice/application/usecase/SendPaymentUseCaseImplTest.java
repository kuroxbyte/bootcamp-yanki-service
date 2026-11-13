package dev.bootcamp.yankiservice.application.usecase;

import dev.bootcamp.yankiservice.application.port.AccountOperationPublisher;
import dev.bootcamp.yankiservice.application.port.DebitCardRepository;
import dev.bootcamp.yankiservice.application.port.WalletRepository;
import dev.bootcamp.yankiservice.application.port.YankiPaymentRepository;
import dev.bootcamp.yankiservice.domain.exception.BusinessRuleException;
import dev.bootcamp.yankiservice.domain.exception.NotFoundException;
import dev.bootcamp.yankiservice.domain.exception.OperationUnavailableException;
import dev.bootcamp.yankiservice.domain.model.debitcard.DebitCard;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendPaymentUseCaseImplTest {

    private static final BigDecimal AMOUNT = new BigDecimal("30");
    private static final String TARGET_PHONE = "912345678";

    @Mock private WalletRepository walletRepository;

    @Mock private YankiPaymentRepository paymentRepository;

    @Mock private DebitCardRepository debitCardRepository;

    @Mock private AccountOperationPublisher operationPublisher;

    private SendPaymentUseCaseImpl useCase;

    @BeforeEach
    void createUseCase() {
        useCase = new SendPaymentUseCaseImpl(
                walletRepository,
                paymentRepository,
                debitCardRepository,
                operationPublisher);
    }

    private void wallets(String sourceCard, String targetCard) {
        when(walletRepository.findById("w1"))
                .thenReturn(Maybe.just(Fixtures.wallet("w1", "987654321", sourceCard)));
        when(walletRepository.findByPhone(TARGET_PHONE))
                .thenReturn(Maybe.just(Fixtures.wallet("w2", TARGET_PHONE, targetCard)));
    }

    private void savedPayments() {
        when(paymentRepository.save(any())).thenAnswer(invocation -> {
            YankiPayment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId("p1");
            }
            return Single.just(payment);
        });
    }

    private Single<YankiPayment> send() {
        return useCase.execute("w1", TARGET_PHONE, AMOUNT, Fixtures.DATE);
    }

    @Test
    void walletToWalletCompletesAtOnce() {
        wallets(null, null);
        savedPayments();
        when(walletRepository.debitBalance("w1", AMOUNT)).thenReturn(Single.just(true));
        when(walletRepository.creditBalance("w2", AMOUNT)).thenReturn(Completable.complete());

        send().test().assertValue(payment -> payment.getStatus() == PaymentStatus.COMPLETED);
        verifyNoInteractions(operationPublisher);
    }

    @Test
    void walletToWalletFailsWhenBalanceIsNotEnough() {
        wallets(null, null);
        when(walletRepository.debitBalance("w1", AMOUNT)).thenReturn(Single.just(false));

        send().test().assertError(error -> error instanceof BusinessRuleException
                && error.getMessage().equals(SendPaymentUseCaseImpl.INSUFFICIENT_BALANCE));
        verify(walletRepository, never()).creditBalance(anyString(), any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void walletToAccountDebitsWalletAndRequestsCredit() {
        wallets(null, "d2");
        savedPayments();
        when(debitCardRepository.findById("d2"))
                .thenReturn(Maybe.just(new DebitCard("d2", "c2", "a2")));
        when(walletRepository.debitBalance("w1", AMOUNT)).thenReturn(Single.just(true));
        when(
                operationPublisher.requestCredit(
                        "payment:p1:credit",
                        "a2",
                        AMOUNT,
                        Fixtures.DATE,
                        false))
                .thenReturn(Completable.complete());

        send().test().assertValue(payment -> payment.getStatus() == PaymentStatus.PENDING
                && payment.getStep() == PaymentStep.CREDITING_TARGET);
    }

    @Test
    void walletToAccountRefundsWalletWhenTheRequestCannotBePublished() {
        wallets(null, "d2");
        savedPayments();
        when(debitCardRepository.findById("d2"))
                .thenReturn(Maybe.just(new DebitCard("d2", "c2", "a2")));
        when(walletRepository.debitBalance("w1", AMOUNT)).thenReturn(Single.just(true));
        when(
                operationPublisher.requestCredit(
                        "payment:p1:credit",
                        "a2",
                        AMOUNT,
                        Fixtures.DATE,
                        false))
                .thenReturn(Completable.error(new IllegalStateException("kafka down")));
        when(walletRepository.creditBalance("w1", AMOUNT)).thenReturn(Completable.complete());

        send().test().assertError(OperationUnavailableException.class);
        verify(walletRepository).creditBalance("w1", AMOUNT);
        verify(paymentRepository, times(2)).save(argThat(
                payment -> payment.getStatus() == PaymentStatus.REJECTED));
    }

    @Test
    void accountSourceRequestsDebitFirst() {
        wallets("d1", null);
        savedPayments();
        when(debitCardRepository.findById("d1"))
                .thenReturn(Maybe.just(new DebitCard("d1", "c1", "a1")));
        when(operationPublisher.requestDebit("payment:p1:debit", "a1", AMOUNT, Fixtures.DATE))
                .thenReturn(Completable.complete());

        send().test().assertValue(payment -> payment.getStatus() == PaymentStatus.PENDING
                && payment.getStep() == PaymentStep.DEBITING_SOURCE);
        verify(walletRepository, never()).debitBalance(anyString(), any());
    }

    @Test
    void accountSourceIsRejectedWhenTheDebitCannotBeRequested() {
        wallets("d1", null);
        savedPayments();
        when(debitCardRepository.findById("d1"))
                .thenReturn(Maybe.just(new DebitCard("d1", "c1", "a1")));
        when(operationPublisher.requestDebit("payment:p1:debit", "a1", AMOUNT, Fixtures.DATE))
                .thenReturn(Completable.error(new IllegalStateException("kafka down")));

        send().test().assertError(OperationUnavailableException.class);
        verify(walletRepository, never()).creditBalance(anyString(), any());
    }

    @Test
    void failsWhenTargetPhoneHasNoWallet() {
        when(walletRepository.findById("w1"))
                .thenReturn(Maybe.just(Fixtures.wallet("w1", "987654321", null)));
        when(walletRepository.findByPhone(TARGET_PHONE)).thenReturn(Maybe.empty());

        send().test().assertError(NotFoundException.class);
    }

    @Test
    void failsWhenWalletIsLinkingCard() {
        when(walletRepository.findById("w1"))
                .thenReturn(Maybe.just(Fixtures.wallet("w1", "987654321", null)));
        when(walletRepository.findByPhone(TARGET_PHONE))
                .thenReturn(Maybe.just(Fixtures.linking("w2", TARGET_PHONE)));

        send().test().assertError(BusinessRuleException.class);
    }

    @Test
    void failsWhenTheCardIsNotInTheLocalCopy() {
        wallets("d1", null);
        when(debitCardRepository.findById("d1")).thenReturn(Maybe.empty());

        send().test().assertError(BusinessRuleException.class);
        verifyNoInteractions(operationPublisher);
    }
}
