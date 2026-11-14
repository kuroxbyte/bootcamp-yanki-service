package dev.bootcamp.yankiservice.infrastructure.messaging;

import dev.bootcamp.yankiservice.domain.model.wallet.DocumentType;
import dev.bootcamp.yankiservice.domain.model.wallet.Wallet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YankiKafkaPublishersTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 15);

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneOffset.UTC);

    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private JsonNode sent(String topic, String key) {
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(topic), eq(key), json.capture());
        return jsonMapper.readTree(json.getValue());
    }

    @Test
    void publishesWalletCreatedAndSwallowsKafkaErrors() {
        WalletKafkaEventPublisher publisher =
                new WalletKafkaEventPublisher(kafkaTemplate, jsonMapper, clock, "yanki-events");
        Wallet wallet = new Wallet(
                DocumentType.DNI, "12345678", "987654321", "490154203237518", "a@b.pe");
        wallet.setId("w1");
        when(kafkaTemplate.send(eq("yanki-events"), eq("w1"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("down")));

        publisher.publishWalletCreated(wallet).test().awaitDone(2, TimeUnit.SECONDS)
                .assertComplete();
        publisher.publishWalletCreated(wallet).test().awaitDone(2, TimeUnit.SECONDS)
                .assertComplete();

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(2))
                .send(eq("yanki-events"), eq("w1"), json.capture());
        JsonNode event = jsonMapper.readTree(json.getAllValues().get(0));
        assertThat(event.get("eventType").asString()).isEqualTo("WalletCreated");
        assertThat(event.get("eventId").asString()).hasSize(36);
        assertThat(event.get("phone").asString()).isEqualTo("987654321");
        assertThat(event.get("occurredAt").asString()).isEqualTo("2026-11-15T10:00:00Z");
    }

    @Test
    void requestsDebitWithTheAccountAsKey() {
        AccountOperationKafkaPublisher publisher = new AccountOperationKafkaPublisher(
                kafkaTemplate, jsonMapper, clock, "yanki-operations");
        when(kafkaTemplate.send(eq("yanki-operations"), eq("a1"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.requestDebit("payment:p1:debit", "a1", new BigDecimal("30"), DATE).test()
                .awaitDone(2, TimeUnit.SECONDS).assertComplete();

        JsonNode event = sent("yanki-operations", "a1");
        assertThat(event.get("eventType").asString()).isEqualTo("YankiAccountDebitRequested");
        assertThat(event.get("operationId").asString()).isEqualTo("payment:p1:debit");
        assertThat(event.get("amount").decimalValue()).isEqualByComparingTo("30");
        assertThat(event.get("date").asString()).isEqualTo("2026-11-15");
        assertThat(event.get("reversal").asBoolean()).isFalse();
    }

    @Test
    void requestsReversalCreditAndPropagatesKafkaErrors() {
        AccountOperationKafkaPublisher publisher = new AccountOperationKafkaPublisher(
                kafkaTemplate, jsonMapper, clock, "yanki-operations");
        when(kafkaTemplate.send(eq("yanki-operations"), eq("a1"), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("down")));

        publisher.requestCredit("payment:p1:refund", "a1", BigDecimal.TEN, DATE, true).test()
                .awaitDone(2, TimeUnit.SECONDS).assertError(IllegalStateException.class);

        JsonNode event = sent("yanki-operations", "a1");
        assertThat(event.get("eventType").asString()).isEqualTo("YankiAccountCreditRequested");
        assertThat(event.get("reversal").asBoolean()).isTrue();
    }

    @Test
    void publishesWalletClosed() {
        WalletKafkaEventPublisher publisher =
                new WalletKafkaEventPublisher(kafkaTemplate, jsonMapper, clock, "yanki-events");
        Wallet wallet = new Wallet(
                DocumentType.DNI, "12345678", "987654321", "490154203237518", "a@b.pe");
        wallet.setId("w1");
        when(kafkaTemplate.send(eq("yanki-events"), eq("w1"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishWalletClosed(wallet).test().awaitDone(2, TimeUnit.SECONDS)
                .assertComplete();

        JsonNode event = sent("yanki-events", "w1");
        assertThat(event.get("eventType").asString()).isEqualTo("WalletClosed");
        assertThat(event.get("phone").asString()).isEqualTo("987654321");
    }
}
