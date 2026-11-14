package dev.bootcamp.yankiservice.infrastructure.persistence;

import dev.bootcamp.yankiservice.domain.model.wallet.DocumentType;
import dev.bootcamp.yankiservice.domain.model.wallet.Wallet;
import dev.bootcamp.yankiservice.domain.model.wallet.WalletStatus;
import dev.bootcamp.yankiservice.infrastructure.cache.WalletPhoneCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@DataMongoTest(properties = "spring.data.mongodb.auto-index-creation=true")
@Import(WalletPersistenceAdapter.class)
class WalletPersistenceAdapterTest {

    private static final String IMEI = "490154203237518";

    @Container @ServiceConnection static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Autowired private WalletPersistenceAdapter adapter;

    @Autowired private WalletMongoRepository mongoRepository;

    @Autowired private ReactiveMongoTemplate mongoTemplate;

    @MockitoBean private WalletPhoneCache phoneCache;

    @BeforeEach
    void cleanCollectionAndCreateIndexes() {
        mongoRepository.deleteAll().block();
        // La creación automática de índices es asíncrona: se crean aquí para probarlos.
        new MongoPersistentEntityIndexResolver(mongoTemplate.getConverter().getMappingContext())
                .resolveIndexFor(WalletDocument.class)
                .forEach(index -> mongoTemplate.indexOps(WalletDocument.class)
                        .createIndex(index)
                        .block());
        when(phoneCache.walletId(anyString())).thenReturn(Mono.empty());
        when(phoneCache.put(anyString(), anyString())).thenReturn(Mono.empty());
    }

    private Wallet saved(String phone, String balance) {
        Wallet wallet = new Wallet(DocumentType.DNI, "12345678", phone, IMEI, "a@b.pe");
        wallet.setBalance(new BigDecimal(balance));
        wallet.setCreatedAt(LocalDate.of(2026, 11, 15));
        return adapter.save(wallet).blockingGet();
    }

    private Wallet reload(String walletId) {
        return adapter.findById(walletId).blockingGet();
    }

    @Test
    void savesAndFindsByIdAndPhoneCachingThePhone() {
        Wallet wallet = saved("987654321", "0");

        assertThat(wallet.getId()).isNotBlank();
        assertThat(reload(wallet.getId()).getPhone()).isEqualTo("987654321");
        adapter.findByPhone("987654321").test().awaitDone(5, TimeUnit.SECONDS)
                .assertValue(found -> found.getId().equals(wallet.getId()));
        adapter.findByPhone("900000000").test().awaitDone(5, TimeUnit.SECONDS).assertNoValues();
        verify(phoneCache).put("987654321", wallet.getId());
    }

    @Test
    void findsByPhoneThroughTheCachedId() {
        Wallet wallet = saved("987654321", "0");
        when(phoneCache.walletId("987654321")).thenReturn(Mono.just(wallet.getId()));

        adapter.findByPhone("987654321").test().awaitDone(5, TimeUnit.SECONDS)
                .assertValue(found -> found.getId().equals(wallet.getId()));
    }

    @Test
    void uniquePhoneRejectsSecondWallet() {
        saved("987654321", "0");

        Wallet duplicate = new Wallet(DocumentType.CEX, "X1", "987654321", IMEI, "c@d.pe");
        adapter.save(duplicate).test().awaitDone(5, TimeUnit.SECONDS)
                .assertError(DuplicateKeyException.class);
    }

    @Test
    void debitsOnlyWhenBalanceIsEnough() {
        Wallet wallet = saved("987654321", "50");

        adapter.debitBalance(wallet.getId(), new BigDecimal("30")).test()
                .awaitDone(5, TimeUnit.SECONDS).assertValue(true);
        adapter.debitBalance(wallet.getId(), new BigDecimal("30")).test()
                .awaitDone(5, TimeUnit.SECONDS).assertValue(false);
        assertThat(reload(wallet.getId()).getBalance()).isEqualByComparingTo("20");

        adapter.creditBalance(wallet.getId(), new BigDecimal("5.50")).blockingAwait();
        assertThat(reload(wallet.getId()).getBalance()).isEqualByComparingTo("25.50");
    }

    @Test
    void linkingMovesTheBalanceOutAndFinishesWithTheCard() {
        Wallet wallet = saved("987654321", "80");

        adapter.startLinking(wallet.getId()).test().awaitDone(5, TimeUnit.SECONDS)
                .assertValue(balance -> balance.compareTo(new BigDecimal("80")) == 0);
        Wallet linking = reload(wallet.getId());
        assertThat(linking.getStatus()).isEqualTo(WalletStatus.LINKING);
        assertThat(linking.getBalance()).isEqualByComparingTo("0");
        adapter.startLinking(wallet.getId()).test().awaitDone(5, TimeUnit.SECONDS)
                .assertNoValues();
        adapter.debitBalance(wallet.getId(), BigDecimal.ONE).test()
                .awaitDone(5, TimeUnit.SECONDS).assertValue(false);

        adapter.finishLinking(wallet.getId(), "d1").blockingAwait();
        Wallet linked = reload(wallet.getId());
        assertThat(linked.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(linked.getDebitCardId()).isEqualTo("d1");
        adapter.existsByDebitCardId("d1").test().awaitDone(5, TimeUnit.SECONDS).assertValue(true);
        adapter.startLinking(wallet.getId()).test().awaitDone(5, TimeUnit.SECONDS)
                .assertNoValues();
    }

    @Test
    void cancelledLinkingGivesTheBalanceBack() {
        Wallet wallet = saved("987654321", "80");
        adapter.startLinking(wallet.getId()).blockingGet();

        adapter.cancelLinking(wallet.getId(), new BigDecimal("80")).blockingAwait();

        Wallet restored = reload(wallet.getId());
        assertThat(restored.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(restored.getBalance()).isEqualByComparingTo("80");
        assertThat(restored.hasDebitCard()).isFalse();
        adapter.existsByDebitCardId("d1").test().awaitDone(5, TimeUnit.SECONDS)
                .assertValue(false);
    }

    @Test
    void severalWalletsWithoutCardDoNotCollideOnTheSparseIndex() {
        saved("987654321", "0");
        saved("912345678", "0");

        assertThat(mongoRepository.count().block()).isEqualTo(2);
    }

    @Test
    void updatesContactWithoutTouchingTheBalance() {
        Wallet wallet = saved("987654321", "40");

        adapter.updateContact(wallet.getId(), "356938035643809", "nuevo@mail.com")
                .blockingAwait();

        Wallet reloaded = reload(wallet.getId());
        assertThat(reloaded.getImei()).isEqualTo("356938035643809");
        assertThat(reloaded.getEmail()).isEqualTo("nuevo@mail.com");
        assertThat(reloaded.getBalance()).isEqualByComparingTo("40");
    }

    @Test
    void closesOnlyWithTheExpectedBalanceAndFreesTheCardAndThePhone() {
        when(phoneCache.evict("987654321")).thenReturn(Mono.empty());
        Wallet wallet = saved("987654321", "0");
        adapter.startLinking(wallet.getId()).blockingGet();
        adapter.finishLinking(wallet.getId(), "d1").blockingAwait();
        Wallet linked = reload(wallet.getId());

        linked.setBalance(BigDecimal.ONE);
        assertThat(adapter.close(linked).blockingGet()).isFalse();
        linked.setBalance(BigDecimal.ZERO);
        assertThat(adapter.close(linked).blockingGet()).isTrue();

        Wallet closed = reload(wallet.getId());
        assertThat(closed.isClosed()).isTrue();
        assertThat(closed.getDebitCardId()).isNull();
        // El monedero dado de baja conserva su celular, pero ya no lo ocupa.
        assertThat(closed.getPhone()).isEqualTo("987654321");
        adapter.existsByDebitCardId("d1").test().awaitDone(5, TimeUnit.SECONDS)
                .assertValue(false);
        adapter.findByPhone("987654321").test().awaitDone(5, TimeUnit.SECONDS).assertNoValues();
        verify(phoneCache).evict("987654321");
        assertThat(adapter.close(linked).blockingGet()).isFalse();
    }

    @Test
    void phoneOfClosedWalletCanBeUsedByNewWallet() {
        when(phoneCache.evict(anyString())).thenReturn(Mono.empty());
        Wallet old = saved("987654321", "0");
        adapter.close(old).blockingGet();
        // Otro monedero sin celular (dado de baja) no choca con el índice disperso.
        Wallet other = saved("912345678", "0");
        adapter.close(other).test().awaitDone(5, TimeUnit.SECONDS).assertNoErrors();

        Wallet reused = saved("987654321", "0");

        assertThat(reused.getId()).isNotEqualTo(old.getId());
        adapter.findByPhone("987654321").test().awaitDone(5, TimeUnit.SECONDS)
                .assertValue(found -> found.getId().equals(reused.getId()));
    }

    @Test
    void cachedIdOfClosedWalletIsIgnored() {
        when(phoneCache.evict("987654321")).thenReturn(Mono.empty());
        Wallet old = saved("987654321", "0");
        adapter.close(old).blockingGet();
        Wallet reused = saved("987654321", "0");
        // La caché todavía apunta al monedero dado de baja.
        when(phoneCache.walletId("987654321")).thenReturn(Mono.just(old.getId()));

        adapter.findByPhone("987654321").test().awaitDone(5, TimeUnit.SECONDS)
                .assertValue(found -> found.getId().equals(reused.getId()));
    }

    @Test
    void savingClosedWalletKeepsThePhoneAside() {
        Wallet wallet = new Wallet(DocumentType.DNI, "12345678", "987654321", IMEI, "a@b.pe");
        wallet.setStatus(WalletStatus.CLOSED);
        wallet.setCreatedAt(LocalDate.of(2026, 11, 15));

        Wallet saved = adapter.save(wallet).blockingGet();

        assertThat(saved.getPhone()).isEqualTo("987654321");
        adapter.findByPhone("987654321").test().awaitDone(5, TimeUnit.SECONDS).assertNoValues();
    }

    @Test
    void releasesTheCardOfTheWalletThatHasIt() {
        Wallet wallet = saved("987654321", "0");
        adapter.startLinking(wallet.getId()).blockingGet();
        adapter.finishLinking(wallet.getId(), "d1").blockingAwait();

        adapter.releaseDebitCard("d1").blockingAwait();
        adapter.releaseDebitCard("d9").blockingAwait();

        assertThat(reload(wallet.getId()).getDebitCardId()).isNull();
    }
}
