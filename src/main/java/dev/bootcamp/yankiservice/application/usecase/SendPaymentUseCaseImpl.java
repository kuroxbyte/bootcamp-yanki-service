package dev.bootcamp.yankiservice.application.usecase;

import dev.bootcamp.yankiservice.application.port.AccountOperationPublisher;
import dev.bootcamp.yankiservice.application.port.DebitCardRepository;
import dev.bootcamp.yankiservice.application.port.WalletRepository;
import dev.bootcamp.yankiservice.application.port.YankiPaymentRepository;
import dev.bootcamp.yankiservice.domain.exception.BusinessRuleException;
import dev.bootcamp.yankiservice.domain.exception.NotFoundException;
import dev.bootcamp.yankiservice.domain.exception.OperationUnavailableException;
import dev.bootcamp.yankiservice.domain.model.operation.OperationId;
import dev.bootcamp.yankiservice.domain.model.payment.YankiPayment;
import dev.bootcamp.yankiservice.domain.model.wallet.Wallet;
import dev.bootcamp.yankiservice.domain.model.wallet.WalletStatus;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Implementación de {@link SendPaymentUseCase}.
 */
public class SendPaymentUseCaseImpl implements SendPaymentUseCase {
    static final String INSUFFICIENT_BALANCE = "Saldo insuficiente en el monedero";
    static final String UNAVAILABLE =
            "No se pudo procesar el pago en este momento; intente nuevamente";

    private final WalletRepository walletRepository;
    private final YankiPaymentRepository paymentRepository;
    private final DebitCardRepository debitCardRepository;
    private final AccountOperationPublisher operationPublisher;

    /**
     * Crea el caso de uso con los adaptadores de sus puertos.
     */
    public SendPaymentUseCaseImpl(
            WalletRepository walletRepository,
            YankiPaymentRepository paymentRepository,
            DebitCardRepository debitCardRepository,
            AccountOperationPublisher operationPublisher) {
        this.walletRepository = walletRepository;
        this.paymentRepository = paymentRepository;
        this.debitCardRepository = debitCardRepository;
        this.operationPublisher = operationPublisher;
    }

    @Override
    public Single<YankiPayment> execute(
            String walletId,
            String targetPhone,
            BigDecimal amount,
            LocalDate date) {
        Single<Wallet> source = activeWallet(walletRepository.findById(walletId),
                new NotFoundException("El monedero no existe"));
        Single<Wallet> target = activeWallet(walletRepository.findByPhone(targetPhone),
                new NotFoundException("El celular " + targetPhone + " no tiene monedero Yanki"));
        return Single.zip(source, target, (from, to) -> new Wallet[] {from, to})
                .flatMap(wallets -> Single.zip(
                        accountOf(wallets[0]),
                        accountOf(wallets[1]),
                        (fromAccount, toAccount) -> new YankiPayment(
                                wallets[0].getId(),
                                wallets[0].getPhone(),
                                fromAccount.orElse(null),
                                wallets[1].getId(),
                                wallets[1].getPhone(),
                                toAccount.orElse(null),
                                amount,
                                date)))
                .flatMap(this::start);
    }

    private Single<YankiPayment> start(YankiPayment payment) {
        if (payment.fromAccount()) {
            // Cuenta → cualquiera: primero el cargo a la cuenta del origen.
            return paymentRepository.save(payment)
                    .flatMap(saved -> request(saved, operationPublisher.requestDebit(
                            OperationId.payment(saved.getId(), OperationId.DEBIT).toString(),
                            saved.getSourceAccountId(),
                            saved.getAmount(),
                            saved.getDate()), Completable.complete()));
        }
        Completable debitWallet = walletRepository
                .debitBalance(payment.getSourceWalletId(), payment.getAmount())
                .flatMapCompletable(debited -> debited
                        ? Completable.complete()
                        : Completable.error(new BusinessRuleException(INSUFFICIENT_BALANCE)));
        if (!payment.toAccount()) {
            // Monedero → monedero: todo en el momento.
            return debitWallet
                    .andThen(Completable.defer(() -> walletRepository.creditBalance(
                            payment.getTargetWalletId(),
                            payment.getAmount())))
                    .andThen(Single.defer(() -> {
                        payment.complete();
                        return paymentRepository.save(payment);
                    }));
        }
        // Monedero → cuenta: se descuenta del monedero y se pide el abono.
        Completable refundWallet = Completable.defer(() -> walletRepository.creditBalance(
                payment.getSourceWalletId(),
                payment.getAmount()));
        return debitWallet
                .andThen(Single.defer(() -> paymentRepository.save(payment)))
                .flatMap(saved -> request(saved, operationPublisher.requestCredit(
                        OperationId.payment(saved.getId(), OperationId.CREDIT).toString(),
                        saved.getTargetAccountId(),
                        saved.getAmount(),
                        saved.getDate(),
                        false), refundWallet));
    }

    // Publica el pedido; si falla, devuelve lo descontado, rechaza el pago y responde 503.
    private Single<YankiPayment> request(
            YankiPayment payment,
            Completable operation,
            Completable compensation) {
        return operation
                .andThen(Single.just(payment))
                .onErrorResumeNext(error -> compensation
                        .andThen(Single.defer(() -> {
                            payment.reject(UNAVAILABLE);
                            return paymentRepository.save(payment);
                        }))
                        .flatMap(rejected -> Single.error(
                                new OperationUnavailableException(UNAVAILABLE, error))));
    }

    private static Single<Wallet> activeWallet(Maybe<Wallet> wallet, RuntimeException notFound) {
        return wallet
                .switchIfEmpty(Single.error(notFound))
                .flatMap(found -> found.getStatus() == WalletStatus.ACTIVE
                        ? Single.just(found)
                        : Single.error(new BusinessRuleException(
                                "El monedero " + found.getPhone()
                                        + " está asociando una tarjeta; intente en unos"
                                        + " segundos")));
    }

    // Cuenta principal de la tarjeta del monedero, o vacío si usa su propio saldo.
    private Single<Optional<String>> accountOf(Wallet wallet) {
        if (!wallet.hasDebitCard()) {
            return Single.just(Optional.empty());
        }
        return debitCardRepository.findById(wallet.getDebitCardId())
                .map(card -> Optional.of(card.primaryAccountId()))
                .switchIfEmpty(Single.error(() -> new BusinessRuleException(
                        "La tarjeta de débito del monedero no está disponible")));
    }
}
