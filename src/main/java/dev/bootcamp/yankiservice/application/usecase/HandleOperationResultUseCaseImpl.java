package dev.bootcamp.yankiservice.application.usecase;

import dev.bootcamp.yankiservice.application.port.AccountOperationPublisher;
import dev.bootcamp.yankiservice.application.port.DebitCardLinkRepository;
import dev.bootcamp.yankiservice.application.port.WalletRepository;
import dev.bootcamp.yankiservice.application.port.YankiPaymentRepository;
import dev.bootcamp.yankiservice.domain.model.link.DebitCardLink;
import dev.bootcamp.yankiservice.domain.model.link.LinkStatus;
import dev.bootcamp.yankiservice.domain.model.operation.OperationId;
import dev.bootcamp.yankiservice.domain.model.payment.PaymentStep;
import dev.bootcamp.yankiservice.domain.model.payment.YankiPayment;
import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Implementación de {@link HandleOperationResultUseCase}.
 */
@RequiredArgsConstructor
public class HandleOperationResultUseCaseImpl implements HandleOperationResultUseCase {
    private final YankiPaymentRepository paymentRepository;
    private final DebitCardLinkRepository linkRepository;
    private final WalletRepository walletRepository;
    private final AccountOperationPublisher operationPublisher;
    private final Clock clock;

    @Override
    public Completable execute(String operationId, boolean completed, String reason) {
        OperationId operation = OperationId.parse(operationId);
        if (OperationId.LINK.equals(operation.kind())) {
            return linkRepository.findById(operation.id())
                    .filter(link -> link.getStatus() == LinkStatus.TRANSFERRING)
                    .flatMapCompletable(link -> applyTransfer(link, completed, reason));
        }
        return paymentRepository.findById(operation.id())
                .filter(payment -> payment.getStep() == expectedStep(operation.action()))
                .flatMapCompletable(payment -> switch (operation.action()) {
                    case OperationId.DEBIT -> afterDebit(payment, completed, reason);
                    case OperationId.CREDIT -> afterCredit(payment, completed, reason);
                    default -> afterRefund(payment, completed, reason);
                });
    }

    private static PaymentStep expectedStep(String action) {
        return switch (action) {
            case OperationId.DEBIT -> PaymentStep.DEBITING_SOURCE;
            case OperationId.CREDIT -> PaymentStep.CREDITING_TARGET;
            default -> PaymentStep.REFUNDING_SOURCE;
        };
    }

    private Completable afterDebit(YankiPayment payment, boolean completed, String reason) {
        if (!completed) {
            payment.reject(reason);
            return save(payment);
        }
        if (payment.toAccount()) {
            payment.setStep(PaymentStep.CREDITING_TARGET);
            return save(payment).andThen(Completable.defer(() -> operationPublisher.requestCredit(
                    OperationId.payment(payment.getId(), OperationId.CREDIT).toString(),
                    payment.getTargetAccountId(),
                    payment.getAmount(),
                    LocalDate.now(clock),
                    false)));
        }
        return walletRepository.creditBalance(payment.getTargetWalletId(), payment.getAmount())
                .andThen(Completable.defer(() -> {
                    payment.complete();
                    return save(payment);
                }));
    }

    private Completable afterCredit(YankiPayment payment, boolean completed, String reason) {
        if (completed) {
            payment.complete();
            return save(payment);
        }
        if (payment.fromAccount()) {
            payment.setStep(PaymentStep.REFUNDING_SOURCE);
            payment.setRejectionReason(reason);
            return save(payment).andThen(Completable.defer(() -> operationPublisher.requestCredit(
                    OperationId.payment(payment.getId(), OperationId.REFUND).toString(),
                    payment.getSourceAccountId(),
                    payment.getAmount(),
                    LocalDate.now(clock),
                    true)));
        }
        return walletRepository.creditBalance(payment.getSourceWalletId(), payment.getAmount())
                .andThen(Completable.defer(() -> {
                    payment.reject(reason);
                    return save(payment);
                }));
    }

    private Completable afterRefund(YankiPayment payment, boolean completed, String reason) {
        payment.reject(completed
                ? payment.getRejectionReason()
                : payment.getRejectionReason() + "; la devolución del cargo no se pudo hacer: "
                        + reason);
        return save(payment);
    }

    private Completable applyTransfer(DebitCardLink link, boolean completed, String reason) {
        if (completed) {
            return walletRepository.finishLinking(link.getWalletId(), link.getDebitCardId())
                    .andThen(Completable.defer(() -> {
                        link.setStatus(LinkStatus.ACTIVE);
                        return linkRepository.save(link).ignoreElement();
                    }));
        }
        return walletRepository.cancelLinking(link.getWalletId(), link.getTransferredBalance())
                .andThen(Completable.defer(() -> {
                    link.setStatus(LinkStatus.REJECTED);
                    link.setRejectionReason(reason);
                    return linkRepository.save(link).ignoreElement();
                }));
    }

    private Completable save(YankiPayment payment) {
        return paymentRepository.save(payment).ignoreElement();
    }
}
