package dev.bootcamp.yankiservice.application.usecase;

import dev.bootcamp.yankiservice.application.port.WalletEventPublisher;
import dev.bootcamp.yankiservice.application.port.WalletRepository;
import dev.bootcamp.yankiservice.application.port.YankiPaymentRepository;
import dev.bootcamp.yankiservice.domain.exception.BusinessRuleException;
import dev.bootcamp.yankiservice.domain.exception.NotFoundException;
import dev.bootcamp.yankiservice.domain.model.payment.PaymentStatus;
import dev.bootcamp.yankiservice.domain.model.wallet.Wallet;
import dev.bootcamp.yankiservice.domain.model.wallet.WalletStatus;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;

/**
 * Implementación de {@link UpdateWalletUseCase}.
 */
@RequiredArgsConstructor
public class UpdateWalletUseCaseImpl implements UpdateWalletUseCase {
    private final WalletRepository walletRepository;
    private final YankiPaymentRepository paymentRepository;
    private final WalletEventPublisher walletEventPublisher;

    @Override
    public Single<Wallet> update(String walletId, String imei, String email) {
        return activeWallet(walletId)
                .map(wallet -> wallet.withContact(imei, email))
                .flatMap(updated -> walletRepository
                        .updateContact(walletId, updated.getImei(), updated.getEmail())
                        .andThen(activeWallet(walletId)));
    }

    @Override
    public Completable close(String walletId) {
        return activeWallet(walletId)
                .flatMap(wallet -> {
                    wallet.validateClosing();
                    return paymentRepository.findByWalletId(walletId)
                            .any(payment -> payment.getStatus() == PaymentStatus.PENDING)
                            .flatMap(pending -> pending
                                    ? Single.<Wallet>error(new BusinessRuleException(
                                            "El monedero tiene pagos pendientes; intente en unos"
                                                    + " segundos"))
                                    : Single.just(wallet));
                })
                .flatMap(wallet -> walletRepository.close(wallet)
                        .flatMap(closed -> closed
                                ? Single.just(wallet)
                                : Single.<Wallet>error(new BusinessRuleException(
                                        "El monedero cambió mientras se daba de baja; intente"
                                                + " de nuevo"))))
                .flatMapCompletable(wallet -> {
                    wallet.setStatus(WalletStatus.CLOSED);
                    return walletEventPublisher.publishWalletClosed(wallet);
                });
    }

    private Single<Wallet> activeWallet(String walletId) {
        return walletRepository.findById(walletId)
                .filter(wallet -> !wallet.isClosed())
                .switchIfEmpty(Single.error(() -> new NotFoundException("El monedero no existe")));
    }
}
