package dev.bootcamp.yankiservice.infrastructure.cache;

import dev.bootcamp.yankiservice.domain.model.debitcard.DebitCard;
import dev.bootcamp.yankiservice.infrastructure.persistence.DebitCardDocument;
import dev.bootcamp.yankiservice.infrastructure.persistence.DebitCardMongoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CachedDebitCardRepositoryTest {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final DebitCard CARD = new DebitCard("d1", "c1", "a1");
    private static final String JSON =
            "{\"id\":\"d1\",\"customerId\":\"c1\",\"primaryAccountId\":\"a1\"}";

    @Mock private DebitCardMongoRepository mongoRepository;

    @Mock private ReactiveStringRedisTemplate redisTemplate;

    @Mock private ReactiveValueOperations<String, String> valueOperations;

    private CachedDebitCardRepository repository;

    @BeforeEach
    void createRepository() {
        // La prueba de borrado no usa opsForValue().
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        repository = new CachedDebitCardRepository(
                mongoRepository,
                redisTemplate,
                JsonMapper.builder().build(),
                TTL);
    }

    @Test
    void savesInMongoAndCaches() {
        when(mongoRepository.save(any()))
                .thenAnswer(inv -> Mono.just(inv.<DebitCardDocument>getArgument(0)));
        when(valueOperations.set("debit-card:d1", JSON, TTL)).thenReturn(Mono.just(true));

        repository.save(CARD).test().assertComplete();

        verify(valueOperations).set("debit-card:d1", JSON, TTL);
    }

    @Test
    void readsFromCacheWithoutQueryingMongo() {
        when(valueOperations.get("debit-card:d1")).thenReturn(Mono.just(JSON));
        when(mongoRepository.findById("d1")).thenReturn(Mono.empty());

        repository.findById("d1").test().assertValue(CARD);

        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void readsFromMongoOnCacheMissEvenIfRedisFails() {
        when(valueOperations.get("debit-card:d1"))
                .thenReturn(Mono.error(new IllegalStateException("redis down")));
        when(mongoRepository.findById("d1"))
                .thenReturn(Mono.just(new DebitCardDocument("d1", "c1", "a1")));
        when(valueOperations.set(eq("debit-card:d1"), anyString(), eq(TTL)))
                .thenReturn(Mono.error(new IllegalStateException("redis down")));

        repository.findById("d1").test().assertValue(CARD);
    }

    @Test
    void deletesFromMongoAndEvictsTheCacheEvenIfRedisFails() {
        when(mongoRepository.deleteById("d1")).thenReturn(Mono.empty());
        when(redisTemplate.delete("debit-card:d1"))
                .thenReturn(Mono.error(new IllegalStateException("redis down")));

        repository.deleteById("d1").test().awaitDone(1, TimeUnit.SECONDS).assertComplete();

        verify(mongoRepository).deleteById("d1");
    }
}
