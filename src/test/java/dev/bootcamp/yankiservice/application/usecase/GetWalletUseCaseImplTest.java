package dev.bootcamp.yankiservice.application.usecase;

import dev.bootcamp.yankiservice.application.port.DebitCardLinkRepository;
import dev.bootcamp.yankiservice.application.port.WalletRepository;
import dev.bootcamp.yankiservice.application.port.YankiPaymentRepository;
import dev.bootcamp.yankiservice.domain.exception.NotFoundException;
import dev.bootcamp.yankiservice.domain.model.link.DebitCardLink;
import dev.bootcamp.yankiservice.domain.model.payment.YankiPayment;
import dev.bootcamp.yankiservice.domain.model.wallet.Wallet;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetWalletUseCaseImplTest {

    @Mock private WalletRepository walletRepository;

    @Mock private YankiPaymentRepository paymentRepository;

    @Mock private DebitCardLinkRepository linkRepository;

    private GetWalletUseCaseImpl useCase;

    @BeforeEach
    void createUseCase() {
        useCase = new GetWalletUseCaseImpl(walletRepository, paymentRepository, linkRepository);
    }

    @Test
    void findsWalletOrFailsWithNotFound() {
        Wallet wallet = Fixtures.wallet("w1", "987654321", null);
        when(walletRepository.findById("w1")).thenReturn(Maybe.just(wallet));
        when(walletRepository.findById("w9")).thenReturn(Maybe.empty());

        useCase.findWallet("w1").test().assertValue(wallet);
        useCase.findWallet("w9").test().assertError(NotFoundException.class);
    }

    @Test
    void findsPaymentOnlyWhenTheWalletTakesPart() {
        YankiPayment payment = Fixtures.payment("p1", null, null);
        when(paymentRepository.findById("p1")).thenReturn(Maybe.just(payment));

        useCase.findPayment("w1", "p1").test().assertValue(payment);
        useCase.findPayment("w2", "p1").test().assertValue(payment);
        useCase.findPayment("w3", "p1").test().assertError(NotFoundException.class);
    }

    @Test
    void listsPaymentsAndLinksOfTheWallet() {
        YankiPayment payment = Fixtures.payment("p1", null, null);
        DebitCardLink link = Fixtures.link("l1");
        when(paymentRepository.findByWalletId("w1")).thenReturn(Flowable.just(payment));
        when(linkRepository.findByWalletId("w1")).thenReturn(Flowable.just(link));

        useCase.findPayments("w1").test().assertValues(payment);
        useCase.findDebitCardLinks("w1").test().assertValues(link);
    }
}
