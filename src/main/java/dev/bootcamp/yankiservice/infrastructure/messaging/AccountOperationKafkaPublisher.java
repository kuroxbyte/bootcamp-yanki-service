package dev.bootcamp.yankiservice.infrastructure.messaging;

import dev.bootcamp.yankiservice.application.port.AccountOperationPublisher;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Implementa el puerto {@link AccountOperationPublisher} publicando en el tópico
 * {@code yanki-operations}, que consume transaction-service. La clave del mensaje es el id de la
 * cuenta, así las operaciones de una cuenta se procesan en orden. El contrato está en
 * {@code docs/events/asyncapi.yaml} del repositorio raíz.
 *
 * <p>A diferencia de los eventos informativos, el error se propaga: el pago o la asociación
 * deshace lo hecho y responde 503.
 */
@Slf4j
@Component
public class AccountOperationKafkaPublisher implements AccountOperationPublisher {

    static final String DEBIT_REQUESTED = "YankiAccountDebitRequested";
    static final String CREDIT_REQUESTED = "YankiAccountCreditRequested";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final String topic;

    /**
     * Crea el publicador con el tópico configurado.
     */
    public AccountOperationKafkaPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            Clock clock,
            @Value("${messaging.topics.yanki-operations}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.topic = topic;
    }

    @Override
    public Completable requestDebit(
            String operationId,
            String accountId,
            BigDecimal amount,
            LocalDate date) {
        return publish(DEBIT_REQUESTED, operationId, accountId, amount, date, false);
    }

    @Override
    public Completable requestCredit(
            String operationId,
            String accountId,
            BigDecimal amount,
            LocalDate date,
            boolean reversal) {
        return publish(CREDIT_REQUESTED, operationId, accountId, amount, date, reversal);
    }

    private Completable publish(
            String eventType,
            String operationId,
            String accountId,
            BigDecimal amount,
            LocalDate date,
            boolean reversal) {
        return Completable.defer(() -> {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventType", eventType);
            event.put("eventId", UUID.randomUUID().toString());
            event.put("operationId", operationId);
            event.put("accountId", accountId);
            event.put("amount", amount);
            event.put("date", date.toString());
            event.put("reversal", reversal);
            event.put("occurredAt", Instant.now(clock).toString());
            return Completable.fromCompletionStage(kafkaTemplate.send(
                    topic,
                    accountId,
                    jsonMapper.writeValueAsString(event)));
        })
                // send() puede bloquear mientras busca al broker: fuera del hilo de eventos.
                .subscribeOn(Schedulers.io())
                .doOnComplete(() -> log.info(
                        "{} published operationId={}",
                        eventType,
                        operationId))
                .doOnError(error -> log.error(
                        "{} could not be published operationId={}",
                        eventType,
                        operationId,
                        error));
    }
}
