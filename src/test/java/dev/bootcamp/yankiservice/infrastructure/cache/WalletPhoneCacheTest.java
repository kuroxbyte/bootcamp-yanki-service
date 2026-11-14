package dev.bootcamp.yankiservice.infrastructure.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletPhoneCacheTest {

    private static final Duration TTL = Duration.ofMinutes(10);

    @Mock private ReactiveStringRedisTemplate redisTemplate;

    @Mock private ReactiveValueOperations<String, String> valueOperations;

    private WalletPhoneCache cache;

    @BeforeEach
    void createCache() {
        // La prueba de borrado no usa opsForValue().
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cache = new WalletPhoneCache(redisTemplate, TTL);
    }

    @Test
    void readsAndWritesTheWalletIdOfPhone() {
        when(valueOperations.get("wallet-phone:987654321")).thenReturn(Mono.just("w1"));
        when(valueOperations.set("wallet-phone:987654321", "w1", TTL))
                .thenReturn(Mono.just(true));

        StepVerifier.create(cache.walletId("987654321")).expectNext("w1").verifyComplete();
        StepVerifier.create(cache.put("987654321", "w1")).verifyComplete();
    }

    @Test
    void redisFailuresAreIgnored() {
        when(valueOperations.get("wallet-phone:987654321"))
                .thenReturn(Mono.error(new IllegalStateException("redis down")));
        when(valueOperations.set("wallet-phone:987654321", "w1", TTL))
                .thenReturn(Mono.error(new IllegalStateException("redis down")));

        StepVerifier.create(cache.walletId("987654321")).verifyComplete();
        StepVerifier.create(cache.put("987654321", "w1")).verifyComplete();
    }

    @Test
    void evictsThePhoneEvenIfRedisFails() {
        when(redisTemplate.delete("wallet-phone:987654321"))
                .thenReturn(Mono.just(1L), Mono.error(new IllegalStateException("redis down")));

        StepVerifier.create(cache.evict("987654321")).verifyComplete();
        StepVerifier.create(cache.evict("987654321")).verifyComplete();
    }
}
