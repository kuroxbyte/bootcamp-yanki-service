package dev.bootcamp.yankiservice.infrastructure.persistence;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

/**
 * Repositorio de Spring Data para {@link WalletDocument}.
 */
public interface WalletMongoRepository extends ReactiveMongoRepository<WalletDocument, String> {

    /**
     * Busca el monedero por su celular.
     */
    Mono<WalletDocument> findByPhone(String phone);

    /**
     * Indica si la tarjeta ya está asociada a algún monedero.
     */
    Mono<Boolean> existsByDebitCardId(String debitCardId);
}
