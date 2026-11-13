package dev.bootcamp.yankiservice.infrastructure.messaging;

import dev.bootcamp.yankiservice.application.usecase.HandleOperationResultUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consume el tópico {@code yanki-operation-results} de transaction-service: la respuesta a cada
 * cargo o abono pedido a una cuenta. El contrato está en {@code docs/events/asyncapi.yaml} del
 * repositorio raíz.
 *
 * <p>Ignora los tipos de evento que no conoce. Si aplicar la respuesta falla, la excepción hace
 * que Kafka vuelva a entregar el mensaje; una respuesta repetida se ignora.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationResultListener {

    static final String COMPLETED = "YankiOperationCompleted";
    static final String REJECTED = "YankiOperationRejected";

    private final HandleOperationResultUseCase handleOperationResultUseCase;
    private final JsonMapper jsonMapper;

    /**
     * Procesa una respuesta de transaction-service.
     *
     * @param message evento en JSON
     */
    @KafkaListener(topics = "${messaging.topics.yanki-operation-results}")
    public void onOperationResult(String message) {
        JsonNode event = jsonMapper.readTree(message);
        String eventType = event.path("eventType").asString();
        if (!COMPLETED.equals(eventType) && !REJECTED.equals(eventType)) {
            log.debug("Ignoring operation result type={}", eventType);
            return;
        }
        String operationId = event.get("operationId").asString();
        String reason = event.path("reason").isMissingNode() || event.path("reason").isNull()
                ? null
                : event.path("reason").asString();
        handleOperationResultUseCase
                .execute(operationId, COMPLETED.equals(eventType), reason)
                .blockingAwait();
        log.info("{} applied operationId={}", eventType, operationId);
    }
}
