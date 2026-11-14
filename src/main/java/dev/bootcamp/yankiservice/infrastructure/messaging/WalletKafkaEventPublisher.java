package dev.bootcamp.yankiservice.infrastructure.messaging;

import dev.bootcamp.yankiservice.application.port.WalletEventPublisher;
import dev.bootcamp.yankiservice.domain.model.wallet.Wallet;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Implementa el puerto {@link WalletEventPublisher} publicando en el tópico {@code yanki-events};
 * la clave del mensaje es el id del monedero. El contrato está en {@code docs/events/asyncapi.yaml}
 * del repositorio raíz.
 *
 * <p>Publica {@code WalletCreated} y {@code WalletClosed}. Si Kafka no responde
 * ({@code max.block.ms}, 1 s en config-repo), el error se registra y la operación no falla: el
 * evento se pierde (limitación conocida; evitarla requiere el patrón outbox).
 */
@Slf4j
@Component
public class WalletKafkaEventPublisher implements WalletEventPublisher {

    static final String WALLET_CREATED = "WalletCreated";
    static final String WALLET_CLOSED = "WalletClosed";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final String topic;

    /**
     * Crea el publicador con el tópico configurado.
     */
    public WalletKafkaEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            Clock clock,
            @Value("${messaging.topics.yanki-events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.topic = topic;
    }

    @Override
    public Completable publishWalletCreated(Wallet wallet) {
        return publish(WALLET_CREATED, wallet);
    }

    @Override
    public Completable publishWalletClosed(Wallet wallet) {
        return publish(WALLET_CLOSED, wallet);
    }

    private Completable publish(String eventType, Wallet wallet) {
        return Completable.defer(() -> {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventType", eventType);
            event.put("eventId", UUID.randomUUID().toString());
            event.put("walletId", wallet.getId());
            event.put("phone", wallet.getPhone());
            event.put("occurredAt", Instant.now(clock).toString());
            return Completable.fromCompletionStage(kafkaTemplate.send(
                    topic,
                    wallet.getId(),
                    jsonMapper.writeValueAsString(event)));
        })
                // send() puede bloquear mientras busca al broker: fuera del hilo de eventos.
                .subscribeOn(Schedulers.io())
                .doOnComplete(() -> log.info(
                        "{} published walletId={}", eventType, wallet.getId()))
                .onErrorComplete(error -> {
                    log.error(
                            "{} could not be published walletId={}",
                            eventType,
                            wallet.getId(),
                            error);
                    return true;
                });
    }
}
