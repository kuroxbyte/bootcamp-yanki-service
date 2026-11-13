package dev.bootcamp.yankiservice.infrastructure.persistence;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

/**
 * Repositorio de Spring Data para {@link YankiPaymentDocument}.
 */
public interface YankiPaymentMongoRepository
        extends ReactiveMongoRepository<YankiPaymentDocument, String> {

    /**
     * Pagos en los que el monedero es origen o destino, del más reciente al más antiguo.
     */
    Flux<YankiPaymentDocument> findBySourceWalletIdOrTargetWalletIdOrderByDateDescIdDesc(
            String sourceWalletId,
            String targetWalletId);
}
