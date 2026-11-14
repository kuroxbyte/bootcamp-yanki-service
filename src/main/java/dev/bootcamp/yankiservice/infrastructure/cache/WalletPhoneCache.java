package dev.bootcamp.yankiservice.infrastructure.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Caché en Redis del id del monedero de cada celular ({@code wallet-phone:{celular}}). Cada pago
 * busca el monedero destino por su celular; el celular de un monedero no cambia, así que la
 * entrada no se invalida, solo vence según {@code cache.wallet-phones.ttl}.
 *
 * <p>Si Redis falla, se registra y se responde vacío: el llamador sigue con MongoDB.
 */
@Slf4j
@Component
public class WalletPhoneCache {

    private static final String KEY_PREFIX = "wallet-phone:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final Duration ttl;

    /**
     * Crea la caché con el tiempo de vida configurado.
     */
    public WalletPhoneCache(
            ReactiveStringRedisTemplate redisTemplate,
            @Value("${cache.wallet-phones.ttl}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    /**
     * Id del monedero del celular, o vacío si no está en caché.
     */
    public Mono<String> walletId(String phone) {
        return redisTemplate.opsForValue()
                .get(KEY_PREFIX + phone)
                .onErrorResume(error -> {
                    log.warn("Redis read failed phone={}", phone, error);
                    return Mono.empty();
                });
    }

    /**
     * Quita el celular de la caché (el monedero se dio de baja).
     */
    public Mono<Void> evict(String phone) {
        return redisTemplate.delete(KEY_PREFIX + phone)
                .then()
                .onErrorResume(error -> {
                    log.warn("Redis delete failed phone={}", phone, error);
                    return Mono.empty();
                });
    }

    /**
     * Guarda el id del monedero del celular.
     */
    public Mono<Void> put(String phone, String walletId) {
        return redisTemplate.opsForValue()
                .set(KEY_PREFIX + phone, walletId, ttl)
                .then()
                .onErrorResume(error -> {
                    log.warn("Redis write failed phone={}", phone, error);
                    return Mono.empty();
                });
    }
}
