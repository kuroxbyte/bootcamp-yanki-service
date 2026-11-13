package dev.bootcamp.yankiservice.application.usecase;

import dev.bootcamp.yankiservice.application.port.DebitCardLinkRepository;
import dev.bootcamp.yankiservice.application.port.WalletRepository;
import dev.bootcamp.yankiservice.application.port.YankiPaymentRepository;
import dev.bootcamp.yankiservice.domain.exception.NotFoundException;
import dev.bootcamp.yankiservice.domain.model.link.DebitCardLink;
import dev.bootcamp.yankiservice.domain.model.payment.YankiPayment;
import dev.bootcamp.yankiservice.domain.model.wallet.Wallet;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

/**
 * Implementación de {@link GetWalletUseCase}.
 */
public class GetWalletUseCaseImpl implements GetWalletUseCase {
    private final WalletRepository walletRepository;
    private final YankiPaymentRepository paymentRepository;
    private final DebitCardLinkRepository linkRepository;

    /**
     * Crea el caso de uso con los adaptadores de sus puertos.
     */
    public GetWalletUseCaseImpl(
            WalletRepository walletRepository,
            YankiPaymentRepository paymentRepository,
            DebitCardLinkRepository linkRepository) {
        this.walletRepository = walletRepository;
        this.paymentRepository = paymentRepository;
        this.linkRepository = linkRepository;
    }

    @Override
    public Single<Wallet> findWallet(String walletId) {
        return walletRepository.findById(walletId)
                .switchIfEmpty(Single.error(() -> new NotFoundException("El monedero no existe")));
    }

    @Override
    public Flowable<YankiPayment> findPayments(String walletId) {
        return paymentRepository.findByWalletId(walletId);
    }

    @Override
    public Single<YankiPayment> findPayment(String walletId, String paymentId) {
        return paymentRepository.findById(paymentId)
                .filter(payment -> payment.getSourceWalletId().equals(walletId)
                        || payment.getTargetWalletId().equals(walletId))
                .switchIfEmpty(Single.error(() -> new NotFoundException(
                        "El pago no existe o no es del monedero")));
    }

    @Override
    public Flowable<DebitCardLink> findDebitCardLinks(String walletId) {
        return linkRepository.findByWalletId(walletId);
    }
}
