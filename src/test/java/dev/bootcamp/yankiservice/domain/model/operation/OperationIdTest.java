package dev.bootcamp.yankiservice.domain.model.operation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationIdTest {

    @Test
    void writesAndParsesPaymentAndLinkOperations() {
        OperationId debit = OperationId.payment("p1", OperationId.DEBIT);
        assertThat(debit.toString()).isEqualTo("payment:p1:debit");
        assertThat(OperationId.parse("payment:p1:debit")).isEqualTo(debit);

        OperationId transfer = OperationId.linkTransfer("l1");
        assertThat(transfer.toString()).isEqualTo("link:l1:transfer");
        assertThat(OperationId.parse(transfer.toString()).kind()).isEqualTo(OperationId.LINK);
    }

    @Test
    void rejectsMalformedId() {
        assertThatThrownBy(() -> OperationId.parse("payment:p1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
