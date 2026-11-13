package dev.bootcamp.yankiservice.application.usecase;

import dev.bootcamp.yankiservice.application.port.WalletEventPublisher;
import dev.bootcamp.yankiservice.application.port.WalletRepository;
import dev.bootcamp.yankiservice.domain.exception.BusinessRuleException;
import dev.bootcamp.yankiservice.domain.model.wallet.DocumentType;
import dev.bootcamp.yankiservice.domain.model.wallet.Wallet;
import io.reactivex.rxjava3.core.Single;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Implementación de {@link CreateWalletUseCase}.
 */
public class CreateWalletUseCaseImpl implements CreateWalletUseCase {
    private final WalletRepository walletRepository;
    private final WalletEventPublisher walletEventPublisher;
    private final Clock clock;

    /**
     * Crea el caso de uso con sus puertos y el reloj que fija la fecha de alta.
     */
    public CreateWalletUseCaseImpl(
            WalletRepository walletRepository,
            WalletEventPublisher walletEventPublisher,
            Clock clock) {
        this.walletRepository = walletRepository;
        this.walletEventPublisher = walletEventPublisher;
        this.clock = clock;
    }

    @Override
    public Single<Wallet> execute(
            DocumentType documentType,
            String documentNumber,
            String phone,
            String imei,
            String email) {
        return Single.fromCallable(
                        () -> new Wallet(documentType, documentNumber, phone, imei, email))
                .flatMap(wallet -> walletRepository.findByPhone(phone)
                        .isEmpty()
                        .flatMap(free -> {
                            if (!free) {
                                return Single.error(new BusinessRuleException(
                                        "El celular ya tiene un monedero Yanki"));
                            }
                            wallet.setCreatedAt(LocalDate.now(clock));
                            return walletRepository.save(wallet);
                        }))
                .flatMap(saved -> walletEventPublisher.publishWalletCreated(saved)
                        .andThen(Single.just(saved)));
    }
}
