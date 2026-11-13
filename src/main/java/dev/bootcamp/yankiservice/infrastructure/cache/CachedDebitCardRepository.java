package dev.bootcamp.yankiservice.infrastructure.cache;

import dev.bootcamp.yankiservice.application.port.DebitCardRepository;
import dev.bootcamp.yankiservice.domain.model.debitcard.DebitCard;
import dev.bootcamp.yankiservice.infrastructure.persistence.DebitCardDocument;
import dev.bootcamp.yankiservice.infrastructure.persistence.DebitCardMongoRepository;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

/**
 * Implementa el puerto {@link DebitCardRepository} con MongoDB como almacén y Redis como caché
 * (cache-aside).
 *
 * <ul>
 *   <li>Guardar: escribe en MongoDB y después en Redis ({@code debit-card:{id}}).
 *   <li>Buscar: lee de Redis; si no está, lee de MongoDB y lo deja en Redis.
 *   <li>Cada entrada vence según {@code cache.debit-cards.ttl}.
 *   <li>Si Redis falla, se registra y se sigue con MongoDB: la caché solo acelera el acceso.
 * </ul>
 */
@Slf4j
@Component
public class CachedDebitCardRepository implements DebitCardRepository {

    private static final String KEY_PREFIX = "debit-card:";

    private final DebitCardMongoRepository mongoRepository;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final Duration ttl;

    /**
     * Crea el repositorio con el tiempo de vida configurado para la caché.
     */
    public CachedDebitCardRepository(
            DebitCardMongoRepository mongoRepository,
            ReactiveStringRedisTemplate redisTemplate,
            JsonMapper jsonMapper,
            @Value("${cache.debit-cards.ttl}") Duration ttl) {
        this.mongoRepository = mongoRepository;
        this.redisTemplate = redisTemplate;
        this.jsonMapper = jsonMapper;
        this.ttl = ttl;
    }

    @Override
    public Completable save(DebitCard debitCard) {
        return Completable.fromPublisher(mongoRepository
                .save(new DebitCardDocument(
                        debitCard.id(),
                        debitCard.customerId(),
                        debitCard.primaryAccountId()))
                .then(cache(debitCard)));
    }

    @Override
    public Maybe<DebitCard> findById(String debitCardId) {
        Mono<DebitCard> fromDatabase = mongoRepository.findById(debitCardId)
                .map(document -> new DebitCard(
                        document.getId(),
                        document.getCustomerId(),
                        document.getPrimaryAccountId()))
                .flatMap(card -> cache(card).thenReturn(card));
        return Maybe.fromPublisher(cached(debitCardId).switchIfEmpty(fromDatabase));
    }

    private Mono<DebitCard> cached(String debitCardId) {
        return redisTemplate.opsForValue()
                .get(KEY_PREFIX + debitCardId)
                .map(json -> jsonMapper.readValue(json, DebitCard.class))
                .onErrorResume(error -> {
                    log.warn("Redis read failed debitCardId={}", debitCardId, error);
                    return Mono.empty();
                });
    }

    private Mono<Void> cache(DebitCard debitCard) {
        return redisTemplate.opsForValue()
                .set(KEY_PREFIX + debitCard.id(), jsonMapper.writeValueAsString(debitCard), ttl)
                .then()
                .onErrorResume(error -> {
                    log.warn("Redis write failed debitCardId={}", debitCard.id(), error);
                    return Mono.empty();
                });
    }
}
