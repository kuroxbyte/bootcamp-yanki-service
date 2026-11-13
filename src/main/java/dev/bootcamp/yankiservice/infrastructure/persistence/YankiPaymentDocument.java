package dev.bootcamp.yankiservice.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Pago Yanki tal como se guarda en la colección {@code yanki_payments}.
 */
@Getter
@AllArgsConstructor
@Document(collection = "yanki_payments")
public class YankiPaymentDocument {
    @Id private final String id;
    @Indexed private final String sourceWalletId;
    private final String sourcePhone;
    private final String sourceAccountId;
    @Indexed private final String targetWalletId;
    private final String targetPhone;
    private final String targetAccountId;

    @Field(targetType = FieldType.DECIMAL128)
    private final BigDecimal amount;

    private final LocalDate date;

    /**
     * PENDING, COMPLETED o REJECTED.
     */
    private final String status;

    /**
     * Paso de la saga en curso (DONE al terminar).
     */
    private final String step;

    private final String rejectionReason;
}
