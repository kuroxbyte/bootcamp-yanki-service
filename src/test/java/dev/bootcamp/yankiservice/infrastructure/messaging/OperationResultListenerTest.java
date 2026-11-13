package dev.bootcamp.yankiservice.infrastructure.messaging;

import dev.bootcamp.yankiservice.application.usecase.HandleOperationResultUseCase;
import io.reactivex.rxjava3.core.Completable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationResultListenerTest {

    @Mock private HandleOperationResultUseCase useCase;

    private OperationResultListener listener;

    @BeforeEach
    void createListener() {
        listener = new OperationResultListener(useCase, JsonMapper.builder().build());
    }

    @Test
    void appliesCompletedAndRejectedResults() {
        when(useCase.execute("payment:p1:debit", true, null)).thenReturn(Completable.complete());
        when(useCase.execute("payment:p2:debit", false, "Saldo insuficiente"))
                .thenReturn(Completable.complete());

        listener.onOperationResult(
                "{\"eventType\":\"YankiOperationCompleted\",\"operationId\":\"payment:p1:debit\"}");
        listener.onOperationResult("{\"eventType\":\"YankiOperationRejected\","
                + "\"operationId\":\"payment:p2:debit\",\"reason\":\"Saldo insuficiente\"}");

        verify(useCase).execute("payment:p1:debit", true, null);
        verify(useCase).execute("payment:p2:debit", false, "Saldo insuficiente");
    }

    @Test
    void treatsNullReasonAsMissing() {
        when(useCase.execute("link:l1:transfer", true, null)).thenReturn(Completable.complete());

        listener.onOperationResult("{\"eventType\":\"YankiOperationCompleted\","
                + "\"operationId\":\"link:l1:transfer\",\"reason\":null}");

        verify(useCase).execute("link:l1:transfer", true, null);
    }

    @Test
    void ignoresUnknownEvents() {
        listener.onOperationResult("{\"eventType\":\"Other\",\"operationId\":\"x\"}");

        verify(useCase, never()).execute(any(), anyBoolean(), any());
    }
}
