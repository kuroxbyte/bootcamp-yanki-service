package dev.bootcamp.yankiservice.application.usecase;

import dev.bootcamp.yankiservice.application.port.AccountOperationPublisher;
import dev.bootcamp.yankiservice.application.port.DebitCardLinkRepository;
import dev.bootcamp.yankiservice.application.port.DebitCardRepository;
import dev.bootcamp.yankiservice.application.port.WalletRepository;
import dev.bootcamp.yankiservice.domain.exception.BusinessRuleException;
import dev.bootcamp.yankiservice.domain.exception.NotFoundException;
import dev.bootcamp.yankiservice.domain.exception.OperationUnavailableException;
import dev.bootcamp.yankiservice.domain.model.debitcard.DebitCard;
import dev.bootcamp.yankiservice.domain.model.link.DebitCardLink;
import dev.bootcamp.yankiservice.domain.model.link.LinkStatus;
import dev.bootcamp.yankiservice.domain.model.operation.OperationId;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Implementación de {@link LinkDebitCardUseCase}.
 */
@RequiredArgsConstructor
public class LinkDebitCardUseCaseImpl implements LinkDebitCardUseCase {
    static final String CARD_ALREADY_LINKED =
            "La tarjeta de débito ya está asociada a un monedero";

    private final DebitCardRepository debitCardRepository;
    private final WalletRepository walletRepository;
    private final DebitCardLinkRepository linkRepository;
    private final AccountOperationPublisher operationPublisher;
    private final Clock clock;

    @Override
    public Single<DebitCardLink> authorize(String customerId, String phone, String debitCardId) {
        return ownedCard(customerId, debitCardId)
                .flatMap(card -> ensureCardFree(debitCardId).andThen(Single.defer(() ->
                        walletRepository.findByPhone(phone)
                                .switchIfEmpty(Single.error(() -> new NotFoundException(
                                        "El celular " + phone + " no tiene monedero Yanki"))))))
                .flatMap(wallet -> {
                    if (wallet.hasDebitCard()) {
                        return Single.error(new BusinessRuleException(
                                "El monedero ya tiene una tarjeta de débito asociada"));
                    }
                    return linkRepository.save(new DebitCardLink(
                            customerId,
                            debitCardId,
                            wallet.getId(),
                            phone,
                            LocalDate.now(clock)));
                });
    }

    @Override
    public Single<DebitCardLink> confirm(String walletId, String linkId) {
        return linkRepository.findById(linkId)
                .filter(link -> link.getWalletId().equals(walletId))
                .switchIfEmpty(Single.error(() -> new NotFoundException(
                        "La asociación no existe o no es del monedero")))
                .flatMap(link -> {
                    if (link.getStatus() != LinkStatus.PENDING_CONFIRMATION) {
                        return Single.error(new BusinessRuleException(
                                "La asociación ya no está pendiente de confirmación"));
                    }
                    return ensureCardFree(link.getDebitCardId())
                            .andThen(Single.defer(() -> debitCardRepository
                                    .findById(link.getDebitCardId())
                                    .switchIfEmpty(Single.error(() -> new BusinessRuleException(
                                            "La tarjeta de débito no está disponible")))))
                            .flatMap(card -> start(link, card));
                });
    }

    private Single<DebitCardLink> start(DebitCardLink link, DebitCard card) {
        return walletRepository.startLinking(link.getWalletId())
                .switchIfEmpty(Single.error(() -> new BusinessRuleException(
                        "El monedero no está disponible para asociar una tarjeta")))
                .flatMap(balance -> {
                    link.setTransferredBalance(balance);
                    if (balance.signum() == 0) {
                        return walletRepository
                                .finishLinking(link.getWalletId(), link.getDebitCardId())
                                .andThen(Single.defer(() -> {
                                    link.setStatus(LinkStatus.ACTIVE);
                                    return linkRepository.save(link);
                                }));
                    }
                    link.setStatus(LinkStatus.TRANSFERRING);
                    return linkRepository.save(link)
                            .flatMap(saved -> transfer(saved, card.primaryAccountId(), balance));
                });
    }

    // Pide abonar el saldo a la cuenta principal; si no se puede, deshace la asociación.
    private Single<DebitCardLink> transfer(
            DebitCardLink link,
            String accountId,
            BigDecimal balance) {
        return operationPublisher.requestCredit(
                        OperationId.linkTransfer(link.getId()).toString(),
                        accountId,
                        balance,
                        LocalDate.now(clock),
                        false)
                .andThen(Single.just(link))
                .onErrorResumeNext(error -> walletRepository
                        .cancelLinking(link.getWalletId(), balance)
                        .andThen(Single.defer(() -> {
                            link.setStatus(LinkStatus.REJECTED);
                            link.setRejectionReason(SendPaymentUseCaseImpl.UNAVAILABLE);
                            return linkRepository.save(link);
                        }))
                        .flatMap(rejected -> Single.error(new OperationUnavailableException(
                                SendPaymentUseCaseImpl.UNAVAILABLE,
                                error))));
    }

    private Single<DebitCard> ownedCard(String customerId, String debitCardId) {
        return debitCardRepository.findById(debitCardId)
                .filter(card -> card.belongsTo(customerId))
                .switchIfEmpty(Single.error(() -> new NotFoundException(
                        "La tarjeta de débito no existe o no pertenece al cliente")));
    }

    private Completable ensureCardFree(String debitCardId) {
        return walletRepository.existsByDebitCardId(debitCardId)
                .flatMapCompletable(linked -> linked
                        ? Completable.error(new BusinessRuleException(CARD_ALREADY_LINKED))
                        : Completable.complete());
    }
}
