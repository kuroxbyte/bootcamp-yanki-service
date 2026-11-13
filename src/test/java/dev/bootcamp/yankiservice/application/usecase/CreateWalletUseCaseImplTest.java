package dev.bootcamp.yankiservice.application.usecase;

import dev.bootcamp.yankiservice.application.port.WalletEventPublisher;
import dev.bootcamp.yankiservice.application.port.WalletRepository;
import dev.bootcamp.yankiservice.domain.exception.BusinessRuleException;
import dev.bootcamp.yankiservice.domain.model.wallet.DocumentType;
import dev.bootcamp.yankiservice.domain.model.wallet.Wallet;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateWalletUseCaseImplTest {

    @Mock private WalletRepository walletRepository;

    @Mock private WalletEventPublisher walletEventPublisher;

    private CreateWalletUseCaseImpl useCase;

    @BeforeEach
    void createUseCase() {
        Clock clock = Clock.fixed(
                Fixtures.DATE.atStartOfDay().toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC);
        useCase = new CreateWalletUseCaseImpl(walletRepository, walletEventPublisher, clock);
    }

    @Test
    void savesWalletAndPublishesWalletCreated() {
        when(walletRepository.findByPhone("987654321")).thenReturn(Maybe.empty());
        when(walletRepository.save(any())).thenAnswer(invocation -> {
            Wallet wallet = invocation.getArgument(0);
            wallet.setId("w1");
            return Single.just(wallet);
        });
        when(walletEventPublisher.publishWalletCreated(any())).thenReturn(Completable.complete());

        useCase.execute(DocumentType.DNI, "12345678", "987654321", Fixtures.IMEI, "a@b.pe")
                .test()
                .assertValue(wallet -> wallet.getId().equals("w1")
                        && wallet.getCreatedAt().equals(Fixtures.DATE));
        verify(walletEventPublisher).publishWalletCreated(any());
    }

    @Test
    void rejectsPhoneThatAlreadyHasWallet() {
        when(walletRepository.findByPhone("987654321"))
                .thenReturn(Maybe.just(Fixtures.wallet("w9", "987654321", null)));

        useCase.execute(DocumentType.DNI, "12345678", "987654321", Fixtures.IMEI, "a@b.pe")
                .test()
                .assertError(BusinessRuleException.class);
        verify(walletRepository, never()).save(any());
        verifyNoInteractions(walletEventPublisher);
    }

    @Test
    void rejectsInvalidDataWithoutTouchingTheRepository() {
        useCase.execute(DocumentType.DNI, "12345678", "123", Fixtures.IMEI, "a@b.pe")
                .test()
                .assertError(BusinessRuleException.class);
        verifyNoInteractions(walletRepository, walletEventPublisher);
    }
}
