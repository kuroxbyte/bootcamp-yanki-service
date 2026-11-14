package dev.bootcamp.yankiservice.infrastructure.messaging;

import dev.bootcamp.yankiservice.application.usecase.SaveDebitCardUseCase;
import dev.bootcamp.yankiservice.domain.model.debitcard.DebitCard;
import io.reactivex.rxjava3.core.Completable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DebitCardEventListenerTest {

    @Mock private SaveDebitCardUseCase saveDebitCardUseCase;

    private DebitCardEventListener listener;

    @BeforeEach
    void createListener() {
        listener = new DebitCardEventListener(saveDebitCardUseCase, JsonMapper.builder().build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DebitCardIssued", "DebitCardAccountsUpdated"})
    void savesTheCardOfEachEvent(String eventType) {
        when(saveDebitCardUseCase.execute(any())).thenReturn(Completable.complete());

        listener.onDebitCardEvent("{\"eventType\":\"" + eventType + "\",\"eventId\":\"e1\","
                + "\"debitCardId\":\"d1\",\"customerId\":\"c1\",\"primaryAccountId\":\"a2\","
                + "\"accountIds\":[\"a1\",\"a2\"],\"occurredAt\":\"2026-11-15T10:00:00Z\"}");

        verify(saveDebitCardUseCase).execute(new DebitCard("d1", "c1", "a2"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Other"})
    void ignoresUnknownEventTypes(String eventType) {
        listener.onDebitCardEvent("{\"eventType\":\"" + eventType + "\"}");

        verify(saveDebitCardUseCase, never()).execute(any());
    }

    @Test
    void releasesTheClosedCard() {
        when(saveDebitCardUseCase.close("d1")).thenReturn(Completable.complete());

        listener.onDebitCardEvent("{\"eventType\":\"DebitCardClosed\",\"debitCardId\":\"d1\","
                + "\"customerId\":\"c1\",\"primaryAccountId\":\"a1\"}");

        verify(saveDebitCardUseCase).close("d1");
        verify(saveDebitCardUseCase, never()).execute(any());
    }
}
