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
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;

/**
 * Implementa el puerto {@link WalletRepository} con MongoDB reactivo.
 *
 * <p>Las operaciones de saldo y de asociación son una sola actualización condicionada, derivada
 * del nombre del método en {@link WalletMongoRepository}: dos pagos a la vez no dejan un saldo
 * negativo. Al iniciar la asociación se lee el saldo y se actualiza solo si no cambió; si un pago
 * se cruzó, se reintenta. La
 * búsqueda por celular usa {@link WalletPhoneCache} para resolver el id del monedero.
 */
@Component
@RequiredArgsConstructor
public class WalletPersistenceAdapter implements WalletRepository {

    private static final String ACTIVE = WalletStatus.ACTIVE.name();
    private static final Decimal128 ZERO = new Decimal128(BigDecimal.ZERO);
    private static final int LINKING_RETRIES = 5;

    private final WalletMongoRepository mongoRepository;
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
        // Si el monedero en caché ya no tiene ese celular (fue dado de baja), se busca en la base.
        Mono<WalletDocument> fromCache = phoneCache.walletId(phone)
                .flatMap(mongoRepository::findById)
                .filter(document -> phone.equals(document.getPhone()));
        return Maybe.fromPublisher(fromCache.switchIfEmpty(fromDatabase))
                .map(WalletPersistenceAdapter::toDomain);
    }

    @Override
    public Single<Boolean> existsByDebitCardId(String debitCardId) {
        return Single.fromPublisher(mongoRepository.existsByDebitCardId(debitCardId));
    }

    @Override
    public Single<Boolean> debitBalance(String walletId, BigDecimal amount) {
        Mono<Long> modified = mongoRepository
                .findAndIncrementBalanceByIdAndStatusAndDebitCardIdIsNullAndBalanceGreaterThanEqual(
                        walletId, ACTIVE, new Decimal128(amount), new Decimal128(amount.negate()));
        return Single.fromPublisher(modified).map(count -> count == 1);
    }

    @Override
    public Completable creditBalance(String walletId, BigDecimal amount) {
        return Completable.fromPublisher(
                mongoRepository.findAndIncrementBalanceById(walletId, new Decimal128(amount)));
    }

    @Override
    public Maybe<BigDecimal> startLinking(String walletId) {
        Mono<BigDecimal> attempt = mongoRepository.findById(walletId)
                .filter(WalletPersistenceAdapter::isAvailable)
                .flatMap(document -> mongoRepository
                        .findAndStartLinkingByIdAndStatusAndDebitCardIdIsNullAndBalance(
                                walletId, ACTIVE, new Decimal128(document.getBalance()), ZERO)
                        .flatMap(modified -> modified == 1
                                ? Mono.just(document.getBalance())
                                : Mono.error(new ConcurrentBalanceChange())));
        // Si un pago cambió el saldo entre la lectura y la actualización, se vuelve a leer.
        return Maybe.fromPublisher(attempt.retryWhen(Retry.max(LINKING_RETRIES)
                .filter(ConcurrentBalanceChange.class::isInstance)));
    }

    @Override
    public Completable finishLinking(String walletId, String debitCardId) {
        return Completable.fromPublisher(
                mongoRepository.findAndSetDebitCardIdById(walletId, debitCardId));
    }

    @Override
    public Completable cancelLinking(String walletId, BigDecimal balance) {
        return Completable.fromPublisher(
                mongoRepository.findAndRestoreBalanceById(walletId, new Decimal128(balance)));
    }

    @Override
    public Completable updateContact(String walletId, String imei, String email) {
        return Completable.fromPublisher(
                mongoRepository.findAndSetContactById(walletId, imei, email));
    }

    @Override
    public Single<Boolean> close(Wallet wallet) {
        Mono<Boolean> closed = mongoRepository.findAndCloseByIdAndStatusAndBalance(
                        wallet.getId(), ACTIVE, new Decimal128(wallet.getBalance()))
                .map(modified -> modified == 1)
                // El celular queda libre: su entrada de caché ya no apunta a un monedero activo.
                .flatMap(done -> done
                        ? phoneCache.evict(wallet.getPhone()).thenReturn(true)
                        : Mono.just(false));
        return Single.fromPublisher(closed);
    }

    @Override
    public Completable releaseDebitCard(String debitCardId) {
        return Completable.fromPublisher(
                mongoRepository.findAndUnsetDebitCardIdByDebitCardId(debitCardId));
    }

    // Monedero activo y sin tarjeta: el único que usa su propio saldo.
    private static boolean isAvailable(WalletDocument document) {
        return ACTIVE.equals(document.getStatus()) && document.getDebitCardId() == null;
    }

    /**
     * Señala que el saldo cambió entre la lectura y la actualización al iniciar la asociación.
     */
    private static final class ConcurrentBalanceChange extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /**
         * Crea la señal sin traza: solo sirve para reintentar.
         */
        ConcurrentBalanceChange() {
            super("El saldo del monedero cambió durante la asociación", null, false, false);
        }
    }

    private static WalletDocument toDocument(Wallet wallet) {
        boolean closed = wallet.isClosed();
        return new WalletDocument(
                wallet.getId(),
                wallet.getDocumentType().name(),
                wallet.getDocumentNumber(),
                closed ? null : wallet.getPhone(),
                wallet.getImei(),
                wallet.getEmail(),
                wallet.getBalance(),
                wallet.getDebitCardId(),
                wallet.getStatus().name(),
                wallet.getCreatedAt(),
                closed ? wallet.getPhone() : null);
    }

    private static Wallet toDomain(WalletDocument document) {
        Wallet wallet = new Wallet(
                DocumentType.valueOf(document.getDocumentType()),
                document.getDocumentNumber(),
                document.getPhone() != null ? document.getPhone() : document.getClosedPhone(),
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
