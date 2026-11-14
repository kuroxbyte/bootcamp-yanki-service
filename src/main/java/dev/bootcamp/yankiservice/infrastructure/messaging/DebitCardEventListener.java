package dev.bootcamp.yankiservice.infrastructure.messaging;

import dev.bootcamp.yankiservice.application.usecase.SaveDebitCardUseCase;
import dev.bootcamp.yankiservice.domain.model.debitcard.DebitCard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

/**
 * Consume el tópico {@code debit-card-events} de account-service: guarda cada tarjeta en la copia
 * local (cada evento trae su estado completo) y, con {@code DebitCardClosed}, la quita y la libera
 * del monedero que la tenga. El contrato está en
 * {@code docs/events/asyncapi.yaml} del repositorio raíz.
 *
 * <p>Ignora los tipos de evento que no conoce. Si guardar falla, la excepción hace que Kafka
 * vuelva a entregar el mensaje.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebitCardEventListener {

    private static final String DEBIT_CARD_CLOSED = "DebitCardClosed";
    private static final Set<String> CARD_EVENTS =
            Set.of("DebitCardIssued", "DebitCardAccountsUpdated");

    private final SaveDebitCardUseCase saveDebitCardUseCase;
    private final JsonMapper jsonMapper;

    /**
     * Procesa un evento del tópico de tarjetas de débito.
     *
     * @param message evento en JSON
     */
    @KafkaListener(topics = "${messaging.topics.debit-card-events}")
    public void onDebitCardEvent(String message) {
        JsonNode event = jsonMapper.readTree(message);
        String eventType = event.path("eventType").asString();
        if (DEBIT_CARD_CLOSED.equals(eventType)) {
            String debitCardId = event.get("debitCardId").asString();
            saveDebitCardUseCase.close(debitCardId).blockingAwait();
            log.info("DebitCardClosed processed debitCardId={}", debitCardId);
            return;
        }
        if (!CARD_EVENTS.contains(eventType)) {
            log.debug("Ignoring debit card event type={}", eventType);
            return;
        }
        DebitCard card = new DebitCard(
                event.get("debitCardId").asString(),
                event.get("customerId").asString(),
                event.get("primaryAccountId").asString());
        saveDebitCardUseCase.execute(card).blockingAwait();
        log.info("{} saved debitCardId={}", eventType, card.id());
    }
}
