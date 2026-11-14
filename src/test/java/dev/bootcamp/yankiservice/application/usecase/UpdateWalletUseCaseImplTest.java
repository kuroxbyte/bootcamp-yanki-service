package dev.bootcamp.yankiservice.application.usecase;

import dev.bootcamp.yankiservice.application.port.WalletEventPublisher;
import dev.bootcamp.yankiservice.application.port.WalletRepository;
import dev.bootcamp.yankiservice.application.port.YankiPaymentRepository;
import dev.bootcamp.yankiservice.domain.exception.BusinessRuleException;
import dev.bootcamp.yankiservice.domain.exception.NotFoundException;
import dev.bootcamp.yankiservice.domain.model.payment.YankiPayment;
import dev.bootcamp.yankiservice.domain.model.wallet.DocumentType;
import dev.bootcamp.yankiservice.domain.model.wallet.Wallet;
import dev.bootcamp.yankiservice.domain.model.wallet.WalletStatus;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateWalletUseCaseImplTest {

    @Mock private WalletRepository walletRepository;

    @Mock private YankiPaymentRepository paymentRepository;

    @Mock private WalletEventPublisher walletEventPublisher;

    private UpdateWalletUseCaseImpl useCase() {
        return new UpdateWalletUseCaseImpl(
                walletRepository, paymentRepository, walletEventPublisher);
    }

    private static Wallet wallet() {
        Wallet wallet = new Wallet(
                DocumentType.DNI, "12345678", "987654321", "490154203237518", "a@b.pe");
        wallet.setId("w1");
        return wallet;
    }

    private static YankiPayment pendingPayment() {
        return new YankiPayment(
                "w1", "987654321", null, "w2", "912345678", "a2", BigDecimal.TEN,
                LocalDate.of(2026, 11, 16));
    }

    @Test
    void updatesContactAndReturnsTheReloadedWallet() {
        Wallet updated = wallet().withContact("356938035643809", "nuevo@mail.com");
        when(walletRepository.findById("w1"))
                .thenReturn(Maybe.just(wallet()), Maybe.just(updated));
        when(walletRepository.updateContact("w1", "356938035643809", "nuevo@mail.com"))
                .thenReturn(Completable.complete());

        useCase().update("w1", "356938035643809", "nuevo@mail.com").test()
                .assertValue(wallet -> wallet.getEmail().equals("nuevo@mail.com"));
    }

    @Test
    void invalidContactIsNotSaved() {
        when(walletRepository.findById("w1")).thenReturn(Maybe.just(wallet()));

        useCase().update("w1", "1", "nuevo@mail.com").test()
                .assertError(BusinessRuleException.class);
        verify(walletRepository, never()).updateContact(anyString(), anyString(), anyString());
    }

    @Test
    void closesWalletAndPublishesIt() {
        when(walletRepository.findById("w1")).thenReturn(Maybe.just(wallet()));
        when(paymentRepository.findByWalletId("w1")).thenReturn(Flowable.empty());
        when(walletRepository.close("w1", BigDecimal.ZERO)).thenReturn(Single.just(true));
        when(walletEventPublisher.publishWalletClosed(any())).thenReturn(Completable.complete());

        useCase().close("w1").test().assertComplete();

        verify(walletEventPublisher).publishWalletClosed(argThat(Wallet::isClosed));
    }

    @Test
    void doesNotCloseWithPendingPaymentsOrWhenItChanged() {
        when(walletRepository.findById("w1")).thenReturn(Maybe.just(wallet()));
        when(paymentRepository.findByWalletId("w1"))
                .thenReturn(Flowable.just(pendingPayment()), Flowable.empty());
        when(walletRepository.close("w1", BigDecimal.ZERO)).thenReturn(Single.just(false));

        useCase().close("w1").test().assertError(BusinessRuleException.class);
        useCase().close("w1").test().assertError(BusinessRuleException.class);
        verify(walletEventPublisher, never()).publishWalletClosed(any());
    }

    @Test
    void closedWalletIsNotFound() {
        Wallet closed = wallet();
        closed.setStatus(WalletStatus.CLOSED);
        when(walletRepository.findById("w1")).thenReturn(Maybe.just(closed));

        useCase().close("w1").test().assertError(NotFoundException.class);
        useCase().update("w1", "356938035643809", "x@y.pe").test()
                .assertError(NotFoundException.class);
    }
}
