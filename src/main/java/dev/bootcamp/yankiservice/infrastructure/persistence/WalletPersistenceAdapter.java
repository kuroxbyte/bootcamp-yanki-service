package dev.bootcamp.yankiservice.infrastructure.persistence;

import dev.bootcamp.yankiservice.application.port.WalletRepository;
import dev.bootcamp.yankiservice.domain.model.wallet.DocumentType;
import dev.bootcamp.yankiservice.domain.model.wallet.Wallet;
import dev.bootcamp.yankiservice.domain.model.wallet.WalletStatus;
import dev.bootcamp.yankiservice.infrastructure.cache.WalletPhoneCache;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import org.bson.types.Decimal128;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Implementa el puerto {@link WalletRepository} con MongoDB reactivo.
 *
 * <p>Las operaciones de saldo y de asociación son una sola actualización con condiciones
 * ({@code updateFirst} o {@code findAndModify}): dos pagos a la vez no dejan un saldo negativo. La
 * búsqueda por celular usa {@link WalletPhoneCache} para resolver el id del monedero.
 */
@Component
@RequiredArgsConstructor
public class WalletPersistenceAdapter implements WalletRepository {

    private static final String ID = "_id";
    private static final String BALANCE = "balance";
    private static final String STATUS = "status";
    private static final String DEBIT_CARD_ID = "debitCardId";

    private final WalletMongoRepository mongoRepository;
    private final ReactiveMongoTemplate mongoTemplate;
    private final WalletPhoneCache phoneCache;

    @Override
    public Single<Wallet> save(Wallet wallet) {
        return Single.fromPublisher(mongoRepository.save(toDocument(wallet)))
                .map(WalletPersistenceAdapter::toDomain);
    }

    @Override
    public Maybe<Wallet> findById(String walletId) {
        return Maybe.fromPublisher(mongoRepository.findById(walletId))
                .map(WalletPersistenceAdapter::toDomain);
    }

    @Override
    public Maybe<Wallet> findByPhone(String phone) {
        Mono<WalletDocument> fromDatabase = mongoRepository.findByPhone(phone)
                .flatMap(document -> phoneCache.put(phone, document.getId())
                        .thenReturn(document));
        Mono<WalletDocument> fromCache = phoneCache.walletId(phone)
                .flatMap(mongoRepository::findById);
        return Maybe.fromPublisher(fromCache.switchIfEmpty(fromDatabase))
                .map(WalletPersistenceAdapter::toDomain);
    }

    @Override
    public Single<Boolean> existsByDebitCardId(String debitCardId) {
        return Single.fromPublisher(mongoRepository.existsByDebitCardId(debitCardId));
    }

    @Override
    public Single<Boolean> debitBalance(String walletId, BigDecimal amount) {
        Query query = Query.query(available(walletId)
                .and(BALANCE).gte(new Decimal128(amount)));
        Update update = new Update().inc(BALANCE, new Decimal128(amount.negate()));
        return Single.fromPublisher(mongoTemplate.updateFirst(query, update, WalletDocument.class))
                .map(result -> result.getModifiedCount() == 1);
    }

    @Override
    public Completable creditBalance(String walletId, BigDecimal amount) {
        Update update = new Update().inc(BALANCE, new Decimal128(amount));
        return Completable.fromPublisher(mongoTemplate.updateFirst(
                Query.query(where(ID).is(walletId)),
                update,
                WalletDocument.class));
    }

    @Override
    public Maybe<BigDecimal> startLinking(String walletId) {
        Update update = new Update()
                .set(STATUS, WalletStatus.LINKING.name())
                .set(BALANCE, new Decimal128(BigDecimal.ZERO));
        return Maybe.fromPublisher(mongoTemplate.findAndModify(
                        Query.query(available(walletId)),
                        update,
                        FindAndModifyOptions.options().returnNew(false),
                        WalletDocument.class))
                .map(WalletDocument::getBalance);
    }

    @Override
    public Completable finishLinking(String walletId, String debitCardId) {
        Update update = new Update()
                .set(DEBIT_CARD_ID, debitCardId)
                .set(STATUS, WalletStatus.ACTIVE.name());
        return Completable.fromPublisher(mongoTemplate.updateFirst(
                Query.query(where(ID).is(walletId)),
                update,
                WalletDocument.class));
    }

    @Override
    public Completable cancelLinking(String walletId, BigDecimal balance) {
        Update update = new Update()
                .set(STATUS, WalletStatus.ACTIVE.name())
                .inc(BALANCE, new Decimal128(balance));
        return Completable.fromPublisher(mongoTemplate.updateFirst(
                Query.query(where(ID).is(walletId)),
                update,
                WalletDocument.class));
    }

    // Monedero activo y sin tarjeta: el único que usa su propio saldo.
    private static Criteria available(String walletId) {
        return where(ID).is(walletId)
                .and(STATUS).is(WalletStatus.ACTIVE.name())
                .and(DEBIT_CARD_ID).is(null);
    }

    private static WalletDocument toDocument(Wallet wallet) {
        return new WalletDocument(
                wallet.getId(),
                wallet.getDocumentType().name(),
                wallet.getDocumentNumber(),
                wallet.getPhone(),
                wallet.getImei(),
                wallet.getEmail(),
                wallet.getBalance(),
                wallet.getDebitCardId(),
                wallet.getStatus().name(),
                wallet.getCreatedAt());
    }

    private static Wallet toDomain(WalletDocument document) {
        Wallet wallet = new Wallet(
                DocumentType.valueOf(document.getDocumentType()),
                document.getDocumentNumber(),
                document.getPhone(),
                document.getImei(),
                document.getEmail());
        wallet.setId(document.getId());
        wallet.setBalance(document.getBalance());
        wallet.setDebitCardId(document.getDebitCardId());
        wallet.setStatus(WalletStatus.valueOf(document.getStatus()));
        wallet.setCreatedAt(document.getCreatedAt());
        return wallet;
    }
}
