package dev.bootcamp.yankiservice.infrastructure.persistence;

import dev.bootcamp.yankiservice.application.port.DebitCardLinkRepository;
import dev.bootcamp.yankiservice.domain.model.link.DebitCardLink;
import dev.bootcamp.yankiservice.domain.model.link.LinkStatus;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implementa el puerto {@link DebitCardLinkRepository} con MongoDB reactivo.
 */
@Component
@RequiredArgsConstructor
public class DebitCardLinkPersistenceAdapter implements DebitCardLinkRepository {

    private final DebitCardLinkMongoRepository mongoRepository;

    @Override
    public Single<DebitCardLink> save(DebitCardLink link) {
        return Single.fromPublisher(mongoRepository.save(new DebitCardLinkDocument(
                        link.getId(),
                        link.getCustomerId(),
                        link.getDebitCardId(),
                        link.getWalletId(),
                        link.getPhone(),
                        link.getRequestedAt(),
                        link.getStatus().name(),
                        link.getTransferredBalance(),
                        link.getRejectionReason())))
                .map(DebitCardLinkPersistenceAdapter::toDomain);
    }

    @Override
    public Maybe<DebitCardLink> findById(String linkId) {
        return Maybe.fromPublisher(mongoRepository.findById(linkId))
                .map(DebitCardLinkPersistenceAdapter::toDomain);
    }

    @Override
    public Flowable<DebitCardLink> findByWalletId(String walletId) {
        return Flowable.fromPublisher(mongoRepository.findByWalletId(walletId))
                .map(DebitCardLinkPersistenceAdapter::toDomain);
    }

    private static DebitCardLink toDomain(DebitCardLinkDocument document) {
        DebitCardLink link = new DebitCardLink(
                document.getCustomerId(),
                document.getDebitCardId(),
                document.getWalletId(),
                document.getPhone(),
                document.getRequestedAt());
        link.setId(document.getId());
        link.setStatus(LinkStatus.valueOf(document.getStatus()));
        link.setTransferredBalance(document.getTransferredBalance());
        link.setRejectionReason(document.getRejectionReason());
        return link;
    }
}
