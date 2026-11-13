package dev.bootcamp.yankiservice.infrastructure.persistence;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

/**
 * Repositorio de Spring Data para {@link DebitCardLinkDocument}.
 */
public interface DebitCardLinkMongoRepository
        extends ReactiveMongoRepository<DebitCardLinkDocument, String> {

    /**
     * Asociaciones dirigidas al monedero.
     */
    Flux<DebitCardLinkDocument> findByWalletId(String walletId);
}
