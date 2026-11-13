package dev.bootcamp.yankiservice.infrastructure.persistence;

import org.bson.types.Decimal128;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Update;
import reactor.core.publisher.Mono;

/**
 * Repositorio de Spring Data para {@link WalletDocument}.
 *
 * <p>Las actualizaciones de saldo y de asociación son métodos derivados con {@link Update}: el
 * filtro sale del nombre del método y la actualización es fija, así que cada una es una sola
 * operación atómica sin consultas dinámicas ni {@code @Query}. Devuelven cuántos documentos
 * cambiaron.
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

    /**
     * Suma {@code increment} al saldo si el monedero está en {@code status}, no tiene tarjeta y su
     * saldo es al menos {@code minimum}.
     */
    @Update("{ '$inc': { 'balance': ?3 } }")
    Mono<Long> findAndIncrementBalanceByIdAndStatusAndDebitCardIdIsNullAndBalanceGreaterThanEqual(
            String id, String status, Decimal128 minimum, Decimal128 increment);

    /**
     * Suma {@code increment} al saldo del monedero.
     */
    @Update("{ '$inc': { 'balance': ?1 } }")
    Mono<Long> findAndIncrementBalanceById(String id, Decimal128 increment);

    /**
     * Pasa a {@code LINKING} y deja el saldo en {@code newBalance} si el monedero sigue en
     * {@code status}, sin tarjeta y con el saldo {@code balance} leído antes.
     */
    @Update("{ '$set': { 'status': 'LINKING', 'balance': ?3 } }")
    Mono<Long> findAndStartLinkingByIdAndStatusAndDebitCardIdIsNullAndBalance(
            String id, String status, Decimal128 balance, Decimal128 newBalance);

    /**
     * Guarda la tarjeta del monedero y lo deja activo.
     */
    @Update("{ '$set': { 'debitCardId': ?1, 'status': 'ACTIVE' } }")
    Mono<Long> findAndSetDebitCardIdById(String id, String debitCardId);

    /**
     * Devuelve {@code balance} al saldo y deja el monedero activo.
     */
    @Update("{ '$set': { 'status': 'ACTIVE' }, '$inc': { 'balance': ?1 } }")
    Mono<Long> findAndRestoreBalanceById(String id, Decimal128 balance);
}
