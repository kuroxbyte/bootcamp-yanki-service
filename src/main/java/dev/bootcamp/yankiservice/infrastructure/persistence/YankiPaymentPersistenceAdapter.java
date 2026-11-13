package dev.bootcamp.yankiservice.infrastructure.persistence;

import dev.bootcamp.yankiservice.application.port.YankiPaymentRepository;
import dev.bootcamp.yankiservice.domain.model.payment.PaymentStatus;
import dev.bootcamp.yankiservice.domain.model.payment.PaymentStep;
import dev.bootcamp.yankiservice.domain.model.payment.YankiPayment;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementa el puerto {@link YankiPaymentRepository} con MongoDB reactivo.
 */
@Component
@RequiredArgsConstructor
public class YankiPaymentPersistenceAdapter implements YankiPaymentRepository {

    private final YankiPaymentMongoRepository mongoRepository;

    @Override
    public Single<YankiPayment> save(YankiPayment payment) {
        return Single.fromPublisher(mongoRepository.save(new YankiPaymentDocument(
                        payment.getId(),
                        payment.getSourceWalletId(),
                        payment.getSourcePhone(),
                        payment.getSourceAccountId(),
                        payment.getTargetWalletId(),
                        payment.getTargetPhone(),
                        payment.getTargetAccountId(),
                        payment.getAmount(),
                        payment.getDate(),
                        payment.getStatus().name(),
                        payment.getStep().name(),
                        payment.getRejectionReason())))
                .map(YankiPaymentPersistenceAdapter::toDomain);
    }

    @Override
    public Maybe<YankiPayment> findById(String paymentId) {
        return Maybe.fromPublisher(mongoRepository.findById(paymentId))
                .map(YankiPaymentPersistenceAdapter::toDomain);
    }

    @Override
    public Flowable<YankiPayment> findByWalletId(String walletId) {
        return Flowable.fromPublisher(mongoRepository
                        .findBySourceWalletIdOrTargetWalletIdOrderByDateDescIdDesc(
                                walletId,
                                walletId))
                .map(YankiPaymentPersistenceAdapter::toDomain);
    }

    private static YankiPayment toDomain(YankiPaymentDocument document) {
        YankiPayment payment = new YankiPayment(
                document.getSourceWalletId(),
                document.getSourcePhone(),
                document.getSourceAccountId(),
                document.getTargetWalletId(),
                document.getTargetPhone(),
                document.getTargetAccountId(),
                document.getAmount(),
                document.getDate());
        payment.setId(document.getId());
        payment.setStatus(PaymentStatus.valueOf(document.getStatus()));
        payment.setStep(PaymentStep.valueOf(document.getStep()));
        payment.setRejectionReason(document.getRejectionReason());
        return payment;
    }
}
